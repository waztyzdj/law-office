package com.lawoffice.system.controller;

import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.dto.UserInfoDTO;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "系统用户信息管理")
public class UserController extends BaseController<IUserService, User> {

    @Autowired
    public UserController(IUserService userService) {
        this.baseService = userService;
    }
    
    /**
     * 获取当前用户详细信息（包含角色、权限）
     */
    @GetMapping("/info")
    @Operation(summary = "获取用户详细信息", description = "获取当前登录用户的详细信息，包括角色和权限")
    public BaseResult<UserInfoDTO> getUserInfo(HttpServletRequest request) {
        try {
            // 从 request 属性中获取用户名（由 JwtAuthFilter 设置）
            String username = (String) request.getAttribute("username");
            
            // 调用 Service 层处理业务逻辑
            UserInfoDTO userInfo = baseService.getCurrentUserDetailInfo(username);
            return BaseResult.success(userInfo);

        } catch (RuntimeException e) {
            log.warn("获取用户信息失败：{}", e.getMessage());
            return BaseResult.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return BaseResult.error(500, "获取用户信息失败：" + e.getMessage());
        }
    }
    
    // 示例：添加权限控制的方法（需要在实际方法上添加注解）
    // @RequiresPermission({"user:add"})
    // public BaseResult<Void> addUser(@RequestBody User user) { ... }
}
