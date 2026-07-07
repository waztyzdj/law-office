package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.AssigneePreviewReq;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.dto.BranchMatchResult;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IConditionBranchRuntimeService;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.service.IRuntimeQueryService;
import com.lawoffice.workflow.service.IRuntimeViewAssemblerService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RuntimeQueryServiceImpl implements IRuntimeQueryService {

    private static final List<String> DONE_TASK_STATUSES = List.of(
            WorkflowConstants.Status.DONE,
            WorkflowConstants.Status.RETURNED,
            WorkflowConstants.Status.TRANSFERRED
    );
    private static final String TASK_INSTANCE_TITLE_SORT_SQL =
            "(select pi.instance_title from wf_process_instance pi "
                    + "where pi.id = wf_task.process_instance_id "
                    + "and pi.tenant_id = wf_task.tenant_id and pi.delete_flag = 0)";
    private static final String TASK_INSTANCE_START_TIME_SORT_SQL =
            "(select pi.start_time from wf_process_instance pi "
                    + "where pi.id = wf_task.process_instance_id "
                    + "and pi.tenant_id = wf_task.tenant_id and pi.delete_flag = 0)";
    private static final String TASK_INSTANCE_STARTER_REALNAME_SORT_SQL =
            "(select pi.starter_realname from wf_process_instance pi "
                    + "where pi.id = wf_task.process_instance_id "
                    + "and pi.tenant_id = wf_task.tenant_id and pi.delete_flag = 0)";
    private static final String DONE_TASK_INSTANCE_TITLE_SORT_SQL =
            "(select pi.instance_title from wf_process_instance pi "
                    + "where pi.id = t.process_instance_id "
                    + "and pi.tenant_id = t.tenant_id and pi.delete_flag = 0)";
    private static final String DONE_TASK_INSTANCE_START_TIME_SORT_SQL =
            "(select pi.start_time from wf_process_instance pi "
                    + "where pi.id = t.process_instance_id "
                    + "and pi.tenant_id = t.tenant_id and pi.delete_flag = 0)";
    private static final String DONE_TASK_INSTANCE_STARTER_REALNAME_SORT_SQL =
            "(select pi.starter_realname from wf_process_instance pi "
                    + "where pi.id = t.process_instance_id "
                    + "and pi.tenant_id = t.tenant_id and pi.delete_flag = 0)";
    private static final String STARTED_PROCESS_NAME_SORT_SQL =
            "(select pm.process_name from wf_process_model pm "
                    + "where pm.id = wf_process_instance.process_model_id "
                    + "and pm.tenant_id = wf_process_instance.tenant_id and pm.delete_flag = 0)";
    private static final Map<String, String> TASK_SORT_FIELDS = Map.ofEntries(
            Map.entry("instanceTitle", TASK_INSTANCE_TITLE_SORT_SQL),
            Map.entry("taskName", "task_name"),
            Map.entry("taskType", "task_type"),
            Map.entry("approvalMode", "approval_mode"),
            Map.entry("starterRealname", TASK_INSTANCE_STARTER_REALNAME_SORT_SQL),
            Map.entry("assigneeRealname", "assignee_realname"),
            Map.entry("startTime", TASK_INSTANCE_START_TIME_SORT_SQL),
            Map.entry("status", "status"),
            Map.entry("completeTime", "complete_time"),
            Map.entry("createTime", "create_time"),
            Map.entry("updateTime", "update_time")
    );
    private static final Map<String, String> DONE_TASK_SORT_FIELDS = Map.ofEntries(
            Map.entry("instanceTitle", DONE_TASK_INSTANCE_TITLE_SORT_SQL),
            Map.entry("taskName", "t.task_name"),
            Map.entry("taskType", "t.task_type"),
            Map.entry("approvalMode", "t.approval_mode"),
            Map.entry("starterRealname", DONE_TASK_INSTANCE_STARTER_REALNAME_SORT_SQL),
            Map.entry("assigneeRealname", "t.assignee_realname"),
            Map.entry("startTime", DONE_TASK_INSTANCE_START_TIME_SORT_SQL),
            Map.entry("status", "t.status"),
            Map.entry("completeTime", "t.complete_time"),
            Map.entry("createTime", "t.create_time"),
            Map.entry("updateTime", "t.update_time")
    );
    private static final Map<String, String> STARTED_INSTANCE_SORT_FIELDS = Map.ofEntries(
            Map.entry("instanceTitle", "instance_title"),
            Map.entry("processName", STARTED_PROCESS_NAME_SORT_SQL),
            Map.entry("currentTaskNames", "current_task_names"),
            Map.entry("currentAssigneeNames", "current_assignee_names"),
            Map.entry("startTime", "start_time"),
            Map.entry("endTime", "end_time"),
            Map.entry("status", "status"),
            Map.entry("createTime", "create_time"),
            Map.entry("updateTime", "update_time")
    );

    private final ProcessModelMapper processModelMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final CcRecordMapper ccRecordMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IProcessNodeConfigService processNodeConfigService;
    private final IConditionBranchRuntimeService conditionBranchRuntimeService;
    private final IAssigneeResolveService assigneeResolveService;
    private final IRuntimeAccessService runtimeAccessService;
    private final IRuntimeViewAssemblerService runtimeViewAssemblerService;
    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    private final IUserService userService;

    public RuntimeQueryServiceImpl(ProcessModelMapper processModelMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            ProcessInstanceMapper processInstanceMapper,
            CcRecordMapper ccRecordMapper,
            OperationRecordMapper operationRecordMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IProcessNodeConfigService processNodeConfigService,
            IConditionBranchRuntimeService conditionBranchRuntimeService,
            IAssigneeResolveService assigneeResolveService,
            IRuntimeAccessService runtimeAccessService,
            IRuntimeViewAssemblerService runtimeViewAssemblerService,
            IWorkflowRuntimeLookupService workflowRuntimeLookupService,
            IUserService userService) {
        this.processModelMapper = processModelMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.ccRecordMapper = ccRecordMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.processNodeConfigService = processNodeConfigService;
        this.conditionBranchRuntimeService = conditionBranchRuntimeService;
        this.assigneeResolveService = assigneeResolveService;
        this.runtimeAccessService = runtimeAccessService;
        this.runtimeViewAssemblerService = runtimeViewAssemblerService;
        this.workflowRuntimeLookupService = workflowRuntimeLookupService;
        this.userService = userService;
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
                    .ne("flowable_process_definition_id", "")
                    .notExists("select 1 from wf_process_model newer "
                            + "where newer.tenant_id = wf_process_model.tenant_id "
                            + "and newer.process_key = wf_process_model.process_key "
                            + "and newer.status = 'published' "
                            + "and newer.delete_flag = 0 "
                            + "and newer.version > wf_process_model.version");
            if (req != null && StringUtils.hasText(req.getCategoryId())) {
                wrapper.eq("category_id", req.getCategoryId());
            }
            if (req != null && StringUtils.hasText(req.getProcessName())) {
                wrapper.like("process_name", req.getProcessName());
            }
            if (req != null && StringUtils.hasText(req.getProcessKey())) {
                wrapper.like("process_key", req.getProcessKey());
            }
            if (req != null && req.getProcessVersion() != null) {
                wrapper.eq("version", req.getProcessVersion());
            }
            if (req != null && StringUtils.hasText(req.getDesignerType())) {
                wrapper.eq("designer_type", req.getDesignerType());
            }
            if (req != null && StringUtils.hasText(req.getStartScopeType())) {
                wrapper.eq("start_scope_type", req.getStartScopeType());
            }
            List<String> matchedFormIds = listMatchedFormDefinitionIds(req, tenantId);
            if (matchedFormIds != null) {
                if (matchedFormIds.isEmpty()) {
                    return BaseResult.success(PageVO.empty(pageNum, pageSize));
                }
                wrapper.in("form_definition_id", matchedFormIds);
            }
            if (req != null && StringUtils.hasText(req.getPublishedTimeGe())) {
                wrapper.ge("published_time", parseDateTime(req.getPublishedTimeGe(), "发布时间开始值不合法"));
            }
            if (req != null && StringUtils.hasText(req.getPublishedTimeLe())) {
                wrapper.le("published_time", parseDateTime(req.getPublishedTimeLe(), "发布时间结束值不合法"));
            }
            wrapper.and(condition -> {
                condition.eq("start_scope_type", WorkflowConstants.StartScopeType.ALL);
                if (!specifiedProcessIds.isEmpty()) {
                    condition.or().in("id", specifiedProcessIds);
                }
            });
            wrapper.orderByDesc("published_time").orderByDesc("create_time");

            Page<ProcessModel> page = processModelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            List<AvailableProcessVO> records = runtimeViewAssemblerService.buildAvailableProcessRecords(page.getRecords(), tenantId);
            return BaseResult.success(new PageVO<>(
                    records,
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
            return BaseResult.success(runtimeViewAssemblerService.buildStartForm(model, form,
                    listFieldPermissions(model.getId(), WorkflowConstants.VirtualNode.START, model.getTenantId()), context));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("获取发起表单失败: " + e.getMessage());
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
            if (req != null && StringUtils.hasText(req.getInstanceNo())) {
                wrapper.like("instance_no", req.getInstanceNo());
            }
            if (req != null && StringUtils.hasText(req.getProcessName())) {
                wrapper.like("process_name", req.getProcessName());
            }
            if (req != null && StringUtils.hasText(req.getCurrentTaskNames())) {
                wrapper.like("current_task_names", req.getCurrentTaskNames());
            }
            if (req != null && StringUtils.hasText(req.getCurrentAssigneeNames())) {
                wrapper.like("current_assignee_names", req.getCurrentAssigneeNames());
            }
            if (req != null && StringUtils.hasText(req.getStartTimeGe())) {
                wrapper.ge("start_time", parseDateTime(req.getStartTimeGe(), "发起时间开始值不合法"));
            }
            if (req != null && StringUtils.hasText(req.getStartTimeLe())) {
                wrapper.le("start_time", parseDateTime(req.getStartTimeLe(), "发起时间结束值不合法"));
            }
            if (req != null && StringUtils.hasText(req.getEndTimeGe())) {
                wrapper.ge("end_time", parseDateTime(req.getEndTimeGe(), "结束时间开始值不合法"));
            }
            if (req != null && StringUtils.hasText(req.getEndTimeLe())) {
                wrapper.le("end_time", parseDateTime(req.getEndTimeLe(), "结束时间结束值不合法"));
            }
            applyStartedInstancePageOrder(wrapper, req);

            Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            return BaseResult.success(new PageVO<>(
                    runtimeViewAssemblerService.buildStartedInstanceRecords(page.getRecords(), tenantId),
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
            return BaseResult.success(pageLatestDoneTasks(req, context));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询已办失败: " + e.getMessage());
        }
    }

    @Override
    public long countTodoTasks(RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        List<String> candidateTaskIds = listCandidateTaskIds(tenantId, userId, WorkflowConstants.Status.ACTIVE);
        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("delete_flag", 0)
                .eq("status", WorkflowConstants.Status.TODO)
                .and(condition -> {
                    condition.eq("assignee_user_id", userId);
                    if (!candidateTaskIds.isEmpty()) {
                        condition.or().in("id", candidateTaskIds);
                    }
                });
        Long count = taskMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public long countDoneTasks(RequestContext context) {
        return taskMapper.countDoneProcessInstances(
                requireTenantId(context),
                requireUserId(context),
                WorkflowConstants.Status.CLAIMED,
                DONE_TASK_STATUSES);
    }

    @Override
    public BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            Task task = requireTodoTask(taskId, tenantId);
            ensureTaskHandler(task, findActiveCandidate(task, context), context);
            ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            boolean startDraftTask = WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType());
            List<FieldPermission> permissions = startDraftTask
                    ? listFieldPermissions(processInstance.getProcessModelId(), WorkflowConstants.VirtualNode.START, tenantId)
                    : listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
            ProcessNodeConfig nodeConfig = startDraftTask
                    ? processNodeConfigService.buildStartDraftNodeConfig()
                    : processNodeConfigService.requireRuntimeNodeConfig(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
            return BaseResult.success(runtimeViewAssemblerService.buildTaskForm(
                    task, processInstance, formInstance, permissions, nodeConfig));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("获取任务表单失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<List<AssigneeSelectNodeVO>> previewNextAssigneeSelectNodes(AssigneePreviewReq req,
            RequestContext context) {
        try {
            if (req == null) {
                throw new IllegalArgumentException("预判请求不能为空");
            }
            String tenantId = requireTenantId(context);
            if (StringUtils.hasText(req.getTaskId())) {
                return BaseResult.success(previewTaskNextAssigneeSelectNodes(req, tenantId, context));
            }
            if (StringUtils.hasText(req.getProcessModelId())) {
                return BaseResult.success(previewStartNextAssigneeSelectNodes(req, tenantId, context));
            }
            throw new IllegalArgumentException("流程模型ID或任务ID不能为空");
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("预判下一审批人失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            ProcessInstance processInstance = requireProcessInstance(id, tenantId);
            runtimeAccessService.ensureInstanceAccess(processInstance, context);
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            List<Task> currentTasks = listCurrentTasks(processInstance.getId(), tenantId);
            List<OperationRecord> records = listOperationRecords(processInstance.getId(), tenantId);
            List<CcRecord> ccRecords = listCcRecords(processInstance.getId(), tenantId);
            return BaseResult.success(runtimeViewAssemblerService.buildInstanceDetail(
                    processInstance, formInstance, currentTasks, records, ccRecords, context));
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
            runtimeAccessService.ensureInstanceAccess(processInstance, context);
            return BaseResult.success(listOperationRecords(processInstance.getId(), tenantId).stream()
                    .map(runtimeViewAssemblerService::buildOperationRecordVO)
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
     * 审批详情页展示当前实例的全部有效抄送记录；“我的抄送”列表仍由抄送运行时服务按接收人过滤。
     */
    private List<CcRecord> listCcRecords(String processInstanceId, String tenantId) {
        return ccRecordMapper.selectList(new QueryWrapper<CcRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByDesc("create_time"));
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
                .eq("delete_flag", 0);
        if (req != null && StringUtils.hasText(req.getStatus())) {
            wrapper.eq("status", req.getStatus());
        } else if (WorkflowConstants.Status.DONE.equals(status)) {
            wrapper.in("status", DONE_TASK_STATUSES);
        } else {
            wrapper.eq("status", status);
        }
        if (req != null && StringUtils.hasText(req.getProcessInstanceId())) {
            wrapper.eq("process_instance_id", req.getProcessInstanceId());
        }
        if (req != null && StringUtils.hasText(req.getTaskType())) {
            wrapper.eq("task_type", req.getTaskType());
        }
        if (req != null && StringUtils.hasText(req.getApprovalMode())) {
            wrapper.eq("approval_mode", req.getApprovalMode());
        }
        if (req != null && StringUtils.hasText(req.getTaskName())) {
            wrapper.like("task_name", req.getTaskName());
        }
        if (req != null && StringUtils.hasText(req.getAssigneeRealname())) {
            wrapper.like("assignee_realname", req.getAssigneeRealname());
        }
        if (req != null && StringUtils.hasText(req.getCompleteTimeGe())) {
            wrapper.ge("complete_time", parseDateTime(req.getCompleteTimeGe(), "办理时间开始值不合法"));
        }
        if (req != null && StringUtils.hasText(req.getCompleteTimeLe())) {
            wrapper.le("complete_time", parseDateTime(req.getCompleteTimeLe(), "办理时间结束值不合法"));
        }
        if (req != null && (StringUtils.hasText(req.getInstanceTitle())
                || StringUtils.hasText(req.getInstanceNo())
                || StringUtils.hasText(req.getStarterRealname())
                || StringUtils.hasText(req.getStartTimeGe())
                || StringUtils.hasText(req.getStartTimeLe()))) {
            QueryWrapper<ProcessInstance> instanceWrapper = new QueryWrapper<>();
            instanceWrapper.select("id")
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0);
            if (StringUtils.hasText(req.getInstanceTitle())) {
                instanceWrapper.like("instance_title", req.getInstanceTitle());
            }
            if (StringUtils.hasText(req.getInstanceNo())) {
                instanceWrapper.like("instance_no", req.getInstanceNo());
            }
            if (StringUtils.hasText(req.getStarterRealname())) {
                instanceWrapper.like("starter_realname", req.getStarterRealname());
            }
            if (StringUtils.hasText(req.getStartTimeGe())) {
                instanceWrapper.ge("start_time", parseDateTime(req.getStartTimeGe(), "发起时间开始值不合法"));
            }
            if (StringUtils.hasText(req.getStartTimeLe())) {
                instanceWrapper.le("start_time", parseDateTime(req.getStartTimeLe(), "发起时间结束值不合法"));
            }
            List<String> matchedInstanceIds = processInstanceMapper.selectList(instanceWrapper)
                    .stream()
                    .map(ProcessInstance::getId)
                    .filter(StringUtils::hasText)
                    .toList();
            if (matchedInstanceIds.isEmpty()) {
                return new PageVO<>(List.of(), 0, pageNum, pageSize);
            }
            wrapper.in("process_instance_id", matchedInstanceIds);
        }
        wrapper.and(condition -> {
            condition.eq("assignee_user_id", userId);
            if (!candidateTaskIds.isEmpty()) {
                condition.or().in("id", candidateTaskIds);
            }
        });
        applyTaskPageOrder(wrapper, req, status);

        Page<Task> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RuntimeTaskVO> records = runtimeViewAssemblerService.buildRuntimeTaskRecords(page.getRecords(), tenantId);
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private PageVO<RuntimeTaskVO> pageLatestDoneTasks(TaskPageReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
        int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
        LocalDateTime startTimeGe = parseOptionalDateTime(req == null ? null : req.getStartTimeGe(), "发起时间开始值不合法");
        LocalDateTime startTimeLe = parseOptionalDateTime(req == null ? null : req.getStartTimeLe(), "发起时间结束值不合法");
        LocalDateTime completeTimeGe = parseOptionalDateTime(req == null ? null : req.getCompleteTimeGe(), "办理时间开始值不合法");
        LocalDateTime completeTimeLe = parseOptionalDateTime(req == null ? null : req.getCompleteTimeLe(), "办理时间结束值不合法");
        boolean hasInstanceFilters = hasInstanceFilters(req) || startTimeGe != null || startTimeLe != null;
        String orderBySql = buildDoneTaskOrderBySql(req);

        IPage<Task> page = taskMapper.selectLatestDonePage(new Page<>(pageNum, pageSize),
                tenantId,
                userId,
                WorkflowConstants.Status.CLAIMED,
                DONE_TASK_STATUSES,
                req,
                startTimeGe,
                startTimeLe,
                completeTimeGe,
                completeTimeLe,
                hasInstanceFilters,
                orderBySql);
        List<RuntimeTaskVO> records = runtimeViewAssemblerService.buildRuntimeTaskRecords(page.getRecords(), tenantId);
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 我发起的列表默认按流程最近一次状态变化时间排序；点击列头时只允许排序白名单字段进入 SQL。
     */
    private void applyStartedInstancePageOrder(QueryWrapper<ProcessInstance> wrapper, StartedInstancePageReq req) {
        if (applyAllowedSorting(wrapper, req == null ? null : req.getSortField(),
                req == null ? null : req.getSortOrder(), STARTED_INSTANCE_SORT_FIELDS)) {
            return;
        }
        wrapper.orderByDesc("update_time").orderByDesc("start_time").orderByDesc("create_time");
    }

    /**
     * 待办默认按任务到达时间倒序，已办默认按任务办理完成时间倒序。
     */
    private void applyTaskPageOrder(QueryWrapper<Task> wrapper, TaskPageReq req, String pageStatus) {
        if (applyAllowedSorting(wrapper, req == null ? null : req.getSortField(),
                req == null ? null : req.getSortOrder(), TASK_SORT_FIELDS)) {
            return;
        }
        if (WorkflowConstants.Status.TODO.equals(pageStatus)) {
            wrapper.orderByDesc("create_time");
            return;
        }
        wrapper.orderByDesc("complete_time").orderByDesc("update_time").orderByDesc("create_time");
    }

    /**
     * 已办列表先在 SQL 层按流程实例去重，再分页。排序字段必须从白名单映射为固定 SQL 片段，
     * 不能把前端字段名直接拼入 order by。
     */
    private String buildDoneTaskOrderBySql(TaskPageReq req) {
        String sortField = req == null ? null : req.getSortField();
        if (!StringUtils.hasText(sortField)) {
            return "t.complete_time desc, t.update_time desc, t.create_time desc, t.id desc";
        }
        String[] fields = sortField.split(",");
        String sortOrder = req == null ? null : req.getSortOrder();
        String[] orders = StringUtils.hasText(sortOrder) ? sortOrder.split(",") : new String[]{"desc"};
        List<String> orderItems = new java.util.ArrayList<>();
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i].trim();
            if (!StringUtils.hasText(field)) {
                continue;
            }
            String column = DONE_TASK_SORT_FIELDS.get(field);
            if (!StringUtils.hasText(column)) {
                throw new IllegalArgumentException("不支持的排序字段");
            }
            String order = i < orders.length ? orders[i].trim() : orders[orders.length - 1].trim();
            if ("asc".equalsIgnoreCase(order)) {
                orderItems.add(column + " asc");
            } else if ("desc".equalsIgnoreCase(order)) {
                orderItems.add(column + " desc");
            } else {
                throw new IllegalArgumentException("不支持的排序方向");
            }
        }
        if (orderItems.isEmpty()) {
            return "t.complete_time desc, t.update_time desc, t.create_time desc, t.id desc";
        }
        orderItems.add("t.id desc");
        return String.join(", ", orderItems);
    }

    /**
     * 运行时列表是自定义查询，不能直接复用通用 QueryWrapperBuilder 的无白名单排序。
     */
    private <T> boolean applyAllowedSorting(QueryWrapper<T> wrapper, String sortField, String sortOrder,
            Map<String, String> allowedFields) {
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
            String column = allowedFields.get(field);
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

    private List<String> listMatchedFormDefinitionIds(AvailableProcessPageReq req, String tenantId) {
        if (req == null || (!StringUtils.hasText(req.getFormName()) && req.getFormVersion() == null)) {
            return null;
        }
        QueryWrapper<FormDefinition> wrapper = new QueryWrapper<>();
        wrapper.select("id")
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        if (StringUtils.hasText(req.getFormName())) {
            wrapper.like("form_name", req.getFormName());
        }
        if (req.getFormVersion() != null) {
            wrapper.eq("version", req.getFormVersion());
        }
        return formDefinitionMapper.selectList(wrapper)
                .stream()
                .map(FormDefinition::getId)
                .filter(StringUtils::hasText)
                .toList();
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

    private LocalDateTime parseOptionalDateTime(String value, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return parseDateTime(value, message);
    }

    private boolean hasInstanceFilters(TaskPageReq req) {
        return req != null && (StringUtils.hasText(req.getInstanceTitle())
                || StringUtils.hasText(req.getInstanceNo())
                || StringUtils.hasText(req.getStarterRealname()));
    }

    private Task requireTodoTask(String taskId, String tenantId) {
        return workflowRuntimeLookupService.requireTodoTask(taskId, tenantId);
    }

    /**
     * 发起前根据当前表单数据预判真实首个审批节点，条件分支命中时只返回命中目标节点的审批人选择项。
     */
    private List<AssigneeSelectNodeVO> previewStartNextAssigneeSelectNodes(AssigneePreviewReq req,
            String tenantId, RequestContext context) {
        requireUserId(context);
        ProcessModel model = requirePublishedModel(req.getProcessModelId(), tenantId);
        checkStartPermission(model, context);
        ProcessInstance processInstance = buildPreviewProcessInstance(model, context);
        FormInstance formInstance = buildPreviewFormInstance(req.getFormDataJson());
        Optional<BranchMatchResult> branchMatch = conditionBranchRuntimeService.previewNextBranch(
                model, processInstance, formInstance, WorkflowConstants.VirtualNode.START, null, tenantId, context);
        if (branchMatch.isPresent()) {
            return assigneeResolveService.buildAssigneeSelectNodesForNode(
                    processInstance, branchMatch.get().getTargetNodeId(), tenantId);
        }
        return assigneeResolveService.buildRequiredAssigneeSelectNodes(
                model.getId(), processInstance, tenantId, WorkflowConstants.VirtualNode.START_DRAFT);
    }

    /**
     * 审批通过前按最新表单数据预判真实下一审批节点，避免条件分支场景继续按静态顺序展示审批人选择弹窗。
     */
    private List<AssigneeSelectNodeVO> previewTaskNextAssigneeSelectNodes(AssigneePreviewReq req,
            String tenantId, RequestContext context) {
        Task task = requireTodoTask(req.getTaskId(), tenantId);
        ensureTaskHandler(task, findActiveCandidate(task, context), context);
        if (!willAdvanceAfterApprove(task, tenantId)) {
            return List.of();
        }
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        formInstance.setFormDataJson(resolvePreviewFormDataJson(req.getFormDataJson(), formInstance.getFormDataJson()));
        ProcessModel model = requireRuntimeModel(processInstance.getProcessModelId(), tenantId);
        Optional<BranchMatchResult> branchMatch = conditionBranchRuntimeService.previewNextBranch(
                model, processInstance, formInstance, task.getNodeId(), task, tenantId, context);
        if (branchMatch.isPresent()) {
            return assigneeResolveService.buildAssigneeSelectNodesForNode(
                    processInstance, branchMatch.get().getTargetNodeId(), tenantId);
        }
        return assigneeResolveService.buildRequiredAssigneeSelectNodes(
                processInstance.getProcessModelId(), processInstance, tenantId, task.getNodeId());
    }

    private ProcessInstance buildPreviewProcessInstance(ProcessModel model, RequestContext context) {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setTenantId(model.getTenantId());
        processInstance.setProcessModelId(model.getId());
        processInstance.setFormDefinitionId(model.getFormDefinitionId());
        processInstance.setStarterUserId(context.getUserId());
        processInstance.setStarterUsername(context.getUsername());
        processInstance.setStarterRealname(context.getUsername());
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        return processInstance;
    }

    private FormInstance buildPreviewFormInstance(String formDataJson) {
        FormInstance formInstance = new FormInstance();
        formInstance.setFormDataJson(resolvePreviewFormDataJson(formDataJson, "{}"));
        return formInstance;
    }

    private String resolvePreviewFormDataJson(String previewFormDataJson, String fallbackFormDataJson) {
        return StringUtils.hasText(previewFormDataJson) ? previewFormDataJson : fallbackFormDataJson;
    }

    private boolean willAdvanceAfterApprove(Task task, String tenantId) {
        if (!isCountersignTask(task)) {
            return true;
        }
        return countGroupTasks(task, tenantId, WorkflowConstants.Status.DONE) + 1 >= resolveGroupTotal(task);
    }

    private boolean isCountersignTask(Task task) {
        return WorkflowConstants.ApprovalMode.COUNTERSIGN.equals(task.getApprovalMode())
                || WorkflowConstants.TaskType.COUNTERSIGN.equals(task.getTaskType());
    }

    private int resolveGroupTotal(Task task) {
        return task.getGroupTotal() == null || task.getGroupTotal() <= 0 ? 1 : task.getGroupTotal();
    }

    private int countGroupTasks(Task task, String tenantId, String status) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return WorkflowConstants.Status.DONE.equals(status) && WorkflowConstants.Status.DONE.equals(task.getStatus()) ? 1 : 0;
        }
        Long count = taskMapper.selectCount(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("task_group_id", task.getTaskGroupId())
                .eq("status", status)
                .eq("delete_flag", 0));
        return count == null ? 0 : count.intValue();
    }

    private ProcessModel requirePublishedModel(String processModelId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedModel(processModelId, tenantId);
    }

    private ProcessModel requireRuntimeModel(String processModelId, String tenantId) {
        return workflowRuntimeLookupService.requireRuntimeModel(processModelId, tenantId);
    }

    private FormDefinition requirePublishedForm(String formDefinitionId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedForm(formDefinitionId, tenantId);
    }

    private void checkStartPermission(ProcessModel model, RequestContext context) {
        workflowRuntimeLookupService.checkStartPermission(model, context);
    }

    private ProcessInstance requireProcessInstance(String processInstanceId, String tenantId) {
        return workflowRuntimeLookupService.requireProcessInstance(processInstanceId, tenantId);
    }

    private FormInstance requireFormInstance(String formInstanceId, String tenantId) {
        return workflowRuntimeLookupService.requireFormInstance(formInstanceId, tenantId);
    }

    private List<FieldPermission> listFieldPermissions(String processModelId, String nodeId, String tenantId) {
        return workflowRuntimeLookupService.listFieldPermissions(processModelId, nodeId, tenantId);
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
        return StringUtils.hasText(context.getUserId()) && context.getUserId().equals(task.getAssigneeUserId());
    }

    private String requireTenantId(RequestContext context) {
        return workflowRuntimeLookupService.requireTenantId(context);
    }

    private String requireUserId(RequestContext context) {
        return workflowRuntimeLookupService.requireUserId(context);
    }
}
