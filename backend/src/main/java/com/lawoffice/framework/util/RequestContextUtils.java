package com.lawoffice.framework.util;

import com.lawoffice.framework.dto.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求上下文工具类
 */
public class RequestContextUtils {

    /**
     * 获取当前登录用户名
     * 
     * @param request HTTP请求对象
     * @return 用户名，如果未获取到则返回"anonymous"
     */
    public static String getCurrentUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : "anonymous";
    }

    /**
     * 构建请求上下文信息
     * 
     * @param request HTTP请求对象
     * @return 请求上下文对象
     */
    public static RequestContext buildContext(HttpServletRequest request) {
        return RequestContext.builder()
                .username(getCurrentUsername(request))
                .token(request.getHeader("Authorization"))
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .build();
    }
}
