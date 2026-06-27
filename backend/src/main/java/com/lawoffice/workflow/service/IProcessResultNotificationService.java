package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.ProcessInstance;

/**
 * 流程结果消息通知服务。
 */
public interface IProcessResultNotificationService {

    /**
     * 流程结束后向发起人发送结果提醒。
     *
     * @param processInstance 已结束的流程实例
     * @param context 请求上下文
     */
    void sendProcessResultMessage(ProcessInstance processInstance, RequestContext context);
}
