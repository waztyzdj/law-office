package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "当前用户组织权限信息")
public class CurrentUserOrganizationVO {

    @Schema(description = "所属部门")
    private List<SysDepartVO> departs = new ArrayList<>();

    @Schema(description = "系统角色")
    private List<RoleVO> roles = new ArrayList<>();

    @Schema(description = "部门角色")
    private List<DepartRoleVO> departRoles = new ArrayList<>();

    @Schema(description = "已授权菜单权限")
    private List<CurrentUserPermissionSummaryVO> menuPermissions = new ArrayList<>();

    @Schema(description = "已授权菜单权限数量")
    private Integer menuPermissionCount = 0;
}
