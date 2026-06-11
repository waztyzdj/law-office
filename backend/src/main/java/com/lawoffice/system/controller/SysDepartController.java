package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.req.AssignIdsReq;
import com.lawoffice.system.req.DepartLeaderReq;
import com.lawoffice.system.req.DepartMemberRelationReq;
import com.lawoffice.system.req.SysDepartReq;
import com.lawoffice.system.service.ISysDepartService;
import com.lawoffice.system.service.IPermissionService;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.system.vo.PermissionVO;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.DepartPermissionSourceVO;
import com.lawoffice.system.vo.DepartMemberRelationVO;
import com.lawoffice.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/depart")
@Tag(name = "组织机构管理", description = "系统组织机构信息管理")
@ModuleInfo(value = "depart", name = "组织机构管理", description = "系统组织机构信息管理")
public class SysDepartController extends BaseController<ISysDepartService, SysDepart, SysDepartVO, SysDepartReq> {

    private final IPermissionService permissionService;

    @Autowired
    public SysDepartController(ISysDepartService sysDepartService, IPermissionService permissionService) {
        this.baseService = sysDepartService;
        this.permissionService = permissionService;
    }

    @PostMapping("/userIds")
    @Operation(summary = "获取部门成员ID列表", description = "获取指定部门已分配的成员用户ID")
    @RequiresPermission("depart:view")
    public BaseResult<List<String>> getDepartUserIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartUserIds(req.getId()));
    }

    @PostMapping("/users")
    @Operation(summary = "获取部门成员列表", description = "获取指定部门已分配的成员用户")
    @RequiresPermission("depart:view")
    public BaseResult<List<UserVO>> getDepartUsers(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartUsers(req.getId()));
    }

    @PostMapping("/assignUsers")
    @Operation(summary = "分配部门成员", description = "覆盖保存指定部门的成员用户，成员默认拥有本部门默认角色")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> assignUsers(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignUsers(req.getId(), req.getIds());
        return BaseResult.success();
    }

    @PostMapping("/member-relation/list")
    @Operation(summary = "获取部门成员组织关系", description = "获取部门成员的主部门、部门负责人和直属上级关系")
    @RequiresPermission("depart:view")
    public BaseResult<List<DepartMemberRelationVO>> getMemberRelations(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartMemberRelations(req.getId()));
    }

    @PostMapping("/member-relation/save")
    @Operation(summary = "保存部门成员组织关系", description = "覆盖保存部门成员的主部门、部门负责人和直属上级关系")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> saveMemberRelations(@Valid @RequestBody DepartMemberRelationReq req) {
        baseService.saveDepartMemberRelations(req);
        return BaseResult.success();
    }

    @PostMapping("/leader/list")
    @Operation(summary = "获取部门负责人", description = "获取指定部门唯一负责人")
    @RequiresPermission("depart:view")
    public BaseResult<List<DepartMemberRelationVO>> getDepartLeaders(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartLeaders(req.getId()));
    }

    @PostMapping("/leader/save")
    @Operation(summary = "保存部门负责人", description = "保存指定部门唯一负责人，用户ID为空时清空负责人")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> saveDepartLeader(@Valid @RequestBody DepartLeaderReq req) {
        baseService.saveDepartLeader(req);
        return BaseResult.success();
    }

    @PostMapping("/roleIds")
    @Operation(summary = "获取部门角色ID列表", description = "获取指定部门已绑定的部门角色ID")
    @RequiresPermission("depart:view")
    public BaseResult<List<String>> getDepartRoleIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartRoleIds(req.getId()));
    }

    @PostMapping("/roles")
    @Operation(summary = "获取部门角色列表", description = "获取指定部门已绑定的部门角色")
    @RequiresPermission("depart:view")
    public BaseResult<List<DepartRole>> getDepartRoles(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartRoles(req.getId()));
    }

    @PostMapping("/assignRoles")
    @Operation(summary = "分配部门角色", description = "将部门角色绑定到指定部门")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> assignRoles(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignRoles(req.getId(), req.getIds());
        return BaseResult.success();
    }

    @PostMapping("/permissionIds")
    @Operation(summary = "获取部门权限ID列表", description = "获取指定部门直接分配的权限ID")
    @RequiresPermission("depart:view")
    public BaseResult<List<String>> getDepartPermissionIds(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartPermissionIds(req.getId()));
    }

    @PostMapping("/permissions")
    @Operation(summary = "获取部门权限列表", description = "获取指定部门直接分配的权限")
    @RequiresPermission("depart:view")
    public BaseResult<List<Permission>> getDepartPermissions(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartPermissions(req.getId()));
    }

    @PostMapping("/permissionSources")
    @Operation(summary = "获取部门权限来源", description = "获取指定部门直接权限和部门角色继承权限的来源")
    @RequiresPermission("depart:view")
    public BaseResult<List<DepartPermissionSourceVO>> getDepartPermissionSources(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(baseService.getDepartPermissionSources(req.getId()));
    }

    @GetMapping("/grantablePermissionTree")
    @Operation(summary = "获取部门可授权权限树", description = "获取当前用户可授予部门角色的菜单和按钮权限树")
    @RequiresPermission("depart:edit")
    public BaseResult<List<PermissionVO>> getGrantablePermissionTree(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return BaseResult.success(permissionService.grantableTree(username != null ? username.toString() : null));
    }

    @PostMapping("/assignPermissions")
    @Operation(summary = "分配部门权限", description = "覆盖保存指定部门的直接权限")
    @RequiresPermission("depart:edit")
    public BaseResult<Void> assignPermissions(@Valid @RequestBody AssignIdsReq req) {
        baseService.assignPermissions(req.getId(), req.getIds());
        return BaseResult.success();
    }
}
