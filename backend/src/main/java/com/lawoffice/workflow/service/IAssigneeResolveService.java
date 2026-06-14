package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.User;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.req.SelectedAssigneeReq;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;

import java.util.List;
import java.util.Map;

/**
 * 审批人解析服务。
 */
public interface IAssigneeResolveService {

    /**
     * 解析显示名。
     *
     * @param realname 真实姓名
     * @param username 用户名
     * @param userId 用户ID
     * @return 显示名
     */
    String resolveDisplayName(String realname, String username, String userId);

    /**
     * 解析当前用户真实姓名。
     *
     * @param context 请求上下文
     * @return 当前用户真实姓名
     */
    String resolveCurrentUserRealname(RequestContext context);

    /**
     * 加载租户内有效用户。
     *
     * @param userIds 用户ID集合
     * @param tenantId 租户ID
     * @return 用户映射
     */
    Map<String, User> loadTenantActiveUsers(List<String> userIds, String tenantId);

    /**
     * 构建需要前端选择的审批人节点。
     *
     * @param processModelId 流程模型ID
     * @param processInstance 流程实例
     * @param tenantId 租户ID
     * @param currentNodeId 当前节点ID
     * @return 审批人选择节点
     */
    List<AssigneeSelectNodeVO> buildRequiredAssigneeSelectNodes(String processModelId,
            ProcessInstance processInstance, String tenantId, String currentNodeId);

    /**
     * 保存发起时审批人选择快照。
     *
     * @param processInstance 流程实例
     * @param selectedAssignees 已选审批人
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void saveFirstAssigneeSnapshot(ProcessInstance processInstance, List<SelectedAssigneeReq> selectedAssignees,
            String tenantId, RequestContext context);

    /**
     * 保存下一节点审批人选择快照。
     *
     * @param processInstance 流程实例
     * @param currentNodeId 当前节点ID
     * @param selectedAssignees 已选审批人
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void saveNextAssigneeSnapshot(ProcessInstance processInstance, String currentNodeId,
            List<SelectedAssigneeReq> selectedAssignees, String tenantId, RequestContext context);

    /**
     * 同步当前任务和候选人。
     *
     * @param processInstance 流程实例
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void syncCurrentTasks(ProcessInstance processInstance, String tenantId, RequestContext context);
}
