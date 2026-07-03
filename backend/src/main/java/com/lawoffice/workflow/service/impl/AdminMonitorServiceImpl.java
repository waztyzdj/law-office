package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.AdminOperationRecord;
import com.lawoffice.workflow.entity.ArchiveRecord;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.AdminOperationRecordMapper;
import com.lawoffice.workflow.mapper.ArchiveRecordMapper;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.AdminMonitorActionReq;
import com.lawoffice.workflow.req.AdminMonitorPageReq;
import com.lawoffice.workflow.service.IAdminMonitorService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.IProcessResultNotificationService;
import com.lawoffice.workflow.service.IRuntimeViewAssemblerService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AdminMonitorInstanceVO;
import com.lawoffice.workflow.vo.AdminOperationRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminMonitorServiceImpl implements IAdminMonitorService {

    private static final Map<String, String> MONITOR_SORT_FIELDS = Map.ofEntries(
            Map.entry("instanceTitle", "instance_title"),
            Map.entry("instanceNo", "instance_no"),
            Map.entry("starterRealname", "starter_realname"),
            Map.entry("currentTaskNames", "current_task_names"),
            Map.entry("currentAssigneeNames", "current_assignee_names"),
            Map.entry("status", "status"),
            Map.entry("startTime", "start_time"),
            Map.entry("endTime", "end_time"),
            Map.entry("updateTime", "update_time"),
            Map.entry("createTime", "create_time")
    );

    private static final Set<String> MANUAL_ARCHIVABLE_STATUSES = Set.of(
            WorkflowConstants.Status.APPROVED,
            WorkflowConstants.Status.REJECTED,
            WorkflowConstants.Status.TERMINATED
    );

    private final AdminOperationRecordMapper adminOperationRecordMapper;
    private final ArchiveRecordMapper archiveRecordMapper;
    private final CcRecordMapper ccRecordMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final IFlowableService flowableService;
    private final IInstanceStateService instanceStateService;
    private final IProcessResultNotificationService processResultNotificationService;
    private final IRuntimeViewAssemblerService runtimeViewAssemblerService;
    private final ITaskNotificationService taskNotificationService;
    private final IUserService userService;
    private final ObjectMapper objectMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessModelMapper processModelMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final TransactionTemplate transactionTemplate;
    private final UserMapper userMapper;

    public AdminMonitorServiceImpl(AdminOperationRecordMapper adminOperationRecordMapper,
            ArchiveRecordMapper archiveRecordMapper,
            CcRecordMapper ccRecordMapper,
            FormInstanceMapper formInstanceMapper,
            IFlowableService flowableService,
            IInstanceStateService instanceStateService,
            IProcessResultNotificationService processResultNotificationService,
            IRuntimeViewAssemblerService runtimeViewAssemblerService,
            ITaskNotificationService taskNotificationService,
            IUserService userService,
            ObjectMapper objectMapper,
            OperationRecordMapper operationRecordMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessModelMapper processModelMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            PlatformTransactionManager transactionManager,
            UserMapper userMapper) {
        this.adminOperationRecordMapper = adminOperationRecordMapper;
        this.archiveRecordMapper = archiveRecordMapper;
        this.ccRecordMapper = ccRecordMapper;
        this.formInstanceMapper = formInstanceMapper;
        this.flowableService = flowableService;
        this.instanceStateService = instanceStateService;
        this.processResultNotificationService = processResultNotificationService;
        this.runtimeViewAssemblerService = runtimeViewAssemblerService;
        this.taskNotificationService = taskNotificationService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processModelMapper = processModelMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.userMapper = userMapper;
    }

    @Override
    public BaseResult<PageVO<AdminMonitorInstanceVO>> page(AdminMonitorPageReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
            int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
            QueryWrapper<ProcessInstance> wrapper = buildMonitorPageWrapper(req, tenantId);
            applyMonitorPageOrder(wrapper, req);
            Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            return BaseResult.success(new PageVO<>(
                    buildMonitorRecords(page.getRecords(), tenantId),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程监控失败", e);
        }
    }

    @Override
    public BaseResult<AdminMonitorDetailVO> detail(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            ProcessInstance processInstance = requireProcessInstance(processInstanceId, tenantId);
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            AdminMonitorDetailVO vo = new AdminMonitorDetailVO();
            vo.setDetail(runtimeViewAssemblerService.buildInstanceDetail(
                    processInstance,
                    formInstance,
                    listCurrentTasks(processInstance.getId(), tenantId),
                    listApprovalRecords(processInstance.getId(), tenantId),
                    listCcRecords(processInstance.getId(), tenantId),
                    context));
            vo.setAdminOperationRecords(listAdminRecords(processInstance.getId(), tenantId).stream()
                    .map(this::buildAdminOperationRecordVO)
                    .toList());
            return BaseResult.success(vo);
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程监控详情失败", e);
        }
    }

    @Override
    public BaseResult<AdminOperationRecordVO> reassign(AdminMonitorActionReq req, RequestContext context) {
        return executeMaintenance(req, WorkflowConstants.AdminOperationType.REASSIGN,
                () -> handleReassign(req, context), context);
    }

    @Override
    public BaseResult<AdminOperationRecordVO> terminate(AdminMonitorActionReq req, RequestContext context) {
        return executeMaintenance(req, WorkflowConstants.AdminOperationType.TERMINATE,
                () -> handleTerminate(req, context), context);
    }

    @Override
    public BaseResult<AdminOperationRecordVO> resendNotice(AdminMonitorActionReq req, RequestContext context) {
        return executeMaintenance(req, WorkflowConstants.AdminOperationType.RESEND_NOTICE,
                () -> handleResendNotice(req, context), context);
    }

    @Override
    public BaseResult<List<AdminOperationRecordVO>> listOperationRecords(String processInstanceId,
            RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            ProcessInstance processInstance = requireProcessInstance(processInstanceId, tenantId);
            return BaseResult.success(listAdminRecords(processInstance.getId(), tenantId).stream()
                    .map(this::buildAdminOperationRecordVO)
                    .toList());
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return systemError("查询流程维护记录失败", e);
        }
    }

    private QueryWrapper<ProcessInstance> buildMonitorPageWrapper(AdminMonitorPageReq req, String tenantId) {
        QueryWrapper<ProcessInstance> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("delete_flag", 0);
        if (req == null) {
            return wrapper;
        }
        if (StringUtils.hasText(req.getInstanceTitle())) {
            wrapper.like("instance_title", req.getInstanceTitle());
        }
        if (StringUtils.hasText(req.getInstanceNo())) {
            wrapper.like("instance_no", req.getInstanceNo());
        }
        if (StringUtils.hasText(req.getStarterRealname())) {
            wrapper.like("starter_realname", req.getStarterRealname());
        }
        if (StringUtils.hasText(req.getCurrentTaskNames())) {
            wrapper.like("current_task_names", req.getCurrentTaskNames());
        }
        if (StringUtils.hasText(req.getCurrentAssigneeNames())) {
            wrapper.like("current_assignee_names", req.getCurrentAssigneeNames());
        }
        if (StringUtils.hasText(req.getStatus())) {
            wrapper.eq("status", req.getStatus());
        }
        if (StringUtils.hasText(req.getStartTimeGe())) {
            wrapper.ge("start_time", parseDateTime(req.getStartTimeGe(), "发起时间开始值不合法"));
        }
        if (StringUtils.hasText(req.getStartTimeLe())) {
            wrapper.le("start_time", parseDateTime(req.getStartTimeLe(), "发起时间结束值不合法"));
        }
        if (StringUtils.hasText(req.getUpdateTimeGe())) {
            wrapper.ge("update_time", parseDateTime(req.getUpdateTimeGe(), "更新时间开始值不合法"));
        }
        if (StringUtils.hasText(req.getUpdateTimeLe())) {
            wrapper.le("update_time", parseDateTime(req.getUpdateTimeLe(), "更新时间结束值不合法"));
        }
        List<String> processModelIds = listMatchedProcessModelIds(req, tenantId);
        if (processModelIds != null) {
            if (processModelIds.isEmpty()) {
                wrapper.eq("id", "__none__");
            } else {
                wrapper.in("process_model_id", processModelIds);
            }
        }
        return wrapper;
    }

    private List<AdminMonitorInstanceVO> buildMonitorRecords(List<ProcessInstance> instances, String tenantId) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        Map<String, ProcessModel> processModelMap = buildProcessModelMap(instances, tenantId);
        Set<String> archivedInstanceIds = listArchivedInstanceIds(instances, tenantId);
        return instances.stream()
                .map(instance -> buildMonitorInstanceVO(instance, processModelMap.get(instance.getProcessModelId()),
                        tenantId, archivedInstanceIds.contains(instance.getId())))
                .toList();
    }

    private AdminMonitorInstanceVO buildMonitorInstanceVO(ProcessInstance instance, ProcessModel processModel,
            String tenantId, boolean archived) {
        AdminMonitorInstanceVO vo = new AdminMonitorInstanceVO();
        vo.setId(instance.getId());
        vo.setCreateBy(instance.getCreateBy());
        vo.setCreateTime(instance.getCreateTime());
        vo.setUpdateBy(instance.getUpdateBy());
        vo.setUpdateTime(instance.getUpdateTime());
        vo.setProcessModelId(instance.getProcessModelId());
        vo.setCategoryId(processModel == null ? null : processModel.getCategoryId());
        vo.setProcessKey(processModel == null ? null : processModel.getProcessKey());
        vo.setProcessName(processModel == null ? null : processModel.getProcessName());
        vo.setProcessVersion(processModel == null ? null : processModel.getVersion());
        vo.setFormInstanceId(instance.getFormInstanceId());
        vo.setFormDefinitionId(instance.getFormDefinitionId());
        vo.setInstanceNo(instance.getInstanceNo());
        vo.setInstanceTitle(instance.getInstanceTitle());
        vo.setBusinessKey(instance.getBusinessKey());
        vo.setStarterUserId(instance.getStarterUserId());
        vo.setStarterUsername(instance.getStarterUsername());
        vo.setStarterRealname(instance.getStarterRealname());
        vo.setStatus(instance.getStatus());
        vo.setStartTime(instance.getStartTime());
        vo.setEndTime(instance.getEndTime());
        vo.setCurrentTaskNames(instance.getCurrentTaskNames());
        vo.setCurrentAssigneeNames(instance.getCurrentAssigneeNames());
        vo.setTodoTaskCount(countTodoTasks(instance.getId(), tenantId));
        vo.setCanMaintain(isMaintainable(instance));
        vo.setArchived(archived);
        vo.setCanArchive(isManuallyArchivable(instance, archived));
        return vo;
    }

    private Set<String> listArchivedInstanceIds(List<ProcessInstance> instances, String tenantId) {
        List<String> instanceIds = instances.stream()
                .map(ProcessInstance::getId)
                .filter(StringUtils::hasText)
                .toList();
        if (instanceIds.isEmpty()) {
            return Set.of();
        }
        return archiveRecordMapper.selectList(new QueryWrapper<ArchiveRecord>()
                        .select("process_instance_id")
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0)
                        .in("process_instance_id", instanceIds))
                .stream()
                .map(ArchiveRecord::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private boolean isManuallyArchivable(ProcessInstance instance, boolean archived) {
        return !archived && instance != null && MANUAL_ARCHIVABLE_STATUSES.contains(instance.getStatus());
    }

    private AdminOperationRecordVO handleReassign(AdminMonitorActionReq req, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        ProcessInstance processInstance = requireMaintainableInstance(req == null ? null : req.getProcessInstanceId(), tenantId);
        Task task = requireTodoTask(req == null ? null : req.getTaskId(), processInstance.getId(), tenantId);
        User targetUser = requireActiveTenantUser(req == null ? null : req.getTargetUserId(), tenantId);
        String reason = requireReason(req);
        ensureReassignTargetAvailable(task, targetUser);
        String beforeSnapshot = toJson(Map.of(
                "taskId", task.getId(),
                "assigneeUserId", nullToEmpty(task.getAssigneeUserId()),
                "assigneeUsername", nullToEmpty(task.getAssigneeUsername()),
                "assigneeRealname", nullToEmpty(task.getAssigneeRealname()),
                "currentAssigneeNames", nullToEmpty(processInstance.getCurrentAssigneeNames())
        ));

        taskNotificationService.expireTodoMessageActions(List.of(task.getId()), tenantId, context);
        task.setAssigneeUserId(targetUser.getId());
        task.setAssigneeUsername(targetUser.getUsername());
        task.setAssigneeRealname(targetUser.getRealname());
        task.setClaimTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        cancelActiveCandidates(task, context);
        flowableService.setTaskAssignee(resolveFlowableAnchorTask(task, tenantId).getFlowableTaskId(), targetUser.getId());
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        taskNotificationService.sendTodoArrivalMessage(processInstance, task, List.of(targetUser.getId()), context);

        String afterSnapshot = toJson(Map.of(
                "taskId", task.getId(),
                "assigneeUserId", targetUser.getId(),
                "assigneeUsername", nullToEmpty(targetUser.getUsername()),
                "assigneeRealname", nullToEmpty(targetUser.getRealname()),
                "currentAssigneeNames", nullToEmpty(processInstance.getCurrentAssigneeNames())
        ));
        return buildAdminOperationRecordVO(insertSuccessRecord(processInstance.getId(), task.getId(),
                WorkflowConstants.AdminOperationType.REASSIGN, reason, beforeSnapshot, afterSnapshot, context));
    }

    private AdminOperationRecordVO handleTerminate(AdminMonitorActionReq req, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        ProcessInstance processInstance = requireMaintainableInstance(req == null ? null : req.getProcessInstanceId(), tenantId);
        String reason = requireReason(req);
        List<Task> todoTasks = listCurrentTasks(processInstance.getId(), tenantId);
        String beforeSnapshot = toJson(Map.of(
                "status", processInstance.getStatus(),
                "currentTaskNames", nullToEmpty(processInstance.getCurrentTaskNames()),
                "currentAssigneeNames", nullToEmpty(processInstance.getCurrentAssigneeNames()),
                "todoTaskIds", todoTasks.stream().map(Task::getId).toList()
        ));

        if (StringUtils.hasText(processInstance.getFlowableProcessInstanceId())) {
            flowableService.terminateProcessInstance(processInstance.getFlowableProcessInstanceId(), reason);
        }
        cancelTodoTasks(todoTasks, tenantId, context);
        processInstance.setStatus(WorkflowConstants.Status.TERMINATED);
        processInstance.setEndTime(LocalDateTime.now());
        processInstance.setCurrentTaskNames(null);
        processInstance.setCurrentAssigneeNames(null);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        FormInstance formInstance = findFormInstance(processInstance.getFormInstanceId(), tenantId);
        if (formInstance != null) {
            instanceStateService.archiveFormInstance(formInstance, context);
        }
        processResultNotificationService.sendProcessResultMessage(processInstance, context);

        String afterSnapshot = toJson(Map.of(
                "status", processInstance.getStatus(),
                "endTime", processInstance.getEndTime(),
                "canceledTaskIds", todoTasks.stream().map(Task::getId).toList()
        ));
        return buildAdminOperationRecordVO(insertSuccessRecord(processInstance.getId(), null,
                WorkflowConstants.AdminOperationType.TERMINATE, reason, beforeSnapshot, afterSnapshot, context));
    }

    private AdminOperationRecordVO handleResendNotice(AdminMonitorActionReq req, RequestContext context) {
        String tenantId = RuntimeSupport.requireTenantId(context);
        ProcessInstance processInstance = requireMaintainableInstance(req == null ? null : req.getProcessInstanceId(), tenantId);
        String reason = requireReason(req);
        List<Task> tasks = StringUtils.hasText(req == null ? null : req.getTaskId())
                ? List.of(requireTodoTask(req.getTaskId(), processInstance.getId(), tenantId))
                : listCurrentTasks(processInstance.getId(), tenantId);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("当前流程没有可补发通知的待办");
        }
        Map<String, List<String>> receiversByTaskId = new HashMap<>();
        for (Task task : tasks) {
            List<String> receivers = listTaskNoticeReceivers(task, tenantId);
            if (!receivers.isEmpty()) {
                taskNotificationService.sendTodoArrivalMessage(processInstance, task, receivers, context);
            }
            receiversByTaskId.put(task.getId(), receivers);
        }
        return buildAdminOperationRecordVO(insertSuccessRecord(processInstance.getId(),
                tasks.size() == 1 ? tasks.get(0).getId() : null,
                WorkflowConstants.AdminOperationType.RESEND_NOTICE,
                reason,
                toJson(Map.of("taskIds", tasks.stream().map(Task::getId).toList())),
                toJson(Map.of("receiversByTaskId", receiversByTaskId)),
                context));
    }

    private BaseResult<AdminOperationRecordVO> executeMaintenance(AdminMonitorActionReq req, String operationType,
            Supplier<AdminOperationRecordVO> action, RequestContext context) {
        try {
            // 维护动作失败时必须回滚业务改动，再单独写失败记录，避免出现半改派或半终止状态。
            AdminOperationRecordVO record = transactionTemplate.execute(status -> {
                try {
                    return action.get();
                } catch (RuntimeException e) {
                    status.setRollbackOnly();
                    throw e;
                }
            });
            return BaseResult.success(record);
        } catch (IllegalArgumentException e) {
            insertFailedRecord(req, operationType, e.getMessage(), context);
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("流程监控维护失败", e);
            insertFailedRecord(req, operationType, "流程监控维护失败", context);
            return BaseResult.error("流程监控维护失败");
        }
    }

    private <T> BaseResult<T> systemError(String message, Exception e) {
        log.error(message, e);
        return BaseResult.error(message);
    }

    private AdminOperationRecord insertSuccessRecord(String processInstanceId, String taskId, String operationType,
            String reason, String beforeSnapshotJson, String afterSnapshotJson, RequestContext context) {
        AdminOperationRecord record = new AdminOperationRecord();
        record.setTenantId(RuntimeSupport.requireTenantId(context));
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setOperationType(operationType);
        record.setOperationReason(reason);
        record.setBeforeSnapshotJson(beforeSnapshotJson);
        record.setAfterSnapshotJson(afterSnapshotJson);
        fillOperator(record, context);
        record.setOperateTime(LocalDateTime.now());
        record.setStatus(WorkflowConstants.AdminOperationStatus.SUCCESS);
        EntityFillUtils.fillAuditFields(record, context, true);
        adminOperationRecordMapper.insert(record);
        return record;
    }

    private void insertFailedRecord(AdminMonitorActionReq req, String operationType, String errorMessage,
            RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getTenantId()) || req == null
                || !StringUtils.hasText(req.getProcessInstanceId())) {
            return;
        }
        AdminOperationRecord record = new AdminOperationRecord();
        record.setTenantId(context.getTenantId());
        record.setProcessInstanceId(req.getProcessInstanceId());
        record.setTaskId(req.getTaskId());
        record.setOperationType(operationType);
        record.setOperationReason(StringUtils.hasText(req.getOperationReason()) ? req.getOperationReason() : "维护失败");
        record.setBeforeSnapshotJson(toJson(Map.of("request", req)));
        fillOperator(record, context);
        record.setOperateTime(LocalDateTime.now());
        record.setStatus(WorkflowConstants.AdminOperationStatus.FAILED);
        record.setErrorMessage(truncate(errorMessage, 500));
        EntityFillUtils.fillAuditFields(record, context, true);
        adminOperationRecordMapper.insert(record);
    }

    private void fillOperator(AdminOperationRecord record, RequestContext context) {
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(resolveOperatorRealname(context));
    }

    private String resolveOperatorRealname(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getUserId())) {
            return context == null ? null : context.getUsername();
        }
        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("id", context.getUserId())
                .eq("delete_flag", 0));
        if (user == null || !StringUtils.hasText(user.getRealname())) {
            return context.getUsername();
        }
        return user.getRealname();
    }

    private ProcessInstance requireMaintainableInstance(String processInstanceId, String tenantId) {
        ProcessInstance processInstance = requireProcessInstance(processInstanceId, tenantId);
        if (!isMaintainable(processInstance)) {
            throw new IllegalArgumentException("当前流程状态不允许维护");
        }
        return processInstance;
    }

    private ProcessInstance requireProcessInstance(String processInstanceId, String tenantId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        return processInstance;
    }

    private FormInstance requireFormInstance(String formInstanceId, String tenantId) {
        FormInstance formInstance = findFormInstance(formInstanceId, tenantId);
        if (formInstance == null) {
            throw new IllegalArgumentException("表单实例不存在");
        }
        return formInstance;
    }

    private FormInstance findFormInstance(String formInstanceId, String tenantId) {
        if (!StringUtils.hasText(formInstanceId)) {
            return null;
        }
        return formInstanceMapper.selectOne(new QueryWrapper<FormInstance>()
                .eq("id", formInstanceId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
    }

    private Task requireTodoTask(String taskId, String processInstanceId, String tenantId) {
        if (!StringUtils.hasText(taskId)) {
            // 列表行只承载实例级入口；只有单待办实例可以安全自动定位，多待办必须显式选择任务。
            List<Task> todoTasks = listCurrentTasks(processInstanceId, tenantId);
            if (todoTasks.size() == 1) {
                return todoTasks.get(0);
            }
            throw new IllegalArgumentException("当前流程存在多个待办，请选择具体任务后再维护");
        }
        Task task = taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("id", taskId)
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0));
        if (task == null) {
            throw new IllegalArgumentException("当前待办任务不存在或已处理");
        }
        return task;
    }

    /**
     * 会签/或签会为每个办理人生成独立业务待办，但 Flowable 只有一个真实用户任务。
     * 管理员改派具体业务待办时，需要使用同组锚点任务同步 Flowable，避免把 group: 本地标识传给引擎。
     */
    private Task resolveFlowableAnchorTask(Task task, String tenantId) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return task;
        }
        Task anchor = taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("task_group_id", task.getTaskGroupId())
                .notLikeRight("flowable_task_id", "group:")
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (anchor == null) {
            throw new IllegalArgumentException("会签/或签任务缺少Flowable锚点任务");
        }
        return anchor;
    }

    /**
     * 会签/或签同组内同一用户只能持有一条有效待办，否则会出现一个人代表多票的歧义。
     */
    private void ensureReassignTargetAvailable(Task task, User targetUser) {
        if (targetUser.getId().equals(task.getAssigneeUserId())) {
            throw new IllegalArgumentException("目标处理人已是当前待办处理人");
        }
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return;
        }
        Long existingCount = taskMapper.selectCount(new QueryWrapper<Task>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_group_id", task.getTaskGroupId())
                .eq("assignee_user_id", targetUser.getId())
                .eq("status", WorkflowConstants.Status.TODO)
                .ne("id", task.getId())
                .eq("delete_flag", 0));
        if (existingCount != null && existingCount > 0) {
            throw new IllegalArgumentException("该人员已存在当前会签/或签待办，不能重复改派");
        }
    }

    private User requireActiveTenantUser(String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("目标处理人不能为空");
        }
        // 改派不能把待办派给其它租户或已停用用户，后端必须重新校验人员边界。
        List<String> tenantUserIds = userService.getTenantUserIds(tenantId);
        if (tenantUserIds == null || !tenantUserIds.contains(userId)) {
            throw new IllegalArgumentException("目标处理人不属于当前租户");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("id", userId)
                .eq("status", 1)
                .eq("delete_flag", 0));
        if (user == null) {
            throw new IllegalArgumentException("目标处理人不存在或已停用");
        }
        return user;
    }

    private String requireReason(AdminMonitorActionReq req) {
        if (req == null || !StringUtils.hasText(req.getOperationReason())) {
            throw new IllegalArgumentException("维护原因不能为空");
        }
        return req.getOperationReason().trim();
    }

    private boolean isMaintainable(ProcessInstance processInstance) {
        return processInstance != null && WorkflowConstants.Status.RUNNING.equals(processInstance.getStatus());
    }

    private List<Task> listCurrentTasks(String processInstanceId, String tenantId) {
        return taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
    }

    private List<OperationRecord> listApprovalRecords(String processInstanceId, String tenantId) {
        return operationRecordMapper.selectList(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByAsc("operate_time")
                .orderByAsc("create_time"));
    }

    private List<CcRecord> listCcRecords(String processInstanceId, String tenantId) {
        return ccRecordMapper.selectList(new QueryWrapper<CcRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByDesc("create_time"));
    }

    private List<AdminOperationRecord> listAdminRecords(String processInstanceId, String tenantId) {
        return adminOperationRecordMapper.selectList(new QueryWrapper<AdminOperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByAsc("operate_time")
                .orderByAsc("create_time"));
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

    private void cancelTodoTasks(List<Task> todoTasks, String tenantId, RequestContext context) {
        if (todoTasks == null || todoTasks.isEmpty()) {
            return;
        }
        List<String> taskIds = todoTasks.stream().map(Task::getId).toList();
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .in("id", taskIds)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("complete_time", LocalDateTime.now())
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
        taskNotificationService.expireTodoMessageActions(taskIds, tenantId, context);
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", taskIds)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private List<String> listTaskNoticeReceivers(Task task, String tenantId) {
        List<String> candidateUserIds = taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .select("candidate_user_id")
                        .eq("tenant_id", tenantId)
                        .eq("task_id", task.getId())
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .stream()
                .map(TaskCandidate::getCandidateUserId)
                .filter(StringUtils::hasText)
                .toList();
        if (!candidateUserIds.isEmpty()) {
            return candidateUserIds;
        }
        return StringUtils.hasText(task.getAssigneeUserId()) ? List.of(task.getAssigneeUserId()) : List.of();
    }

    private Long countTodoTasks(String processInstanceId, String tenantId) {
        return taskMapper.selectCount(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0));
    }

    private Map<String, ProcessModel> buildProcessModelMap(List<ProcessInstance> instances, String tenantId) {
        List<String> processModelIds = instances.stream()
                .map(ProcessInstance::getProcessModelId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (processModelIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ProcessModel> map = new HashMap<>();
        processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .in("id", processModelIds)
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .forEach(model -> map.put(model.getId(), model));
        return map;
    }

    /**
     * 流程监控左侧树按“最新发布定义”展示，但实例查询必须覆盖同一流程编码下的所有历史版本。
     */
    private List<String> listMatchedProcessModelIds(AdminMonitorPageReq req, String tenantId) {
        String categoryId = req == null ? null : req.getCategoryId();
        String processKey = req == null ? null : req.getProcessKey();
        String processName = req == null ? null : req.getProcessName();
        Integer processVersion = req == null ? null : req.getProcessVersion();
        if (!StringUtils.hasText(categoryId) && !StringUtils.hasText(processKey)
                && !StringUtils.hasText(processName) && processVersion == null) {
            return null;
        }
        QueryWrapper<ProcessModel> wrapper = new QueryWrapper<ProcessModel>()
                .select("id")
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        if (StringUtils.hasText(categoryId)) {
            List<String> categoryProcessKeys = listPublishedProcessKeysByCategory(categoryId, tenantId);
            if (categoryProcessKeys.isEmpty()) {
                return List.of();
            }
            wrapper.in("process_key", categoryProcessKeys);
        }
        if (StringUtils.hasText(processKey)) {
            wrapper.eq("process_key", processKey);
        }
        if (StringUtils.hasText(processName)) {
            wrapper.like("process_name", processName);
        }
        if (processVersion != null) {
            wrapper.eq("version", processVersion);
        }
        return processModelMapper.selectList(wrapper).stream()
                .map(ProcessModel::getId)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> listPublishedProcessKeysByCategory(String categoryId, String tenantId) {
        return processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .select("process_key")
                        .eq("tenant_id", tenantId)
                        .eq("category_id", categoryId)
                        .eq("status", WorkflowConstants.Status.PUBLISHED)
                        .eq("delete_flag", 0))
                .stream()
                .map(ProcessModel::getProcessKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private void applyMonitorPageOrder(QueryWrapper<ProcessInstance> wrapper, AdminMonitorPageReq req) {
        if (applyAllowedSorting(wrapper, req == null ? null : req.getSortField(),
                req == null ? null : req.getSortOrder())) {
            return;
        }
        wrapper.orderByDesc("update_time").orderByDesc("start_time").orderByDesc("create_time");
    }

    private boolean applyAllowedSorting(QueryWrapper<ProcessInstance> wrapper, String sortField, String sortOrder) {
        if (!StringUtils.hasText(sortField)) {
            return false;
        }
        String[] fields = sortField.split(",");
        String[] orders = StringUtils.hasText(sortOrder) ? sortOrder.split(",") : new String[]{"desc"};
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i].trim();
            if (!StringUtils.hasText(field)) {
                continue;
            }
            String column = MONITOR_SORT_FIELDS.get(field);
            if (!StringUtils.hasText(column)) {
                throw new IllegalArgumentException("不支持的排序字段");
            }
            String order = i < orders.length ? orders[i].trim() : orders[orders.length - 1].trim();
            if ("asc".equalsIgnoreCase(order)) {
                wrapper.orderByAsc(column);
            } else if ("desc".equalsIgnoreCase(order)) {
                wrapper.orderByDesc(column);
            } else {
                throw new IllegalArgumentException("不支持的排序方向");
            }
        }
        return true;
    }

    private LocalDateTime parseDateTime(String value, String message) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignored) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private AdminOperationRecordVO buildAdminOperationRecordVO(AdminOperationRecord record) {
        AdminOperationRecordVO vo = new AdminOperationRecordVO();
        vo.setId(record.getId());
        vo.setCreateBy(record.getCreateBy());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setUpdateTime(record.getUpdateTime());
        vo.setTenantId(record.getTenantId());
        vo.setProcessInstanceId(record.getProcessInstanceId());
        vo.setTaskId(record.getTaskId());
        vo.setOperationType(record.getOperationType());
        vo.setOperationReason(record.getOperationReason());
        vo.setBeforeSnapshotJson(record.getBeforeSnapshotJson());
        vo.setAfterSnapshotJson(record.getAfterSnapshotJson());
        vo.setOperatorUserId(record.getOperatorUserId());
        vo.setOperatorUsername(record.getOperatorUsername());
        vo.setOperatorRealname(record.getOperatorRealname());
        vo.setOperateTime(record.getOperateTime());
        vo.setStatus(record.getStatus());
        vo.setErrorMessage(record.getErrorMessage());
        return vo;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
