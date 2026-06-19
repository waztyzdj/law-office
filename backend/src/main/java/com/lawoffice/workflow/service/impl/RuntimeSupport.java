package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import org.springframework.util.StringUtils;

final class RuntimeSupport {

    private RuntimeSupport() {
    }

    static String requireTenantId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getTenantId())) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        return context.getTenantId();
    }

    static String requireUserId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getUserId())) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        return context.getUserId();
    }

    static String username(RequestContext context) {
        return context == null || !StringUtils.hasText(context.getUsername()) ? "system" : context.getUsername();
    }
}
