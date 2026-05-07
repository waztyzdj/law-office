package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.SysDepart;

import java.util.List;

public interface ISysDepartService extends IBaseService<SysDepart> {

    /**
     * 为部门分配角色
     * @param departId 部门ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(String departId, List<String> roleIds);

    /**
     * 获取部门的角色列表
     * @param departId 部门ID
     * @return 角色列表
     */
    List<DepartRole> getDepartRoles(String departId);

    /**
     * 移除部门的指定角色
     * @param departId 部门ID
     * @param roleIds 角色ID列表
     */
    void removeRoles(String departId, List<String> roleIds);

    /**
     * 为部门分配权限
     * @param departId 部门ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(String departId, List<String> permissionIds);

    /**
     * 获取部门的权限列表
     * @param departId 部门ID
     * @return 权限列表
     */
    List<Permission> getDepartPermissions(String departId);

    /**
     * 移除部门的指定权限
     * @param departId 部门ID
     * @param permissionIds 权限ID列表
     */
    void removePermissions(String departId, List<String> permissionIds);
}
