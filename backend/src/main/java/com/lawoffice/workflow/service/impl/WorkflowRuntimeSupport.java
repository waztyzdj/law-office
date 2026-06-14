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
import com.lawoffice.system.service.IUserService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableStartResult;
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
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class WorkflowRuntimeSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INSTANCE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String START_DRAFT_NODE_ID = "start_draft";
    private static final String START_DRAFT_TASK_NAME = "提交申请";

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
    private final IAssigneeResolveService assigneeResolveService;
    private final IInstanceStateService instanceStateService;
    private final IUserService userService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public WorkflowRuntimeSupport(ProcessModelMapper processModelMapper,
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
            IAssigneeResolveService assigneeResolveService,
            IInstanceStateService instanceStateService,
            IUserService userService,
            PlatformTransactionManager transactionManager) {
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
        this.assigneeResolveService = assigneeResolveService;
        this.instanceStateService = instanceStateService;
        this.userService = userService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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

    public BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context) {
        return executeInTransaction(() -> {
            validateStartReq(req);
            validateJson(req.getFormDataJson(), "表单数据JSON");

            String tenantId = requireTenantId(context);
            requireUserId(context);
            ProcessModel model = requirePublishedModel(req.getProcessModelId(), tenantId);
            checkStartPermission(model, context);
            FormDefinition form = requirePublishedForm(model.getFormDefinitionId(), tenantId);

            FormInstance formInstance = createFormInstance(req, form, tenantId, context, WorkflowConstants.Status.ACTIVE);
            saveStartFormData(req.getFormDataJson(), formInstance,
                    listFieldPermissions(model.getId(), WorkflowConstants.VirtualNode.START, tenantId), context, true);
            ProcessInstance processInstance = createProcessInstance(req, model, form, formInstance, tenantId, context,
                    WorkflowConstants.Status.RUNNING);
            formInstance.setProcessInstanceId(processInstance.getId());
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
            assigneeResolveService.saveFirstAssigneeSnapshot(processInstance, req.getSelectedAssignees(), tenantId, context);

            FlowableStartResult flowableStartResult = flowableService.startProcessInstance(
                    model,
                    processInstance.getId(),
                    buildFlowableVariables(processInstance, formInstance, context));
            processInstance.setFlowableProcessInstanceId(flowableStartResult.getProcessInstanceId());
            processInstance.setFlowableProcessDefinitionId(flowableStartResult.getProcessDefinitionId());
            createSubmittedStartTask(processInstance, tenantId, context);
            assigneeResolveService.syncCurrentTasks(processInstance, tenantId, context);
            EntityFillUtils.fillAuditFields(processInstance, context, false);
            processInstanceMapper.updateById(processInstance);
            instanceStateService.createStartRecord(processInstance, formInstance, tenantId, context);

            return BaseResult.success(buildStartResult(processInstance, formInstance));
        }, "发起申请失败");
    }

    public BaseResult<StartProcessVO> saveStartDraft(StartProcessReq req, RequestContext context) {
        return executeInTransaction(() -> {
            validateStartReq(req);
            validateJson(req.getFormDataJson(), "表单数据JSON");

            String tenantId = requireTenantId(context);
            requireUserId(context);
            ProcessModel model = requirePublishedModel(req.getProcessModelId(), tenantId);
            checkStartPermission(model, context);
            FormDefinition form = requirePublishedForm(model.getFormDefinitionId(), tenantId);

            FormInstance formInstance = createFormInstance(req, form, tenantId, context, WorkflowConstants.Status.DRAFT);
            saveStartFormData(req.getFormDataJson(), formInstance,
                    listFieldPermissions(model.getId(), WorkflowConstants.VirtualNode.START, tenantId), context, false);
            ProcessInstance processInstance = createProcessInstance(req, model, form, formInstance, tenantId, context,
                    WorkflowConstants.Status.DRAFT);
            formInstance.setProcessInstanceId(processInstance.getId());
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
            createStartDraftTask(processInstance, tenantId, context);
            instanceStateService.createDraftRecord(processInstance, formInstance, tenantId, context);

            return BaseResult.success(buildStartResult(processInstance, formInstance));
        }, "保存申请草稿失败");
    }

    public BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(() -> BaseResult.success(handleSubmitStartDraft(taskId, req, context)), "提交申请草稿失败");
    }

    public BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(() -> BaseResult.success(handleSaveStartDraftTask(taskId, req, context)), "保存申请草稿失败");
    }

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

    public BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context) {
        try {
            return BaseResult.success(pageTasks(req, context, WorkflowConstants.Status.TODO));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询待办失败: " + e.getMessage());
        }
    }

    public BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context) {
        try {
            return BaseResult.success(pageTasks(req, context, WorkflowConstants.Status.DONE));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询已办失败: " + e.getMessage());
        }
    }

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

    public BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleTaskAction(taskId, req, context, WorkflowConstants.Action.APPROVE)),
                "审批通过失败");
    }

    public BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleTaskAction(taskId, req, context, WorkflowConstants.Action.REJECT)),
                "审批不通过失败");
    }

    public BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleTransfer(taskId, req, context)),
                "转办失败");
    }

    public BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleReturn(taskId, req, context)),
                "退回失败");
    }

    public BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleAddSign(taskId, req, context)),
                "加签失败");
    }

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
                assigneeResolveService.saveNextAssigneeSnapshot(processInstance, task.getNodeId(),
                        req == null ? null : req.getSelectedAssignees(), tenantId, context);
                completeApprove(task, processInstance, formInstance, req, tenantId, context);
            }
        } else if (WorkflowConstants.Action.REJECT.equals(action)) {
            ensureNotAddSignTask(task, "加签任务不允许不通过流程");
            ensureNoActiveAddSignChild(task);
            completeReject(task, processInstance, formInstance, req, tenantId, context);
        } else {
            throw new IllegalArgumentException("不支持的审批动作");
        }
        instanceStateService.createTaskRecord(task, processInstance, formInstance, req, action, tenantId, context);
        return buildTaskActionResult(task, processInstance);
    }

    private TaskActionVO handleSubmitStartDraft(String taskId, TaskActionReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        Task task = requireTodoTask(taskId, tenantId);
        if (!WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType())) {
            throw new IllegalArgumentException("当前任务不是发起申请草稿");
        }
        ensureTaskHandler(task, null, context);
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        if (!WorkflowConstants.Status.DRAFT.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException("申请草稿已提交或已处理");
        }
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        saveStartDraftFormData(req, processInstance, formInstance, tenantId, context, true);

        ProcessModel model = requirePublishedModel(processInstance.getProcessModelId(), tenantId);
        checkStartPermission(model, context);
        assigneeResolveService.saveFirstAssigneeSnapshot(processInstance, req == null ? null : req.getSelectedAssignees(), tenantId, context);
        FlowableStartResult flowableStartResult = flowableService.startProcessInstance(
                model,
                processInstance.getId(),
                buildFlowableVariables(processInstance, formInstance, context));
        processInstance.setFlowableProcessInstanceId(flowableStartResult.getProcessInstanceId());
        processInstance.setFlowableProcessDefinitionId(flowableStartResult.getProcessDefinitionId());
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        processInstance.setStartTime(LocalDateTime.now());
        instanceStateService.markTaskDone(task, context);
        assigneeResolveService.syncCurrentTasks(processInstance, tenantId, context);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);

        formInstance.setStatus(WorkflowConstants.Status.ACTIVE);
        formInstance.setSubmittedTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(formInstance, context, false);
        formInstanceMapper.updateById(formInstance);

        instanceStateService.createStartRecord(processInstance, formInstance, tenantId, context);
        return buildTaskActionResult(task, processInstance);
    }

    private TaskActionVO handleSaveStartDraftTask(String taskId, TaskActionReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        Task task = requireTodoTask(taskId, tenantId);
        if (!WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType())) {
            throw new IllegalArgumentException("当前任务不是发起申请草稿");
        }
        ensureTaskHandler(task, null, context);
        ProcessInstance processInstance = requireProcessInstance(task.getProcessInstanceId(), tenantId);
        if (!WorkflowConstants.Status.DRAFT.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException("申请草稿已提交或已处理");
        }
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        saveStartDraftFormData(req, processInstance, formInstance, tenantId, context, false);
        // 已有草稿重复保存只更新表单数据，草稿创建记录在首次保存草稿时生成。
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
        instanceStateService.cancelActiveCandidates(task, context);
        flowableService.setTaskAssignee(task.getFlowableTaskId(), targetUser.getId());
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
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
        ProcessNodeConfig targetNodeConfig = START_DRAFT_NODE_ID.equals(req.getTargetNodeId())
                ? buildStartDraftNodeConfig()
                : requireNodeConfig(processInstance.getProcessModelId(), req.getTargetNodeId(), tenantId);
        ensureReturnTargetAllowed(processInstance, currentNodeConfig, targetNodeConfig, tenantId);
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        saveTaskFormData(req, formInstance, listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId), context);

        autoClaimIfNeeded(task, candidate, context);
        task.setStatus(WorkflowConstants.Status.RETURNED);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
            instanceStateService.cancelActiveCandidates(task, context);
        if (START_DRAFT_NODE_ID.equals(targetNodeConfig.getNodeId())) {
            returnToStartDraft(processInstance, formInstance, tenantId, context);
        } else {
            flowableService.moveActivityTo(processInstance.getFlowableProcessInstanceId(), task.getNodeId(), targetNodeConfig.getNodeId());
            assigneeResolveService.syncCurrentTasks(processInstance, tenantId, context);
            EntityFillUtils.fillAuditFields(processInstance, context, false);
            processInstanceMapper.updateById(processInstance);
        }
        instanceStateService.createTaskRecord(task, processInstance, formInstance, req, WorkflowConstants.Action.RETURN, tenantId, context, targetNodeConfig, null);
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
        instanceStateService.cancelActiveCandidates(task, context);

        Task addSignTask = createAddSignTask(task, targetUser, context);
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                req, WorkflowConstants.Action.ADD_SIGN, tenantId, context, null, targetUser);
        return buildTaskActionResult(addSignTask, processInstance);
    }

    private void completeApprove(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context) {
        flowableService.completeTask(task.getFlowableTaskId(), buildTaskVariables(task, req, context, WorkflowConstants.Action.APPROVE));
        instanceStateService.markTaskDone(task, context);
        assigneeResolveService.syncCurrentTasks(processInstance, tenantId, context);
        if (!flowableService.isProcessInstanceActive(processInstance.getFlowableProcessInstanceId())) {
            processInstance.setStatus(WorkflowConstants.Status.APPROVED);
            processInstance.setEndTime(LocalDateTime.now());
            processInstance.setCurrentTaskNames(null);
            processInstance.setCurrentAssigneeNames(null);
            instanceStateService.archiveFormInstance(formInstance, context);
        }
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    private void completeReject(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context) {
        flowableService.terminateProcessInstance(processInstance.getFlowableProcessInstanceId(), resolveActionComment(req, "审批不通过"));
        instanceStateService.markTaskDone(task, context);
        instanceStateService.cancelTodoTasks(processInstance.getId(), task.getId(), tenantId, context);
        processInstance.setStatus(WorkflowConstants.Status.REJECTED);
        processInstance.setEndTime(LocalDateTime.now());
        processInstance.setCurrentTaskNames(null);
        processInstance.setCurrentAssigneeNames(null);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        instanceStateService.archiveFormInstance(formInstance, context);
    }

    private void completeAddSignTask(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context) {
        instanceStateService.markTaskDone(task, context);
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
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
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
        if (START_DRAFT_NODE_ID.equals(targetNodeConfig.getNodeId())) {
            return;
        }
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
        Map<String, User> users = assigneeResolveService.loadTenantActiveUsers(List.of(req.getTargetUserId()), tenantId);
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
        saveRuntimeFormData(req.getFormDataJson(), formInstance, permissions, context, true);
    }

    private void saveStartDraftFormData(TaskActionReq req, ProcessInstance processInstance,
            FormInstance formInstance, String tenantId, RequestContext context, boolean validateRequired) {
        if (req == null || !StringUtils.hasText(req.getFormDataJson())) {
            return;
        }
        saveStartFormData(req.getFormDataJson(), formInstance,
                listFieldPermissions(processInstance.getProcessModelId(), WorkflowConstants.VirtualNode.START, tenantId),
                context, validateRequired);
    }

    private void saveStartFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired) {
        if (!StringUtils.hasText(formDataJson)) {
            return;
        }
        saveRuntimeFormData(formDataJson, formInstance, permissions, context, validateRequired);
    }

    private void saveRuntimeFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired) {
        try {
            JsonNode submitted = OBJECT_MAPPER.readTree(formDataJson);
            JsonNode current = StringUtils.hasText(formInstance.getFormDataJson())
                    ? OBJECT_MAPPER.readTree(formInstance.getFormDataJson()) : OBJECT_MAPPER.createObjectNode();
            if (!submitted.isObject() || !current.isObject()) {
                throw new IllegalArgumentException("表单数据必须是JSON对象");
            }
            if (permissions == null || permissions.isEmpty()) {
                if (validateRequired) {
                    validateRequiredFields(List.of(), (ObjectNode) submitted);
                }
                formInstance.setFormDataJson(OBJECT_MAPPER.writeValueAsString(submitted));
                EntityFillUtils.fillAuditFields(formInstance, context, false);
                formInstanceMapper.updateById(formInstance);
                return;
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
            if (validateRequired) {
                validateRequiredFields(permissions, merged);
            }
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
        return StringUtils.hasText(context.getUserId()) && context.getUserId().equals(task.getAssigneeUserId());
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
        task.setAssigneeRealname(assigneeResolveService.resolveCurrentUserRealname(context));
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

    /**
     * 退回到发起人时回到本地草稿任务，重新提交时再启动新的 Flowable 实例。
     */
    private void returnToStartDraft(ProcessInstance processInstance, FormInstance formInstance, String tenantId, RequestContext context) {
        flowableService.terminateProcessInstance(processInstance.getFlowableProcessInstanceId(), "退回发起人重新提交");
        instanceStateService.cancelTodoTasks(processInstance.getId(), null, tenantId, context);
        formInstance.setStatus(WorkflowConstants.Status.DRAFT);
        formInstance.setSubmittedTime(null);
        EntityFillUtils.fillAuditFields(formInstance, context, false);
        formInstanceMapper.updateById(formInstance);
        formInstanceMapper.update(null, new UpdateWrapper<FormInstance>()
                .eq("id", formInstance.getId())
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.DRAFT)
                .set("submitted_time", null)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
        processInstance.setStatus(WorkflowConstants.Status.DRAFT);
        processInstance.setFlowableProcessInstanceId(null);
        processInstance.setEndTime(null);
        createStartDraftTask(processInstance, tenantId, context);
        processInstanceMapper.update(null, new UpdateWrapper<ProcessInstance>()
                .eq("id", processInstance.getId())
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.DRAFT)
                .set("flowable_process_instance_id", null)
                .set("end_time", null)
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

    private FormInstance createFormInstance(StartProcessReq req, FormDefinition form, String tenantId, RequestContext context,
            String status) {
        FormInstance formInstance = new FormInstance();
        formInstance.setTenantId(tenantId);
        formInstance.setFormDefinitionId(form.getId());
        formInstance.setFormKey(form.getFormKey());
        formInstance.setFormName(form.getFormName());
        formInstance.setFormVersion(form.getVersion());
        formInstance.setFormDataJson(req.getFormDataJson());
        formInstance.setFormSchemaSnapshotJson(form.getSchemaJson());
        formInstance.setFormOptionSnapshotJson(form.getOptionJson());
        formInstance.setStatus(status);
        if (WorkflowConstants.Status.ACTIVE.equals(status)) {
            formInstance.setSubmittedTime(LocalDateTime.now());
        }
        EntityFillUtils.fillAuditFields(formInstance, context, true);
        formInstanceMapper.insert(formInstance);
        return formInstance;
    }

    private ProcessInstance createProcessInstance(StartProcessReq req, ProcessModel model, FormDefinition form,
            FormInstance formInstance, String tenantId, RequestContext context, String status) {
        String currentUserRealname = assigneeResolveService.resolveCurrentUserRealname(context);
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
        processInstance.setStarterRealname(currentUserRealname);
        processInstance.setStatus(status);
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

    private void createStartDraftTask(ProcessInstance processInstance, String tenantId, RequestContext context) {
        String starterDisplayName = assigneeResolveService.resolveDisplayName(
                processInstance.getStarterRealname(),
                processInstance.getStarterUsername(),
                processInstance.getStarterUserId());
        Task task = new Task();
        task.setId(newId());
        task.setTenantId(tenantId);
        task.setProcessInstanceId(processInstance.getId());
        task.setFlowableTaskId("draft:" + task.getId());
        task.setNodeId(START_DRAFT_NODE_ID);
        task.setTaskName(START_DRAFT_TASK_NAME);
        task.setTaskType(WorkflowConstants.TaskType.START_DRAFT);
        task.setAssigneeUserId(processInstance.getStarterUserId());
        task.setAssigneeUsername(processInstance.getStarterUsername());
        task.setAssigneeRealname(starterDisplayName);
        task.setStatus(WorkflowConstants.Status.TODO);
        EntityFillUtils.fillAuditFields(task, context, true);
        taskMapper.insert(task);
        processInstance.setCurrentTaskNames(START_DRAFT_TASK_NAME);
        processInstance.setCurrentAssigneeNames(starterDisplayName);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    /**
     * 直接发起没有本地草稿任务，也要补齐一条已办任务，保证“直接发起”和“草稿提交”的已办口径一致。
     */
    private void createSubmittedStartTask(ProcessInstance processInstance, String tenantId, RequestContext context) {
        String starterDisplayName = assigneeResolveService.resolveDisplayName(
                processInstance.getStarterRealname(),
                processInstance.getStarterUsername(),
                processInstance.getStarterUserId());
        Task task = new Task();
        task.setId(newId());
        task.setTenantId(tenantId);
        task.setProcessInstanceId(processInstance.getId());
        task.setFlowableTaskId("start:" + task.getId());
        task.setNodeId(START_DRAFT_NODE_ID);
        task.setTaskName(START_DRAFT_TASK_NAME);
        task.setTaskType(WorkflowConstants.TaskType.START_DRAFT);
        task.setAssigneeUserId(processInstance.getStarterUserId());
        task.setAssigneeUsername(processInstance.getStarterUsername());
        task.setAssigneeRealname(starterDisplayName);
        task.setStatus(WorkflowConstants.Status.DONE);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, true);
        taskMapper.insert(task);
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
     * 运行时写操作需要在捕获异常后返回 BaseResult，同时不能让 Spring 在方法返回后再提交已回滚事务。
     */
    private <T> BaseResult<T> executeInTransaction(Supplier<BaseResult<T>> action, String errorMessage) {
        return transactionTemplate.execute(status -> {
            try {
                return action.get();
            } catch (IllegalArgumentException e) {
                status.setRollbackOnly();
                return BaseResult.error(400, e.getMessage());
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error(errorMessage, e);
                return BaseResult.error(errorMessage + ": " + e.getMessage());
            }
        });
    }
}


