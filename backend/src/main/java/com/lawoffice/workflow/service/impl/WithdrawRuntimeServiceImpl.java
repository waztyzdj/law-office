package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessInstanceAssignee;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceAssigneeMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IProcessResultNotificationService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import com.lawoffice.workflow.service.IWithdrawRuntimeService;
import com.lawoffice.workflow.vo.TaskActionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class WithdrawRuntimeServiceImpl implements IWithdrawRuntimeService {

    private static final Set<String> HANDLED_ACTIONS = Set.of(
            WorkflowConstants.Action.APPROVE,
            WorkflowConstants.Action.REJECT,
            WorkflowConstants.Action.RETURN,
            WorkflowConstants.Action.TRANSFER,
            WorkflowConstants.Action.ADD_SIGN
    );

    private final FormInstanceMapper formInstanceMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final ProcessInstanceAssigneeMapper processInstanceAssigneeMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IFlowableService flowableService;
    private final IMessageService messageService;
    private final IProcessResultNotificationService processResultNotificationService;
    private final ITaskNotificationService taskNotificationService;

    public WithdrawRuntimeServiceImpl(FormInstanceMapper formInstanceMapper,
            OperationRecordMapper operationRecordMapper,
            ProcessInstanceAssigneeMapper processInstanceAssigneeMapper,
            ProcessInstanceMapper processInstanceMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IFlowableService flowableService,
            IMessageService messageService,
            IProcessResultNotificationService processResultNotificationService,
            ITaskNotificationService taskNotificationService) {
        this.formInstanceMapper = formInstanceMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.processInstanceAssigneeMapper = processInstanceAssigneeMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.flowableService = flowableService;
        this.messageService = messageService;
        this.processResultNotificationService = processResultNotificationService;
        this.taskNotificationService = taskNotificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TaskActionVO> withdraw(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            ProcessInstance processInstance = requireWithdrawableInstance(processInstanceId, tenantId, userId);
            ensureNoApproverHandled(processInstance.getId(), tenantId);
            List<Task> todoTasks = listTodoTasks(processInstance.getId(), tenantId);
            if (todoTasks.isEmpty()) {
                throw new IllegalArgumentException("当前流程没有可撤回的待办任务");
            }
            FormInstance formInstance = requireFormInstance(processInstance.getFormInstanceId(), tenantId);
            List<String> receiverUserIds = collectReceiverUserIds(processInstance, todoTasks, tenantId);

            terminateFlowable(processInstance);
            markTodoTasksWithdrawn(todoTasks, tenantId, context);
            cancelCandidates(todoTasks, tenantId, context);
            cancelAssigneeSnapshots(processInstance.getId(), tenantId, context);
            markInstanceWithdrawn(processInstance, context);
            archiveFormInstance(formInstance, context);
            createWithdrawRecord(processInstance, formInstance, todoTasks.get(0), tenantId, context);
            sendWithdrawMessage(processInstance, receiverUserIds, context);
            processResultNotificationService.sendProcessResultMessage(processInstance, context);

            return BaseResult.success(buildResult(processInstance));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("撤回审批失败: " + e.getMessage());
        }
    }

    private ProcessInstance requireWithdrawableInstance(String processInstanceId, String tenantId, String userId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("starter_user_id", userId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("审批实例不存在或无权撤回");
        }
        if (!WorkflowConstants.Status.RUNNING.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException("只有审批中的流程可以撤回");
        }
        return processInstance;
    }

    /**
     * 二期只支持发起人撤回首个审批人尚未办理的申请；一旦有审批办理动作，就应进入取回/终止等后续能力。
     */
    private void ensureNoApproverHandled(String processInstanceId, String tenantId) {
        Long handledCount = operationRecordMapper.selectCount(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .in("action", HANDLED_ACTIONS)
                .eq("delete_flag", 0));
        if (handledCount != null && handledCount > 0) {
            throw new IllegalArgumentException("流程已产生审批办理结果，不能撤回");
        }
    }

    private List<Task> listTodoTasks(String processInstanceId, String tenantId) {
        return taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
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

    private void terminateFlowable(ProcessInstance processInstance) {
        if (StringUtils.hasText(processInstance.getFlowableProcessInstanceId())) {
            flowableService.terminateProcessInstance(processInstance.getFlowableProcessInstanceId(), "发起人撤回");
        }
    }

    private void markTodoTasksWithdrawn(List<Task> todoTasks, String tenantId, RequestContext context) {
        List<String> taskIds = todoTasks.stream().map(Task::getId).toList();
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .in("id", taskIds)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.WITHDRAWN)
                .set("complete_time", LocalDateTime.now())
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
        taskNotificationService.expireTodoMessageActions(taskIds, tenantId, context);
    }

    private void cancelCandidates(List<Task> todoTasks, String tenantId, RequestContext context) {
        List<String> taskIds = todoTasks.stream().map(Task::getId).toList();
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", taskIds)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private List<String> collectReceiverUserIds(ProcessInstance processInstance, List<Task> todoTasks, String tenantId) {
        List<String> taskIds = todoTasks.stream().map(Task::getId).toList();
        List<String> receivers = new java.util.ArrayList<>();
        todoTasks.stream()
                .map(Task::getAssigneeUserId)
                .filter(StringUtils::hasText)
                .forEach(receivers::add);
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .select("candidate_user_id")
                        .eq("tenant_id", tenantId)
                        .in("task_id", taskIds)
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .stream()
                .map(TaskCandidate::getCandidateUserId)
                .filter(StringUtils::hasText)
                .forEach(receivers::add);
        return receivers.stream().distinct().toList();
    }

    private void cancelAssigneeSnapshots(String processInstanceId, String tenantId, RequestContext context) {
        processInstanceAssigneeMapper.update(null, new UpdateWrapper<ProcessInstanceAssignee>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private void markInstanceWithdrawn(ProcessInstance processInstance, RequestContext context) {
        processInstance.setStatus(WorkflowConstants.Status.WITHDRAWN);
        processInstance.setEndTime(LocalDateTime.now());
        processInstance.setCurrentTaskNames(null);
        processInstance.setCurrentAssigneeNames(null);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    private void archiveFormInstance(FormInstance formInstance, RequestContext context) {
        formInstance.setStatus(WorkflowConstants.Status.ARCHIVED);
        EntityFillUtils.fillAuditFields(formInstance, context, false);
        formInstanceMapper.updateById(formInstance);
    }

    private void createWithdrawRecord(ProcessInstance processInstance, FormInstance formInstance, Task currentTask,
            String tenantId, RequestContext context) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setTaskId(currentTask.getId());
        record.setFlowableTaskId(currentTask.getFlowableTaskId());
        record.setNodeId(currentTask.getNodeId());
        record.setNodeName(currentTask.getTaskName());
        record.setAction(WorkflowConstants.Action.WITHDRAW);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(processInstance.getStarterRealname());
        record.setComment("发起人撤回");
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    /**
     * 撤回消息是协同提醒通道，发送失败不能破坏已经完成的撤回事务。
     */
    private void sendWithdrawMessage(ProcessInstance processInstance, List<String> receiverUserIds,
            RequestContext context) {
        if (receiverUserIds.isEmpty()) {
            return;
        }
        try {
            SendMessageReq req = new SendMessageReq();
            req.setTitle("审批已撤回：" + processInstance.getInstanceTitle());
            req.setContent("发起人已撤回该审批，当前待办已失效。");
            req.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
            req.setMessageType(MessageConstants.MESSAGE_TYPE_NOTICE);
            req.setPriority(MessageConstants.PRIORITY_NORMAL);
            req.setReceiverIds(receiverUserIds);
            req.setActions(List.of(buildStartedMessageAction(processInstance)));
            messageService.sendMessage(req, RuntimeSupport.username(context));
        } catch (Exception e) {
            log.warn("审批撤回消息发送失败，instanceId={}", processInstance.getId(), e);
        }
    }

    private MessageActionReq buildStartedMessageAction(ProcessInstance processInstance) {
        MessageActionReq action = new MessageActionReq();
        action.setActionType(MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
        action.setActionName("查看审批");
        action.setRoutePath("/workflow/started");
        action.setRouteQuery("{\"instanceId\":\"" + processInstance.getId() + "\"}");
        action.setBizType("workflow_withdraw");
        action.setBizId(processInstance.getId());
        action.setOpenType(MessageConstants.OPEN_TYPE_CURRENT);
        action.setSortOrder(1);
        return action;
    }

    private TaskActionVO buildResult(ProcessInstance processInstance) {
        TaskActionVO vo = new TaskActionVO();
        vo.setProcessInstanceId(processInstance.getId());
        vo.setProcessStatus(processInstance.getStatus());
        return vo;
    }
}
