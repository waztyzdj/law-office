package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.AssigneePreviewReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskFormVO;

import java.util.List;

/**
 * 运行时查询服务。
 */
public interface IRuntimeQueryService {

    /**
     * 分页查询可发起流程。
     *
     * @param req 查询请求
     * @param context 请求上下文
     * @return 可发起流程分页
     */
    BaseResult<PageVO<AvailableProcessVO>> pageAvailableProcesses(AvailableProcessPageReq req, RequestContext context);

    /**
     * 获取发起表单。
     *
     * @param processModelId 流程模型ID
     * @param context 请求上下文
     * @return 发起表单
     */
    BaseResult<StartFormVO> getStartForm(String processModelId, RequestContext context);

    /**
     * 分页查询我发起的流程实例。
     *
     * @param req 查询请求
     * @param context 请求上下文
     * @return 流程实例分页
     */
    BaseResult<PageVO<StartedInstanceVO>> pageStartedInstances(StartedInstancePageReq req, RequestContext context);

    /**
     * 分页查询待办任务。
     *
     * @param req 查询请求
     * @param context 请求上下文
     * @return 待办任务分页
     */
    BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context);

    /**
     * 分页查询已办任务。
     *
     * @param req 查询请求
     * @param context 请求上下文
     * @return 已办任务分页
     */
    BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context);

    /**
     * 统计当前用户待办任务数。
     *
     * @param context 请求上下文
     * @return 待办任务数
     */
    long countTodoTasks(RequestContext context);

    /**
     * 统计当前用户已办流程实例数。
     *
     * @param context 请求上下文
     * @return 已办流程实例数
     */
    long countDoneTasks(RequestContext context);

    /**
     * 获取任务表单。
     *
     * @param taskId 任务ID
     * @param context 请求上下文
     * @return 任务表单
     */
    BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context);

    /**
     * 按当前表单数据预判下一审批节点需要选择的审批人。
     *
     * @param req 预判请求
     * @param context 请求上下文
     * @return 只包含真实下一节点的审批人选择信息
     */
    BaseResult<List<AssigneeSelectNodeVO>> previewNextAssigneeSelectNodes(AssigneePreviewReq req, RequestContext context);

    /**
     * 获取流程实例详情。
     *
     * @param id 流程实例ID
     * @param context 请求上下文
     * @return 流程实例详情
     */
    BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context);

    /**
     * 查询流程实例操作记录。
     *
     * @param id 流程实例ID
     * @param context 请求上下文
     * @return 操作记录
     */
    BaseResult<List<OperationRecordVO>> listInstanceRecords(String id, RequestContext context);
}
