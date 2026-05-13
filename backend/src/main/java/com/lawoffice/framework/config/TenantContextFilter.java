package com.lawoffice.framework.config;

import com.lawoffice.system.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 租户上下文过滤器
 * 从JWT Token中提取租户ID并设置到ThreadLocal中
 */
@Slf4j
@Component
public class TenantContextFilter implements Filter {

    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    public void setJwtSecret(String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();
        
        // 排除不需要租户上下文的路径
        if (isExcludedPath(uri)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            // 从请求头获取Token
            String authHeader = httpRequest.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    // 解析Token获取租户ID
                    Claims claims = jwtUtil.parseToken(token);
                    String tenantId = claims.get("tenantId", String.class);
                    
                    if (tenantId != null && !tenantId.isEmpty()) {
                        // 设置租户ID到ThreadLocal
                        TenantContextHolder.setCurrentTenantId(tenantId);
                        log.debug("设置租户上下文: {}", tenantId);
                    }
                } catch (Exception e) {
                    log.warn("解析Token获取租户ID失败: {}", e.getMessage());
                }
            }
            
            // 继续过滤链
            chain.doFilter(request, response);
            
        } finally {
            // 请求结束后清除ThreadLocal，防止内存泄漏
            TenantContextHolder.clear();
        }
    }

    /**
     * 判断是否为排除路径
     */
    private boolean isExcludedPath(String uri) {
        return uri.startsWith("/auth/") 
            || uri.startsWith("/swagger-ui/")
            || uri.startsWith("/v3/api-docs/")
            || uri.equals("/favicon.ico");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
