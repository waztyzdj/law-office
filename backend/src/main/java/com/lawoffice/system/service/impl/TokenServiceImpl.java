package com.lawoffice.system.service.impl;

import com.lawoffice.system.service.ITokenService;
import com.lawoffice.system.util.JwtUtil;
import com.lawoffice.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token管理服务实现类
 */
@Slf4j
@Service
public class TokenServiceImpl implements ITokenService {

    @Autowired
    private RedisUtils redisUtils;

    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    public void setJwtSecret(String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    /**
     * Token过期时间：24小时（秒）
     */
    private static final long TOKEN_EXPIRE_TIME = 24 * 60 * 60;

    /**
     * Redis Key前缀 - Token
     */
    private static final String TOKEN_PREFIX = "auth:token:";

    /**
     * Redis Key前缀 - 用户权限
     */
    private static final String PERMISSION_PREFIX = "auth:permission:";

    /**
     * Redis Key前缀 - 用户角色
     */
    private static final String ROLE_PREFIX = "auth:role:";

    @Override
    public String generateAndStoreToken(String username, String userId, String realName, 
                                        List<String> permissions, 
                                        List<String> roles) {
        return generateAndStoreTokenWithTenant(username, userId, realName, permissions, roles, "0");
    }

    @Override
    public String generateAndStoreTokenWithTenant(String username, String userId, String realName,
                                                   List<String> permissions,
                                                   List<String> roles,
                                                   String tenantId) {
        // 1. 生成JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("realName", realName);
        claims.put("tenantId", tenantId != null ? tenantId : "0");
        
        String token = jwtUtil.generateToken(username, claims);

        // 2. 存储Token信息到Redis
        String tokenKey = TOKEN_PREFIX + token;
        Map<String, Object> tokenInfo = new HashMap<>();
        tokenInfo.put("username", username);
        tokenInfo.put("userId", userId);
        tokenInfo.put("realName", realName);
        tokenInfo.put("loginTime", System.currentTimeMillis());
        tokenInfo.put("tenantId", tenantId != null ? tenantId : "0");
        
        redisUtils.hmset(tokenKey, tokenInfo, TOKEN_EXPIRE_TIME);

        // 3. 存储用户权限到Redis
        String permissionKey = PERMISSION_PREFIX + username;
        redisUtils.set(permissionKey, permissions, TOKEN_EXPIRE_TIME);

        // 4. 存储用户角色到Redis
        String roleKey = ROLE_PREFIX + username;
        redisUtils.set(roleKey, roles, TOKEN_EXPIRE_TIME);

        log.info("Token生成并存储成功，用户: {}, 租户ID: {}, 过期时间: {}小时", 
                username, tenantId, TOKEN_EXPIRE_TIME / 3600);
        return token;
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            // 1. 检查JWT格式是否有效
            if (!jwtUtil.isTokenValid(token)) {
                return false;
            }

            // 2. 检查Redis中是否存在该Token
            String tokenKey = TOKEN_PREFIX + token;
            return redisUtils.hasKey(tokenKey);

        } catch (Exception e) {
            log.error("Token验证失败", e);
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromRedis(String username) {
        String permissionKey = PERMISSION_PREFIX + username;
        Object permissions = redisUtils.get(permissionKey);
        
        if (permissions instanceof List) {
            return (List<String>) permissions;
        }
        
        return java.util.Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromRedis(String username) {
        String roleKey = ROLE_PREFIX + username;
        Object roles = redisUtils.get(roleKey);
        
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        
        return java.util.Collections.emptyList();
    }

    @Override
    public void removeToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        try {
            // 1. 从Redis删除Token信息
            String tokenKey = TOKEN_PREFIX + token;
            redisUtils.del(tokenKey);

            // 2. 获取用户名，删除权限和角色缓存
            String username = jwtUtil.getUsernameFromToken(token);
            if (username != null && !username.isEmpty()) {
                redisUtils.del(PERMISSION_PREFIX + username);
                redisUtils.del(ROLE_PREFIX + username);
            }

            log.info("Token已删除: {}", token.substring(0, Math.min(20, token.length())) + "...");
        } catch (Exception e) {
            log.error("删除Token失败", e);
        }
    }

    @Override
    public boolean refreshToken(String token) {
        if (!validateToken(token)) {
            return false;
        }

        try {
            String tokenKey = TOKEN_PREFIX + token;
            redisUtils.expire(tokenKey, TOKEN_EXPIRE_TIME);

            String username = jwtUtil.getUsernameFromToken(token);
            if (username != null && !username.isEmpty()) {
                redisUtils.expire(PERMISSION_PREFIX + username, TOKEN_EXPIRE_TIME);
                redisUtils.expire(ROLE_PREFIX + username, TOKEN_EXPIRE_TIME);
            }

            log.debug("Token有效期已刷新: {}", username);
            return true;
        } catch (Exception e) {
            log.error("刷新Token失败", e);
            return false;
        }
    }

    @Override
    public long getTokenRemainingTime(String token) {
        String tokenKey = TOKEN_PREFIX + token;
        return redisUtils.getExpire(tokenKey);
    }

    @Override
    public void forceLogout(String username) {
        // 注意：由于JWT是无状态的，这里只能清除Redis中的权限缓存
        // 如果需要完全强制下线，需要额外的Token黑名单机制
        redisUtils.del(PERMISSION_PREFIX + username);
        redisUtils.del(ROLE_PREFIX + username);
        log.info("用户 {} 已强制下线", username);
    }
}

