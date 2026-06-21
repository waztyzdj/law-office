package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.User;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.BranchMatchResult;
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
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.SelectedAssigneeReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.ICcRuntimeService;
import com.lawoffice.workflow.service.IConditionBranchRuntimeService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.service.ITaskActionService;
import com.lawoffice.workflow.service.IWorkflowFormDataService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import com.lawoffice.workflow.vo.TaskActionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class TaskActionServiceImpl implements ITaskActionService {

    private final FormInstanceMapper formInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskMapper taskMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final IConditionBranchRuntimeService conditionBranchRuntimeService;
    private final ICcRuntimeService ccRuntimeService;
    private final IFlowableService flowableService;
    private final IAssigneeResolveService assigneeResolveService;
    private final IInstanceStateService instanceStateService;
    private final IProcessNodeConfigService processNodeConfigService;
    private final IWorkflowFormDataService workflowFormDataService;
    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    private final TransactionTemplate transactionTemplate;

    public TaskActionServiceImpl(FormInstanceMapper formInstanceMapper,
            ProcessInstanceMapper processInstanceMapper,
            TaskMapper taskMapper,
            TaskCandidateMapper taskCandidateMapper,
            IConditionBranchRuntimeService conditionBranchRuntimeService,
            ICcRuntimeService ccRuntimeService,
            IFlowableService flowableService,
            IAssigneeResolveService assigneeResolveService,
            IInstanceStateService instanceStateService,
            IProcessNodeConfigService processNodeConfigService,
            IWorkflowFormDataService workflowFormDataService,
            IWorkflowRuntimeLookupService workflowRuntimeLookupService,
            PlatformTransactionManager transactionManager) {
        this.formInstanceMapper = formInstanceMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.taskMapper = taskMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.conditionBranchRuntimeService = conditionBranchRuntimeService;
        this.ccRuntimeService = ccRuntimeService;
        this.flowableService = flowableService;
        this.assigneeResolveService = assigneeResolveService;
        this.instanceStateService = instanceStateService;
        this.processNodeConfigService = processNodeConfigService;
        this.workflowFormDataService = workflowFormDataService;
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
        boolean nodeApprovedCcTrigger = WorkflowConstants.Action.APPROVE.equals(action)
                && !WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType())
                && willAdvanceAfterApprove(task, tenantId);
        if (WorkflowConstants.Action.APPROVE.equals(action)) {
            if (WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType()) && StringUtils.hasText(task.getParentTaskId())) {
                completeAddSignTask(task, processInstance, formInstance, req, tenantId, context);
            } else {
                ensureNoActiveAddSignChild(task);
                Optional<BranchMatchResult> branchMatch = Optional.empty();
                if (willAdvanceAfterApprove(task, tenantId)) {
                    ProcessModel model = requirePublishedModel(processInstance.getProcessModelId(), tenantId);
                    branchMatch = conditionBranchRuntimeService.matchNextBranch(
                            model, processInstance, formInstance, task.getNodeId(), task, tenantId, context);
                    saveNextAssigneeSnapshot(processInstance, task.getNodeId(), branchMatch,
                            req == null ? null : req.getSelectedAssignees(), tenantId, context);
                }
                completeApprove(task, processInstance, formInstance, req, tenantId, context, branchMatch);
            }
        } else if (WorkflowConstants.Action.REJECT.equals(action)) {
            ensureNotAddSignTask(task, "加签任务不允许不通过流程");
            ensureNoActiveAddSignChild(task);
            completeReject(task, processInstance, formInstance, req, tenantId, context);
        } else {
            throw new IllegalArgumentException("不支持的审批动作");
        }
        instanceStateService.createTaskRecord(task, processInstance, formInstance, req, action, tenantId, context);
        if (nodeApprovedCcTrigger) {
            ccRuntimeService.triggerConfiguredCc(processInstance, task,
                    WorkflowConstants.CcTriggerAction.APPROVE, tenantId, context);
        }
        if (isProcessFinished(processInstance)) {
            ccRuntimeService.triggerConfiguredCc(processInstance, task,
                    WorkflowConstants.CcTriggerAction.PROCESS_FINISHED, tenantId, context);
        }
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
        Optional<BranchMatchResult> branchMatch = conditionBranchRuntimeService.matchNextBranch(
                model, processInstance, formInstance, WorkflowConstants.VirtualNode.START, null, tenantId, context);
        saveFirstAssigneeSnapshot(processInstance, branchMatch,
                req == null ? null : req.getSelectedAssignees(), tenantId, context);
        FlowableStartResult flowableStartResult = flowableService.startProcessInstance(
                model,
                processInstance.getId(),
                buildFlowableVariables(processInstance, formInstance, context, branchMatch));
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
        ccRuntimeService.triggerConfiguredCc(processInstance, task,
                WorkflowConstants.CcTriggerAction.START, tenantId, context);
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
        if (isOrsignTask(task)) {
            return handleOrsignTransfer(task, req, context, tenantId, processInstance, targetUser);
        }
        Task existingTargetTask = findTodoCurrentStepTaskByAssignee(task, targetUser.getId(), tenantId);
        if (existingTargetTask != null) {
            if (!existingTargetTask.getId().equals(task.getId())) {
                markTaskTransferred(task, context);
                shrinkGroupTotalAfterMergedTransfer(task, tenantId, context);
                instanceStateService.cancelActiveCandidates(task, context);
                instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
                EntityFillUtils.fillAuditFields(processInstance, context, false);
                processInstanceMapper.updateById(processInstance);
                instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                        req, WorkflowConstants.Action.TRANSFER, tenantId, context, null, targetUser);
            }
            return buildTaskActionResult(existingTargetTask, processInstance);
        }
        fillOwnerFromCurrentAssignee(task);
        task.setAssigneeUserId(targetUser.getId());
        task.setAssigneeUsername(targetUser.getUsername());
        task.setAssigneeRealname(targetUser.getRealname());
        task.setTaskType(WorkflowConstants.TaskType.TRANSFER);
        task.setClaimTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        instanceStateService.cancelActiveCandidates(task, context);
        flowableService.setTaskAssignee(resolveFlowableAnchorTask(task, tenantId).getFlowableTaskId(), targetUser.getId());
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                req, WorkflowConstants.Action.TRANSFER, tenantId, context, null, targetUser);
        return buildTaskActionResult(task, processInstance);
    }

    private TaskActionVO handleOrsignTransfer(Task task, TaskActionReq req, RequestContext context,
            String tenantId, ProcessInstance processInstance, User targetUser) {
        Task existingTargetTask = findTodoGroupTaskByAssignee(task, targetUser.getId(), tenantId);
        if (existingTargetTask != null) {
            if (!existingTargetTask.getId().equals(task.getId())) {
                markTaskTransferred(task, context);
                shrinkGroupTotalAfterMergedTransfer(task, tenantId, context);
                instanceStateService.cancelActiveCandidates(task, context);
                instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
                EntityFillUtils.fillAuditFields(processInstance, context, false);
                processInstanceMapper.updateById(processInstance);
                instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                        req, WorkflowConstants.Action.TRANSFER, tenantId, context, null, targetUser);
            }
            return buildTaskActionResult(existingTargetTask, processInstance);
        }

        Task anchorTask = resolveFlowableAnchorTask(task, tenantId);
        Task targetTask = createGroupTransferTask(task, targetUser, context);
        markTaskTransferred(task, context);
        instanceStateService.cancelActiveCandidates(task, context);
        flowableService.addCandidateUsers(anchorTask.getFlowableTaskId(), List.of(targetUser.getId()));
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
        instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                req, WorkflowConstants.Action.TRANSFER, tenantId, context, null, targetUser);
        return buildTaskActionResult(targetTask, processInstance);
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
        ProcessNodeConfig targetNodeConfig = WorkflowConstants.VirtualNode.START_DRAFT.equals(req.getTargetNodeId())
                ? processNodeConfigService.buildStartDraftNodeConfig()
                : requireNodeConfig(processInstance.getProcessModelId(), req.getTargetNodeId(), tenantId);
        processNodeConfigService.ensureReturnTargetAllowed(processInstance, currentNodeConfig, targetNodeConfig, tenantId);
        FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
        saveTaskFormData(req, formInstance, listFieldPermissions(processInstance.getProcessModelId(), task.getNodeId(), tenantId), context);

        autoClaimIfNeeded(task, candidate, context);
        task.setStatus(WorkflowConstants.Status.RETURNED);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        instanceStateService.cancelActiveCandidates(task, context);
        cancelSiblingGroupTodoTasks(task, tenantId, context);
        if (WorkflowConstants.VirtualNode.START_DRAFT.equals(targetNodeConfig.getNodeId())) {
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
        Task existingTargetTask = findTodoCurrentStepTaskByAssignee(task, targetUser.getId(), tenantId);
        if (existingTargetTask != null) {
            instanceStateService.createTaskRecord(task, processInstance, requireFormInstance(processInstance.getFormInstanceId(), tenantId),
                    req, WorkflowConstants.Action.ADD_SIGN, tenantId, context, null, targetUser);
            return buildTaskActionResult(existingTargetTask, processInstance);
        }

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
            TaskActionReq req, String tenantId, RequestContext context, Optional<BranchMatchResult> branchMatch) {
        if (isCountersignTask(task)) {
            completeCountersignApprove(task, processInstance, formInstance, req, tenantId, context, branchMatch);
            return;
        }
        if (isOrsignTask(task)) {
            completeOrsignApprove(task, processInstance, formInstance, req, tenantId, context, branchMatch);
            return;
        }
        completeFlowableTask(task, req, context, branchMatch);
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

    /**
     * 会签通过只在最后一个同组任务完成时推进 Flowable，避免第一个办理人通过后流程提前进入下一节点。
     */
    private void completeCountersignApprove(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context, Optional<BranchMatchResult> branchMatch) {
        instanceStateService.markTaskDone(task, context);
        int completedCount = countGroupTasks(task, tenantId, WorkflowConstants.Status.DONE);
        updateGroupCompleted(task, completedCount, tenantId, context);
        if (completedCount < resolveGroupTotal(task)) {
            instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
            EntityFillUtils.fillAuditFields(processInstance, context, false);
            processInstanceMapper.updateById(processInstance);
            return;
        }
        completeFlowableTask(resolveFlowableAnchorTask(task, tenantId), req, context, branchMatch);
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

    /**
     * 或签任一人通过即可推进 Flowable，其它同组待办立即取消，防止旧待办继续提交造成重复流转。
     */
    private void completeOrsignApprove(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String tenantId, RequestContext context, Optional<BranchMatchResult> branchMatch) {
        instanceStateService.markTaskDone(task, context);
        cancelSiblingGroupTodoTasks(task, tenantId, context);
        updateGroupCompleted(task, countGroupTasks(task, tenantId, WorkflowConstants.Status.DONE), tenantId, context);
        completeFlowableTask(resolveFlowableAnchorTask(task, tenantId), req, context, branchMatch);
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
        Task parentTask = taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("id", task.getParentTaskId())
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (parentTask == null) {
            throw new IllegalArgumentException("加签父任务不存在");
        }
        if (!WorkflowConstants.Status.TRANSFERRED.equals(parentTask.getStatus())) {
            throw new IllegalArgumentException("加签任务所属审批环节已结束，不能继续办理");
        }
        instanceStateService.markTaskDone(task, context);
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

    /**
     * 判断本次通过是否会真正推进流程。会签未完成前不推进下一节点，也不应强制当前办理人选择下一节点审批人。
     */
    private boolean willAdvanceAfterApprove(Task task, String tenantId) {
        if (!isCountersignTask(task)) {
            return true;
        }
        return countGroupTasks(task, tenantId, WorkflowConstants.Status.DONE) + 1 >= resolveGroupTotal(task);
    }

    private boolean isProcessFinished(ProcessInstance processInstance) {
        return WorkflowConstants.Status.APPROVED.equals(processInstance.getStatus())
                || WorkflowConstants.Status.REJECTED.equals(processInstance.getStatus())
                || WorkflowConstants.Status.TERMINATED.equals(processInstance.getStatus())
                || WorkflowConstants.Status.WITHDRAWN.equals(processInstance.getStatus());
    }

    /**
     * 条件分支命中时，下一审批人必须保存到真实目标节点；没有分支时沿用静态顺序节点。
     */
    private void saveNextAssigneeSnapshot(ProcessInstance processInstance, String currentNodeId,
            Optional<BranchMatchResult> branchMatch, List<SelectedAssigneeReq> selectedAssignees,
            String tenantId, RequestContext context) {
        if (branchMatch.isPresent()) {
            assigneeResolveService.saveAssigneeSnapshotForNode(processInstance, branchMatch.get().getTargetNodeId(),
                    selectedAssignees, tenantId, context);
            return;
        }
        assigneeResolveService.saveNextAssigneeSnapshot(processInstance, currentNodeId, selectedAssignees, tenantId, context);
    }

    /**
     * 发起后紧接条件分支时，第一步审批人选择也应落到分支命中的目标节点。
     */
    private void saveFirstAssigneeSnapshot(ProcessInstance processInstance, Optional<BranchMatchResult> branchMatch,
            List<SelectedAssigneeReq> selectedAssignees, String tenantId, RequestContext context) {
        if (branchMatch.isPresent()) {
            assigneeResolveService.saveAssigneeSnapshotForNode(processInstance, branchMatch.get().getTargetNodeId(),
                    selectedAssignees, tenantId, context);
            return;
        }
        assigneeResolveService.saveFirstAssigneeSnapshot(processInstance, selectedAssignees, tenantId, context);
    }

    private void ensureNotAddSignTask(Task task, String message) {
        if (WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType())) {
            throw new IllegalArgumentException(message);
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

    private void markTaskTransferred(Task task, RequestContext context) {
        task.setStatus(WorkflowConstants.Status.TRANSFERRED);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
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

    private boolean isCountersignTask(Task task) {
        return WorkflowConstants.ApprovalMode.COUNTERSIGN.equals(task.getApprovalMode())
                || WorkflowConstants.TaskType.COUNTERSIGN.equals(task.getTaskType());
    }

    private boolean isOrsignTask(Task task) {
        return WorkflowConstants.ApprovalMode.ORSIGN.equals(task.getApprovalMode())
                || WorkflowConstants.TaskType.ORSIGN.equals(task.getTaskType());
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

    /**
     * 或签同组内一个人只保留一条待办。转办/加签选择到已经在本环节待办中的用户时，
     * 复用既有待办，避免同一用户在同一个审批节点看到重复任务。
     */
    private Task findTodoGroupTaskByAssignee(Task task, String targetUserId, String tenantId) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return null;
        }
        return taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("task_group_id", task.getTaskGroupId())
                .eq("assignee_user_id", targetUserId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .last("limit 1"));
    }

    /**
     * 加签去重不能跨节点、跨流程实例判断；只复用当前流程实例当前节点中目标人已有的待办。
     * 会签/或签优先按 taskGroupId 限定，普通任务按流程实例 + 节点限定。
     */
    private Task findTodoCurrentStepTaskByAssignee(Task task, String targetUserId, String tenantId) {
        if (StringUtils.hasText(task.getTaskGroupId())) {
            return findTodoGroupTaskByAssignee(task, targetUserId, tenantId);
        }
        return taskMapper.selectOne(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", task.getProcessInstanceId())
                .eq("node_id", task.getNodeId())
                .eq("assignee_user_id", targetUserId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .last("limit 1"));
    }

    /**
     * 同组业务任务共享一个真实 Flowable 用户任务，非锚点任务完成时需要找到保存真实 taskId 的组内任务。
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

    private void completeFlowableTask(Task task, TaskActionReq req, RequestContext context,
            Optional<BranchMatchResult> branchMatch) {
        Map<String, Object> variables = buildTaskVariables(task, req, context, WorkflowConstants.Action.APPROVE);
        variables.putAll(conditionBranchRuntimeService.buildFlowableVariables(branchMatch));
        flowableService.completeTask(task.getFlowableTaskId(), variables);
    }

    /**
     * 取消同组其它待办并释放候选人，或签通过、会签/或签退回等场景都不能让旧任务继续办理。
     */
    private void cancelSiblingGroupTodoTasks(Task task, String tenantId, RequestContext context) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return;
        }
        List<String> canceledTaskIds = taskMapper.selectList(new QueryWrapper<Task>()
                        .select("id")
                        .eq("tenant_id", tenantId)
                        .eq("task_group_id", task.getTaskGroupId())
                        .in("status", List.of(WorkflowConstants.Status.TODO, WorkflowConstants.Status.TRANSFERRED))
                        .ne("id", task.getId())
                        .eq("delete_flag", 0))
                .stream()
                .map(Task::getId)
                .toList();
        if (canceledTaskIds.isEmpty()) {
            return;
        }
        cancelActiveAddSignChildren(canceledTaskIds, tenantId, context);
        cancelTasksByIds(canceledTaskIds, tenantId, context);
        cancelCandidatesByTaskIds(canceledTaskIds, tenantId, context);
    }

    /**
     * 或签节点任一人通过后，同组其它未完成任务下挂的加签必须一起失效。
     * 发起加签的父任务会处于 transferred 状态，因此父任务 ID 范围必须包含 transferred sibling。
     * 否则旧加签完成会恢复已取消父任务，造成当前节点和下一节点并行办理。
     */
    private void cancelActiveAddSignChildren(List<String> parentTaskIds, String tenantId, RequestContext context) {
        if (parentTaskIds == null || parentTaskIds.isEmpty()) {
            return;
        }
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .in("parent_task_id", parentTaskIds)
                .eq("task_type", WorkflowConstants.TaskType.ADD_SIGN)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private void cancelTasksByIds(List<String> taskIds, String tenantId, RequestContext context) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .in("id", taskIds)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private void cancelCandidatesByTaskIds(List<String> taskIds, String tenantId, RequestContext context) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", taskIds)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private void updateGroupCompleted(Task task, int completedCount, String tenantId, RequestContext context) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return;
        }
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("task_group_id", task.getTaskGroupId())
                .eq("delete_flag", 0)
                .set("group_completed", completedCount)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    /**
     * 会签/或签转办给同组已有待办人时不会新增待办，原办理人的完成责任合并到目标人的待办上。
     * 因此同组应完成人数必须同步收缩，否则会签会停在类似 2/3 的不可完成状态。
     */
    private void shrinkGroupTotalAfterMergedTransfer(Task task, String tenantId, RequestContext context) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return;
        }
        int nextTotal = Math.max(resolveGroupTotal(task) - 1, 1);
        task.setGroupTotal(nextTotal);
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("task_group_id", task.getTaskGroupId())
                .eq("delete_flag", 0)
                .set("group_total", nextTotal)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    /**
     * 或签转办给新用户时，原办理人的待办结束，目标人获得同组新待办。
     * Flowable 仍只有一个真实用户任务，因此新增本地任务使用 group: 前缀保存本地标识。
     */
    private Task createGroupTransferTask(Task sourceTask, User targetUser, RequestContext context) {
        Task targetTask = new Task();
        targetTask.setId(newId());
        targetTask.setTenantId(sourceTask.getTenantId());
        targetTask.setProcessInstanceId(sourceTask.getProcessInstanceId());
        targetTask.setParentTaskId(sourceTask.getId());
        targetTask.setFlowableTaskId("group:" + targetTask.getId());
        targetTask.setNodeId(sourceTask.getNodeId());
        targetTask.setTaskName(sourceTask.getTaskName());
        targetTask.setTaskType(WorkflowConstants.TaskType.TRANSFER);
        targetTask.setApprovalMode(sourceTask.getApprovalMode());
        targetTask.setTaskGroupId(sourceTask.getTaskGroupId());
        targetTask.setGroupTotal(sourceTask.getGroupTotal());
        targetTask.setGroupCompleted(sourceTask.getGroupCompleted());
        targetTask.setOwnerUserId(sourceTask.getAssigneeUserId());
        targetTask.setOwnerUsername(sourceTask.getAssigneeUsername());
        targetTask.setOwnerRealname(sourceTask.getAssigneeRealname());
        targetTask.setAssigneeUserId(targetUser.getId());
        targetTask.setAssigneeUsername(targetUser.getUsername());
        targetTask.setAssigneeRealname(targetUser.getRealname());
        targetTask.setStatus(WorkflowConstants.Status.TODO);
        targetTask.setDueTime(sourceTask.getDueTime());
        targetTask.setClaimTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(targetTask, context, true);
        taskMapper.insert(targetTask);
        return targetTask;
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
        workflowFormDataService.saveRuntimeFormData(req.getFormDataJson(), formInstance, permissions, context, true);
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
        workflowFormDataService.saveStartFormData(formDataJson, formInstance, permissions, context, validateRequired);
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

    private ProcessModel requirePublishedModel(String processModelId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedModel(processModelId, tenantId);
    }

    private void checkStartPermission(ProcessModel model, RequestContext context) {
        workflowRuntimeLookupService.checkStartPermission(model, context);
    }

    private ProcessNodeConfig requireNodeConfig(String processModelId, String nodeId, String tenantId) {
        return processNodeConfigService.requireRuntimeNodeConfig(processModelId, nodeId, tenantId);
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
        task.setNodeId(WorkflowConstants.VirtualNode.START_DRAFT);
        task.setTaskName(WorkflowConstants.VirtualNodeName.START_DRAFT);
        task.setTaskType(WorkflowConstants.TaskType.START_DRAFT);
        task.setAssigneeUserId(processInstance.getStarterUserId());
        task.setAssigneeUsername(processInstance.getStarterUsername());
        task.setAssigneeRealname(starterDisplayName);
        task.setStatus(WorkflowConstants.Status.TODO);
        EntityFillUtils.fillAuditFields(task, context, true);
        taskMapper.insert(task);
        processInstance.setCurrentTaskNames(WorkflowConstants.VirtualNodeName.START_DRAFT);
        processInstance.setCurrentAssigneeNames(starterDisplayName);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    private Map<String, Object> buildFlowableVariables(ProcessInstance processInstance,
            FormInstance formInstance, RequestContext context, Optional<BranchMatchResult> branchMatch) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", processInstance.getTenantId());
        variables.put("processInstanceId", processInstance.getId());
        variables.put("formInstanceId", formInstance.getId());
        variables.put("starterUserId", context.getUserId());
        variables.put("starterUsername", context.getUsername());
        variables.putAll(conditionBranchRuntimeService.buildFlowableVariables(branchMatch));
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
