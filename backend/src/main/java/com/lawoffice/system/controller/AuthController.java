package com.lawoffice.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.system.dto.AuthUser;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.service.ITokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户登录和认证相关接口")
public class AuthController {

    @Autowired
    private IUserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ITokenService tokenService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回 JWT Token")
    @AutoLog(value = "用户登录", logType = LogType.LOGIN, operateType = OperateType.CUSTOM)
    public BaseResult<Map<String, Object>> login(@RequestBody AuthUser authUser, HttpServletRequest request) {
        try {
            String username = authUser.getUsername();
            String password = authUser.getPassword();

            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                return BaseResult.error(400, "用户名和密码不能为空");
            }

            // 使用专用方法查询用户（包含密码字段）
            User user = userMapper.selectByUsernameForLogin(username);

            if (user == null) {
                log.warn("登录失败：用户名不存在 - {}", username);
                return BaseResult.error(401, "用户名或密码错误");
            }

            if (user.getStatus() != null && user.getStatus() == 0) {
                log.warn("登录失败：用户已被禁用 - {}", username);
                return BaseResult.error(403, "用户已被禁用，请联系管理员");
            }

            if (!userService.verifyPassword(password, user.getPassword())) {
                log.warn("登录失败：密码错误 - {}", username);
                return BaseResult.error(401, "用户名或密码错误");
            }

            // 获取用户权限列表
            List<String> permissionCodes = userService.getUserPermissionCodes(user.getId());
            log.info("用户 {} 的权限列表: {}", username, permissionCodes);
            
            // 获取用户角色列表
            List<com.lawoffice.system.entity.Role> roles = userService.getUserRoles(user.getId());
            List<String> roleCodes = roles.stream()
                    .map(com.lawoffice.system.entity.Role::getRoleCode)
                    .toList();
            log.info("用户 {} 的角色列表: {}", username, roleCodes);

            // 强制清除所有旧的 Redis 缓存（包括 Token、权限、角色）
            tokenService.forceLogout(username);

            // 使用TokenService生成并存储Token到Redis
            String token = tokenService.generateAndStoreToken(
                    username, 
                    user.getId(), 
                    user.getRealname(),
                    permissionCodes,
                    roleCodes
            );

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("username", user.getUsername());
            result.put("realName", user.getRealname());
            result.put("userId", user.getId());
            result.put("permissions", permissionCodes);
            result.put("roles", roleCodes);
            result.put("expireTime", 24); // 24小时

            log.info("用户登录成功：{}, 权限数量: {}, 角色数量: {}", username, permissionCodes.size(), roleCodes.size());
            return BaseResult.success(result);

        } catch (Exception e) {
            log.error("登录异常", e);
            return BaseResult.error(500, "登录失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    public BaseResult<User> getUserInfo(HttpServletRequest request) {
        try {
            // 从Shiro SecurityUtils获取当前用户
            String username = (String) SecurityUtils.getSubject().getPrincipal();

            if (!StringUtils.hasText(username)) {
                return BaseResult.error(401, "未登录或登录已过期");
            }

            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username)
                   .eq(User::getDeleteFlag, 0);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                return BaseResult.error(404, "用户不存在");
            }

            return BaseResult.success(user);

        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return BaseResult.error(500, "获取用户信息失败：" + e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "退出登录")
    @AutoLog(value = "用户登出", logType = LogType.OPERATION, operateType = OperateType.CUSTOM)
    public BaseResult<Void> logout(HttpServletRequest request) {
        try {
            // 从请求头获取Token
            String authHeader = request.getHeader("Authorization");
            String username = "unknown";
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // 从 Token 中获取用户名（用于日志记录）
                try {
                    com.lawoffice.system.util.JwtUtil jwtUtil = new com.lawoffice.system.util.JwtUtil(
                        org.springframework.beans.factory.annotation.Value.class.cast(null) != null ? 
                        "lawoffice_jwt_secret_key_2026_change_this_in_production" : "lawoffice_jwt_secret_key_2026_change_this_in_production"
                    );
                    username = jwtUtil.getUsernameFromToken(token);
                } catch (Exception e) {
                    log.warn("从Token中获取用户名失败: {}", e.getMessage());
                }
                
                // 删除Redis中的Token和权限信息
                tokenService.removeToken(token);
            }
            
            log.info("用户登出：{}", username);
            return BaseResult.success();
        } catch (Exception e) {
            log.error("登出异常", e);
            return BaseResult.error(500, "登出失败：" + e.getMessage());
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/changePassword")
    @Operation(summary = "修改密码", description = "修改当前登录用户的密码")
    @AutoLog(value = "修改密码", logType = LogType.OPERATION, operateType = OperateType.CUSTOM)
    public BaseResult<Void> changePassword(@RequestBody AuthUser authUser, HttpServletRequest request) {
        try {
            String oldPassword = authUser.getOldPassword();
            String newPassword = authUser.getNewPassword();
            String confirmPassword = authUser.getConfirmPassword();

            if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
                return BaseResult.error(400, "旧密码和新密码不能为空");
            }

            if (newPassword.length() < 6) {
                return BaseResult.error(400, "新密码长度不能小于6位");
            }

            // 验证两次输入的新密码是否一致
            if (!StringUtils.hasText(confirmPassword) || !newPassword.equals(confirmPassword)) {
                return BaseResult.error(400, "两次输入的新密码不一致");
            }

            // 从 request 属性中获取用户名（由 JwtAuthFilter 设置）
            String username = (String) request.getAttribute("username");
            
            if (!StringUtils.hasText(username)) {
                return BaseResult.error(401, "未登录或登录已过期");
            }

            // 使用专用方法查询用户（包含密码字段）
            User user = userMapper.selectByUsernameForLogin(username);

            if (user == null) {
                return BaseResult.error(404, "用户不存在");
            }

            if (!userService.verifyPassword(oldPassword, user.getPassword())) {
                return BaseResult.error(401, "旧密码错误");
            }

            userService.resetPassword(user.getId(), newPassword);
            log.info("用户修改密码成功：{}", username);

            return BaseResult.success();

        } catch (Exception e) {
            log.error("修改密码异常", e);
            return BaseResult.error(500, "修改密码失败：" + e.getMessage());
        }
    }
}
