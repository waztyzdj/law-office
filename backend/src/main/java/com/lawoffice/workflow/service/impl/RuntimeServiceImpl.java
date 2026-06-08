package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableStartResult;
import com.lawoffice.workflow.dto.FlowableTaskInfo;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.RuntimeFieldPermissionVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.FormInstanceVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.ProcessInstanceVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartProcessVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskActionVO;
import com.lawoffice.workflow.vo.TaskActionPermissionVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import com.lawoffice.workflow.vo.TaskReturnNodeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class RuntimeServiceImpl implements IRuntimeService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INSTANCE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private record ResolvedAssignee(String userId, String username, String realname, String sourceType, String sourceId) {
    }

    private final ProcessModelMapper processModelMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final TaskMapper taskMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final FieldPermissionMapper fieldPermissionMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final IFlowableService flowableService;
    private final IUserService userService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserTenantMapper userTenantMapper;

    @Autowired
    public RuntimeServiceImpl(ProcessModelMapper processModelMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            FormInstanceMapper formInstanceMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            TaskMapper taskMapper,
            OperationRecordMapper operationRecordMapper,
            FieldPermissionMapper fieldPermissionMapper,
            TaskCandidateMapper taskCandidateMapper,
            IFlowableService flowableService,
            IUserService userService,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            UserDepartMapper userDepartMapper,
            UserTenantMapper userTenantMapper) {
        this.processModelMapper = processModelMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.formInstanceMapper = formInstanceMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.taskMapper = taskMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.flowableService = flowableService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.userDepartMapper = userDepartMapper;
        this.userTenantMapper = userTenantMapper;
    }

    @Override
    public BaseResult<PageVO<AvailableProcessVO>> pageAvailableProcesses(AvailableProcessPageReq req, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            requireUserId(context);
            int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
            int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
            Set<String> specifiedProcessIds = resolveSpecifiedStartProcessIds(tenantId, context);

            QueryWrapper<ProcessModel> wrapper = new QueryWrapper<>();
            wrapper.eq("tenant_id", tenantId)
                    .eq("status", WorkflowConstants.Status.PUBLISHED)
                    .eq("delete_flag", 0)
                    .isNotNull("flowable_process_definition_id")
                    .ne("flowable_process_definition_id", "");
            if (req != null && StringUtils.hasText(req.getCategoryId())) {
                wrapper.eq("category_id", req.getCategoryId());
            }
            if (req != null && StringUtils.hasText(req.getProcessName())) {
                wrapper.like("process_name", req.getProcessName());
            }
            wrapper.and(condition -> {
                condition.eq("start_scope_type", WorkflowConstants.StartScopeType.ALL);
                if (!specifiedProcessIds.isEmpty()) {
                    condition.or().in("id", specifiedProcessIds);
                }
            });
            wrapper.orderByDesc("published_time").orderByDesc("create_time");

            Page<ProcessModel> page = processModelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            return BaseResult.success(new PageVO<>(
                    buildAvailableProcessRecords(page.getRecords(), tenantId),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询可发起流程失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<StartFormVO> getStartForm(String processModelId, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            ProcessModel model = requirePublishedModel(processModelId, tenantId);
            checkStartPermission(model, context);
            FormDefinition form = requirePublishedForm(model.getFormDefinitionId(), tenantId);
            return BaseResult.success(buildStartForm(model, form));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("获取发起表单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context) {
        try {
            validateStartReq(req);
            validateJson(req.getFormDataJson(), "表单数据JSON");

            String tenantId = requireTenantId(context);
            requireUserId(context);
            ProcessModel model = requirePublishedModel(req.getProcessModelId(), tenantId);
            checkStartPermission(model, context);
            FormDefinition form = requirePublishedForm(model.getFormDefinitionId(), tenantId);

            FormInstance formInstance = createFormInstance(req, form, tenantId, context);
            ProcessInstance processInstance = createProcessInstance(req, model, form, formInstance, tenantId, context);
            formInstance.setProcessInstanceId(processInstance.getId());
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);

            FlowableStartResult flowableStartResult = flowableService.startProcessInstance(
                    model,
                    processInstance.getId(),
                    buildFlowableVariables(processInstance, formInstance, context));
            processInstance.setFlowableProcessInstanceId(flowableStartResult.getProcessInstanceId());
            processInstance.setFlowableProcessDefinitionId(flowableStartResult.getProcessDefinitionId());
            syncCurrentTasks(processInstance, tenantId, context);
            EntityFillUtils.fillAuditFields(processInstance, context, false);
            processInstanceMapper.updateById(processInstance);
            createStartRecord(processInstance, formInstance, tenantId, context);

            return BaseResult.success(buildStartResult(processInstance, formInstance));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Start workflow process failed", e);
            markRollbackOnly();
            return BaseResult.error("发起申请失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<PageVO<StartedInstanceVO>> pageStartedInstances(StartedInstancePageReq req, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            String userId = requireUserId(context);
            int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
            int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);

            QueryWrapper<ProcessInstance> wrapper = new QueryWrapper<>();
            wrapper.eq("tenant_id", tenantId)
                    .eq("starter_user_id", userId)
                    .eq("delete_flag", 0);
            if (req != null && StringUtils.hasText(req.getStatus())) {
                wrapper.eq("status", req.getStatus());
            }
            if (req != null && StringUtils.hasText(req.getInstanceTitle())) {
                wrapper.like("instance_title", req.getInstanceTitle());
            }
            wrapper.orderByDesc("start_time").orderByDesc("create_time");

            Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            return BaseResult.success(new PageVO<>(
                    buildStartedInstanceRecords(page.getRecords(), tenantId),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询我发起的审批失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context) {
        try {
            return BaseResult.success(pageTasks(req, context, WorkflowConstants.Status.TODO));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询待办失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context) {
        try {
            return BaseResult.success(pageTasks(req, context, WorkflowConstants.Status.DONE));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询已办失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            Task task = requireTodoTask(taskId, tenantId);
            ensureTaskHandler(task, findActiveCandidate(task, context), context);
            ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            List<FieldPermission> permissions = listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
            ProcessNodeConfig nodeConfig = requireNodeConfig(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
            return BaseResult.success(buildTaskForm(task, processInstance, formInstance, permissions, nodeConfig));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("获取任务表单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context) {
        try {
            return BaseResult.success(handleTaskAction(taskId, req, context, WorkflowConstants.Action.APPROVE));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Approve workflow task failed, taskId={}", taskId, e);
            markRollbackOnly();
            return BaseResult.error("审批通过失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context) {
        try {
            return BaseResult.success(handleTaskAction(taskId, req, context, WorkflowConstants.Action.REJECT));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Reject workflow task failed, taskId={}", taskId, e);
            markRollbackOnly();
            return BaseResult.error("审批拒绝失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context) {
        try {
            return BaseResult.success(handleTransfer(taskId, req, context));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Transfer workflow task failed, taskId={}", taskId, e);
            markRollbackOnly();
            return BaseResult.error("转办失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context) {
        try {
            return BaseResult.success(handleReturn(taskId, req, context));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Return workflow task failed, taskId={}", taskId, e);
            markRollbackOnly();
            return BaseResult.error("退回失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context) {
        try {
            return BaseResult.success(handleAddSign(taskId, req, context));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Add sign workflow task failed, taskId={}", taskId, e);
            markRollbackOnly();
            return BaseResult.error("加签失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            ProcessInstance processInstance = requireProcessInstance(id, tenantId);
            ensureInstanceAccess(processInstance, context);
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            List<Task> currentTasks = listCurrentTasks(processInstance.getId(), tenantId);
            List<OperationRecord> records = listOperationRecords(processInstance.getId(), tenantId);
            return BaseResult.success(buildInstanceDetail(processInstance, formInstance, currentTasks, records));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("获取审批详情失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<List<OperationRecordVO>> listInstanceRecords(String id, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            ProcessInstance processInstance = requireProcessInstance(id, tenantId);
            ensureInstanceAccess(processInstance, context);
            return BaseResult.success(listOperationRecords(processInstance.getId(), tenantId).stream()
                    .map(this::buildOperationRecordVO)
                    .toList());
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("获取审批记录失败: " + e.getMessage());
        }
    }

    private Set<String> resolveSpecifiedStartProcessIds(String tenantId, RequestContext context) {
        String userId = requireUserId(context);
        Set<String> roleIds = new HashSet<>(userService.getUserRoleIds(userId));
        Set<String> departIds = new HashSet<>(userService.getUserDeparts(userId).stream()
                .map(SysDepart::getId)
                .filter(StringUtils::hasText)
                .toList());
        return processStartPermissionMapper.selectList(new QueryWrapper<ProcessStartPermission>()
                        .eq("tenant_id", tenantId)
                        .eq("status", WorkflowConstants.Status.ENABLED)
                        .eq("delete_flag", 0))
                .stream()
                .filter(permission -> matchesSpecifiedStartPermission(permission, context, roleIds, departIds))
                .map(ProcessStartPermission::getProcessModelId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean matchesSpecifiedStartPermission(ProcessStartPermission permission, RequestContext context,
            Set<String> roleIds, Set<String> departIds) {
        return switch (permission.getTargetType()) {
            case WorkflowConstants.TargetType.USER -> permission.getTargetId().equals(context.getUserId());
            case WorkflowConstants.TargetType.TENANT -> permission.getTargetId().equals(context.getTenantId());
            case WorkflowConstants.TargetType.ROLE -> roleIds.contains(permission.getTargetId());
            case WorkflowConstants.TargetType.DEPART -> departIds.contains(permission.getTargetId());
            default -> false;
        };
    }

    private List<AvailableProcessVO> buildAvailableProcessRecords(List<ProcessModel> processModels, String tenantId) {
        if (processModels == null || processModels.isEmpty()) {
            return List.of();
        }
        Set<String> formIds = new HashSet<>();
        processModels.stream()
                .map(ProcessModel::getFormDefinitionId)
                .filter(StringUtils::hasText)
                .forEach(formIds::add);
        Map<String, FormDefinition> formMap = new HashMap<>();
        if (!formIds.isEmpty()) {
            formDefinitionMapper.selectList(new QueryWrapper<FormDefinition>()
                            .in("id", formIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(form -> formMap.put(form.getId(), form));
        }
        return processModels.stream()
                .map(model -> buildAvailableProcessVO(model, formMap.get(model.getFormDefinitionId())))
                .toList();
    }

    private AvailableProcessVO buildAvailableProcessVO(ProcessModel model, FormDefinition form) {
        AvailableProcessVO vo = new AvailableProcessVO();
        vo.setId(model.getId());
        vo.setCreateTime(model.getCreateTime());
        vo.setCreateBy(model.getCreateBy());
        vo.setUpdateTime(model.getUpdateTime());
        vo.setUpdateBy(model.getUpdateBy());
        vo.setCategoryId(model.getCategoryId());
        vo.setProcessKey(model.getProcessKey());
        vo.setProcessName(model.getProcessName());
        vo.setProcessVersion(model.getVersion());
        vo.setDesignerType(model.getDesignerType());
        vo.setFormDefinitionId(model.getFormDefinitionId());
        vo.setStartScopeType(model.getStartScopeType());
        vo.setRemark(model.getRemark());
        vo.setPublishedTime(model.getPublishedTime());
        if (form != null) {
            vo.setFormKey(form.getFormKey());
            vo.setFormName(form.getFormName());
            vo.setFormVersion(form.getVersion());
        }
        return vo;
    }

    private List<StartedInstanceVO> buildStartedInstanceRecords(List<ProcessInstance> instances, String tenantId) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        Set<String> processModelIds = new HashSet<>();
        Set<String> formInstanceIds = new HashSet<>();
        instances.stream()
                .map(ProcessInstance::getProcessModelId)
                .filter(StringUtils::hasText)
                .forEach(processModelIds::add);
        instances.stream()
                .map(ProcessInstance::getFormInstanceId)
                .filter(StringUtils::hasText)
                .forEach(formInstanceIds::add);
        Map<String, ProcessModel> processModelMap = new HashMap<>();
        if (!processModelIds.isEmpty()) {
            processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                            .in("id", processModelIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(model -> processModelMap.put(model.getId(), model));
        }
        Map<String, FormInstance> formInstanceMap = new HashMap<>();
        if (!formInstanceIds.isEmpty()) {
            formInstanceMapper.selectList(new QueryWrapper<FormInstance>()
                            .in("id", formInstanceIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(formInstance -> formInstanceMap.put(formInstance.getId(), formInstance));
        }
        return instances.stream()
                .map(instance -> buildStartedInstanceVO(
                        instance,
                        processModelMap.get(instance.getProcessModelId()),
                        formInstanceMap.get(instance.getFormInstanceId())))
                .toList();
    }

    private StartedInstanceVO buildStartedInstanceVO(ProcessInstance instance, ProcessModel processModel, FormInstance formInstance) {
        StartedInstanceVO vo = new StartedInstanceVO();
        vo.setId(instance.getId());
        vo.setCreateTime(instance.getCreateTime());
        vo.setCreateBy(instance.getCreateBy());
        vo.setUpdateTime(instance.getUpdateTime());
        vo.setUpdateBy(instance.getUpdateBy());
        vo.setProcessModelId(instance.getProcessModelId());
        vo.setFormInstanceId(instance.getFormInstanceId());
        vo.setFormDefinitionId(instance.getFormDefinitionId());
        vo.setInstanceNo(instance.getInstanceNo());
        vo.setInstanceTitle(instance.getInstanceTitle());
        vo.setBusinessKey(instance.getBusinessKey());
        vo.setStatus(instance.getStatus());
        vo.setStartTime(instance.getStartTime());
        vo.setEndTime(instance.getEndTime());
        vo.setCurrentTaskNames(instance.getCurrentTaskNames());
        vo.setCurrentAssigneeNames(instance.getCurrentAssigneeNames());
        if (processModel != null) {
            vo.setProcessName(processModel.getProcessName());
        }
        if (formInstance != null) {
            vo.setFormName(formInstance.getFormName());
        }
        return vo;
    }

    private InstanceDetailVO buildInstanceDetail(ProcessInstance processInstance, FormInstance formInstance,
            List<Task> currentTasks, List<OperationRecord> records) {
        InstanceDetailVO vo = new InstanceDetailVO();
        vo.setProcessInstance(buildProcessInstanceVO(processInstance));
        vo.setFormInstance(buildFormInstanceVO(formInstance));
        vo.setCurrentTasks(currentTasks.stream()
                .map(task -> buildRuntimeTaskVO(task, processInstance))
                .toList());
        vo.setRecords(records.stream()
                .map(this::buildOperationRecordVO)
                .toList());
        return vo;
    }

    private List<Task> listCurrentTasks(String processInstanceId, String tenantId) {
        return taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
    }

    private List<OperationRecord> listOperationRecords(String processInstanceId, String tenantId) {
        return operationRecordMapper.selectList(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByAsc("operate_time")
                .orderByAsc("create_time"));
    }

    /**
     * 审批详情属于运行时业务数据，只允许发起人、处理人、候选人或审批记录操作人查看。
     */
    private void ensureInstanceAccess(ProcessInstance processInstance, RequestContext context) {
        String userId = requireUserId(context);
        if (userId.equals(processInstance.getStarterUserId())) {
            return;
        }
        if (hasTaskAccess(processInstance.getId(), processInstance.getTenantId(), context)) {
            return;
        }
        if (hasRecordAccess(processInstance.getId(), processInstance.getTenantId(), userId)) {
            return;
        }
        throw new IllegalArgumentException("当前用户无权查看该审批实例");
    }

    private boolean hasTaskAccess(String processInstanceId, String tenantId, RequestContext context) {
        QueryWrapper<Task> taskWrapper = new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .and(condition -> {
                    condition.eq("assignee_user_id", context.getUserId());
                    if (StringUtils.hasText(context.getUsername())) {
                        condition.or().eq("assignee_username", context.getUsername());
                    }
                });
        if (taskMapper.selectCount(taskWrapper) > 0) {
            return true;
        }
        List<String> taskIds = taskMapper.selectList(new QueryWrapper<Task>()
                        .select("id")
                        .eq("tenant_id", tenantId)
                        .eq("process_instance_id", processInstanceId)
                        .eq("delete_flag", 0))
                .stream()
                .map(Task::getId)
                .toList();
        if (taskIds.isEmpty()) {
            return false;
        }
        return taskCandidateMapper.selectCount(new QueryWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", taskIds)
                .eq("candidate_user_id", context.getUserId())
                .eq("delete_flag", 0)) > 0;
    }

    private boolean hasRecordAccess(String processInstanceId, String tenantId, String userId) {
        return operationRecordMapper.selectCount(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("operator_user_id", userId)
                .eq("delete_flag", 0)) > 0;
    }

    private ProcessInstanceVO buildProcessInstanceVO(ProcessInstance processInstance) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setId(processInstance.getId());
        vo.setCreateTime(processInstance.getCreateTime());
        vo.setCreateBy(processInstance.getCreateBy());
        vo.setUpdateTime(processInstance.getUpdateTime());
        vo.setUpdateBy(processInstance.getUpdateBy());
        vo.setTenantId(processInstance.getTenantId());
        vo.setProcessModelId(processInstance.getProcessModelId());
        vo.setFormInstanceId(processInstance.getFormInstanceId());
        vo.setFlowableProcessInstanceId(processInstance.getFlowableProcessInstanceId());
        vo.setFlowableProcessDefinitionId(processInstance.getFlowableProcessDefinitionId());
        vo.setFormDefinitionId(processInstance.getFormDefinitionId());
        vo.setInstanceNo(processInstance.getInstanceNo());
        vo.setInstanceTitle(processInstance.getInstanceTitle());
        vo.setBusinessKey(processInstance.getBusinessKey());
        vo.setStarterUserId(processInstance.getStarterUserId());
        vo.setStarterUsername(processInstance.getStarterUsername());
        vo.setStarterRealname(processInstance.getStarterRealname());
        vo.setStatus(processInstance.getStatus());
        vo.setStartTime(processInstance.getStartTime());
        vo.setEndTime(processInstance.getEndTime());
        vo.setCurrentTaskNames(processInstance.getCurrentTaskNames());
        vo.setCurrentAssigneeNames(processInstance.getCurrentAssigneeNames());
        return vo;
    }

    private FormInstanceVO buildFormInstanceVO(FormInstance formInstance) {
        FormInstanceVO vo = new FormInstanceVO();
        vo.setId(formInstance.getId());
        vo.setCreateTime(formInstance.getCreateTime());
        vo.setCreateBy(formInstance.getCreateBy());
        vo.setUpdateTime(formInstance.getUpdateTime());
        vo.setUpdateBy(formInstance.getUpdateBy());
        vo.setTenantId(formInstance.getTenantId());
        vo.setProcessInstanceId(formInstance.getProcessInstanceId());
        vo.setFormDefinitionId(formInstance.getFormDefinitionId());
        vo.setFormKey(formInstance.getFormKey());
        vo.setFormName(formInstance.getFormName());
        vo.setFormVersion(formInstance.getFormVersion());
        vo.setFormDataJson(formInstance.getFormDataJson());
        vo.setFormSchemaSnapshotJson(formInstance.getFormSchemaSnapshotJson());
        vo.setFormOptionSnapshotJson(formInstance.getFormOptionSnapshotJson());
        vo.setStatus(formInstance.getStatus());
        vo.setSubmittedTime(formInstance.getSubmittedTime());
        return vo;
    }

    private OperationRecordVO buildOperationRecordVO(OperationRecord record) {
        OperationRecordVO vo = new OperationRecordVO();
        vo.setId(record.getId());
        vo.setCreateTime(record.getCreateTime());
        vo.setCreateBy(record.getCreateBy());
        vo.setUpdateTime(record.getUpdateTime());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setTenantId(record.getTenantId());
        vo.setProcessInstanceId(record.getProcessInstanceId());
        vo.setTaskId(record.getTaskId());
        vo.setFlowableTaskId(record.getFlowableTaskId());
        vo.setNodeId(record.getNodeId());
        vo.setNodeName(record.getNodeName());
        vo.setAction(record.getAction());
        vo.setOperatorUserId(record.getOperatorUserId());
        vo.setOperatorUsername(record.getOperatorUsername());
        vo.setOperatorRealname(record.getOperatorRealname());
        vo.setTargetUserId(record.getTargetUserId());
        vo.setTargetUsername(record.getTargetUsername());
        vo.setTargetRealname(record.getTargetRealname());
        vo.setTargetNodeId(record.getTargetNodeId());
        vo.setTargetNodeName(record.getTargetNodeName());
        vo.setComment(record.getComment());
        vo.setFormDataSnapshotJson(record.getFormDataSnapshotJson());
        vo.setOperateTime(record.getOperateTime());
        return vo;
    }

    private PageVO<RuntimeTaskVO> pageTasks(TaskPageReq req, RequestContext context, String status) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
        int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
        String candidateStatus = WorkflowConstants.Status.TODO.equals(status)
                ? WorkflowConstants.Status.ACTIVE : WorkflowConstants.Status.CLAIMED;
        List<String> candidateTaskIds = listCandidateTaskIds(tenantId, userId, candidateStatus);

        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("status", status)
                .eq("delete_flag", 0);
        if (req != null && StringUtils.hasText(req.getProcessInstanceId())) {
            wrapper.eq("process_instance_id", req.getProcessInstanceId());
        }
        wrapper.and(condition -> {
            condition.eq("assignee_user_id", userId);
            if (StringUtils.hasText(context.getUsername())) {
                condition.or().eq("assignee_username", context.getUsername());
            }
            if (!candidateTaskIds.isEmpty()) {
                condition.or().in("id", candidateTaskIds);
            }
        });
        wrapper.orderByDesc("create_time");

        Page<Task> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RuntimeTaskVO> records = buildRuntimeTaskRecords(page.getRecords(), tenantId);
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private List<String> listCandidateTaskIds(String tenantId, String userId, String status) {
        return taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .select("task_id")
                        .eq("tenant_id", tenantId)
                        .eq("candidate_user_id", userId)
                        .eq("status", status)
                        .eq("delete_flag", 0))
                .stream()
                .map(TaskCandidate::getTaskId)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<RuntimeTaskVO> buildRuntimeTaskRecords(List<Task> tasks, String tenantId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Set<String> processInstanceIds = new HashSet<>();
        tasks.stream()
                .map(Task::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .forEach(processInstanceIds::add);
        Map<String, ProcessInstance> instanceMap = new HashMap<>();
        if (!processInstanceIds.isEmpty()) {
            processInstanceMapper.selectList(new QueryWrapper<ProcessInstance>()
                            .in("id", processInstanceIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(instance -> instanceMap.put(instance.getId(), instance));
        }
        return tasks.stream()
                .map(task -> buildRuntimeTaskVO(task, instanceMap.get(task.getProcessInstanceId())))
                .toList();
    }

    private RuntimeTaskVO buildRuntimeTaskVO(Task task, ProcessInstance processInstance) {
        RuntimeTaskVO vo = new RuntimeTaskVO();
        vo.setId(task.getId());
        vo.setCreateTime(task.getCreateTime());
        vo.setCreateBy(task.getCreateBy());
        vo.setUpdateTime(task.getUpdateTime());
        vo.setUpdateBy(task.getUpdateBy());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setFlowableTaskId(task.getFlowableTaskId());
        vo.setNodeId(task.getNodeId());
        vo.setTaskName(task.getTaskName());
        vo.setTaskType(task.getTaskType());
        vo.setAssigneeUserId(task.getAssigneeUserId());
        vo.setAssigneeUsername(task.getAssigneeUsername());
        vo.setAssigneeRealname(task.getAssigneeRealname());
        vo.setStatus(task.getStatus());
        vo.setClaimTime(task.getClaimTime());
        vo.setCompleteTime(task.getCompleteTime());
        if (processInstance != null) {
            vo.setInstanceNo(processInstance.getInstanceNo());
            vo.setInstanceTitle(processInstance.getInstanceTitle());
            vo.setStarterUserId(processInstance.getStarterUserId());
            vo.setStarterUsername(processInstance.getStarterUsername());
            vo.setStarterRealname(processInstance.getStarterRealname());
            vo.setStartTime(processInstance.getStartTime());
        }
        return vo;
    }

    private TaskActionVO handleTaskAction(String taskId, TaskActionReq req, RequestContext context, String action) {
        String tenantId = requireTenantId(context);
        Task task = requireTodoTask(taskId, tenantId);
        TaskCandidate candidate = findActiveCandidate(task, context);
        ensureTaskHandler(task, candidate, context);
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        List<FieldPermission> permissions = listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId);

        saveTaskFormData(req, formInstance, permissions, context);
        autoClaimIfNeeded(task, candidate, context);
        if (WorkflowConstants.Action.APPROVE.equals(action)) {
            if (WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType()) && StringUtils.hasText(task.getParentTaskId())) {
                completeAddSignTask(task, processInstance, formInstance, req, tenantId, context);
            } else {
                ensureNoActiveAddSignChild(task);
                completeApprove(task, processInstance, formInstance, req, tenantId, context);
            }
        } else if (WorkflowConstants.Action.REJECT.equals(action)) {
            ensureNotAddSignTask(task, "加签任务不允许拒绝流程");
            ensureNoActiveAddSignChild(task);
            completeReject(task, processInstance, formInstance, req, tenantId, context);
        } else {
            throw new IllegalArgumentException("不支持的审批动作");
        }
        createTaskRecord(task, processInstance, formInstance, req, action, tenantId, context);
        return buildTaskActionResult(task, processInstance);
    }

    private TaskActionVO handleTransfer(String taskId, TaskActionReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        Task task = requireTodoTask(taskId, tenantId);
        ensureNotAddSignTask(task, "加签任务不允许转办");
        ensureNoActiveAddSignChild(task);
        TaskCandidate candidate = findActiveCandidate(task, context);
        ensureTaskHandler(task, candidate, context);
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        ProcessNodeConfig nodeConfig = requireNodeConfig(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
        ensureNodeActionAllowed(nodeConfig.getAllowTransfer(), "当前节点不允许转办");
        User targetUser = requireTargetUser(req, tenantId);

        autoClaimIfNeeded(task, candidate, context);
        fillOwnerFromCurrentAssignee(task);
        task.setAssigneeUserId(targetUser.getId());
        task.setAssigneeUsername(targetUser.getUsername());
        task.setAssigneeRealname(targetUser.getRealname());
        task.setTaskType(WorkflowConstants.TaskType.TRANSFER);
        task.setClaimTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        cancelActiveCandidates(task, context);
        flowableService.setTaskAssignee(task.getFlowableTaskId(), targetUser.getId());
        refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                req, WorkflowConstants.Action.TRANSFER, tenantId, context, null, targetUser);
        return buildTaskActionResult(task, processInstance);
    }

    private TaskActionVO handleReturn(String taskId, TaskActionReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        Task task = requireTodoTask(taskId, tenantId);
        ensureNotAddSignTask(task, "加签任务不允许退回");
        ensureNoActiveAddSignChild(task);
        TaskCandidate candidate = findActiveCandidate(task, context);
        ensureTaskHandler(task, candidate, context);
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        ProcessNodeConfig currentNodeConfig = requireNodeConfig(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
        ensureNodeActionAllowed(currentNodeConfig.getAllowReturn(), "当前节点不允许退回");
        if (req == null || !StringUtils.hasText(req.getTargetNodeId())) {
            throw new IllegalArgumentException("退回目标节点不能为空");
        }
        ProcessNodeConfig targetNodeConfig = requireNodeConfig(processInstance.getProcessModelId(), req.getTargetNodeId(), tenantId);
        ensureReturnTargetAllowed(processInstance, currentNodeConfig, targetNodeConfig, tenantId);
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        saveTaskFormData(req, formInstance, listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId), context);

        autoClaimIfNeeded(task, candidate, context);
        flowableService.moveActivityTo(processInstance.getFlowableProcessInstanceId(), task.getNodeId(), req.getTargetNodeId());
        task.setStatus(WorkflowConstants.Status.RETURNED);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        cancelActiveCandidates(task, context);
        syncCurrentTasks(processInstance, tenantId, context);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        createTaskRecord(task, processInstance, formInstance, req, WorkflowConstants.Action.RETURN, tenantId, context, targetNodeConfig, null);
        return buildTaskActionResult(task, processInstance);
    }

    private TaskActionVO handleAddSign(String taskId, TaskActionReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        Task task = requireTodoTask(taskId, tenantId);
        ensureNotAddSignTask(task, "加签任务不允许再次加签");
        ensureNoActiveAddSignChild(task);
        TaskCandidate candidate = findActiveCandidate(task, context);
        ensureTaskHandler(task, candidate, context);
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        ProcessNodeConfig nodeConfig = requireNodeConfig(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
        ensureNodeActionAllowed(nodeConfig.getAllowAddSign(), "当前节点不允许加签");
        User targetUser = requireTargetUser(req, tenantId);

        autoClaimIfNeeded(task, candidate, context);
        task.setStatus(WorkflowConstants.Status.TRANSFERRED);
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        cancelActiveCandidates(task, context);

        Task addSignTask = createAddSignTask(task, targetUser, context);
        refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                req, WorkflowConstants.Action.ADD_SIGN, tenantId, context, null, targetUser);
        return buildTaskActionResult(addSignTask, processInstance);
    }

    private void completeApprove(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context) {
        flowableService.completeTask(task.getFlowableTaskId(), buildTaskVariables(task, req, context, WorkflowConstants.Action.APPROVE));
        markTaskDone(task, context);
        syncCurrentTasks(processInstance, tenantId, context);
        if (!flowableService.isProcessInstanceActive(processInstance.getFlowableProcessInstanceId())) {
            processInstance.setStatus(WorkflowConstants.Status.APPROVED);
            processInstance.setEndTime(LocalDateTime.now());
            processInstance.setCurrentTaskNames(null);
            processInstance.setCurrentAssigneeNames(null);
            archiveFormInstance(formInstance, context);
        }
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    private void completeReject(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context) {
        flowableService.terminateProcessInstance(processInstance.getFlowableProcessInstanceId(), resolveActionComment(req, "审批拒绝"));
        markTaskDone(task, context);
        cancelTodoTasks(processInstance.getId(), task.getId(), tenantId, context);
        processInstance.setStatus(WorkflowConstants.Status.REJECTED);
        processInstance.setEndTime(LocalDateTime.now());
        processInstance.setCurrentTaskNames(null);
        processInstance.setCurrentAssigneeNames(null);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        archiveFormInstance(formInstance, context);
    }

    private void completeAddSignTask(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context) {
        markTaskDone(task, context);
        Task parentTask = taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("id", task.getParentTaskId())
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (parentTask == null) {
            throw new IllegalArgumentException("加签父任务不存在");
        }
        parentTask.setStatus(WorkflowConstants.Status.TODO);
        EntityFillUtils.fillAuditFields(parentTask, context, false);
        taskMapper.updateById(parentTask);
        refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    private void ensureNodeActionAllowed(Integer allowFlag, String message) {
        if (!Integer.valueOf(1).equals(allowFlag)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureNotAddSignTask(Task task, String message) {
        if (WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType())) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureReturnTargetAllowed(ProcessInstance processInstance, ProcessNodeConfig currentNodeConfig,
            ProcessNodeConfig targetNodeConfig, String tenantId) {
        boolean allowed = listReturnableNodeConfigs(processInstance, currentNodeConfig, tenantId).stream()
                .anyMatch(nodeConfig -> nodeConfig.getNodeId().equals(targetNodeConfig.getNodeId()));
        if (!allowed) {
            throw new IllegalArgumentException("退回目标节点不允许");
        }
    }

    private User requireTargetUser(TaskActionReq req, String tenantId) {
        if (req == null || !StringUtils.hasText(req.getTargetUserId())) {
            throw new IllegalArgumentException("目标用户不能为空");
        }
        Map<String, User> users = loadTenantActiveUsers(List.of(req.getTargetUserId()), tenantId);
        User user = users.get(req.getTargetUserId());
        if (user == null) {
            throw new IllegalArgumentException("目标用户不存在、已禁用或不属于当前租户");
        }
        return user;
    }

    private void fillOwnerFromCurrentAssignee(Task task) {
        task.setOwnerUserId(task.getAssigneeUserId());
        task.setOwnerUsername(task.getAssigneeUsername());
        task.setOwnerRealname(task.getAssigneeRealname());
    }

    private void cancelActiveCandidates(Task task, RequestContext context) {
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_id", task.getId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private void ensureNoActiveAddSignChild(Task task) {
        if (taskMapper.selectCount(new QueryWrapper<Task>()
                .eq("tenant_id", task.getTenantId())
                .eq("parent_task_id", task.getId())
                .eq("task_type", WorkflowConstants.TaskType.ADD_SIGN)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)) > 0) {
            throw new IllegalArgumentException("当前任务存在未完成的加签任务");
        }
    }

    private Task createAddSignTask(Task parentTask, User targetUser, RequestContext context) {
        Task addSignTask = new Task();
        addSignTask.setTenantId(parentTask.getTenantId());
        addSignTask.setProcessInstanceId(parentTask.getProcessInstanceId());
        addSignTask.setParentTaskId(parentTask.getId());
        addSignTask.setFlowableTaskId("ADD_SIGN_" + newId());
        addSignTask.setNodeId(parentTask.getNodeId());
        addSignTask.setTaskName(parentTask.getTaskName() + "-加签");
        addSignTask.setTaskType(WorkflowConstants.TaskType.ADD_SIGN);
        addSignTask.setOwnerUserId(parentTask.getAssigneeUserId());
        addSignTask.setOwnerUsername(parentTask.getAssigneeUsername());
        addSignTask.setOwnerRealname(parentTask.getAssigneeRealname());
        addSignTask.setAssigneeUserId(targetUser.getId());
        addSignTask.setAssigneeUsername(targetUser.getUsername());
        addSignTask.setAssigneeRealname(targetUser.getRealname());
        addSignTask.setStatus(WorkflowConstants.Status.TODO);
        EntityFillUtils.fillAuditFields(addSignTask, context, true);
        taskMapper.insert(addSignTask);
        return addSignTask;
    }

    private void saveTaskFormData(TaskActionReq req, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context) {
        if (req == null || !StringUtils.hasText(req.getFormDataJson())) {
            return;
        }
        try {
            JsonNode submitted = OBJECT_MAPPER.readTree(req.getFormDataJson());
            JsonNode current = StringUtils.hasText(formInstance.getFormDataJson())
                    ? OBJECT_MAPPER.readTree(formInstance.getFormDataJson()) : OBJECT_MAPPER.createObjectNode();
            if (!submitted.isObject() || !current.isObject()) {
                throw new IllegalArgumentException("表单数据必须是JSON对象");
            }
            ObjectNode merged = ((ObjectNode) current).deepCopy();
            Set<String> editableFields = new HashSet<>();
            for (FieldPermission permission : permissions) {
                if (WorkflowConstants.FieldPermission.EDITABLE.equals(permission.getPermission())) {
                    editableFields.add(permission.getFieldKey());
                }
            }
            for (String fieldKey : editableFields) {
                if (submitted.has(fieldKey)) {
                    merged.set(fieldKey, submitted.get(fieldKey));
                }
            }
            validateRequiredFields(permissions, merged);
            formInstance.setFormDataJson(OBJECT_MAPPER.writeValueAsString(merged));
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("表单数据JSON处理失败");
        }
    }

    private void validateRequiredFields(List<FieldPermission> permissions, ObjectNode formData) {
        for (FieldPermission permission : permissions) {
            if (Integer.valueOf(1).equals(permission.getRequiredFlag())
                    && WorkflowConstants.FieldPermission.EDITABLE.equals(permission.getPermission())
                    && isEmptyJsonValue(formData.get(permission.getFieldKey()))) {
                throw new IllegalArgumentException("必填字段不能为空: " + permission.getFieldKey());
            }
        }
    }

    private boolean isEmptyJsonValue(JsonNode node) {
        return node == null || node.isNull()
                || (node.isTextual() && !StringUtils.hasText(node.asText()))
                || (node.isArray() && node.isEmpty());
    }

    private Task requireTodoTask(String taskId, String tenantId) {
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

    private ProcessInstance requireProcessInstance(String processInstanceId, String tenantId) {
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("审批实例不存在");
        }
        return processInstance;
    }

    private FormInstance requireFormInstance(String formInstanceId, String tenantId) {
        FormInstance formInstance = formInstanceMapper.selectOne(new QueryWrapper<FormInstance>()
                .eq("id", formInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (formInstance == null) {
            throw new IllegalArgumentException("表单实例不存在");
        }
        return formInstance;
    }

    private List<FieldPermission> listFieldPermissions(String processModelId, String nodeId, String tenantId) {
        return fieldPermissionMapper.selectList(new QueryWrapper<FieldPermission>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("node_id", nodeId)
                .eq("delete_flag", 0));
    }

    private TaskCandidate findActiveCandidate(Task task, RequestContext context) {
        if (!StringUtils.hasText(context.getUserId())) {
            return null;
        }
        return taskCandidateMapper.selectOne(new QueryWrapper<TaskCandidate>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_id", task.getId())
                .eq("candidate_user_id", context.getUserId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .last("limit 1"));
    }

    private void ensureTaskHandler(Task task, TaskCandidate candidate, RequestContext context) {
        if (isDirectAssignee(task, context) || candidate != null) {
            return;
        }
        throw new IllegalArgumentException("当前用户无权处理该任务");
    }

    private boolean isDirectAssignee(Task task, RequestContext context) {
        return (StringUtils.hasText(context.getUserId()) && context.getUserId().equals(task.getAssigneeUserId()))
                || (StringUtils.hasText(context.getUsername()) && context.getUsername().equals(task.getAssigneeUsername()));
    }

    /**
     * 多人候选任务在提交审批动作时自动认领，保证本地任务和 Flowable 处理人一致。
     */
    private void autoClaimIfNeeded(Task task, TaskCandidate candidate, RequestContext context) {
        if (candidate == null) {
            return;
        }
        flowableService.claimTask(task.getFlowableTaskId(), context.getUserId());
        LocalDateTime now = LocalDateTime.now();
        task.setAssigneeUserId(context.getUserId());
        task.setAssigneeUsername(context.getUsername());
        task.setAssigneeRealname(context.getUsername());
        task.setClaimTime(now);
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);

        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("id", candidate.getId())
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CLAIMED)
                .set("update_by", context.getUsername())
                .set("update_time", now));
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_id", task.getId())
                .ne("id", candidate.getId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", now));
    }

    private void markTaskDone(Task task, RequestContext context) {
        task.setStatus(WorkflowConstants.Status.DONE);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_id", task.getId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private void cancelTodoTasks(String processInstanceId, String completedTaskId, String tenantId, RequestContext context) {
        List<String> canceledTaskIds = taskMapper.selectList(new QueryWrapper<Task>()
                        .select("id")
                        .eq("tenant_id", tenantId)
                        .eq("process_instance_id", processInstanceId)
                        .ne("id", completedTaskId)
                        .eq("status", WorkflowConstants.Status.TODO)
                        .eq("delete_flag", 0))
                .stream()
                .map(Task::getId)
                .toList();
        if (canceledTaskIds.isEmpty()) {
            return;
        }
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .in("id", canceledTaskIds)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", canceledTaskIds)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private Map<String, Object> buildTaskVariables(Task task, TaskActionReq req, RequestContext context, String action) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("action", action);
        variables.put("approved", WorkflowConstants.Action.APPROVE.equals(action));
        variables.put("taskId", task.getId());
        variables.put("operatorUserId", context.getUserId());
        variables.put("operatorUsername", context.getUsername());
        variables.put("comment", req == null ? null : req.getComment());
        return variables;
    }

    private String resolveActionComment(TaskActionReq req, String defaultComment) {
        if (req != null && StringUtils.hasText(req.getComment())) {
            return req.getComment();
        }
        return defaultComment;
    }

    private void archiveFormInstance(FormInstance formInstance, RequestContext context) {
        formInstance.setStatus(WorkflowConstants.Status.ARCHIVED);
        EntityFillUtils.fillAuditFields(formInstance, context, false);
        formInstanceMapper.updateById(formInstance);
    }

    private void createTaskRecord(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String action, String tenantId, RequestContext context) {
        createTaskRecord(task, processInstance, formInstance, req, action, tenantId, context, null, null);
    }

    private void createTaskRecord(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String action, String tenantId, RequestContext context,
            ProcessNodeConfig targetNodeConfig, User targetUser) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setTaskId(task.getId());
        record.setFlowableTaskId(task.getFlowableTaskId());
        record.setNodeId(task.getNodeId());
        record.setNodeName(task.getTaskName());
        record.setAction(action);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(context.getUsername());
        if (targetUser != null) {
            record.setTargetUserId(targetUser.getId());
            record.setTargetUsername(targetUser.getUsername());
            record.setTargetRealname(targetUser.getRealname());
        }
        if (targetNodeConfig != null) {
            record.setTargetNodeId(targetNodeConfig.getNodeId());
            record.setTargetNodeName(targetNodeConfig.getNodeName());
        }
        record.setComment(resolveActionComment(req, resolveDefaultActionComment(action)));
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    private String resolveDefaultActionComment(String action) {
        return switch (action) {
            case WorkflowConstants.Action.APPROVE -> "审批通过";
            case WorkflowConstants.Action.REJECT -> "审批拒绝";
            case WorkflowConstants.Action.TRANSFER -> "转办";
            case WorkflowConstants.Action.RETURN -> "退回";
            case WorkflowConstants.Action.ADD_SIGN -> "加签";
            default -> action;
        };
    }

    private TaskActionVO buildTaskActionResult(Task task, ProcessInstance processInstance) {
        TaskActionVO vo = new TaskActionVO();
        vo.setTaskId(task.getId());
        vo.setProcessInstanceId(processInstance.getId());
        vo.setTaskStatus(task.getStatus());
        vo.setProcessStatus(processInstance.getStatus());
        return vo;
    }

    private TaskFormVO buildTaskForm(Task task, ProcessInstance processInstance, FormInstance formInstance,
            List<FieldPermission> permissions, ProcessNodeConfig nodeConfig) {
        List<ProcessNodeConfig> returnableNodes = listReturnableNodeConfigs(processInstance, nodeConfig, task.getTenantId());
        TaskFormVO vo = new TaskFormVO();
        vo.setTaskId(task.getId());
        vo.setProcessInstanceId(processInstance.getId());
        vo.setInstanceNo(processInstance.getInstanceNo());
        vo.setInstanceTitle(processInstance.getInstanceTitle());
        vo.setNodeId(task.getNodeId());
        vo.setTaskName(task.getTaskName());
        vo.setTaskType(task.getTaskType());
        vo.setParentTaskId(task.getParentTaskId());
        vo.setFormInstanceId(formInstance.getId());
        vo.setFormDefinitionId(formInstance.getFormDefinitionId());
        vo.setFormKey(formInstance.getFormKey());
        vo.setFormName(formInstance.getFormName());
        vo.setFormVersion(formInstance.getFormVersion());
        vo.setSchemaJson(formInstance.getFormSchemaSnapshotJson());
        vo.setOptionJson(formInstance.getFormOptionSnapshotJson());
        vo.setFormDataJson(formInstance.getFormDataJson());
        vo.setActionPermissions(buildTaskActionPermissions(task, nodeConfig, returnableNodes));
        vo.setReturnNodes(returnableNodes.stream().map(this::buildTaskReturnNode).toList());
        vo.setFieldPermissions(permissions.stream().map(this::buildRuntimeFieldPermission).toList());
        return vo;
    }

    private TaskActionPermissionVO buildTaskActionPermissions(Task task, ProcessNodeConfig nodeConfig,
            List<ProcessNodeConfig> returnableNodes) {
        TaskActionPermissionVO vo = new TaskActionPermissionVO();
        boolean addSignTask = WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType());
        vo.setAllowApprove(true);
        vo.setAllowReject(!addSignTask);
        vo.setAllowTransfer(!addSignTask && isEnabled(nodeConfig.getAllowTransfer()));
        vo.setAllowAddSign(!addSignTask && isEnabled(nodeConfig.getAllowAddSign()));
        vo.setAllowReturn(!addSignTask && isEnabled(nodeConfig.getAllowReturn()) && !returnableNodes.isEmpty());
        return vo;
    }

    private TaskReturnNodeVO buildTaskReturnNode(ProcessNodeConfig nodeConfig) {
        TaskReturnNodeVO vo = new TaskReturnNodeVO();
        vo.setNodeId(nodeConfig.getNodeId());
        vo.setNodeName(nodeConfig.getNodeName());
        vo.setNodeType(nodeConfig.getNodeType());
        vo.setSortOrder(nodeConfig.getSortOrder());
        return vo;
    }

    /**
     * 一期退回目标只开放当前流程模型中顺序在当前节点之前的审批节点，避免退回到开始、结束或未来节点。
     */
    private List<ProcessNodeConfig> listReturnableNodeConfigs(ProcessInstance processInstance,
            ProcessNodeConfig currentNodeConfig, String tenantId) {
        if (!isEnabled(currentNodeConfig.getAllowReturn())) {
            return List.of();
        }
        Integer currentSortOrder = currentNodeConfig.getSortOrder();
        return processNodeConfigMapper.selectList(new QueryWrapper<ProcessNodeConfig>()
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

    private boolean isEnabled(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }

    private RuntimeFieldPermissionVO buildRuntimeFieldPermission(FieldPermission permission) {
        RuntimeFieldPermissionVO vo = new RuntimeFieldPermissionVO();
        vo.setFieldKey(permission.getFieldKey());
        vo.setPermission(permission.getPermission());
        vo.setRequiredFlag(permission.getRequiredFlag());
        return vo;
    }

    private void validateStartReq(StartProcessReq req) {
        if (req == null) {
            throw new IllegalArgumentException("发起申请请求不能为空");
        }
        if (!StringUtils.hasText(req.getProcessModelId())) {
            throw new IllegalArgumentException("流程模型ID不能为空");
        }
        if (!StringUtils.hasText(req.getFormDataJson())) {
            throw new IllegalArgumentException("表单数据不能为空");
        }
    }

    private void validateJson(String json, String fieldName) {
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "不是合法JSON");
        }
    }

    private ProcessModel requirePublishedModel(String processModelId, String tenantId) {
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
        return model;
    }

    private FormDefinition requirePublishedForm(String formDefinitionId, String tenantId) {
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

    private void checkStartPermission(ProcessModel model, RequestContext context) {
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

    private FormInstance createFormInstance(StartProcessReq req, FormDefinition form, String tenantId, RequestContext context) {
        FormInstance formInstance = new FormInstance();
        formInstance.setTenantId(tenantId);
        formInstance.setFormDefinitionId(form.getId());
        formInstance.setFormKey(form.getFormKey());
        formInstance.setFormName(form.getFormName());
        formInstance.setFormVersion(form.getVersion());
        formInstance.setFormDataJson(req.getFormDataJson());
        formInstance.setFormSchemaSnapshotJson(form.getSchemaJson());
        formInstance.setFormOptionSnapshotJson(form.getOptionJson());
        formInstance.setStatus(WorkflowConstants.Status.ACTIVE);
        formInstance.setSubmittedTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(formInstance, context, true);
        formInstanceMapper.insert(formInstance);
        return formInstance;
    }

    private ProcessInstance createProcessInstance(StartProcessReq req, ProcessModel model, FormDefinition form,
            FormInstance formInstance, String tenantId, RequestContext context) {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(newId());
        processInstance.setTenantId(tenantId);
        processInstance.setProcessModelId(model.getId());
        processInstance.setFormInstanceId(formInstance.getId());
        processInstance.setFormDefinitionId(form.getId());
        processInstance.setFlowableProcessDefinitionId(model.getFlowableProcessDefinitionId());
        processInstance.setInstanceNo(generateInstanceNo());
        processInstance.setInstanceTitle(resolveInstanceTitle(req, model));
        processInstance.setBusinessKey(StringUtils.hasText(req.getBusinessKey()) ? req.getBusinessKey() : processInstance.getId());
        processInstance.setStarterUserId(context.getUserId());
        processInstance.setStarterUsername(context.getUsername());
        processInstance.setStarterRealname(context.getUsername());
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        processInstance.setStartTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(processInstance, context, true);
        processInstanceMapper.insert(processInstance);
        return processInstance;
    }

    private Map<String, Object> buildFlowableVariables(ProcessInstance processInstance, FormInstance formInstance, RequestContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", processInstance.getTenantId());
        variables.put("processInstanceId", processInstance.getId());
        variables.put("formInstanceId", formInstance.getId());
        variables.put("starterUserId", context.getUserId());
        variables.put("starterUsername", context.getUsername());
        return variables;
    }

    private void syncCurrentTasks(ProcessInstance processInstance, String tenantId, RequestContext context) {
        List<FlowableTaskInfo> activeTasks = flowableService.listActiveTasks(processInstance.getFlowableProcessInstanceId());
        Set<String> existingFlowableTaskIds = new HashSet<>();
        if (!activeTasks.isEmpty()) {
            taskMapper.selectList(new QueryWrapper<Task>()
                            .select("flowable_task_id")
                            .eq("tenant_id", tenantId)
                            .eq("process_instance_id", processInstance.getId())
                            .in("flowable_task_id", activeTasks.stream().map(FlowableTaskInfo::getTaskId).toList())
                            .eq("delete_flag", 0))
                    .stream()
                    .map(Task::getFlowableTaskId)
                    .filter(StringUtils::hasText)
                    .forEach(existingFlowableTaskIds::add);
        }
        for (FlowableTaskInfo flowableTask : activeTasks) {
            if (existingFlowableTaskIds.contains(flowableTask.getTaskId())) {
                continue;
            }
            ProcessNodeConfig nodeConfig = requireNodeConfig(processInstance.getProcessModelId(), flowableTask.getTaskDefinitionKey(), tenantId);
            List<ResolvedAssignee> assignees = resolveTaskAssignees(nodeConfig, processInstance, tenantId);
            applyFlowableAssignees(flowableTask.getTaskId(), assignees);

            Task task = new Task();
            task.setTenantId(tenantId);
            task.setProcessInstanceId(processInstance.getId());
            task.setFlowableTaskId(flowableTask.getTaskId());
            task.setNodeId(flowableTask.getTaskDefinitionKey());
            task.setTaskName(flowableTask.getTaskName());
            task.setTaskType(WorkflowConstants.TaskType.NORMAL);
            task.setOwnerUsername(flowableTask.getOwner());
            if (assignees.size() == 1) {
                ResolvedAssignee assignee = assignees.get(0);
                task.setAssigneeUserId(assignee.userId());
                task.setAssigneeUsername(assignee.username());
                task.setAssigneeRealname(assignee.realname());
            }
            task.setStatus(WorkflowConstants.Status.TODO);
            EntityFillUtils.fillAuditFields(task, context, true);
            taskMapper.insert(task);
            if (assignees.size() > 1) {
                createTaskCandidates(task, assignees, context);
            }
        }
        refreshCurrentTaskSummary(processInstance, tenantId);
    }

    private ProcessNodeConfig requireNodeConfig(String processModelId, String nodeId, String tenantId) {
        ProcessNodeConfig nodeConfig = processNodeConfigMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
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

    /**
     * 解析系统主数据中的审批人，Flowable 不作为用户、角色或部门数据来源。
     */
    private List<ResolvedAssignee> resolveTaskAssignees(ProcessNodeConfig nodeConfig, ProcessInstance processInstance, String tenantId) {
        List<ResolvedAssignee> assignees = switch (nodeConfig.getAssigneeType()) {
            case WorkflowConstants.AssigneeType.USER -> resolveUserAssignees(nodeConfig, tenantId);
            case WorkflowConstants.AssigneeType.ROLE -> resolveRoleAssignees(nodeConfig, tenantId);
            case WorkflowConstants.AssigneeType.DEPART_LEADER -> resolveDepartLeaderAssignees(nodeConfig, processInstance, tenantId);
            case WorkflowConstants.AssigneeType.STARTER -> resolveStarterAssignee(processInstance, tenantId);
            default -> throw new IllegalArgumentException("不支持的审批人类型: " + nodeConfig.getAssigneeType());
        };
        if (assignees.isEmpty()) {
            throw new IllegalArgumentException("未解析到审批人: " + nodeConfig.getNodeName());
        }
        return assignees;
    }

    private List<ResolvedAssignee> resolveUserAssignees(ProcessNodeConfig nodeConfig, String tenantId) {
        List<String> userIds = readIdList(nodeConfig.getAssigneeJson(), "userIds", "users", "ids");
        Map<String, User> users = loadTenantActiveUsers(userIds, tenantId);
        return users.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.TargetType.USER, user.getId()))
                .toList();
    }

    private List<ResolvedAssignee> resolveRoleAssignees(ProcessNodeConfig nodeConfig, String tenantId) {
        List<String> roleIds = readIdList(nodeConfig.getAssigneeJson(), "roleIds", "roles", "ids");
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<UserRole> userRoles = userRoleMapper.selectList(new QueryWrapper<UserRole>()
                .in("role_id", roleIds)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        Map<String, String> sourceRoleByUserId = new LinkedHashMap<>();
        for (UserRole userRole : userRoles) {
            sourceRoleByUserId.putIfAbsent(userRole.getUserId(), userRole.getRoleId());
        }
        Map<String, User> users = loadTenantActiveUsers(new ArrayList<>(sourceRoleByUserId.keySet()), tenantId);
        return users.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.TargetType.ROLE, sourceRoleByUserId.get(user.getId())))
                .toList();
    }

    private List<ResolvedAssignee> resolveDepartLeaderAssignees(ProcessNodeConfig nodeConfig,
            ProcessInstance processInstance, String tenantId) {
        List<String> departIds = readIdList(nodeConfig.getAssigneeJson(), "departIds", "departs", "ids");
        if (departIds.isEmpty()) {
            departIds = userDepartMapper.selectList(new QueryWrapper<UserDepart>()
                            .select("dep_id")
                            .eq("tenant_id", tenantId)
                            .eq("user_id", processInstance.getStarterUserId())
                            .eq("delete_flag", 0))
                    .stream()
                    .map(UserDepart::getDepId)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (departIds.isEmpty()) {
            return List.of();
        }
        List<String> resolvedDepartIds = departIds;
        List<String> tenantUserIds = userTenantMapper.selectList(new QueryWrapper<UserTenant>()
                        .select("user_id")
                        .eq("tenant_id", tenantId)
                        .eq("status", "1")
                        .eq("delete_flag", 0))
                .stream()
                .map(UserTenant::getUserId)
                .toList();
        if (tenantUserIds.isEmpty()) {
            return List.of();
        }
        List<User> leaders = userMapper.selectList(new QueryWrapper<User>()
                .in("id", tenantUserIds)
                .eq("user_identity", 2)
                .eq("status", 1)
                .eq("delete_flag", 0));
        Map<String, User> matchedLeaders = new LinkedHashMap<>();
        for (User leader : leaders) {
            if (hasAnyDepart(leader.getDepartIds(), resolvedDepartIds)) {
                matchedLeaders.putIfAbsent(leader.getId(), leader);
            }
        }
        return matchedLeaders.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.AssigneeType.DEPART_LEADER, String.join(",", resolvedDepartIds)))
                .toList();
    }

    private List<ResolvedAssignee> resolveStarterAssignee(ProcessInstance processInstance, String tenantId) {
        Map<String, User> users = loadTenantActiveUsers(List.of(processInstance.getStarterUserId()), tenantId);
        return users.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.AssigneeType.STARTER, user.getId()))
                .toList();
    }

    private Map<String, User> loadTenantActiveUsers(List<String> userIds, String tenantId) {
        List<String> normalizedUserIds = userIds == null ? List.of() : userIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }
        Set<String> tenantUserIds = userTenantMapper.selectList(new QueryWrapper<UserTenant>()
                        .select("user_id")
                        .in("user_id", normalizedUserIds)
                        .eq("tenant_id", tenantId)
                        .eq("status", "1")
                        .eq("delete_flag", 0))
                .stream()
                .map(UserTenant::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        if (tenantUserIds.isEmpty()) {
            return Map.of();
        }
        Map<String, User> userMap = new LinkedHashMap<>();
        userMapper.selectList(new QueryWrapper<User>()
                        .in("id", tenantUserIds)
                        .eq("status", 1)
                        .eq("delete_flag", 0))
                .forEach(user -> userMap.put(user.getId(), user));
        return userMap;
    }

    private List<String> readIdList(String json, String... fieldNames) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            List<String> ids = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(node -> addTextValue(ids, node));
            } else if (root.isObject()) {
                for (String fieldName : fieldNames) {
                    JsonNode node = root.get(fieldName);
                    if (node == null) {
                        continue;
                    }
                    if (node.isArray()) {
                        node.forEach(item -> addTextValue(ids, item));
                    } else {
                        addTextValue(ids, node);
                    }
                }
            }
            return ids.stream().filter(StringUtils::hasText).distinct().toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("审批人配置JSON不合法");
        }
    }

    private void addTextValue(List<String> values, JsonNode node) {
        if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
            values.add(node.asText());
        }
    }

    private boolean hasAnyDepart(String leaderDepartIds, List<String> departIds) {
        if (!StringUtils.hasText(leaderDepartIds) || departIds == null || departIds.isEmpty()) {
            return false;
        }
        Set<String> leaderDepartIdSet = java.util.Arrays.stream(leaderDepartIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        return departIds.stream().anyMatch(leaderDepartIdSet::contains);
    }

    private void applyFlowableAssignees(String flowableTaskId, List<ResolvedAssignee> assignees) {
        if (assignees.size() == 1) {
            flowableService.setTaskAssignee(flowableTaskId, assignees.get(0).userId());
            return;
        }
        flowableService.addCandidateUsers(flowableTaskId, assignees.stream()
                .map(ResolvedAssignee::userId)
                .toList());
    }

    private void createTaskCandidates(Task task, List<ResolvedAssignee> assignees, RequestContext context) {
        for (ResolvedAssignee assignee : assignees) {
            TaskCandidate candidate = new TaskCandidate();
            candidate.setTenantId(task.getTenantId());
            candidate.setTaskId(task.getId());
            candidate.setFlowableTaskId(task.getFlowableTaskId());
            candidate.setCandidateUserId(assignee.userId());
            candidate.setCandidateUsername(assignee.username());
            candidate.setCandidateRealname(assignee.realname());
            candidate.setSourceType(assignee.sourceType());
            candidate.setSourceId(assignee.sourceId());
            candidate.setStatus(WorkflowConstants.Status.ACTIVE);
            EntityFillUtils.fillAuditFields(candidate, context, true);
            taskCandidateMapper.insert(candidate);
        }
    }

    private void refreshCurrentTaskSummary(ProcessInstance processInstance, String tenantId) {
        List<Task> todoTasks = taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstance.getId())
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
        if (todoTasks.isEmpty()) {
            processInstance.setCurrentTaskNames(null);
            processInstance.setCurrentAssigneeNames(null);
            return;
        }
        processInstance.setCurrentTaskNames(String.join(",", todoTasks.stream()
                .map(Task::getTaskName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList()));
        Set<String> taskIds = todoTasks.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        Map<String, List<String>> candidateNamesByTaskId = new HashMap<>();
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .in("task_id", taskIds)
                        .eq("tenant_id", tenantId)
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .forEach(candidate -> candidateNamesByTaskId
                        .computeIfAbsent(candidate.getTaskId(), key -> new ArrayList<>())
                        .add(resolveDisplayName(candidate.getCandidateRealname(), candidate.getCandidateUsername(), candidate.getCandidateUserId())));
        List<String> assigneeNames = new ArrayList<>();
        for (Task task : todoTasks) {
            if (StringUtils.hasText(task.getAssigneeUserId())) {
                assigneeNames.add(resolveDisplayName(task.getAssigneeRealname(), task.getAssigneeUsername(), task.getAssigneeUserId()));
            } else {
                assigneeNames.addAll(candidateNamesByTaskId.getOrDefault(task.getId(), List.of()));
            }
        }
        processInstance.setCurrentAssigneeNames(String.join(",", assigneeNames.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList()));
    }

    private String resolveDisplayName(String realname, String username, String userId) {
        if (StringUtils.hasText(realname)) {
            return realname;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return userId;
    }

    private void createStartRecord(ProcessInstance processInstance, FormInstance formInstance, String tenantId, RequestContext context) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setAction(WorkflowConstants.Action.START);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(context.getUsername());
        record.setComment("发起申请");
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    private StartFormVO buildStartForm(ProcessModel model, FormDefinition form) {
        StartFormVO vo = new StartFormVO();
        vo.setProcessModelId(model.getId());
        vo.setProcessName(model.getProcessName());
        vo.setFormDefinitionId(form.getId());
        vo.setFormKey(form.getFormKey());
        vo.setFormName(form.getFormName());
        vo.setFormVersion(form.getVersion());
        vo.setSchemaJson(form.getSchemaJson());
        vo.setOptionJson(form.getOptionJson());
        return vo;
    }

    private StartProcessVO buildStartResult(ProcessInstance processInstance, FormInstance formInstance) {
        StartProcessVO vo = new StartProcessVO();
        vo.setProcessInstanceId(processInstance.getId());
        vo.setFormInstanceId(formInstance.getId());
        vo.setFlowableProcessInstanceId(processInstance.getFlowableProcessInstanceId());
        vo.setInstanceNo(processInstance.getInstanceNo());
        vo.setStatus(processInstance.getStatus());
        return vo;
    }

    private String resolveInstanceTitle(StartProcessReq req, ProcessModel model) {
        if (StringUtils.hasText(req.getInstanceTitle())) {
            return req.getInstanceTitle();
        }
        return model.getProcessName() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }

    private String generateInstanceNo() {
        return "WF" + LocalDateTime.now().format(INSTANCE_NO_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String requireTenantId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getTenantId())) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        return context.getTenantId();
    }

    private String requireUserId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getUserId())) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }
        return context.getUserId();
    }

    /**
     * 发起申请需要同时写入业务表和启动 Flowable，捕获异常返回前必须显式回滚。
     */
    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (Exception ignored) {
            // No active transaction when invoked outside Spring proxy.
        }
    }
}
