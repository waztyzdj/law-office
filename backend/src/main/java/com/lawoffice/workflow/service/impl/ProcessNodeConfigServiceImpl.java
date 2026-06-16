package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
                WorkflowConstants.NodeType.END);
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

    private void rejectIfPublished(ProcessModel model) {
        if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
            throw new IllegalArgumentException("已发布流程版本的节点配置不可修改");
        }
    }

    private boolean isEnabled(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }
}
