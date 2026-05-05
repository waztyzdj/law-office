package com.lawoffice.system.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class SwaggerAuthenticationFilter implements Filter {

    @Value("${swagger.username:admin}")
    private String swaggerUsername;

    @Value("${swagger.password:admin123}")
    private String swaggerPassword;

    @Value("${swagger.enabled:true}")
    private boolean swaggerEnabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        
        if (!swaggerEnabled || (!uri.startsWith("/swagger-ui") && !uri.startsWith("/v3/api-docs"))) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring(6);
                byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
                
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2 && 
                    swaggerUsername.equals(parts[0]) && 
                    swaggerPassword.equals(parts[1])) {
                    chain.doFilter(request, response);
                    return;
                }
            } catch (Exception e) {
            }
        }

        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"Swagger UI\"");
        httpResponse.setContentType("text/plain;charset=UTF-8");
        httpResponse.getWriter().write("需要认证才能访问 Swagger 文档");
    }
}
