package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.entity.User;

import java.util.List;

public interface IUserService extends IBaseService<User> {

    boolean verifyPassword(String rawPassword, String encodedPassword);

    void resetPassword(String userId, String newPassword);

    /**
     * 为用户分配角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(String userId, List<String> roleIds);

    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> getUserRoles(String userId);

    /**
     * 移除用户的指定角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    void removeRoles(String userId, List<String> roleIds);

    /**
     * 为用户分配部门
     * @param userId 用户ID
     * @param departIds 部门ID列表
     */
    void assignDeparts(String userId, List<String> departIds);

    /**
     * 获取用户的部门列表
     * @param userId 用户ID
     * @return 部门列表
     */
    List<SysDepart> getUserDeparts(String userId);

    /**
     * 移除用户的指定部门
     * @param userId 用户ID
     * @param departIds 部门ID列表
     */
    void removeDeparts(String userId, List<String> departIds);

    /**
     * 为用户分配租户
     * @param userId 用户ID
     * @param tenantIds 租户ID列表
     */
    void assignTenants(String userId, List<String> tenantIds);

    /**
     * 获取用户的租户列表
     * @param userId 用户ID
     * @return 租户列表
     */
    List<Tenant> getUserTenants(String userId);

    /**
     * 移除用户的指定租户
     * @param userId 用户ID
     * @param tenantIds 租户ID列表
     */
    void removeTenants(String userId, List<String> tenantIds);

    /**
     * 获取用户的权限列表（通过角色关联）
     * @param userId 用户ID
     * @return 权限列表
     */
    List<com.lawoffice.system.entity.Permission> getUserPermissions(String userId);

    /**
     * 获取用户的权限编码列表（用于JWT Token和权限验证）
     * @param userId 用户ID
     * @return 权限编码列表（perms字段）
     */
    List<String> getUserPermissionCodes(String userId);

    /**
     * 用户登录业务逻辑
     * @param username 用户名
     * @param password 密码
     * @return 登录结果，包含token、用户信息等
     */
    java.util.Map<String, Object> login(String username, String password);

    /**
     * 获取当前用户信息
     * @param username 用户名
     * @return 用户信息
     */
    User getCurrentUserInfo(String username);

    /**
     * 用户登出业务逻辑
     * @param token Token字符串
     * @param username 用户名（可选，从request中获取）
     */
    void logout(String token, String username);

    /**
     * 修改密码业务逻辑
     * @param username 用户名
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(String username, String oldPassword, String newPassword);
}
