package com.lawoffice.system.controller;

import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.req.AssignIdsReq;
import com.lawoffice.system.service.ISystemPickerService;
import com.lawoffice.system.vo.RoleVO;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/picker")
@Tag(name = "系统选择器", description = "登录用户可用的租户内选择器数据接口")
@RequiredArgsConstructor
public class SystemPickerController {

    private final ISystemPickerService systemPickerService;

    @GetMapping("/users")
    @Operation(summary = "查询租户用户选择项", description = "查询当前租户下可选择的有效用户")
    public BaseResult<List<UserVO>> listUsers() {
        return BaseResult.success(systemPickerService.listUsers());
    }

    @GetMapping("/departs")
    @Operation(summary = "查询租户组织选择项", description = "查询当前租户下可选择的组织机构")
    public BaseResult<List<SysDepartVO>> listDeparts() {
        return BaseResult.success(systemPickerService.listDeparts());
    }

    @GetMapping("/roles")
    @Operation(summary = "查询租户角色选择项", description = "查询当前租户下可选择的系统角色")
    public BaseResult<List<RoleVO>> listRoles() {
        return BaseResult.success(systemPickerService.listRoles());
    }

    @PostMapping("/depart-users")
    @Operation(summary = "查询部门用户选择项", description = "查询当前租户下指定部门的有效成员")
    public BaseResult<List<UserVO>> listDepartUsers(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(systemPickerService.listDepartUsers(req.getId()));
    }

    @PostMapping("/role-users")
    @Operation(summary = "查询角色用户选择项", description = "查询当前租户下指定角色的有效成员")
    public BaseResult<List<UserVO>> listRoleUsers(@Valid @RequestBody AssignIdsReq req) {
        return BaseResult.success(systemPickerService.listRoleUsers(req.getId()));
    }
}
