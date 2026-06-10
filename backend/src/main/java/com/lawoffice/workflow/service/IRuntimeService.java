package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartProcessVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskActionVO;
import com.lawoffice.workflow.vo.TaskFormVO;

import java.util.List;

/**
 * 审批运行时服务。
 */
public interface IRuntimeService {

    /**
     * 查询当前用户有权限发起的已发布流程。
     *
     * @param req 分页筛选请求
     * @param context 当前请求上下文
     * @return 可发起流程分页
     */
    BaseResult<PageVO<AvailableProcessVO>> pageAvailableProcesses(AvailableProcessPageReq req, RequestContext context);

    /**
     * 获取当前用户可发起的流程表单快照。
     *
     * @param processModelId 流程模型ID
     * @param context 当前请求上下文
     * @return 发起表单信息
     */
    BaseResult<StartFormVO> getStartForm(String processModelId, RequestContext context);

    /**
     * 发起审批申请，写入业务实例并启动 Flowable 流程。
     *
     * @param req 发起申请请求
     * @param context 当前请求上下文
     * @return 发起结果
     */
    BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context);

    /**
     * 保存发起申请草稿，并生成发起人的待提交任务。
     *
     * @param req 发起申请草稿请求
     * @param context 当前请求上下文
     * @return 草稿实例结果
     */
    BaseResult<StartProcessVO> saveStartDraft(StartProcessReq req, RequestContext context);

    /**
     * 提交发起申请草稿，启动 Flowable 流程。
     *
     * @param taskId 草稿待提交任务ID
     * @param req 提交请求
     * @param context 当前请求上下文
     * @return 提交结果
     */
    BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 保存待提交的发起申请草稿，不启动 Flowable 流程。
     *
     * @param taskId 草稿待提交任务ID
     * @param req 保存请求
     * @param context 当前请求上下文
     * @return 保存结果
     */
    BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 查询当前用户发起的审批实例。
     *
     * @param req 分页筛选请求
     * @param context 当前请求上下文
     * @return 我发起的实例分页
     */
    BaseResult<PageVO<StartedInstanceVO>> pageStartedInstances(StartedInstancePageReq req, RequestContext context);

    /**
     * 查询当前用户待办任务。
     *
     * @param req 分页筛选请求
     * @param context 当前请求上下文
     * @return 待办任务分页
     */
    BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context);

    /**
     * 查询当前用户已办任务。
     *
     * @param req 分页筛选请求
     * @param context 当前请求上下文
     * @return 已办任务分页
     */
    BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context);

    /**
     * 查询任务办理表单和当前节点字段权限。
     *
     * @param taskId 本地任务ID
     * @param context 当前请求上下文
     * @return 任务表单
     */
    BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context);

    /**
     * 审批通过当前任务。
     *
     * @param taskId 本地任务ID
     * @param req 审批动作请求
     * @param context 当前请求上下文
     * @return 处理结果
     */
    BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 拒绝当前流程实例。
     *
     * @param taskId 本地任务ID
     * @param req 审批动作请求
     * @param context 当前请求上下文
     * @return 处理结果
     */
    BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 转办当前任务。
     *
     * @param taskId 本地任务ID
     * @param req 转办请求
     * @param context 当前请求上下文
     * @return 处理结果
     */
    BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 退回当前任务到目标节点。
     *
     * @param taskId 本地任务ID
     * @param req 退回请求
     * @param context 当前请求上下文
     * @return 处理结果
     */
    BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 加签当前任务。
     *
     * @param taskId 本地任务ID
     * @param req 加签请求
     * @param context 当前请求上下文
     * @return 处理结果
     */
    BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context);

    /**
     * 查询审批详情聚合数据。
     *
     * @param id 审批实例ID
     * @param context 当前请求上下文
     * @return 审批详情
     */
    BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context);

    /**
     * 查询审批记录时间线。
     *
     * @param id 审批实例ID
     * @param context 当前请求上下文
     * @return 审批记录
     */
    BaseResult<List<OperationRecordVO>> listInstanceRecords(String id, RequestContext context);
}
