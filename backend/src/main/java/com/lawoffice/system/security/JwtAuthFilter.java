package com.lawoffice.system.security;

import com.lawoffice.system.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证过滤器
 * 独立实现，不依赖Shiro的Filter基类，避免Jakarta兼容性问题
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    public void setJwtSecret(String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    /**
     * 不需要拦截的路径
     */
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/auth/login",
            "/auth/logout",
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars",
            "/static",
            "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        String uri = request.getRequestURI();
        
        // 检查是否是排除的路径
        if (isExcludedPath(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 处理OPTIONS请求（跨域预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            handleCors(response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // 获取Authorization头
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 没有Token，返回401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未授权访问，请先登录\"}");
            return;
        }

        String token = authHeader.substring(7);
        
        try {
            // 验证Token
            if (!jwtUtil.isTokenValid(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
                return;
            }

            // 从Token中获取用户名
            String username = jwtUtil.getUsernameFromToken(token);
            
            // 创建ShiroJwtToken并进行认证
            ShiroJwtToken shiroToken = new ShiroJwtToken(token);
            Subject subject = SecurityUtils.getSubject();
            subject.login(shiroToken);
            
            // 将用户信息存入request属性
            request.setAttribute("username", username);
            request.setAttribute("token", token);
            
            log.debug("用户 {} 认证成功", username);
            
        } catch (Exception e) {
            log.error("Token认证失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"认证失败: " + e.getMessage() + "\"}");
            return;
        }

        // 继续过滤链
        filterChain.doFilter(request, response);
    }

    /**
     * 检查是否是排除的路径
     */
    private boolean isExcludedPath(String uri) {
        return EXCLUDED_PATHS.stream().anyMatch(uri::startsWith);
    }

    /**
     * 处理CORS
     */
    private void handleCors(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Requested-With");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");
    }
}
