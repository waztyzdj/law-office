package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.ProcessInstance;

/**
 * 审批运行时访问授权服务。
 */
public interface IRuntimeAccessService {

    /**
     * 校验当前用户是否允许访问审批实例。
     *
     * @param processInstance 流程实例
     * @param context 请求上下文
     */
    void ensureInstanceAccess(ProcessInstance processInstance, RequestContext context);
}
