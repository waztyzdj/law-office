package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.workflow.constant.WorkflowConstants;
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
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.service.IRuntimeQueryService;
import com.lawoffice.workflow.service.IRuntimeViewAssemblerService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
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
import java.util.Set;

@Service
public class RuntimeQueryServiceImpl implements IRuntimeQueryService {

    private final ProcessModelMapper processModelMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IProcessNodeConfigService processNodeConfigService;
    private final IRuntimeAccessService runtimeAccessService;
    private final IRuntimeViewAssemblerService runtimeViewAssemblerService;
    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    private final IUserService userService;

    public RuntimeQueryServiceImpl(ProcessModelMapper processModelMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            ProcessInstanceMapper processInstanceMapper,
            OperationRecordMapper operationRecordMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IProcessNodeConfigService processNodeConfigService,
            IRuntimeAccessService runtimeAccessService,
            IRuntimeViewAssemblerService runtimeViewAssemblerService,
            IWorkflowRuntimeLookupService workflowRuntimeLookupService,
            IUserService userService) {
        this.processModelMapper = processModelMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.processNodeConfigService = processNodeConfigService;
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
            wrapper.orderByDesc("start_time").orderByDesc("create_time");

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
    public BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context) {
        try {
            String tenantId = requireTenantId(context);
            ProcessInstance processInstance = requireProcessInstance(id, tenantId);
            runtimeAccessService.ensureInstanceAccess(processInstance, context);
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            List<Task> currentTasks = listCurrentTasks(processInstance.getId(), tenantId);
            List<OperationRecord> records = listOperationRecords(processInstance.getId(), tenantId);
            return BaseResult.success(runtimeViewAssemblerService.buildInstanceDetail(
                    processInstance, formInstance, currentTasks, records));
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
        if (req != null && StringUtils.hasText(req.getTaskType())) {
            wrapper.eq("task_type", req.getTaskType());
        }
        if (req != null && StringUtils.hasText(req.getStatus())) {
            wrapper.eq("status", req.getStatus());
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
        wrapper.orderByDesc("create_time");

        Page<Task> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RuntimeTaskVO> records = runtimeViewAssemblerService.buildRuntimeTaskRecords(page.getRecords(), tenantId);
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

    private Task requireTodoTask(String taskId, String tenantId) {
        return workflowRuntimeLookupService.requireTodoTask(taskId, tenantId);
    }

    private ProcessModel requirePublishedModel(String processModelId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedModel(processModelId, tenantId);
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
