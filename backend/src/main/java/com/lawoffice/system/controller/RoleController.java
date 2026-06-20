package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.req.AssignIdsReq;
import com.lawoffice.system.req.RoleReq;
import com.lawoffice.system.service.IRoleService;
import com.lawoffice.system.vo.RoleVO;
import com.lawoffice.system.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/role")
@Tag(name = "角色管理", description = "系统角色信息管理")
@ModuleInfo(value = "role", name = "角色管理", description = "系统角色信息管理")
public class RoleController extends BaseController<IRoleService, Role, RoleVO, RoleReq> {

    @Autowired
    public RoleController(IRoleService roleService) {
        this.baseService = roleService;
    }

    @PostMapping("/permissionIds")
    @Operation(summary = "获取角色权限ID列表", description = "获取指定角色已分配的菜单和按钮权限ID")
    @RequiresPermission("role:view")
    public BaseResult<List<String>> getRolePermissionIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getRolePermissionIds(req.getId()));
    }

    @PostMapping("/assignPermissions")
    @Operation(summary = "分配角色权限", description = "覆盖保存指定角色的菜单和按钮权限")
    @RequiresPermission("role:edit")
    public BaseResult<Void> assignPermissions(@Valid @RequestBody AssignIdsReq req, HttpServletRequest request) {
        Object username = request.getAttribute("username");
        baseService.assignPermissions(req.getId(), req.getIds(), username != null ? username.toString() : null);
        return BaseResult.success();
    }

    @PostMapping("/userIds")
    @Operation(summary = "获取角色用户ID列表", description = "获取指定角色已分配的用户ID")
    @RequiresPermission("role:view")
    public BaseResult<List<String>> getRoleUserIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getRoleUserIds(req.getId()));
    }

    @PostMapping("/assignUsers")
    @Operation(summary = "分配角色用户", description = "覆盖保存指定角色的用户")
    @RequiresPermission("role:edit")
    public BaseResult<Void> assignUsers(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignUsers(req.getId(), req.getIds());
        return BaseResult.success();
    }
}
