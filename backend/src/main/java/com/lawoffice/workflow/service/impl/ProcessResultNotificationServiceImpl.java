package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.service.IProcessResultNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class ProcessResultNotificationServiceImpl implements IProcessResultNotificationService {

    private static final String BIZ_TYPE_WORKFLOW_RESULT = "workflow_result";

    private final IMessageService messageService;

    public ProcessResultNotificationServiceImpl(IMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void sendProcessResultMessage(ProcessInstance processInstance, RequestContext context) {
        if (processInstance == null || !StringUtils.hasText(processInstance.getStarterUserId())) {
            return;
        }
        if (!isFinishedStatus(processInstance.getStatus())) {
            return;
        }
        try {
            SendMessageReq req = new SendMessageReq();
            req.setTitle(buildTitle(processInstance));
            req.setContent(buildContent(processInstance));
            req.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
            req.setMessageType(MessageConstants.MESSAGE_TYPE_NOTICE);
            req.setPriority(MessageConstants.PRIORITY_NORMAL);
            req.setReceiverIds(List.of(processInstance.getStarterUserId()));
            req.setActions(List.of(buildDetailMessageAction(processInstance)));
            messageService.sendMessage(req, resolveOperator(processInstance, context));
        } catch (Exception e) {
            log.warn("审批结果消息发送失败，instanceId={}, status={}",
                    processInstance.getId(), processInstance.getStatus(), e);
        }
    }

    private boolean isFinishedStatus(String status) {
        return WorkflowConstants.Status.APPROVED.equals(status)
                || WorkflowConstants.Status.REJECTED.equals(status)
                || WorkflowConstants.Status.TERMINATED.equals(status)
                || WorkflowConstants.Status.WITHDRAWN.equals(status);
    }

    private String buildTitle(ProcessInstance processInstance) {
        return switch (processInstance.getStatus()) {
            case WorkflowConstants.Status.APPROVED -> "审批已通过：" + processInstance.getInstanceTitle();
            case WorkflowConstants.Status.REJECTED -> "审批未通过：" + processInstance.getInstanceTitle();
            case WorkflowConstants.Status.WITHDRAWN -> "审批已撤回：" + processInstance.getInstanceTitle();
            case WorkflowConstants.Status.TERMINATED -> "审批已终止：" + processInstance.getInstanceTitle();
            default -> "审批已结束：" + processInstance.getInstanceTitle();
        };
    }

    private String buildContent(ProcessInstance processInstance) {
        return switch (processInstance.getStatus()) {
            case WorkflowConstants.Status.APPROVED -> "你的申请已审批通过。";
            case WorkflowConstants.Status.REJECTED -> "你的申请审批未通过，请查看审批意见。";
            case WorkflowConstants.Status.WITHDRAWN -> "你已撤回该审批，流程已结束。";
            case WorkflowConstants.Status.TERMINATED -> "该审批已终止，流程已结束。";
            default -> "该审批流程已结束。";
        };
    }

    private MessageActionReq buildDetailMessageAction(ProcessInstance processInstance) {
        MessageActionReq action = new MessageActionReq();
        action.setActionType(MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
        action.setActionName("查看审批");
        action.setRoutePath("/workflow/started");
        action.setRouteQuery("{\"instanceId\":\"" + processInstance.getId() + "\"}");
        action.setBizType(BIZ_TYPE_WORKFLOW_RESULT);
        action.setBizId(processInstance.getId());
        action.setOpenType(MessageConstants.OPEN_TYPE_CURRENT);
        action.setSortOrder(1);
        return action;
    }

    private String resolveOperator(ProcessInstance processInstance, RequestContext context) {
        if (context != null && StringUtils.hasText(context.getUsername())) {
            return context.getUsername();
        }
        return processInstance.getStarterUsername();
    }
}
