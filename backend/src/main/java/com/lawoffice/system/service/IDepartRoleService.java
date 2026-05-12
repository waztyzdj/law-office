package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.DepartRolePermission;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.vo.DepartRoleVO;

import java.util.List;

public interface IDepartRoleService extends IBaseService<DepartRole, DepartRoleVO> {

    /**
     * 为部门角色分配权限
     * @param departRoleId 部门角色ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(String departRoleId, List<String> permissionIds);

    /**
     * 获取部门角色的权限列表
     * @param departRoleId 部门角色ID
     * @return 权限列表
     */
    List<Permission> getDepartRolePermissions(String departRoleId);

    /**
     * 移除部门角色的指定权限
     * @param departRoleId 部门角色ID
     * @param permissionIds 权限ID列表
     */
    void removePermissions(String departRoleId, List<String> permissionIds);

    /**
     * 为部门角色分配用户
     * @param departRoleId 部门角色ID
     * @param userIds 用户ID列表
     */
    void assignUsers(String departRoleId, List<String> userIds);

    /**
     * 获取部门角色的用户列表
     * @param departRoleId 部门角色ID
     * @return 用户列表
     */
    List<User> getDepartRoleUsers(String departRoleId);

    /**
     * 移除部门角色的指定用户
     * @param departRoleId 部门角色ID
     * @param userIds 用户ID列表
     */
    void removeUsers(String departRoleId, List<String> userIds);
}
