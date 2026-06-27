package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;

import java.util.List;

/**
 * 审批待办消息通知服务。
 */
public interface ITaskNotificationService {

    /**
     * 发送新待办到达通知。
     *
     * @param processInstance 流程实例
     * @param task 新增或重新激活的待办任务
     * @param receiverUserIds 接收人用户ID，候选任务可传多个候选人
     * @param context 请求上下文
     */
    void sendTodoArrivalMessage(ProcessInstance processInstance, Task task, List<String> receiverUserIds,
            RequestContext context);

    /**
     * 发送新待办到达通知给任务当前处理人。
     *
     * @param processInstance 流程实例
     * @param task 新增或重新激活的待办任务
     * @param context 请求上下文
     */
    void sendTodoArrivalMessage(ProcessInstance processInstance, Task task, RequestContext context);

    /**
     * 将已完成、转办或取消任务对应的待办消息动作降级为查看详情。
     *
     * @param taskIds 任务ID列表
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void expireTodoMessageActions(List<String> taskIds, String tenantId, RequestContext context);
}
