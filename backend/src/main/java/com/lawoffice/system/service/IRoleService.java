package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.vo.RoleVO;

import java.util.List;

public interface IRoleService extends IBaseService<Role, RoleVO> {

    /**
     * 为角色覆盖保存权限。
     *
     * @param roleId 角色 ID
     * @param permissionIds 权限 ID 列表
     */
    void assignPermissions(String roleId, List<String> permissionIds);

    /**
     * 以指定操作人的授权范围为边界，为角色覆盖保存权限。
     * <p>
     * 非超级管理员不能给角色授予超过自身权限范围的权限。
     *
     * @param roleId 角色 ID
     * @param permissionIds 权限 ID 列表
     * @param operatorUsername 当前操作人账号
     */
    void assignPermissions(String roleId, List<String> permissionIds, String operatorUsername);

    /**
     * 为角色覆盖保存成员。
     *
     * @param roleId 角色 ID
     * @param userIds 用户 ID 列表
     */
    void assignUsers(String roleId, List<String> userIds);

    /**
     * 查询角色已授权限。
     *
     * @param roleId 角色 ID
     * @return 权限列表
     */
    List<Permission> getRolePermissions(String roleId);

    /**
     * 移除角色的指定权限。
     *
     * @param roleId 角色 ID
     * @param permissionIds 权限 ID 列表
     */
    void removePermissions(String roleId, List<String> permissionIds);

    /**
     * 查询角色已授权限 ID。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<String> getRolePermissionIds(String roleId);

    /**
     * 查询角色已分配成员 ID。
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表
     */
    List<String> getRoleUserIds(String roleId);
}
