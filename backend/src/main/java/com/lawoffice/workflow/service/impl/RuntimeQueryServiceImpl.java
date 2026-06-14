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
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IRuntimeQueryService;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.FormInstanceVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.ProcessInstanceVO;
import com.lawoffice.workflow.vo.RuntimeFieldPermissionVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskActionPermissionVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import com.lawoffice.workflow.vo.TaskReturnNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeQueryServiceImpl implements IRuntimeQueryService {

    private static final String START_DRAFT_NODE_ID = "start_draft";
    private static final String START_DRAFT_TASK_NAME = "提交申请";

    private final ProcessModelMapper processModelMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final FieldPermissionMapper fieldPermissionMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IAssigneeResolveService assigneeResolveService;
    private final IUserService userService;

    public RuntimeQueryServiceImpl(ProcessModelMapper processModelMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            FormInstanceMapper formInstanceMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            OperationRecordMapper operationRecordMapper,
            FieldPermissionMapper fieldPermissionMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IAssigneeResolveService assigneeResolveService,
            IUserService userService) {
        this.processModelMapper = processModelMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.formInstanceMapper = formInstanceMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.assigneeResolveService = assigneeResolveService;
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
            List<AvailableProcessVO> records = buildAvailableProcessRecords(page.getRecords(), tenantId);
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
            return BaseResult.success(buildStartForm(model, form, context));
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
            boolean startDraftTask = WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType());
            List<FieldPermission> permissions = startDraftTask
                    ? listFieldPermissions(processInstance.getProcessModelId(), WorkflowConstants.VirtualNode.START, tenantId)
                    : listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
            ProcessNodeConfig nodeConfig = startDraftTask
                    ? buildStartDraftNodeConfig()
                    : requireNodeConfig(processInstance.getProcessModelId(), task.getNodeId(), tenantId);
            return BaseResult.success(buildTaskForm(task, processInstance, formInstance, permissions, nodeConfig));
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
        Map<String, String> candidateNamesByTaskId = buildCandidateNamesByTaskId(currentTasks, processInstance.getTenantId());
        vo.setCurrentTasks(currentTasks.stream()
                .map(task -> buildRuntimeTaskVO(task, processInstance, candidateNamesByTaskId.get(task.getId())))
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
                .eq("assignee_user_id", context.getUserId());
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
        return buildRuntimeTaskVO(task, processInstance, null);
    }

    private RuntimeTaskVO buildRuntimeTaskVO(Task task, ProcessInstance processInstance, String candidateAssigneeNames) {
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
        vo.setCandidateAssigneeNames(candidateAssigneeNames);
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

    private Map<String, String> buildCandidateNamesByTaskId(List<Task> tasks, String tenantId) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        Set<String> taskIds = tasks.stream()
                .map(Task::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> candidateNames = new HashMap<>();
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .in("task_id", taskIds)
                        .eq("tenant_id", tenantId)
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .forEach(candidate -> candidateNames
                        .computeIfAbsent(candidate.getTaskId(), key -> new ArrayList<>())
                        .add(assigneeResolveService.resolveDisplayName(
                                candidate.getCandidateRealname(),
                                candidate.getCandidateUsername(),
                                candidate.getCandidateUserId())));
        return candidateNames.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue().stream()
                                .filter(StringUtils::hasText)
                                .distinct()
                                .toList())));
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
        return StringUtils.hasText(context.getUserId()) && context.getUserId().equals(task.getAssigneeUserId());
    }

    private TaskFormVO buildTaskForm(Task task, ProcessInstance processInstance, FormInstance formInstance,
            List<FieldPermission> permissions, ProcessNodeConfig nodeConfig) {
        boolean startDraftTask = WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType());
        List<ProcessNodeConfig> returnableNodes = startDraftTask
                ? List.of()
                : listReturnableNodeConfigs(processInstance, nodeConfig, task.getTenantId());
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
        List<TaskReturnNodeVO> returnNodes = new ArrayList<>();
        if (!startDraftTask && isEnabled(nodeConfig.getAllowReturn())) {
            returnNodes.add(buildStartDraftReturnNode());
        }
        returnNodes.addAll(returnableNodes.stream().map(this::buildTaskReturnNode).toList());
        vo.setReturnNodes(returnNodes);
        vo.setFieldPermissions(permissions.stream().map(this::buildRuntimeFieldPermission).toList());
        vo.setAssigneeSelectNodes(assigneeResolveService.buildRequiredAssigneeSelectNodes(
                processInstance.getProcessModelId(), processInstance, task.getTenantId(), task.getNodeId()));
        return vo;
    }

    private TaskActionPermissionVO buildTaskActionPermissions(Task task, ProcessNodeConfig nodeConfig,
            List<ProcessNodeConfig> returnableNodes) {
        TaskActionPermissionVO vo = new TaskActionPermissionVO();
        if (WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType())) {
            vo.setAllowApprove(true);
            vo.setAllowReject(false);
            vo.setAllowTransfer(false);
            vo.setAllowAddSign(false);
            vo.setAllowReturn(false);
            return vo;
        }
        boolean addSignTask = WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType());
        vo.setAllowApprove(true);
        vo.setAllowReject(!addSignTask);
        vo.setAllowTransfer(!addSignTask && isEnabled(nodeConfig.getAllowTransfer()));
        vo.setAllowAddSign(!addSignTask && isEnabled(nodeConfig.getAllowAddSign()));
        vo.setAllowReturn(!addSignTask && isEnabled(nodeConfig.getAllowReturn()));
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

    private TaskReturnNodeVO buildStartDraftReturnNode() {
        TaskReturnNodeVO vo = new TaskReturnNodeVO();
        vo.setNodeId(START_DRAFT_NODE_ID);
        vo.setNodeName(START_DRAFT_TASK_NAME);
        vo.setNodeType(WorkflowConstants.NodeType.START);
        vo.setSortOrder(0);
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

    private ProcessNodeConfig buildStartDraftNodeConfig() {
        ProcessNodeConfig nodeConfig = new ProcessNodeConfig();
        nodeConfig.setNodeId(START_DRAFT_NODE_ID);
        nodeConfig.setNodeName(START_DRAFT_TASK_NAME);
        nodeConfig.setNodeType(WorkflowConstants.NodeType.START);
        nodeConfig.setAllowTransfer(0);
        nodeConfig.setAllowReturn(0);
        nodeConfig.setAllowAddSign(0);
        return nodeConfig;
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

    private ProcessInstance buildStarterContextProcessInstance(ProcessModel model, RequestContext context) {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setTenantId(model.getTenantId());
        processInstance.setProcessModelId(model.getId());
        processInstance.setStarterUserId(context.getUserId());
        processInstance.setStarterUsername(context.getUsername());
        processInstance.setStarterRealname(assigneeResolveService.resolveCurrentUserRealname(context));
        return processInstance;
    }

    private StartFormVO buildStartForm(ProcessModel model, FormDefinition form, RequestContext context) {
        StartFormVO vo = new StartFormVO();
        vo.setProcessModelId(model.getId());
        vo.setProcessName(model.getProcessName());
        vo.setFormDefinitionId(form.getId());
        vo.setFormKey(form.getFormKey());
        vo.setFormName(form.getFormName());
        vo.setFormVersion(form.getVersion());
        vo.setSchemaJson(form.getSchemaJson());
        vo.setOptionJson(form.getOptionJson());
        vo.setFieldPermissions(listFieldPermissions(model.getId(), WorkflowConstants.VirtualNode.START, model.getTenantId())
                .stream()
                .map(this::buildRuntimeFieldPermission)
                .toList());
        vo.setAssigneeSelectNodes(assigneeResolveService.buildRequiredAssigneeSelectNodes(
                model.getId(), buildStarterContextProcessInstance(model, context), model.getTenantId(), START_DRAFT_NODE_ID));
        return vo;
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
}
