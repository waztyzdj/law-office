package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.vo.ProcessNodeConfigVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProcessNodeConfigServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessNodeConfigMapper, ProcessNodeConfig, ProcessNodeConfigVO> implements IProcessNodeConfigService {

    private final ProcessModelMapper processModelMapper;

    @Autowired
    public ProcessNodeConfigServiceImpl(ProcessModelMapper processModelMapper) {
        this.processModelMapper = processModelMapper;
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
}
