package com.lawoffice.system.controller;

import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.system.req.AssignIdsReq;
import com.lawoffice.system.req.SwitchTenantReq;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.vo.UserInfoVO;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.req.UserReq;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "系统用户信息管理")
@ModuleInfo(value = "user", name = "用户管理", description = "系统用户信息管理")
public class UserController extends BaseController<IUserService, User, UserVO, UserReq> {

    @Autowired
    public UserController(IUserService userService) {
        this.baseService = userService;
    }
    
    /**
     * 获取当前用户详细信息（包含角色、权限）
     */
    @GetMapping("/info")
    @Operation(summary = "获取用户详细信息", description = "获取当前登录用户的详细信息，包括角色和权限")
    public BaseResult<UserInfoVO> getUserInfo(HttpServletRequest request) {
        try {
            // 从 request 属性中获取用户名（由 JwtAuthFilter 设置）
            String username = (String) request.getAttribute("username");
            
            // 调用 Service 层处理业务逻辑
            UserInfoVO userInfo = baseService.getCurrentUserDetailInfo(username);
            return BaseResult.success(userInfo);

        } catch (RuntimeException e) {
            log.warn("获取用户信息失败：{}", e.getMessage());
            return BaseResult.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return BaseResult.error(500, "获取用户信息失败：" + e.getMessage());
        }
    }

    @PostMapping("/roleIds")
    @Operation(summary = "获取用户角色ID列表", description = "获取指定用户已分配的角色ID")
    @RequiresPermission("user:view")
    public BaseResult<List<String>> getUserRoleIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getUserRoleIds(req.getId()));
    }

    @PostMapping("/assignRoles")
    @Operation(summary = "分配用户角色", description = "覆盖保存指定用户的角色")
    @RequiresPermission("user:edit")
    public BaseResult<Void> assignRoles(@Valid @RequestBody AssignIdsReq req, HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        baseService.assignRoles(req.getId(), req.getIds(), username);
        return BaseResult.success();
    }

    @PostMapping("/tenants")
    @Operation(summary = "获取当前用户租户列表", description = "获取当前登录用户可切换的租户列表")
    public BaseResult<List<Tenant>> getCurrentUserTenants(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(baseService.getCurrentUserTenants(username));
    }

    @PostMapping("/switchTenant")
    @Operation(summary = "切换当前租户", description = "切换当前登录用户租户并返回新的 JWT Token")
    public BaseResult<Map<String, Object>> switchTenant(
            @Valid @RequestBody SwitchTenantReq req,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        String token = (String) request.getAttribute("token");
        return BaseResult.success(baseService.switchTenant(username, req.getTenantId(), token));
    }
    
    // 示例：添加权限控制的方法（需要在实际方法上添加注解）
    // @RequiresPermission({"user:add"})
    // public BaseResult<Void> addUser(@RequestBody User user) { ... }
}
