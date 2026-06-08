package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.service.IFieldPermissionService;
import com.lawoffice.workflow.vo.FieldPermissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FieldPermissionServiceImpl extends AbstractWorkflowConfigServiceImpl<FieldPermissionMapper, FieldPermission, FieldPermissionVO> implements IFieldPermissionService {

    private final ProcessModelMapper processModelMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;

    @Autowired
    public FieldPermissionServiceImpl(ProcessModelMapper processModelMapper,
            ProcessNodeConfigMapper processNodeConfigMapper) {
        this.processModelMapper = processModelMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
    }

    @Override
    protected void doBeforeSave(BaseDTO<FieldPermission> saveDTO) {
        FieldPermission permission = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(permission, saveDTO);
        permission.setNodeId(trimToNull(permission.getNodeId()));
        permission.setFieldKey(trimToNull(permission.getFieldKey()));
        if (!org.springframework.util.StringUtils.hasText(permission.getPermission())) {
            permission.setPermission(WorkflowConstants.FieldPermission.READONLY);
        }
        if (permission.getRequiredFlag() == null) {
            permission.setRequiredFlag(0);
        }

        ProcessModel model = requireActiveById(processModelMapper, permission.getProcessModelId(), permission.getTenantId(), "流程模型不存在");
        if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
            throw new IllegalArgumentException("已发布流程版本的字段权限不可修改");
        }
        requireText(permission.getNodeId(), "节点ID不能为空");
        requireText(permission.getFieldKey(), "字段标识不能为空");
        validateIn(permission.getPermission(), "字段权限不合法",
                WorkflowConstants.FieldPermission.HIDDEN,
                WorkflowConstants.FieldPermission.READONLY,
                WorkflowConstants.FieldPermission.EDITABLE);
        if (countActive(processNodeConfigMapper, permission.getTenantId(),
                "process_model_id", permission.getProcessModelId(),
                "node_id", permission.getNodeId()) == 0) {
            throw new IllegalArgumentException("字段权限对应的节点配置不存在");
        }
        validateUnique(permission, "同一流程节点下字段权限不能重复",
                "process_model_id", permission.getProcessModelId(),
                "node_id", permission.getNodeId(),
                "field_key", permission.getFieldKey());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<FieldPermission> deleteDTO) {
        String tenantId = resolveTenantId(null, deleteDTO.getContext());
        for (String id : resolveDeleteIds(deleteDTO)) {
            FieldPermission permission = requireCurrent(id, tenantId, "字段权限不存在");
            ProcessModel model = requireActiveById(processModelMapper, permission.getProcessModelId(), tenantId, "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
                throw new IllegalArgumentException("已发布流程版本的字段权限不可删除");
            }
        }
    }
}
