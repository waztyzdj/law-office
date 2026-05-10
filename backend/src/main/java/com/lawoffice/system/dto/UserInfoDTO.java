package com.lawoffice.system.dto;

import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户详细信息")
public class UserInfoDTO {
    
    @Schema(description = "用户基本信息")
    private User user;
    
    @Schema(description = "用户角色列表")
    private List<Role> roles;
    
    @Schema(description = "用户权限列表")
    private List<Permission> permissions;
    
    @Schema(description = "用户菜单列表（树形结构）")
    private List<Permission> menus;
    
    @Schema(description = "权限编码列表")
    private List<String> permissionCodes;
    
    @Schema(description = "角色编码列表")
    private List<String> roleCodes;
}
