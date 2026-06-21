package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.BranchMatchResult;
import com.lawoffice.workflow.entity.BranchRecord;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.BranchRecordMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.service.IConditionBranchRuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConditionBranchRuntimeServiceImpl implements IConditionBranchRuntimeService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FLOWABLE_BRANCH_VARIABLE = "branch";

    private record BranchItem(
            String branchId,
            String branchName,
            int priority,
            String targetNodeId,
            boolean defaultBranch,
            String logic,
            JsonNode raw,
            JsonNode conditions) {
    }

    private final BranchRecordMapper branchRecordMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserRoleMapper userRoleMapper;

    public ConditionBranchRuntimeServiceImpl(BranchRecordMapper branchRecordMapper,
            OperationRecordMapper operationRecordMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            UserDepartMapper userDepartMapper,
            UserRoleMapper userRoleMapper) {
        this.branchRecordMapper = branchRecordMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.userDepartMapper = userDepartMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public Optional<BranchMatchResult> matchNextBranch(ProcessModel model, ProcessInstance processInstance,
            FormInstance formInstance, String sourceNodeId, Task task, String tenantId, RequestContext context) {
        Optional<BranchMatchResult> matchResult = resolveNextBranch(model, processInstance, formInstance, sourceNodeId, tenantId);
        matchResult.ifPresent(result -> {
            writeBranchRecord(result, processInstance, formInstance, task, tenantId, context);
            writeOperationRecord(result, processInstance, formInstance, task, tenantId, context);
        });
        return matchResult;
    }

    @Override
    public Optional<BranchMatchResult> previewNextBranch(ProcessModel model, ProcessInstance processInstance,
            FormInstance formInstance, String sourceNodeId, Task task, String tenantId, RequestContext context) {
        return resolveNextBranch(model, processInstance, formInstance, sourceNodeId, tenantId);
    }

    @Override
    public Map<String, Object> buildFlowableVariables(Optional<BranchMatchResult> matchResult) {
        if (matchResult.isEmpty()) {
            return Map.of();
        }
        return Map.of(FLOWABLE_BRANCH_VARIABLE, matchResult.get().getBranchId());
    }

    /**
     * 条件分支命中计算必须可复用：预判弹窗不落库，真正流转时再写分支和操作记录。
     */
    private Optional<BranchMatchResult> resolveNextBranch(ProcessModel model, ProcessInstance processInstance,
            FormInstance formInstance, String sourceNodeId, String tenantId) {
        Optional<String> gatewayNodeId = findDirectExclusiveGateway(model.getBpmnXml(), sourceNodeId);
        if (gatewayNodeId.isEmpty()) {
            return Optional.empty();
        }
        ProcessNodeConfig gatewayConfig = requireGatewayConfig(model.getId(), gatewayNodeId.get(), tenantId);
        JsonNode formData = parseJson(formInstance.getFormDataJson(), "表单实例数据");
        BranchItem matchedBranch = matchBranch(gatewayConfig, processInstance, formData, tenantId);
        ProcessNodeConfig targetNode = requireTargetNodeConfig(model.getId(), matchedBranch.targetNodeId(), tenantId);
        BranchMatchResult result = buildMatchResult(gatewayConfig, matchedBranch, targetNode);
        return Optional.of(result);
    }

    private ProcessNodeConfig requireGatewayConfig(String processModelId, String gatewayNodeId, String tenantId) {
        ProcessNodeConfig config = processNodeConfigMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("node_id", gatewayNodeId)
                .eq("node_type", WorkflowConstants.NodeType.GATEWAY)
                .eq("delete_flag", 0));
        if (config == null || !StringUtils.hasText(config.getBranchJson())) {
            throw new IllegalArgumentException("排他网关缺少条件分支配置: " + gatewayNodeId);
        }
        return config;
    }

    private ProcessNodeConfig requireTargetNodeConfig(String processModelId, String targetNodeId, String tenantId) {
        ProcessNodeConfig config = processNodeConfigMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("node_id", targetNodeId)
                .eq("delete_flag", 0));
        if (config == null) {
            throw new IllegalArgumentException("条件分支目标节点不存在: " + targetNodeId);
        }
        return config;
    }

    private BranchItem matchBranch(ProcessNodeConfig gatewayConfig, ProcessInstance processInstance,
            JsonNode formData, String tenantId) {
        List<BranchItem> branches = parseBranches(gatewayConfig);
        Optional<BranchItem> defaultBranch = branches.stream().filter(BranchItem::defaultBranch).findFirst();
        Optional<BranchItem> matched = branches.stream()
                .filter(branch -> !branch.defaultBranch())
                .sorted(Comparator.comparingInt(BranchItem::priority))
                .filter(branch -> matchesBranch(branch, processInstance, formData, tenantId))
                .findFirst();
        return matched.orElseGet(() -> defaultBranch
                .orElseThrow(() -> new IllegalArgumentException("条件分支缺少默认分支: " + gatewayConfig.getNodeName())));
    }

    private List<BranchItem> parseBranches(ProcessNodeConfig gatewayConfig) {
        JsonNode root = parseJson(gatewayConfig.getBranchJson(), "条件分支配置");
        JsonNode branchesNode = root.get("branches");
        if (branchesNode == null || !branchesNode.isArray() || branchesNode.isEmpty()) {
            throw new IllegalArgumentException("条件分支配置必须包含branches数组: " + gatewayConfig.getNodeName());
        }
        List<BranchItem> branches = new ArrayList<>();
        for (JsonNode branch : branchesNode) {
            branches.add(new BranchItem(
                    branch.path("branchId").asText(),
                    branch.path("branchName").asText(branch.path("branchId").asText()),
                    branch.path("priority").asInt(999),
                    branch.path("targetNodeId").asText(),
                    branch.path("defaultBranch").asBoolean(false),
                    branch.path("logic").asText("and"),
                    branch,
                    branch.path("conditions")));
        }
        return branches;
    }

    private boolean matchesBranch(BranchItem branch, ProcessInstance processInstance, JsonNode formData, String tenantId) {
        if (branch.conditions() == null || !branch.conditions().isArray() || branch.conditions().isEmpty()) {
            return false;
        }
        boolean useOrLogic = "or".equalsIgnoreCase(branch.logic());
        boolean matchedAny = false;
        for (JsonNode condition : branch.conditions()) {
            boolean matched = matchesCondition(condition, processInstance, formData, tenantId);
            if (useOrLogic && matched) {
                return true;
            }
            if (!useOrLogic && !matched) {
                return false;
            }
            matchedAny = matchedAny || matched;
        }
        return !useOrLogic || matchedAny;
    }

    private boolean matchesCondition(JsonNode condition, ProcessInstance processInstance, JsonNode formData, String tenantId) {
        JsonNode actualValue = resolveActualValue(condition, processInstance, formData, tenantId);
        String operator = condition.path("operator").asText();
        JsonNode expectedValue = condition.get("value");
        return switch (operator) {
            case "eq" -> compare(actualValue, expectedValue, condition) == 0;
            case "ne" -> compare(actualValue, expectedValue, condition) != 0;
            case "contains" -> contains(actualValue, expectedValue);
            case "not_contains" -> !contains(actualValue, expectedValue);
            case "empty" -> isEmptyValue(actualValue);
            case "not_empty" -> !isEmptyValue(actualValue);
            case "gt" -> compare(actualValue, expectedValue, condition) > 0;
            case "ge" -> compare(actualValue, expectedValue, condition) >= 0;
            case "lt" -> compare(actualValue, expectedValue, condition) < 0;
            case "le" -> compare(actualValue, expectedValue, condition) <= 0;
            case "between" -> between(actualValue, expectedValue, condition);
            case "in" -> expectedValues(expectedValue).contains(asText(actualValue));
            case "not_in" -> !expectedValues(expectedValue).contains(asText(actualValue));
            case "contains_any" -> actualValues(actualValue).stream().anyMatch(expectedValues(expectedValue)::contains);
            case "contains_all" -> actualValues(actualValue).containsAll(expectedValues(expectedValue));
            case "is_true" -> asBoolean(actualValue);
            case "is_false" -> !asBoolean(actualValue);
            default -> throw new IllegalArgumentException("不支持的条件操作符: " + operator);
        };
    }

    private JsonNode resolveActualValue(JsonNode condition, ProcessInstance processInstance, JsonNode formData, String tenantId) {
        String sourceType = condition.path("sourceType").asText();
        String fieldKey = condition.path("fieldKey").asText(null);
        return switch (sourceType) {
            case "form_field" -> StringUtils.hasText(fieldKey) ? formData.path(fieldKey) : OBJECT_MAPPER.nullNode();
            case "starter" -> resolveStarterValue(processInstance, fieldKey);
            case "starter_depart" -> OBJECT_MAPPER.valueToTree(resolveStarterDepartIds(processInstance, tenantId));
            case "starter_role" -> OBJECT_MAPPER.valueToTree(resolveStarterRoleIds(processInstance, tenantId));
            case "instance" -> resolveInstanceValue(processInstance, fieldKey);
            default -> throw new IllegalArgumentException("不支持的条件来源类型: " + sourceType);
        };
    }

    private JsonNode resolveStarterValue(ProcessInstance processInstance, String fieldKey) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("id", processInstance.getStarterUserId());
        values.put("userId", processInstance.getStarterUserId());
        values.put("username", processInstance.getStarterUsername());
        values.put("realname", processInstance.getStarterRealname());
        return OBJECT_MAPPER.valueToTree(values.getOrDefault(StringUtils.hasText(fieldKey) ? fieldKey : "id",
                processInstance.getStarterUserId()));
    }

    private JsonNode resolveInstanceValue(ProcessInstance processInstance, String fieldKey) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("id", processInstance.getId());
        values.put("instanceNo", processInstance.getInstanceNo());
        values.put("businessKey", processInstance.getBusinessKey());
        values.put("status", processInstance.getStatus());
        values.put("processModelId", processInstance.getProcessModelId());
        values.put("formDefinitionId", processInstance.getFormDefinitionId());
        values.put("starterUserId", processInstance.getStarterUserId());
        return OBJECT_MAPPER.valueToTree(values.get(StringUtils.hasText(fieldKey) ? fieldKey : "id"));
    }

    private List<String> resolveStarterDepartIds(ProcessInstance processInstance, String tenantId) {
        return userDepartMapper.selectList(new QueryWrapper<UserDepart>()
                        .select("dep_id")
                        .eq("tenant_id", tenantId)
                        .eq("user_id", processInstance.getStarterUserId())
                        .eq("delete_flag", 0))
                .stream()
                .map(UserDepart::getDepId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> resolveStarterRoleIds(ProcessInstance processInstance, String tenantId) {
        return userRoleMapper.selectList(new QueryWrapper<UserRole>()
                        .select("role_id")
                        .eq("tenant_id", tenantId)
                        .eq("user_id", processInstance.getStarterUserId())
                        .eq("delete_flag", 0))
                .stream()
                .map(UserRole::getRoleId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private int compare(JsonNode actualValue, JsonNode expectedValue, JsonNode condition) {
        if ("number".equals(condition.path("valueType").asText())) {
            return toDecimal(actualValue).compareTo(toDecimal(expectedValue));
        }
        return asText(actualValue).compareTo(asText(expectedValue));
    }

    private boolean between(JsonNode actualValue, JsonNode expectedValue, JsonNode condition) {
        List<String> values = expectedValues(expectedValue);
        if (values.size() < 2) {
            return false;
        }
        if ("number".equals(condition.path("valueType").asText())) {
            BigDecimal actual = toDecimal(actualValue);
            return actual.compareTo(new BigDecimal(values.get(0))) >= 0
                    && actual.compareTo(new BigDecimal(values.get(1))) <= 0;
        }
        String actual = asText(actualValue);
        return actual.compareTo(values.get(0)) >= 0 && actual.compareTo(values.get(1)) <= 0;
    }

    private boolean contains(JsonNode actualValue, JsonNode expectedValue) {
        if (actualValue != null && actualValue.isArray()) {
            return actualValues(actualValue).contains(asText(expectedValue));
        }
        return asText(actualValue).contains(asText(expectedValue));
    }

    private BigDecimal toDecimal(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return BigDecimal.ZERO;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = asText(value);
        return StringUtils.hasText(text) ? new BigDecimal(text) : BigDecimal.ZERO;
    }

    private boolean asBoolean(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText());
    }

    private boolean isEmptyValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return true;
        }
        if (value.isArray() || value.isObject()) {
            return value.isEmpty();
        }
        return !StringUtils.hasText(value.asText());
    }

    private String asText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private List<String> actualValues(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(asText(item)));
            return values;
        }
        return List.of(asText(value));
    }

    private List<String> expectedValues(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(asText(item)));
            return values;
        }
        String text = asText(value);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Optional<String> findDirectExclusiveGateway(String bpmnXml, String sourceNodeId) {
        if (!StringUtils.hasText(bpmnXml) || !StringUtils.hasText(sourceNodeId)) {
            return Optional.empty();
        }
        try {
            Document document = parseBpmnDocument(bpmnXml);
            String normalizedSourceNodeId = normalizeSourceNodeId(document, sourceNodeId);
            Map<String, String> elementTypeById = new LinkedHashMap<>();
            collectElementIds(document, elementTypeById, "exclusiveGateway");
            NodeList sequenceFlows = document.getElementsByTagNameNS("*", "sequenceFlow");
            for (int i = 0; i < sequenceFlows.getLength(); i++) {
                Element flow = (Element) sequenceFlows.item(i);
                if (!normalizedSourceNodeId.equals(flow.getAttribute("sourceRef"))) {
                    continue;
                }
                String targetRef = flow.getAttribute("targetRef");
                if ("exclusiveGateway".equals(elementTypeById.get(targetRef))) {
                    return Optional.of(targetRef);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalArgumentException("BPMN XML解析失败");
        }
    }

    private String normalizeSourceNodeId(Document document, String sourceNodeId) {
        if (!WorkflowConstants.VirtualNode.START.equals(sourceNodeId)) {
            return sourceNodeId;
        }
        NodeList startEvents = document.getElementsByTagNameNS("*", "startEvent");
        if (startEvents.getLength() == 0) {
            return sourceNodeId;
        }
        String startEventId = ((Element) startEvents.item(0)).getAttribute("id");
        return StringUtils.hasText(startEventId) ? startEventId : sourceNodeId;
    }

    private void collectElementIds(Document document, Map<String, String> elementTypeById, String elementName) {
        NodeList elements = document.getElementsByTagNameNS("*", elementName);
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String id = element.getAttribute("id");
            if (StringUtils.hasText(id)) {
                elementTypeById.put(id, elementName);
            }
        }
    }

    private BranchMatchResult buildMatchResult(ProcessNodeConfig gatewayConfig, BranchItem branch,
            ProcessNodeConfig targetNode) {
        BranchMatchResult result = new BranchMatchResult();
        result.setGatewayNodeId(gatewayConfig.getNodeId());
        result.setGatewayNodeName(gatewayConfig.getNodeName());
        result.setBranchId(branch.branchId());
        result.setBranchName(branch.branchName());
        result.setTargetNodeId(branch.targetNodeId());
        result.setTargetNodeName(targetNode.getNodeName());
        result.setConditionSnapshotJson(branch.raw().toString());
        return result;
    }

    private void writeBranchRecord(BranchMatchResult result, ProcessInstance processInstance,
            FormInstance formInstance, Task task, String tenantId, RequestContext context) {
        BranchRecord record = new BranchRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setProcessModelId(processInstance.getProcessModelId());
        record.setSourceNodeId(result.getGatewayNodeId());
        record.setSourceNodeName(result.getGatewayNodeName());
        record.setBranchId(result.getBranchId());
        record.setBranchName(result.getBranchName());
        record.setTargetNodeId(result.getTargetNodeId());
        record.setTargetNodeName(result.getTargetNodeName());
        record.setConditionSnapshotJson(result.getConditionSnapshotJson());
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setMatchedTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        branchRecordMapper.insert(record);
    }

    private void writeOperationRecord(BranchMatchResult result, ProcessInstance processInstance,
            FormInstance formInstance, Task task, String tenantId, RequestContext context) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        if (task != null) {
            record.setTaskId(task.getId());
            record.setFlowableTaskId(task.getFlowableTaskId());
        }
        record.setNodeId(result.getGatewayNodeId());
        record.setNodeName(result.getGatewayNodeName());
        record.setAction(WorkflowConstants.Action.BRANCH_MATCH);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(context.getUsername());
        record.setTargetNodeId(result.getTargetNodeId());
        record.setTargetNodeName(result.getTargetNodeName());
        record.setComment("条件分支命中：" + result.getBranchName());
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    private JsonNode parseJson(String json, String fieldName) {
        try {
            return StringUtils.hasText(json) ? OBJECT_MAPPER.readTree(json) : OBJECT_MAPPER.createObjectNode();
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "不是合法JSON");
        }
    }

    private Document parseBpmnDocument(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(bpmnXml)));
    }
}
