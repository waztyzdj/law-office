package com.lawoffice.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    public void setJwtSecret(String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回 JWT Token")
    @AutoLog(value = "用户登录", logType = LogType.LOGIN, operateType = OperateType.CUSTOM)
    public BaseResult<Map<String, Object>> login(@RequestBody Map<String, String> loginParams, HttpServletRequest request) {
        try {
            String username = loginParams.get("username");
            String password = loginParams.get("password");

            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                return BaseResult.error(400, "用户名和密码不能为空");
            }

            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username)
                   .eq(User::getDeleteFlag, 0);
            User user = userMapper.selectOne(wrapper);

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

            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("realName", user.getRealName());
            claims.put("tenantId", user.getTenantId());

            String token = jwtUtil.generateToken(username, claims);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("username", user.getUsername());
            result.put("realName", user.getRealName());

            log.info("用户登录成功：{}", username);
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
            String username = (String) request.getAttribute("username");

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
            String username = (String) request.getAttribute("username");
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
    public BaseResult<Void> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        try {
            String oldPassword = params.get("oldPassword");
            String newPassword = params.get("newPassword");

            if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
                return BaseResult.error(400, "旧密码和新密码不能为空");
            }

            if (newPassword.length() < 6) {
                return BaseResult.error(400, "新密码长度不能小于6位");
            }

            String username = (String) request.getAttribute("username");

            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username)
                   .eq(User::getDeleteFlag, 0);
            User user = userMapper.selectOne(wrapper);

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
