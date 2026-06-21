package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.vo.ProcessNodeConfigVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProcessNodeConfigServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessNodeConfigMapper, ProcessNodeConfig, ProcessNodeConfigVO> implements IProcessNodeConfigService {

    private static final Set<String> BRANCH_SOURCE_TYPES = Set.of(
            "form_field",
            "starter",
            "starter_depart",
            "starter_role",
            "instance"
    );
    private static final Set<String> BRANCH_VALUE_TYPES = Set.of(
            "text",
            "number",
            "date",
            "single_select",
            "multi_select",
            "boolean"
    );
    private static final Set<String> BRANCH_OPERATORS = Set.of(
            "eq",
            "ne",
            "contains",
            "not_contains",
            "empty",
            "not_empty",
            "gt",
            "ge",
            "lt",
            "le",
            "between",
            "in",
            "not_in",
            "contains_any",
            "contains_all",
            "is_true",
            "is_false"
    );
    private static final Set<String> BRANCH_LOGICS = Set.of("and", "or");
    private static final Pattern BRANCH_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final ProcessModelMapper processModelMapper;
    private final TaskMapper taskMapper;

    @Autowired
    public ProcessNodeConfigServiceImpl(ProcessModelMapper processModelMapper, TaskMapper taskMapper) {
        this.processModelMapper = processModelMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public ProcessNodeConfig requireRuntimeNodeConfig(String processModelId, String nodeId, String tenantId) {
        ProcessNodeConfig nodeConfig = baseMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("node_id", nodeId)
                .eq("delete_flag", 0));
        if (nodeConfig == null) {
            throw new IllegalArgumentException("流程节点未配置审批人: " + nodeId);
        }
        if (!StringUtils.hasText(nodeConfig.getAssigneeType())) {
            throw new IllegalArgumentException("流程节点审批人类型不能为空: " + nodeConfig.getNodeName());
        }
        return nodeConfig;
    }

    @Override
    public ProcessNodeConfig buildStartDraftNodeConfig() {
        ProcessNodeConfig nodeConfig = new ProcessNodeConfig();
        nodeConfig.setNodeId(WorkflowConstants.VirtualNode.START_DRAFT);
        nodeConfig.setNodeName(WorkflowConstants.VirtualNodeName.START_DRAFT);
        nodeConfig.setNodeType(WorkflowConstants.NodeType.START);
        nodeConfig.setAllowTransfer(0);
        nodeConfig.setAllowReturn(0);
        nodeConfig.setAllowAddSign(0);
        return nodeConfig;
    }

    @Override
    public List<ProcessNodeConfig> listReturnableNodeConfigs(ProcessInstance processInstance,
            ProcessNodeConfig currentNodeConfig, String tenantId) {
        if (!isEnabled(currentNodeConfig.getAllowReturn())) {
            return List.of();
        }
        Integer currentSortOrder = currentNodeConfig.getSortOrder();
        Set<String> visitedNodeIds = listVisitedApproverNodeIds(processInstance, currentNodeConfig.getNodeId(), tenantId);
        if (visitedNodeIds.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectList(new QueryWrapper<ProcessNodeConfig>()
                        .eq("tenant_id", tenantId)
                        .eq("process_model_id", processInstance.getProcessModelId())
                        .eq("node_type", WorkflowConstants.NodeType.APPROVER)
                        .eq("delete_flag", 0)
                        .orderByAsc("sort_order")
                        .orderByAsc("create_time"))
                .stream()
                .filter(nodeConfig -> !currentNodeConfig.getNodeId().equals(nodeConfig.getNodeId()))
                .filter(nodeConfig -> visitedNodeIds.contains(nodeConfig.getNodeId()))
                .filter(nodeConfig -> currentSortOrder == null
                        || (nodeConfig.getSortOrder() != null && nodeConfig.getSortOrder() < currentSortOrder))
                .toList();
    }

    @Override
    public void ensureReturnTargetAllowed(ProcessInstance processInstance, ProcessNodeConfig currentNodeConfig,
            ProcessNodeConfig targetNodeConfig, String tenantId) {
        if (WorkflowConstants.VirtualNode.START_DRAFT.equals(targetNodeConfig.getNodeId())) {
            return;
        }
        boolean allowed = listReturnableNodeConfigs(processInstance, currentNodeConfig, tenantId).stream()
                .anyMatch(nodeConfig -> nodeConfig.getNodeId().equals(targetNodeConfig.getNodeId()));
        if (!allowed) {
            throw new IllegalArgumentException("退回目标节点不允许");
        }
    }

    /**
     * 退回目标必须来自当前实例真实到达过的审批节点，避免条件分支未命中的节点被静态顺序误判为可退回。
     */
    private Set<String> listVisitedApproverNodeIds(ProcessInstance processInstance, String currentNodeId, String tenantId) {
        return taskMapper.selectList(new QueryWrapper<Task>()
                        .select("node_id")
                        .eq("tenant_id", tenantId)
                        .eq("process_instance_id", processInstance.getId())
                        .isNotNull("node_id")
                        .ne("node_id", currentNodeId)
                        .eq("delete_flag", 0)
                        .orderByAsc("create_time"))
                .stream()
                .map(Task::getNodeId)
                .filter(StringUtils::hasText)
                .filter(nodeId -> !WorkflowConstants.VirtualNode.START_DRAFT.equals(nodeId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    protected void doBeforeSave(BaseDTO<ProcessNodeConfig> saveDTO) {
        ProcessNodeConfig config = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(config, saveDTO);
        normalize(config);
        ProcessModel model = requireActiveById(processModelMapper, config.getProcessModelId(), config.getTenantId(), "流程模型不存在");
        rejectIfPublished(model);
        requireText(config.getNodeId(), "节点ID不能为空");
        requireText(config.getNodeName(), "节点名称不能为空");
        validateJson(config.getAssigneeJson(), "审批人配置JSON", false);
        validateJson(config.getBranchJson(), "条件分支配置JSON", false);
        validateJson(config.getCcJson(), "抄送配置JSON", false);
        validateJson(config.getTimeoutJson(), "超时提醒配置JSON", false);
        validateJson(config.getAttachmentJson(), "附件权限配置JSON", false);
        validateDefinitionRules(config);
        validateUnique(config, "同一流程模型下节点ID不能重复",
                "process_model_id", config.getProcessModelId(),
                "node_id", config.getNodeId());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<ProcessNodeConfig> deleteDTO) {
        String tenantId = resolveTenantId(null, deleteDTO.getContext());
        for (String id : resolveDeleteIds(deleteDTO)) {
            ProcessNodeConfig config = requireCurrent(id, tenantId, "流程节点配置不存在");
            ProcessModel model = requireActiveById(processModelMapper, config.getProcessModelId(), tenantId, "流程模型不存在");
            rejectIfPublished(model);
        }
    }

    private void normalize(ProcessNodeConfig config) {
        config.setNodeId(trimToNull(config.getNodeId()));
        config.setNodeName(trimToNull(config.getNodeName()));
        if (!StringUtils.hasText(config.getNodeType())) {
            config.setNodeType(WorkflowConstants.NodeType.APPROVER);
        }
        validateIn(config.getNodeType(), "节点类型不合法",
                WorkflowConstants.NodeType.START,
                WorkflowConstants.NodeType.APPROVER,
                WorkflowConstants.NodeType.GATEWAY,
                WorkflowConstants.NodeType.END);
        if (!StringUtils.hasText(config.getApprovalMode())) {
            config.setApprovalMode(WorkflowConstants.ApprovalMode.SINGLE);
        }
        validateIn(config.getApprovalMode(), "办理策略不合法",
                WorkflowConstants.ApprovalMode.SINGLE,
                WorkflowConstants.ApprovalMode.COUNTERSIGN,
                WorkflowConstants.ApprovalMode.ORSIGN);
        if (WorkflowConstants.ApprovalMode.SINGLE.equals(config.getApprovalMode())) {
            config.setAssigneeResolveMode(WorkflowConstants.AssigneeResolveMode.SELECT);
        } else if (!StringUtils.hasText(config.getAssigneeResolveMode())) {
            config.setAssigneeResolveMode(defaultAssigneeResolveMode(config.getApprovalMode()));
        }
        validateIn(config.getAssigneeResolveMode(), "执行人确定方式不合法",
                WorkflowConstants.AssigneeResolveMode.ALL,
                WorkflowConstants.AssigneeResolveMode.SELECT);
        if (!StringUtils.hasText(config.getRejectPolicy())) {
            config.setRejectPolicy(WorkflowConstants.RejectPolicy.TERMINATE);
        }
        validateIn(config.getRejectPolicy(), "不通过策略不合法",
                WorkflowConstants.RejectPolicy.TERMINATE);
        if (StringUtils.hasText(config.getAssigneeType())) {
            validateIn(config.getAssigneeType(), "审批人类型不合法",
                    WorkflowConstants.AssigneeType.USER,
                    WorkflowConstants.AssigneeType.ROLE,
                    WorkflowConstants.AssigneeType.DEPART_LEADER,
                    WorkflowConstants.AssigneeType.DEPART_ROLE,
                    WorkflowConstants.AssigneeType.STARTER_SUPERVISOR,
                    WorkflowConstants.AssigneeType.STARTER_SELECT,
                    WorkflowConstants.AssigneeType.STARTER);
        }
        if (config.getAllowTransfer() == null) {
            config.setAllowTransfer(1);
        }
        if (config.getAllowAddSign() == null) {
            config.setAllowAddSign(1);
        }
        if (config.getAllowReturn() == null) {
            config.setAllowReturn(1);
        }
        if (config.getSortOrder() == null) {
            config.setSortOrder(0);
        }
    }

    /**
     * 默认值按二期产品语义兜底：单人审批固定上一步选择，会签通常需要圈定实际参与人，或签默认发给全部候选人。
     */
    private String defaultAssigneeResolveMode(String approvalMode) {
        return WorkflowConstants.ApprovalMode.ORSIGN.equals(approvalMode)
                ? WorkflowConstants.AssigneeResolveMode.ALL
                : WorkflowConstants.AssigneeResolveMode.SELECT;
    }

    private void validateDefinitionRules(ProcessNodeConfig config) {
        if (WorkflowConstants.NodeType.APPROVER.equals(config.getNodeType())) {
            requireText(config.getAssigneeType(), "审批节点审批人类型不能为空");
            if (WorkflowConstants.ApprovalMode.COUNTERSIGN.equals(config.getApprovalMode())
                    || WorkflowConstants.ApprovalMode.ORSIGN.equals(config.getApprovalMode())) {
                validateConfiguredAssigneeJson(config);
            }
        } else if (StringUtils.hasText(config.getAssigneeType()) || StringUtils.hasText(config.getAssigneeJson())) {
            throw new IllegalArgumentException("非审批节点不能配置审批人");
        }

        if (WorkflowConstants.NodeType.GATEWAY.equals(config.getNodeType())) {
            validateBranchJson(config.getBranchJson(), true);
        } else {
            validateBranchJson(config.getBranchJson(), false);
        }
        validateCcJson(config.getCcJson());
        validateTimeoutJson(config.getTimeoutJson());
        validateAttachmentJson(config.getAttachmentJson());
    }

    private void validateBranchJson(String branchJson, boolean required) {
        if (!StringUtils.hasText(branchJson)) {
            if (required) {
                throw new IllegalArgumentException("网关节点必须配置条件分支");
            }
            return;
        }
        JsonNode root = parseJson(branchJson, "条件分支配置JSON");
        JsonNode branches = root.get("branches");
        if (branches == null || !branches.isArray() || branches.isEmpty()) {
            throw new IllegalArgumentException("条件分支配置必须包含branches数组");
        }
        Set<String> branchIds = new java.util.HashSet<>();
        boolean hasDefaultBranch = false;
        for (JsonNode branch : branches) {
            requireJsonText(branch, "branchId", "条件分支ID不能为空");
            String branchId = branch.path("branchId").asText();
            if (!BRANCH_ID_PATTERN.matcher(branchId).matches()) {
                throw new IllegalArgumentException("条件分支ID只能包含字母、数字、下划线和短横线");
            }
            if (!branchIds.add(branchId)) {
                throw new IllegalArgumentException("条件分支ID不能重复");
            }
            requireJsonText(branch, "targetNodeId", "条件分支目标节点不能为空");
            boolean defaultBranch = branch.path("defaultBranch").asBoolean(false);
            if (defaultBranch && hasDefaultBranch) {
                throw new IllegalArgumentException("条件分支只能配置一个默认分支");
            }
            hasDefaultBranch = hasDefaultBranch || defaultBranch;
            if (!defaultBranch) {
                validateBranchConditions(branch);
            }
        }
        if (!hasDefaultBranch) {
            throw new IllegalArgumentException("条件分支必须配置默认分支");
        }
    }

    /**
     * 定义侧只允许结构化条件，不允许保存脚本或表达式，运行时据此安全计算命中分支。
     */
    private void validateBranchConditions(JsonNode branch) {
        String logic = branch.path("logic").asText("and");
        if (!BRANCH_LOGICS.contains(logic)) {
            throw new IllegalArgumentException("条件分支logic只允许and或or");
        }
        JsonNode conditions = branch.get("conditions");
        if (conditions == null || !conditions.isArray() || conditions.isEmpty()) {
            throw new IllegalArgumentException("非默认条件分支必须配置conditions数组");
        }
        for (JsonNode condition : conditions) {
            String sourceType = condition.path("sourceType").asText(null);
            String valueType = condition.path("valueType").asText(null);
            String operator = condition.path("operator").asText(null);
            if (!BRANCH_SOURCE_TYPES.contains(sourceType)) {
                throw new IllegalArgumentException("条件来源类型不合法");
            }
            if (!BRANCH_VALUE_TYPES.contains(valueType)) {
                throw new IllegalArgumentException("条件值类型不合法");
            }
            if (!BRANCH_OPERATORS.contains(operator)) {
                throw new IllegalArgumentException("条件操作符不合法");
            }
            if ("form_field".equals(sourceType)) {
                requireJsonText(condition, "fieldKey", "表单字段条件必须配置fieldKey");
            }
            if (requiresCompareValue(operator) && !hasConditionValue(condition)) {
                throw new IllegalArgumentException("条件操作符必须配置比较值");
            }
        }
    }

    private boolean hasConditionValue(JsonNode condition) {
        JsonNode value = condition.get("value");
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return StringUtils.hasText(value.asText());
        }
        if (value.isArray()) {
            return !value.isEmpty();
        }
        return true;
    }

    private boolean requiresCompareValue(String operator) {
        return !Set.of("empty", "not_empty", "is_true", "is_false").contains(operator);
    }

    private void validateConfiguredAssigneeJson(ProcessNodeConfig config) {
        if (WorkflowConstants.AssigneeType.USER.equals(config.getAssigneeType())
                || WorkflowConstants.AssigneeType.ROLE.equals(config.getAssigneeType())
                || WorkflowConstants.AssigneeType.DEPART_ROLE.equals(config.getAssigneeType())) {
            validateJson(config.getAssigneeJson(), "会签/或签审批人配置JSON", true);
        }
    }

    private void validateCcJson(String ccJson) {
        if (!StringUtils.hasText(ccJson)) {
            return;
        }
        JsonNode root = parseJson(ccJson, "抄送配置JSON");
        JsonNode events = root.has("events") ? root.get("events") : root.get("triggerActions");
        if (events != null && !events.isArray()) {
            throw new IllegalArgumentException("抄送触发时机必须为数组");
        }
        JsonNode targets = root.has("targets") ? root.get("targets") : root.get("receivers");
        if (targets != null && !targets.isArray()) {
            throw new IllegalArgumentException("抄送对象必须为数组");
        }
    }

    private void validateTimeoutJson(String timeoutJson) {
        if (!StringUtils.hasText(timeoutJson)) {
            return;
        }
        JsonNode root = parseJson(timeoutJson, "超时提醒配置JSON");
        if (root.has("durationMinutes") && root.path("durationMinutes").asInt(0) < 0) {
            throw new IllegalArgumentException("超时时长不能小于0");
        }
        if (root.has("maxRemindCount") && root.path("maxRemindCount").asInt(0) < 0) {
            throw new IllegalArgumentException("最大提醒次数不能小于0");
        }
    }

    private void validateAttachmentJson(String attachmentJson) {
        if (!StringUtils.hasText(attachmentJson)) {
            return;
        }
        JsonNode root = parseJson(attachmentJson, "附件权限配置JSON");
        if (root.has("allowUpload") && !root.path("allowUpload").isBoolean()) {
            throw new IllegalArgumentException("附件权限allowUpload必须为布尔值");
        }
        if (root.has("allowDelete") && !root.path("allowDelete").isBoolean()) {
            throw new IllegalArgumentException("附件权限allowDelete必须为布尔值");
        }
    }

    private JsonNode parseJson(String json, String fieldName) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "不是合法JSON");
        }
    }

    private void requireJsonText(JsonNode node, String fieldName, String message) {
        if (node == null || !StringUtils.hasText(node.path(fieldName).asText(null))) {
            throw new IllegalArgumentException(message);
        }
    }

    private void rejectIfPublished(ProcessModel model) {
        if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
            throw new IllegalArgumentException("已发布流程版本的节点配置不可修改");
        }
    }

    private boolean isEnabled(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }
}
