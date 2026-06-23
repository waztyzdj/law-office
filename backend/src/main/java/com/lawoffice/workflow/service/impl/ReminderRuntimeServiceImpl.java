package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IReminderRuntimeService;
import com.lawoffice.workflow.vo.ReminderRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReminderRuntimeServiceImpl implements IReminderRuntimeService {

    private final OperationRecordMapper operationRecordMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ReminderRecordMapper reminderRecordMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IMessageService messageService;

    public ReminderRuntimeServiceImpl(OperationRecordMapper operationRecordMapper,
            ProcessInstanceMapper processInstanceMapper,
            ReminderRecordMapper reminderRecordMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IMessageService messageService) {
        this.operationRecordMapper = operationRecordMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.reminderRecordMapper = reminderRecordMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.messageService = messageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<List<ReminderRecordVO>> urge(String processInstanceId, String remark, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            ProcessInstance processInstance = requireUrgeableInstance(processInstanceId, tenantId, userId);
            List<Task> todoTasks = listTodoTasks(processInstance.getId(), tenantId);
            if (todoTasks.isEmpty()) {
                throw new IllegalArgumentException("当前流程没有可催办的待办任务");
            }
            ensureNotRecentlyUrged(todoTasks, tenantId);
            List<ReminderTarget> targets = resolveTargets(todoTasks, tenantId);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("当前待办没有可催办的处理人");
            }

            List<ReminderRecord> records = createReminderRecords(processInstance, targets, remark, tenantId, context);
            createUrgeOperationRecord(processInstance, todoTasks.get(0), targets, remark, tenantId, context);
            sendUrgeMessages(processInstance, records, context);
            return BaseResult.success(BeanUtil.copyToList(records, ReminderRecordVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("催办审批失败", e);
            return BaseResult.error("催办审批失败: " + e.getMessage());
        }
    }

    private ProcessInstance requireUrgeableInstance(String processInstanceId, String tenantId, String userId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", processInstanceId)
                .eq("tenant_id", tenantId)
                .eq("starter_user_id", userId)
                .eq("delete_flag", 0));
        if (processInstance == null) {
            throw new IllegalArgumentException("审批实例不存在或无权催办");
        }
        if (!WorkflowConstants.Status.RUNNING.equals(processInstance.getStatus())) {
            throw new IllegalArgumentException("只有审批中的流程可以催办");
        }
        return processInstance;
    }

    private List<Task> listTodoTasks(String processInstanceId, String tenantId) {
        return taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
    }

    /**
     * 催办是人工提醒，二期先用固定时间窗口防刷，避免同一待办被连续发送站内消息。
     */
    private void ensureNotRecentlyUrged(List<Task> tasks, String tenantId) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(WorkflowConstants.Reminder.URGE_INTERVAL_MINUTES);
        Long recentCount = reminderRecordMapper.selectCount(new QueryWrapper<ReminderRecord>()
                .eq("tenant_id", tenantId)
                .in("task_id", tasks.stream().map(Task::getId).toList())
                .eq("remind_type", WorkflowConstants.RemindType.URGE)
                .ge("operate_time", since)
                .eq("delete_flag", 0));
        if (recentCount != null && recentCount > 0) {
            throw new IllegalArgumentException("当前待办已催办，请稍后再试");
        }
    }

    private List<ReminderTarget> resolveTargets(List<Task> tasks, String tenantId) {
        Map<String, ReminderTarget> targets = new LinkedHashMap<>();
        for (Task task : tasks) {
            if (StringUtils.hasText(task.getAssigneeUserId())) {
                putTarget(targets, new ReminderTarget(
                        task,
                        task.getAssigneeUserId(),
                        task.getAssigneeUsername(),
                        task.getAssigneeRealname()));
            }
        }
        Map<String, Task> taskMap = new LinkedHashMap<>();
        tasks.forEach(task -> taskMap.put(task.getId(), task));
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .eq("tenant_id", tenantId)
                        .in("task_id", taskMap.keySet())
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .forEach(candidate -> {
                    Task task = taskMap.get(candidate.getTaskId());
                    if (task != null && StringUtils.hasText(candidate.getCandidateUserId())) {
                        putTarget(targets, new ReminderTarget(
                                task,
                                candidate.getCandidateUserId(),
                                candidate.getCandidateUsername(),
                                candidate.getCandidateRealname()));
                    }
                });
        return new ArrayList<>(targets.values());
    }

    private void putTarget(Map<String, ReminderTarget> targets, ReminderTarget target) {
        targets.putIfAbsent(target.task().getId() + ":" + target.userId(), target);
    }

    private List<ReminderRecord> createReminderRecords(ProcessInstance processInstance, List<ReminderTarget> targets,
            String remark, String tenantId, RequestContext context) {
        List<ReminderRecord> records = new ArrayList<>();
        for (ReminderTarget target : targets) {
            ReminderRecord record = new ReminderRecord();
            record.setTenantId(tenantId);
            record.setProcessInstanceId(processInstance.getId());
            record.setTaskId(target.task().getId());
            record.setFlowableTaskId(target.task().getFlowableTaskId());
            record.setRemindType(WorkflowConstants.RemindType.URGE);
            record.setSenderUserId(context.getUserId());
            record.setSenderUsername(context.getUsername());
            record.setSenderRealname(processInstance.getStarterRealname());
            record.setReceiverUserId(target.userId());
            record.setReceiverUsername(target.username());
            record.setReceiverRealname(target.realname());
            record.setRemindRound(resolveNextRound(target.task().getId(), tenantId));
            record.setOperateTime(LocalDateTime.now());
            record.setRemark(remark);
            EntityFillUtils.fillAuditFields(record, context, true);
            reminderRecordMapper.insert(record);
            records.add(record);
        }
        return records;
    }

    private int resolveNextRound(String taskId, String tenantId) {
        Long count = reminderRecordMapper.selectCount(new QueryWrapper<ReminderRecord>()
                .eq("tenant_id", tenantId)
                .eq("task_id", taskId)
                .eq("remind_type", WorkflowConstants.RemindType.URGE)
                .eq("delete_flag", 0));
        return count == null ? 1 : count.intValue() + 1;
    }

    private void createUrgeOperationRecord(ProcessInstance processInstance, Task task, List<ReminderTarget> targets,
            String remark, String tenantId, RequestContext context) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setTaskId(task.getId());
        record.setFlowableTaskId(task.getFlowableTaskId());
        record.setNodeId(task.getNodeId());
        record.setNodeName(task.getTaskName());
        record.setAction(WorkflowConstants.Action.URGE);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(processInstance.getStarterRealname());
        record.setTargetRealname(joinTargetNames(targets));
        record.setComment(StringUtils.hasText(remark) ? remark : "发起人催办当前审批人");
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    private String joinTargetNames(List<ReminderTarget> targets) {
        return String.join("、", targets.stream()
                .map(this::targetDisplayName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
    }

    private String targetDisplayName(ReminderTarget target) {
        if (StringUtils.hasText(target.realname())) {
            return target.realname();
        }
        if (StringUtils.hasText(target.username())) {
            return target.username();
        }
        return target.userId();
    }

    /**
     * 站内消息只是催办通知通道，失败后保留催办记录，不能回滚主审批数据。
     */
    private void sendUrgeMessages(ProcessInstance processInstance, List<ReminderRecord> records,
            RequestContext context) {
        for (ReminderRecord record : records) {
            try {
                SendMessageReq req = new SendMessageReq();
                req.setTitle("审批催办：" + processInstance.getInstanceTitle());
                req.setContent("发起人催办你尽快处理当前审批。");
                req.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
                req.setMessageType(MessageConstants.MESSAGE_TYPE_NOTICE);
                req.setPriority(MessageConstants.PRIORITY_NORMAL);
                req.setReceiverIds(List.of(record.getReceiverUserId()));
                req.setActions(List.of(buildDetailMessageAction(processInstance, record)));
                MessageSendResultVO result = messageService.sendMessage(req, RuntimeSupport.username(context));
                if (result != null && StringUtils.hasText(result.getMessageId())) {
                    record.setMessageId(result.getMessageId());
                    reminderRecordMapper.updateById(record);
                }
            } catch (Exception e) {
                log.warn("审批催办消息发送失败，instanceId={}, receiverUserId={}",
                        processInstance.getId(), record.getReceiverUserId(), e);
            }
        }
    }

    private MessageActionReq buildDetailMessageAction(ProcessInstance processInstance, ReminderRecord record) {
        MessageActionReq action = new MessageActionReq();
        action.setActionType(MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
        action.setActionName("查看审批");
        action.setRoutePath("/workflow/todo");
        action.setRouteQuery("{\"instanceId\":\"" + processInstance.getId()
                + "\",\"taskId\":\"" + record.getTaskId() + "\"}");
        action.setBizType("workflow_urge");
        action.setBizId(processInstance.getId());
        action.setOpenType(MessageConstants.OPEN_TYPE_CURRENT);
        action.setSortOrder(1);
        return action;
    }

    private record ReminderTarget(Task task, String userId, String username, String realname) {
    }
}
