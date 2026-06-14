package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.User;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableStartResult;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.ITaskActionService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import com.lawoffice.workflow.vo.TaskActionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class TaskActionServiceImpl implements ITaskActionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String START_DRAFT_NODE_ID = "start_draft";
    private static final String START_DRAFT_TASK_NAME = "提交申请";

    private final FormInstanceMapper formInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final TaskMapper taskMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final IFlowableService flowableService;
    private final IAssigneeResolveService assigneeResolveService;
    private final IInstanceStateService instanceStateService;
    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    private final TransactionTemplate transactionTemplate;

    public TaskActionServiceImpl(FormInstanceMapper formInstanceMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            TaskMapper taskMapper,
            TaskCandidateMapper taskCandidateMapper,
            IFlowableService flowableService,
            IAssigneeResolveService assigneeResolveService,
            IInstanceStateService instanceStateService,
            IWorkflowRuntimeLookupService workflowRuntimeLookupService,
            PlatformTransactionManager transactionManager) {
        this.formInstanceMapper = formInstanceMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.taskMapper = taskMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.flowableService = flowableService;
        this.assigneeResolveService = assigneeResolveService;
        this.instanceStateService = instanceStateService;
        this.workflowRuntimeLookupService = workflowRuntimeLookupService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(() -> BaseResult.success(handleSubmitStartDraft(taskId, req, context)), "提交申请草稿失败");
    }

    @Override
    public BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(() -> BaseResult.success(handleSaveStartDraftTask(taskId, req, context)), "保存申请草稿失败");
    }

    @Override
    public BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleTaskAction(taskId, req, context, WorkflowConstants.Action.APPROVE)),
                "审批通过失败");
    }

    @Override
    public BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleTaskAction(taskId, req, context, WorkflowConstants.Action.REJECT)),
                "审批不通过失败");
    }

    @Override
    public BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleTransfer(taskId, req, context)),
                "转办失败");
    }

    @Override
    public BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleReturn(taskId, req, context)),
                "退回失败");
    }

    @Override
    public BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context) {
        return executeInTransaction(
                () -> BaseResult.success(handleAddSign(taskId, req, context)),
                "加签失败");
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

    private List<FieldPermission> listFieldPermissions(String processModelId, String nodeId, String tenantId) {
        return workflowRuntimeLookupService.listFieldPermissions(processModelId, nodeId, tenantId);
    }

    private Task requireTodoTask(String taskId, String tenantId) {
        return workflowRuntimeLookupService.requireTodoTask(taskId, tenantId);
    }

    private ProcessInstance requireProcessInstance(String processInstanceId, String tenantId) {
        return workflowRuntimeLookupService.requireProcessInstance(processInstanceId, tenantId);
    }

    private FormInstance requireFormInstance(String formInstanceId, String tenantId) {
        return workflowRuntimeLookupService.requireFormInstance(formInstanceId, tenantId);
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

    private ProcessModel requirePublishedModel(String processModelId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedModel(processModelId, tenantId);
    }

    private void checkStartPermission(ProcessModel model, RequestContext context) {
        workflowRuntimeLookupService.checkStartPermission(model, context);
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

    private Map<String, Object> buildFlowableVariables(ProcessInstance processInstance, FormInstance formInstance, RequestContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", processInstance.getTenantId());
        variables.put("processInstanceId", processInstance.getId());
        variables.put("formInstanceId", formInstance.getId());
        variables.put("starterUserId", context.getUserId());
        variables.put("starterUsername", context.getUsername());
        return variables;
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String requireTenantId(RequestContext context) {
        return workflowRuntimeLookupService.requireTenantId(context);
    }

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
