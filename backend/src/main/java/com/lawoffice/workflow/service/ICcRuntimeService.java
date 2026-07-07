package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.vo.CcRecordVO;

import java.util.List;

/**
 * 审批抄送运行时服务。
 */
public interface ICcRuntimeService {

    /**
     * 分页查询当前用户收到的抄送记录。
     *
     * @param req 查询请求
     * @param context 请求上下文
     * @return 抄送记录分页
     */
    BaseResult<PageVO<CcRecordVO>> pageMine(CcPageReq req, RequestContext context);

    /**
     * 统计当前用户收到的抄送记录。
     *
     * @param status 抄送状态，空值表示全部
     * @param context 请求上下文
     * @return 抄送记录数
     */
    long countMine(String status, RequestContext context);

    /**
     * 将当前用户收到的一条抄送记录标记为已读。
     *
     * @param ccRecordId 抄送记录ID
     * @param context 请求上下文
     * @return 标记后的抄送记录
     */
    BaseResult<CcRecordVO> markRead(String ccRecordId, RequestContext context);

    /**
     * 由有实例查看权的运行时用户手动抄送流程实例。
     *
     * @param processInstanceId 流程实例ID
     * @param receiverUserIds 接收人用户ID列表
     * @param context 请求上下文
     * @return 抄送记录列表
     */
    BaseResult<List<CcRecordVO>> sendManual(String processInstanceId, List<String> receiverUserIds,
            RequestContext context);

    /**
     * 按流程配置触发抄送。
     * <p>
     * 抄送只授予查看权，不生成待办，也不改变流程状态。
     *
     * @param processInstance 流程实例
     * @param task 触发任务；发起后或流程结束等无具体任务时可为空
     * @param triggerAction 触发动作，见 {@code WorkflowConstants.CcTriggerAction}
     * @param tenantId 租户ID
     * @param context 请求上下文
     */
    void triggerConfiguredCc(ProcessInstance processInstance, Task task, String triggerAction,
            String tenantId, RequestContext context);
}
