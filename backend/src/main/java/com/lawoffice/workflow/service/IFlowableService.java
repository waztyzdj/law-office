package com.lawoffice.workflow.service;

import com.lawoffice.workflow.dto.FlowableDeploymentResult;
import com.lawoffice.workflow.dto.FlowableStartResult;
import com.lawoffice.workflow.dto.FlowableTaskInfo;
import com.lawoffice.workflow.entity.ProcessModel;

import java.util.List;
import java.util.Map;

/**
 * Flowable 引擎边界服务。
 */
public interface IFlowableService {

    /**
     * 部署流程模型中的 BPMN XML，并返回 Flowable 生成的部署和流程定义信息。
     *
     * @param processModel 当前系统流程模型版本
     * @return Flowable 部署结果
     */
    FlowableDeploymentResult deployProcessModel(ProcessModel processModel);

    /**
     * 按已发布流程定义 ID 启动 Flowable 流程实例。
     *
     * @param processModel 当前系统流程模型版本
     * @param businessKey 业务主键
     * @param variables 流程变量
     * @return Flowable 启动结果
     */
    FlowableStartResult startProcessInstance(ProcessModel processModel, String businessKey, Map<String, Object> variables);

    /**
     * 查询 Flowable 流程实例当前活动任务。
     *
     * @param flowableProcessInstanceId Flowable 流程实例ID
     * @return 当前任务摘要
     */
    List<FlowableTaskInfo> listActiveTasks(String flowableProcessInstanceId);

    /**
     * 将候选任务认领给当前处理人。
     *
     * @param flowableTaskId Flowable任务ID
     * @param assigneeUserId 处理人用户ID
     */
    void claimTask(String flowableTaskId, String assigneeUserId);

    /**
     * 设置 Flowable 用户任务处理人。
     *
     * @param flowableTaskId Flowable任务ID
     * @param assigneeUserId 处理人用户ID
     */
    void setTaskAssignee(String flowableTaskId, String assigneeUserId);

    /**
     * 添加 Flowable 用户任务候选人。
     *
     * @param flowableTaskId Flowable任务ID
     * @param candidateUserIds 候选用户ID列表
     */
    void addCandidateUsers(String flowableTaskId, List<String> candidateUserIds);

    /**
     * 完成 Flowable 用户任务。
     *
     * @param flowableTaskId Flowable任务ID
     * @param variables 流程变量
     */
    void completeTask(String flowableTaskId, Map<String, Object> variables);

    /**
     * 终止 Flowable 流程实例，用于拒绝等结束类动作。
     *
     * @param flowableProcessInstanceId Flowable流程实例ID
     * @param reason 终止原因
     */
    void terminateProcessInstance(String flowableProcessInstanceId, String reason);

    /**
     * 将运行中的流程实例从当前节点跳转到目标节点。
     *
     * @param flowableProcessInstanceId Flowable流程实例ID
     * @param currentActivityId 当前活动节点ID
     * @param targetActivityId 目标活动节点ID
     */
    void moveActivityTo(String flowableProcessInstanceId, String currentActivityId, String targetActivityId);

    /**
     * 判断 Flowable 流程实例是否仍在运行。
     *
     * @param flowableProcessInstanceId Flowable流程实例ID
     * @return true表示运行中
     */
    boolean isProcessInstanceActive(String flowableProcessInstanceId);
}
