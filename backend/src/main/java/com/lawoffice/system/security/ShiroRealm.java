package com.lawoffice.system.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.ITokenService;
import com.lawoffice.system.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shiro自定义Realm
 * 实现用户认证和授权逻辑
 */
@Slf4j
@Component
public class ShiroRealm extends AuthorizingRealm {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ITokenService tokenService;

    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    public void setJwtSecret(String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    /**
     * 支持ShiroJwtToken类型
     */
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof ShiroJwtToken;
    }

    /**
     * 授权 - 从 Redis 获取用户的角色和权限（提升性能）
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String username = (String) principals.getPrimaryPrincipal();
        
        if (!StringUtils.hasText(username)) {
            return null;
        }

        // 从 Redis 获取用户权限和角色（避免频繁查询数据库）
        List<String> permissionCodes = tokenService.getPermissionsFromRedis(username);
        List<String> roleCodes = tokenService.getRolesFromRedis(username);

        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        
        // 设置角色
        if (roleCodes != null && !roleCodes.isEmpty()) {
            authorizationInfo.setRoles(new HashSet<>(roleCodes));
        }
        
        // 设置权限
        if (permissionCodes != null && !permissionCodes.isEmpty()) {
            authorizationInfo.setStringPermissions(new HashSet<>(permissionCodes));
        }

        log.debug("用户 {} 授权成功，角色数: {}, 权限数: {}", username, 
                roleCodes != null ? roleCodes.size() : 0, 
                permissionCodes != null ? permissionCodes.size() : 0);
        return authorizationInfo;
    }

    /**
     * 认证 - 验证JWT Token（从 Redis 获取用户信息，避免频繁查库）
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) 
            throws AuthenticationException {
        
        ShiroJwtToken jwtToken = (ShiroJwtToken) authenticationToken;
        String token = jwtToken.getToken();

        try {
            // 验证Token有效性
            if (!jwtUtil.isTokenValid(token)) {
                throw new AuthenticationException("Token无效或已过期");
            }

            // 从Token中获取用户名
            String username = jwtUtil.getUsernameFromToken(token);
            
            if (!StringUtils.hasText(username)) {
                throw new AuthenticationException("Token中未包含用户名");
            }

            // 从 Redis 验证 Token 是否存在（JwtAuthFilter 已经验证过，这里再次确认）
            if (!tokenService.validateToken(token)) {
                throw new AuthenticationException("Token已失效");
            }

            // 注意：这里不再查询数据库，因为：
            // 1. JwtAuthFilter 已经验证了 Token 在 Redis 中存在
            // 2. Token 存在说明用户已经登录且未过期
            // 3. 用户状态变化会通过清除 Redis Token 来强制下线
            
            log.debug("用户 {} 认证成功（使用缓存）", username);
            return new SimpleAuthenticationInfo(username, token, getName());

        } catch (AuthenticationException e) {
            log.error("认证失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("认证异常", e);
            throw new AuthenticationException("认证失败: " + e.getMessage());
        }
    }
}
