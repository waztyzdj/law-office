package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.service.IProcessStartPermissionService;
import com.lawoffice.workflow.vo.ProcessStartPermissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProcessStartPermissionServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessStartPermissionMapper, ProcessStartPermission, ProcessStartPermissionVO> implements IProcessStartPermissionService {

    private final ProcessModelMapper processModelMapper;

    @Autowired
    public ProcessStartPermissionServiceImpl(ProcessModelMapper processModelMapper) {
        this.processModelMapper = processModelMapper;
    }

    @Override
    protected void doBeforeSave(BaseDTO<ProcessStartPermission> saveDTO) {
        ProcessStartPermission permission = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(permission, saveDTO);
        permission.setTargetType(trimToNull(permission.getTargetType()));
        permission.setTargetId(trimToNull(permission.getTargetId()));
        if (!StringUtils.hasText(permission.getStatus())) {
            permission.setStatus(WorkflowConstants.Status.ENABLED);
        }

        requireActiveById(processModelMapper, permission.getProcessModelId(), permission.getTenantId(), "流程模型不存在");
        requireText(permission.getTargetType(), "授权目标类型不能为空");
        requireText(permission.getTargetId(), "授权目标ID不能为空");
        validateIn(permission.getTargetType(), "授权目标类型不合法",
                WorkflowConstants.TargetType.USER,
                WorkflowConstants.TargetType.ROLE,
                WorkflowConstants.TargetType.DEPART,
                WorkflowConstants.TargetType.TENANT);
        validateIn(permission.getStatus(), "发起权限状态不合法",
                WorkflowConstants.Status.ENABLED,
                WorkflowConstants.Status.DISABLED);
        validateUnique(permission, "同一流程模型下发起权限不能重复",
                "process_model_id", permission.getProcessModelId(),
                "target_type", permission.getTargetType(),
                "target_id", permission.getTargetId());
    }
}
