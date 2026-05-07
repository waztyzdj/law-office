package com.lawoffice.system.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IUserService;
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
    private IUserService userService;

    @Autowired
    private PermissionMapper permissionMapper;

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
     * 授权 - 获取用户的角色和权限
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String username = (String) principals.getPrimaryPrincipal();
        
        if (!StringUtils.hasText(username)) {
            return null;
        }

        // 查询用户信息
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
               .eq(User::getDeleteFlag, 0);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            log.warn("授权失败：用户不存在 - {}", username);
            return null;
        }

        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();

        // 获取用户角色
        List<com.lawoffice.system.entity.Role> roles = userService.getUserRoles(user.getId());
        Set<String> roleCodes = new HashSet<>();
        if (roles != null && !roles.isEmpty()) {
            for (com.lawoffice.system.entity.Role role : roles) {
                roleCodes.add(role.getRoleCode());
            }
        }
        authorizationInfo.setRoles(roleCodes);

        // 获取用户权限
        List<String> permissionCodes = userService.getUserPermissionCodes(user.getId());
        Set<String> permissions = new HashSet<>();
        if (permissionCodes != null && !permissionCodes.isEmpty()) {
            permissions.addAll(permissionCodes);
        }
        authorizationInfo.setStringPermissions(permissions);

        log.debug("用户 {} 授权成功，角色数: {}, 权限数: {}", username, roleCodes.size(), permissions.size());
        return authorizationInfo;
    }

    /**
     * 认证 - 验证JWT Token
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

            // 查询用户信息
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username)
                   .eq(User::getDeleteFlag, 0);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                throw new AuthenticationException("用户不存在");
            }

            // 检查用户状态
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new AuthenticationException("用户已被禁用");
            }

            log.info("用户 {} 认证成功", username);
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
