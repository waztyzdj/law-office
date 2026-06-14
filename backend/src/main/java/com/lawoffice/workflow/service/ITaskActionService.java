package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.vo.TaskActionVO;

/**
 * 运行时任务动作服务。
 */
public interface ITaskActionService {

    /**
     * 提交申请草稿。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 保存申请草稿任务。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 审批通过。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 审批不通过。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 转办任务。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 退回任务。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 加签任务。
     *
     * @param taskId 任务ID
     * @param req 操作请求
     * @param context 请求上下文
     * @return 操作结果
     */
    BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context);
}
