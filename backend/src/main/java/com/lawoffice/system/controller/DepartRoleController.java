package com.lawoffice.system.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.req.AssignIdsReq;
import com.lawoffice.system.req.DepartRoleReq;
import com.lawoffice.system.service.IDepartRoleService;
import com.lawoffice.system.vo.DepartRoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/departRole")
@Tag(name = "部门角色管理", description = "系统部门角色信息管理")
@ModuleInfo(value = "depart-role", name = "部门角色管理", description = "系统部门角色信息管理")
public class DepartRoleController extends BaseController<IDepartRoleService, DepartRole, DepartRoleVO, DepartRoleReq> {

    @Autowired
    public DepartRoleController(IDepartRoleService departRoleService) {
        this.baseService = departRoleService;
    }

    @PostMapping("/saveByDepart")
    @Operation(summary = "保存部门角色", description = "在部门管理中新增或编辑普通部门角色")
    @RequiresPermission("depart:edit")
    public BaseResult<DepartRoleVO> saveByDepart(
            @Valid @RequestBody DepartRoleReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<DepartRole> saveDTO = new BaseDTO<>();
        saveDTO.setEntity(BeanUtil.copyProperties(req, DepartRole.class));
        initBaseDTO(saveDTO, request, response);
        return baseService.save(saveDTO);
    }

    @PostMapping("/deleteByDepart")
    @Operation(summary = "删除部门角色", description = "在部门管理中删除普通部门角色")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> deleteByDepart(
            @RequestBody DepartRoleReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<DepartRole> deleteDTO = new BaseDTO<>();
        if (req != null) {
            deleteDTO.setId(req.getId());
        }
        initBaseDTO(deleteDTO, request, response);
        return baseService.delete(deleteDTO);
    }

    @PostMapping("/permissionIds")
    @Operation(summary = "获取部门角色权限ID列表", description = "获取指定部门角色已分配的权限ID")
    @RequiresPermission("depart:view")
    public BaseResult<List<String>> getDepartRolePermissionIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartRolePermissionIds(req.getId()));
    }

    @PostMapping("/permissions")
    @Operation(summary = "获取部门角色权限列表", description = "获取指定部门角色已分配的权限")
    @RequiresPermission("depart:view")
    public BaseResult<List<Permission>> getDepartRolePermissions(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartRolePermissions(req.getId()));
    }

    @PostMapping("/assignPermissions")
    @Operation(summary = "分配部门角色权限", description = "覆盖保存指定部门角色的权限")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> assignPermissions(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignPermissions(req.getId(), req.getIds());
        return BaseResult.success();
    }

    @PostMapping("/userIds")
    @Operation(summary = "获取部门角色用户ID列表", description = "获取指定部门角色已分配的用户ID")
    @RequiresPermission("depart:view")
    public BaseResult<List<String>> getDepartRoleUserIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartRoleUserIds(req.getId()));
    }

    @PostMapping("/users")
    @Operation(summary = "获取部门角色用户列表", description = "获取指定部门角色已分配的用户")
    @RequiresPermission("depart:view")
    public BaseResult<List<User>> getDepartRoleUsers(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartRoleUsers(req.getId()));
    }

    @PostMapping("/assignUsers")
    @Operation(summary = "分配部门角色用户", description = "覆盖保存指定部门角色的用户")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> assignUsers(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignUsers(req.getId(), req.getIds());
        return BaseResult.success();
    }
}
