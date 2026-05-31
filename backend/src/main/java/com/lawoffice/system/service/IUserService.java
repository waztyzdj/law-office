package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.req.CurrentUserProfileReq;
import com.lawoffice.system.vo.CurrentUserLogVO;
import com.lawoffice.system.vo.CurrentUserOrganizationVO;
import com.lawoffice.system.vo.CurrentUserProfileVO;
import com.lawoffice.system.vo.CurrentUserTenantVO;
import com.lawoffice.system.vo.UserInfoVO;
import com.lawoffice.system.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IUserService extends IBaseService<User, UserVO> {

    /**
     * 校验明文密码与加密密码是否匹配。
     *
     * @param rawPassword 明文密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    boolean verifyPassword(String rawPassword, String encodedPassword);

    /**
     * 重置指定用户密码。
     *
     * @param userId 用户 ID
     * @param newPassword 新密码
     */
    void resetPassword(String userId, String newPassword);

    /**
     * 为用户覆盖保存角色。
     *
     * @param userId 用户 ID
     * @param roleIds 角色 ID 列表
     */
    void assignRoles(String userId, List<String> roleIds);

    /**
     * 以指定操作人的授权范围为边界，为用户覆盖保存角色。
     * <p>
     * 非超级管理员只能为当前租户成员分配当前租户角色，且角色权限不能超过自身权限范围。
     *
     * @param userId 用户 ID
     * @param roleIds 角色 ID 列表
     * @param operatorUsername 当前操作人账号
     */
    void assignRoles(String userId, List<String> roleIds, String operatorUsername);

    /**
     * 查询用户已分配角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<Role> getUserRoles(String userId);

    /**
     * 查询用户已分配角色 ID。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<String> getUserRoleIds(String userId);

    /**
     * 移除用户的指定角色。
     *
     * @param userId 用户 ID
     * @param roleIds 角色 ID 列表
     */
    void removeRoles(String userId, List<String> roleIds);

    /**
     * 为用户分配部门。
     *
     * @param userId 用户 ID
     * @param departIds 部门 ID 列表
     */
    void assignDeparts(String userId, List<String> departIds);

    /**
     * 查询用户所属部门。
     *
     * @param userId 用户 ID
     * @return 部门列表
     */
    List<SysDepart> getUserDeparts(String userId);

    /**
     * 移除用户的指定部门。
     *
     * @param userId 用户 ID
     * @param departIds 部门 ID 列表
     */
    void removeDeparts(String userId, List<String> departIds);

    /**
     * 为用户覆盖保存可访问租户。
     *
     * @param userId 用户 ID
     * @param tenantIds 租户 ID 列表
     */
    void assignTenants(String userId, List<String> tenantIds);

    /**
     * 为租户覆盖保存成员用户。
     *
     * @param tenantId 租户 ID
     * @param userIds 用户 ID 列表
     */
    void assignTenantUsers(String tenantId, List<String> userIds);

    /**
     * 查询用户可访问租户。
     *
     * @param userId 用户 ID
     * @return 租户列表
     */
    List<Tenant> getUserTenants(String userId);

    /**
     * 查询租户下正常状态的用户 ID。
     *
     * @param tenantId 租户 ID
     * @return 用户 ID 列表
     */
    List<String> getTenantUserIds(String tenantId);

    /**
     * 查询当前登录用户可切换的租户。
     * <p>
     * 超级管理员返回全部启用租户，普通用户返回已分配且启用的租户。
     *
     * @param username 用户名
     * @return 可切换租户列表
     */
    List<Tenant> getCurrentUserTenants(String username);

    /**
     * 查询当前租户下可接收站内消息的用户列表。
     *
     * @param username 用户名
     * @return 当前租户用户列表
     */
    List<UserVO> getCurrentTenantUsers(String username);

    /**
     * 移除用户的指定租户关系。
     *
     * @param userId 用户 ID
     * @param tenantIds 租户 ID 列表
     */
    void removeTenants(String userId, List<String> tenantIds);

    /**
     * 查询用户通过角色获得的权限。
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    List<Permission> getUserPermissions(String userId);

    /**
     * 查询用户在当前租户上下文下的权限。
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    List<Permission> getUserPermissionsInCurrentTenant(String userId);

    /**
     * 查询用户权限编码。
     *
     * @param userId 用户 ID
     * @return 权限编码列表
     */
    List<String> getUserPermissionCodes(String userId);

    /**
     * 按用户名查询用户权限编码。
     *
     * @param username 用户名
     * @return 权限编码列表
     */
    List<String> getUserPermissionCodesByUsername(String username);

    /**
     * 用户名密码登录。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录结果，包含 token、当前租户和用户信息
     */
    java.util.Map<String, Object> login(String username, String password);

    /**
     * 切换当前用户租户并签发新的 token。
     * <p>
     * 普通用户只能切换到已分配且启用的租户；超级管理员可切换到任一启用租户。
     *
     * @param username 用户名
     * @param tenantId 目标租户 ID
     * @param currentToken 当前 token，可为空
     * @return 新登录态信息
     */
    java.util.Map<String, Object> switchTenant(String username, String tenantId, String currentToken);

    /**
     * 查询当前用户基础信息。
     *
     * @param username 用户名
     * @return 用户信息
     */
    User getCurrentUserInfo(String username);

    /**
     * 用户登出。
     *
     * @param token token 字符串
     * @param username 用户名，可为空
     */
    void logout(String token, String username);

    /**
     * 修改当前用户密码。
     *
     * @param username 用户名
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(String username, String oldPassword, String newPassword);

    /**
     * 查询当前用户详情。
     *
     * @param username 用户名
     * @return 用户详情，包含角色、权限、菜单和当前租户
     */
    UserInfoVO getCurrentUserDetailInfo(String username);

    /**
     * 查询当前用户个人中心基础资料。
     *
     * @param username 用户名
     * @return 当前用户个人资料
     */
    CurrentUserProfileVO getCurrentUserProfile(String username);

    /**
     * 修改当前用户个人中心基础资料，仅允许修改非权限边界字段。
     *
     * @param username 用户名
     * @param req 个人资料修改请求
     * @return 修改后的当前用户个人资料
     */
    CurrentUserProfileVO updateCurrentUserProfile(String username, CurrentUserProfileReq req);

    /**
     * 上传当前用户头像并保存头像地址。
     *
     * @param username 用户名
     * @param file 头像图片文件
     * @return 修改后的当前用户个人资料
     */
    CurrentUserProfileVO uploadCurrentUserAvatar(String username, MultipartFile file);

    /**
     * 查询当前用户组织、角色和权限摘要。
     *
     * @param username 用户名
     * @return 组织权限信息
     */
    CurrentUserOrganizationVO getCurrentUserOrganization(String username);

    /**
     * 查询当前用户可切换租户并标记当前租户。
     *
     * @param username 用户名
     * @return 租户列表
     */
    List<CurrentUserTenantVO> getCurrentUserTenantOptions(String username);

    /**
     * 分页查询当前用户登录和操作日志，仅返回当前登录用户自己的日志。
     *
     * @param username 用户名
     * @param req 分页、筛选和排序请求
     * @return 当前用户日志分页结果
     */
    PageVO<CurrentUserLogVO> pageCurrentUserLogs(String username, BasePageReq req);
}
