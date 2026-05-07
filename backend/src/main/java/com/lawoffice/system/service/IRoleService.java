package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;

import java.util.List;

public interface IRoleService extends IBaseService<Role> {

    /**
     * 为角色分配权限
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(String roleId, List<String> permissionIds);

    /**
     * 获取角色的权限列表
     * @param roleId 角色ID
     * @return 权限列表
     */
    List<Permission> getRolePermissions(String roleId);

    /**
     * 移除角色的指定权限
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     */
    void removePermissions(String roleId, List<String> permissionIds);
}
