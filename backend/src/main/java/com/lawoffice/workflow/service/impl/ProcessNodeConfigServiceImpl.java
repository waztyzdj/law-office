package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.vo.ProcessNodeConfigVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProcessNodeConfigServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessNodeConfigMapper, ProcessNodeConfig, ProcessNodeConfigVO> implements IProcessNodeConfigService {

    private final ProcessModelMapper processModelMapper;

    @Autowired
    public ProcessNodeConfigServiceImpl(ProcessModelMapper processModelMapper) {
        this.processModelMapper = processModelMapper;
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
        return baseMapper.selectList(new QueryWrapper<ProcessNodeConfig>()
                        .eq("tenant_id", tenantId)
                        .eq("process_model_id", processInstance.getProcessModelId())
                        .eq("node_type", WorkflowConstants.NodeType.APPROVER)
                        .eq("delete_flag", 0)
                        .orderByAsc("sort_order")
                        .orderByAsc("create_time"))
                .stream()
                .filter(nodeConfig -> !currentNodeConfig.getNodeId().equals(nodeConfig.getNodeId()))
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
        boolean hasDefaultBranch = false;
        for (JsonNode branch : branches) {
            requireJsonText(branch, "branchId", "条件分支ID不能为空");
            requireJsonText(branch, "targetNodeId", "条件分支目标节点不能为空");
            hasDefaultBranch = hasDefaultBranch || branch.path("defaultBranch").asBoolean(false);
        }
        if (!hasDefaultBranch) {
            throw new IllegalArgumentException("条件分支必须配置默认分支");
        }
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
        JsonNode receivers = root.get("receivers");
        if (receivers != null && (!receivers.isArray() || receivers.isEmpty())) {
            throw new IllegalArgumentException("抄送配置receivers必须为非空数组");
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
