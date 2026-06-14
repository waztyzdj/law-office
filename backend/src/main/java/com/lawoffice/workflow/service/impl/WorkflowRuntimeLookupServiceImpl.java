package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class WorkflowRuntimeLookupServiceImpl implements IWorkflowRuntimeLookupService {

    private final TaskMapper taskMapper;
    private final ProcessModelMapper processModelMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final FieldPermissionMapper fieldPermissionMapper;
    private final IUserService userService;

    public WorkflowRuntimeLookupServiceImpl(TaskMapper taskMapper,
            ProcessModelMapper processModelMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            ProcessInstanceMapper processInstanceMapper,
            FormInstanceMapper formInstanceMapper,
            FieldPermissionMapper fieldPermissionMapper,
            IUserService userService) {
        this.taskMapper = taskMapper;
        this.processModelMapper = processModelMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.formInstanceMapper = formInstanceMapper;
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.userService = userService;
    }

    @Override
    public String requireTenantId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getTenantId())) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        return context.getTenantId();
    }

    @Override
    public String requireUserId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getUserId())) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }
        return context.getUserId();
    }

    @Override
    public Task requireTodoTask(String taskId, String tenantId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        Task task = taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("id", taskId)
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0));
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已处理");
        }
        return task;
    }

    @Override
    public ProcessInstance requireProcessInstance(String processInstanceId, String tenantId) {
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("审批实例不存在");
        }
        return processInstance;
    }

    @Override
    public FormInstance requireFormInstance(String formInstanceId, String tenantId) {
        FormInstance formInstance = formInstanceMapper.selectOne(new QueryWrapper<FormInstance>()
                .eq("id", formInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (formInstance == null) {
            throw new IllegalArgumentException("表单实例不存在");
        }
        return formInstance;
    }

    @Override
    public List<FieldPermission> listFieldPermissions(String processModelId, String nodeId, String tenantId) {
        return fieldPermissionMapper.selectList(new QueryWrapper<FieldPermission>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("node_id", nodeId)
                .eq("delete_flag", 0));
    }

    @Override
    public ProcessModel requirePublishedModel(String processModelId, String tenantId) {
        QueryWrapper<ProcessModel> wrapper = new QueryWrapper<>();
        wrapper.eq("id", processModelId)
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.Status.PUBLISHED)
                .eq("delete_flag", 0);
        ProcessModel model = processModelMapper.selectOne(wrapper);
        if (model == null) {
            throw new IllegalArgumentException("流程不存在或未发布");
        }
        if (!StringUtils.hasText(model.getFlowableProcessDefinitionId())) {
            throw new IllegalArgumentException("流程未部署到Flowable，不能发起");
        }
        long newerPublishedCount = processModelMapper.selectCount(new QueryWrapper<ProcessModel>()
                .eq("tenant_id", tenantId)
                .eq("process_key", model.getProcessKey())
                .eq("status", WorkflowConstants.Status.PUBLISHED)
                .eq("delete_flag", 0)
                .gt("version", model.getVersion()));
        if (newerPublishedCount > 0) {
            throw new IllegalArgumentException("流程已有新发布版本，请使用最新版本发起");
        }
        return model;
    }

    @Override
    public FormDefinition requirePublishedForm(String formDefinitionId, String tenantId) {
        QueryWrapper<FormDefinition> wrapper = new QueryWrapper<>();
        wrapper.eq("id", formDefinitionId)
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.Status.PUBLISHED)
                .eq("delete_flag", 0);
        FormDefinition form = formDefinitionMapper.selectOne(wrapper);
        if (form == null) {
            throw new IllegalArgumentException("流程绑定的表单不存在或未发布");
        }
        return form;
    }

    @Override
    public void checkStartPermission(ProcessModel model, RequestContext context) {
        if (WorkflowConstants.StartScopeType.ALL.equals(model.getStartScopeType())) {
            return;
        }
        String tenantId = requireTenantId(context);
        List<ProcessStartPermission> permissions = processStartPermissionMapper.selectList(new QueryWrapper<ProcessStartPermission>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", model.getId())
                .eq("status", WorkflowConstants.Status.ENABLED)
                .eq("delete_flag", 0));
        if (permissions.stream().noneMatch(permission -> matchesStartPermission(permission, context))) {
            throw new IllegalArgumentException("当前用户无权发起该流程");
        }
    }

    private boolean matchesStartPermission(ProcessStartPermission permission, RequestContext context) {
        String userId = context.getUserId();
        String tenantId = context.getTenantId();
        return switch (permission.getTargetType()) {
            case WorkflowConstants.TargetType.USER -> StringUtils.hasText(userId) && permission.getTargetId().equals(userId);
            case WorkflowConstants.TargetType.TENANT -> permission.getTargetId().equals(tenantId);
            case WorkflowConstants.TargetType.ROLE -> StringUtils.hasText(userId)
                    && userService.getUserRoleIds(userId).contains(permission.getTargetId());
            case WorkflowConstants.TargetType.DEPART -> StringUtils.hasText(userId)
                    && userService.getUserDeparts(userId).stream()
                    .map(SysDepart::getId)
                    .anyMatch(permission.getTargetId()::equals);
            default -> false;
        };
    }
}
