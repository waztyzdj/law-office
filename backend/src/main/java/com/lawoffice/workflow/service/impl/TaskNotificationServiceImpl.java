package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.entity.SysMessageAction;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.message.mapper.SysMessageActionMapper;
import com.lawoffice.workflow.service.ITaskNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class TaskNotificationServiceImpl implements ITaskNotificationService {

    private static final String BIZ_TYPE_WORKFLOW_TODO = "workflow_todo";

    private final IMessageService messageService;
    private final SysMessageActionMapper sysMessageActionMapper;

    public TaskNotificationServiceImpl(IMessageService messageService,
            SysMessageActionMapper sysMessageActionMapper) {
        this.messageService = messageService;
        this.sysMessageActionMapper = sysMessageActionMapper;
    }

    @Override
    public void sendTodoArrivalMessage(ProcessInstance processInstance, Task task, List<String> receiverUserIds,
            RequestContext context) {
        if (processInstance == null || task == null || receiverUserIds == null || receiverUserIds.isEmpty()) {
            return;
        }
        List<String> distinctReceiverIds = receiverUserIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctReceiverIds.isEmpty()) {
            return;
        }
        try {
            SendMessageReq req = new SendMessageReq();
            req.setTitle("新的审批待办：" + processInstance.getInstanceTitle());
            req.setContent("你有新的审批待办“" + task.getTaskName() + "”，请及时处理。");
            req.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
            req.setMessageType(MessageConstants.MESSAGE_TYPE_TODO);
            req.setPriority(MessageConstants.PRIORITY_NORMAL);
            req.setReceiverIds(distinctReceiverIds);
            req.setActions(List.of(buildTodoMessageAction(processInstance, task)));
            messageService.sendMessage(req, resolveOperator(processInstance, task, context));
        } catch (Exception e) {
            log.warn("审批新待办消息发送失败，instanceId={}, taskId={}",
                    processInstance.getId(), task.getId(), e);
        }
    }

    @Override
    public void sendTodoArrivalMessage(ProcessInstance processInstance, Task task, RequestContext context) {
        if (task == null || !StringUtils.hasText(task.getAssigneeUserId())) {
            return;
        }
        sendTodoArrivalMessage(processInstance, task, List.of(task.getAssigneeUserId()), context);
    }

    @Override
    public void expireTodoMessageActions(List<String> taskIds, String tenantId, RequestContext context) {
        if (taskIds == null || taskIds.isEmpty() || !StringUtils.hasText(tenantId)) {
            return;
        }
        List<String> distinctTaskIds = taskIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctTaskIds.isEmpty()) {
            return;
        }
        sysMessageActionMapper.update(null, new UpdateWrapper<SysMessageAction>()
                .eq("tenant_id", tenantId)
                .in("biz_id", distinctTaskIds)
                .eq("biz_type", BIZ_TYPE_WORKFLOW_TODO)
                .eq("delete_flag", 0)
                .set("action_name", "查看详情")
                .set("update_by", context == null ? null : context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    private MessageActionReq buildTodoMessageAction(ProcessInstance processInstance, Task task) {
        MessageActionReq action = new MessageActionReq();
        action.setActionType(MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
        action.setActionName("办理审批");
        action.setRoutePath("/workflow/todo");
        action.setRouteQuery("{\"instanceId\":\"" + processInstance.getId()
                + "\",\"taskId\":\"" + task.getId() + "\"}");
        action.setBizType(BIZ_TYPE_WORKFLOW_TODO);
        action.setBizId(task.getId());
        action.setOpenType(MessageConstants.OPEN_TYPE_CURRENT);
        action.setSortOrder(1);
        return action;
    }

    private String resolveOperator(ProcessInstance processInstance, Task task, RequestContext context) {
        if (context != null && StringUtils.hasText(context.getUsername())) {
            return context.getUsername();
        }
        if (StringUtils.hasText(processInstance.getStarterUsername())) {
            return processInstance.getStarterUsername();
        }
        return task.getAssigneeUsername();
    }
}
