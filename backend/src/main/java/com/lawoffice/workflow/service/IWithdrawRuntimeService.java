package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.vo.TaskActionVO;

/**
 * 审批发起人撤回运行时服务。
 */
public interface IWithdrawRuntimeService {

    /**
     * 发起人撤回自己发起且尚未产生审批办理结果的运行中实例。
     *
     * @param processInstanceId 流程实例ID
     * @param context 当前请求上下文
     * @return 撤回结果
     */
    BaseResult<TaskActionVO> withdraw(String processInstanceId, RequestContext context);
}
