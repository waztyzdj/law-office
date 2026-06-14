package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.User;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.req.TaskActionReq;

/**
 * 流程实例状态服务。
 */
public interface IInstanceStateService {

    /**
     * 标记任务完成并取消任务候选人。
     *
     * @param task 任务
     * @param context 请求上下文
     */
    void markTaskDone(Task task, RequestContext context);

    /**
     * 取消任务的活跃候选人。
     *
     * @param task 任务
     * @param context 请求上下文
     */
    void cancelActiveCandidates(Task task, RequestContext context);

    /**
     * 取消流程实例下其它待办任务。
     *
     * @param processInstanceId 流程实例ID
     * @param completedTaskId 已完成任务ID
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void cancelTodoTasks(String processInstanceId, String completedTaskId, String tenantId, RequestContext context);

    /**
     * 归档表单实例。
     *
     * @param formInstance 表单实例
     * @param context 请求上下文
     */
    void archiveFormInstance(FormInstance formInstance, RequestContext context);

    /**
     * 写入任务操作记录。
     *
     * @param task 任务
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param req 操作请求
     * @param action 操作类型
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void createTaskRecord(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String action, String tenantId, RequestContext context);

    /**
     * 写入带目标节点或目标用户的任务操作记录。
     *
     * @param task 任务
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param req 操作请求
     * @param action 操作类型
     * @param tenantId 租户ID
     * @param context 请求上下文
     * @param targetNodeConfig 目标节点
     * @param targetUser 目标用户
     */
    void createTaskRecord(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String action, String tenantId, RequestContext context,
            ProcessNodeConfig targetNodeConfig, User targetUser);

    /**
     * 刷新流程实例当前任务摘要。
     *
     * @param processInstance 流程实例
     * @param tenantId 租户ID
     */
    void refreshCurrentTaskSummary(ProcessInstance processInstance, String tenantId);

    /**
     * 写入发起申请记录。
     *
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void createStartRecord(ProcessInstance processInstance, FormInstance formInstance, String tenantId, RequestContext context);

    /**
     * 写入保存草稿记录。
     *
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void createDraftRecord(ProcessInstance processInstance, FormInstance formInstance, String tenantId, RequestContext context);
}
