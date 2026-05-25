package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.req.AssignIdsReq;
import com.lawoffice.system.req.TenantReq;
import com.lawoffice.system.service.ITenantService;
import com.lawoffice.system.vo.TenantVO;
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
@RequestMapping("/tenant")
@Tag(name = "多租户管理", description = "系统多租户信息管理")
@ModuleInfo(value = "tenant", name = "多租户管理", description = "系统多租户信息管理")
public class TenantController extends BaseController<ITenantService, Tenant, TenantVO, TenantReq> {

    @Autowired
    public TenantController(ITenantService tenantService) {
        this.baseService = tenantService;
    }

    @PostMapping("/userIds")
    @Operation(summary = "获取租户用户ID列表", description = "获取指定租户已分配的用户ID")
    @RequiresPermission("tenant:view")
    public BaseResult<List<String>> getTenantUserIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getTenantUserIds(req.getId()));
    }

    @PostMapping("/assignUsers")
    @Operation(summary = "分配租户用户", description = "覆盖保存指定租户的用户")
    @RequiresPermission("tenant:edit")
    public BaseResult<Void> assignUsers(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignTenantUsers(req.getId(), req.getIds());
        return BaseResult.success();
    }

    @PostMapping("/adminUserIds")
    @Operation(summary = "获取租户管理员用户ID列表", description = "获取指定租户管理员角色下的用户ID")
    @RequiresPermission("tenant:view")
    public BaseResult<List<String>> getTenantAdminUserIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getTenantAdminUserIds(req.getId()));
    }

    @PostMapping("/assignAdmins")
    @Operation(summary = "分配租户管理员", description = "保存指定租户管理员角色下的用户")
    @RequiresPermission("tenant:edit")
    public BaseResult<Void> assignAdmins(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignTenantAdmins(req.getId(), req.getIds());
        return BaseResult.success();
    }

    @PostMapping("/adminPermissionIds")
    @Operation(summary = "获取租户管理员权限ID列表", description = "获取指定租户默认管理员角色已分配的权限ID")
    @RequiresPermission("tenant:view")
    public BaseResult<List<String>> getTenantAdminPermissionIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getTenantAdminPermissionIds(req.getId()));
    }

    @PostMapping("/assignAdminPermissions")
    @Operation(summary = "分配租户管理员权限", description = "覆盖保存指定租户默认管理员角色的权限")
    @RequiresPermission("tenant:edit")
    public BaseResult<Void> assignAdminPermissions(@Valid @RequestBody AssignIdsReq req, HttpServletRequest request) {
        Object username = request.getAttribute("username");
        baseService.assignTenantAdminPermissions(req.getId(), req.getIds(), username != null ? username.toString() : null);
        return BaseResult.success();
    }
}
