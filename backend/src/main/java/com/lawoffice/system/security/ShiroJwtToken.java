package com.lawoffice.system.security;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * Shiro JWT Token
 * 用于将JWT Token集成到Shiro的认证体系中
 */
public class ShiroJwtToken implements AuthenticationToken {

    private final String token;

    public ShiroJwtToken(String token) {
        this.token = token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    public String getToken() {
        return token;
    }
}
