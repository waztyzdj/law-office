package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.dto.BranchMatchResult;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.Task;

import java.util.Map;
import java.util.Optional;

/**
 * 条件分支运行时服务。
 */
public interface IConditionBranchRuntimeService {

    /**
     * 如果指定来源节点后面直接连接排他网关，则按网关的结构化分支配置计算命中结果并写入审计记录。
     *
     * @param model 已发布流程模型
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param sourceNodeId 来源节点ID，发起节点使用 BPMN startEvent ID
     * @param task 当前审批任务，发起阶段为空
     * @param tenantId 租户ID
     * @param context 请求上下文
     * @return 命中的条件分支；没有直接连接排他网关时为空
     */
    Optional<BranchMatchResult> matchNextBranch(ProcessModel model, ProcessInstance processInstance,
            FormInstance formInstance, String sourceNodeId, Task task, String tenantId, RequestContext context);

    /**
     * 预判指定来源节点后的条件分支命中结果，不写入分支记录和操作记录。
     * <p>
     * 用于前端提交前刷新“下一审批人选择”弹窗，保证弹窗节点与最终 Flowable 流转节点一致。
     *
     * @param model 已发布流程模型
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param sourceNodeId 来源节点ID，发起节点使用虚拟 start
     * @param task 当前审批任务，发起阶段为空
     * @param tenantId 租户ID
     * @param context 请求上下文
     * @return 命中的条件分支；没有直接连接排他网关时为空
     */
    Optional<BranchMatchResult> previewNextBranch(ProcessModel model, ProcessInstance processInstance,
            FormInstance formInstance, String sourceNodeId, Task task, String tenantId, RequestContext context);

    /**
     * 将分支命中结果转换为 Flowable 条件表达式使用的变量。
     *
     * @param matchResult 分支命中结果
     * @return Flowable 变量
     */
    Map<String, Object> buildFlowableVariables(Optional<BranchMatchResult> matchResult);
}
