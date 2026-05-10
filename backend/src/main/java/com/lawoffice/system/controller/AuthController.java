package com.lawoffice.system.controller;

import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.system.dto.AuthUser;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户登录和认证相关接口")
public class AuthController {

    @Autowired
    private IUserService userService;

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

            // 调用 Service 层处理登录业务逻辑
            Map<String, Object> result = userService.login(username, password);
            return BaseResult.success(result);

        } catch (RuntimeException e) {
            log.warn("登录失败：{}", e.getMessage());
            return BaseResult.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("登录异常", e);
            return BaseResult.error(500, "登录失败：" + e.getMessage());
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
            String token = null;
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
            
            // 从 request 属性中获取用户名（由 JwtAuthFilter 设置）
            String username = (String) request.getAttribute("username");
            
            // 调用 Service 层处理登出业务逻辑
            userService.logout(token, username);
            
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

            // 调用 Service 层处理修改密码业务逻辑
            userService.changePassword(username, oldPassword, newPassword);
            return BaseResult.success();

        } catch (RuntimeException e) {
            log.warn("修改密码失败：{}", e.getMessage());
            return BaseResult.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("修改密码异常", e);
            return BaseResult.error(500, "修改密码失败：" + e.getMessage());
        }
    }
}
