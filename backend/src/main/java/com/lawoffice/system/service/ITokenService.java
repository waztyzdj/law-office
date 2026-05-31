package com.lawoffice.system.service;

import java.util.List;

/**
 * Token管理服务接口
 * 负责Token的生成、验证、存储和刷新
 */
public interface ITokenService {

    /**
     * 生成Token并存储到Redis
     *
     * @param username 用户名
     * @param userId 用户ID
     * @param realName 真实姓名
     * @param permissions 权限列表
     * @param roles 角色列表
     * @return Token字符串
     */
    String generateAndStoreToken(String username, String userId, String realName, 
                                 List<String> permissions, 
                                 List<String> roles);

    /**
     * 生成Token并存储到Redis（支持租户ID）
     *
     * @param username 用户名
     * @param userId 用户ID
     * @param realName 真实姓名
     * @param permissions 权限列表
     * @param roles 角色列表
     * @param tenantId 租户ID
     * @return Token字符串
     */
    String generateAndStoreTokenWithTenant(String username, String userId, String realName,
                                           List<String> permissions,
                                           List<String> roles,
                                           String tenantId);

    /**
     * 验证Token是否有效
     *
     * @param token Token字符串
     * @return true-有效 false-无效
     */
    boolean validateToken(String token);

    /**
     * 从Redis获取用户权限
     *
     * @param username 用户名
     * @return 权限列表
     */
    List<String> getPermissionsFromRedis(String username);

    /**
     * 从Redis获取用户角色
     *
     * @param username 用户名
     * @return 角色列表
     */
    List<String> getRolesFromRedis(String username);

    /**
     * 删除Token（登出时使用）
     *
     * @param token Token字符串
     */
    void removeToken(String token);

    /**
     * 刷新Token有效期
     *
     * @param token Token字符串
     * @return true-成功 false-失败
     */
    boolean refreshToken(String token);

    /**
     * 获取Token剩余有效期（秒）
     *
     * @param token Token字符串
     * @return 剩余秒数
     */
    long getTokenRemainingTime(String token);

    /**
     * 强制用户下线（删除该用户的所有Token）
     *
     * @param username 用户名
     */
    void forceLogout(String username);

    /**
     * 刷新用户权限和角色缓存，不删除已登录 Token。
     *
     * @param username 用户名
     * @param permissions 最新权限列表
     * @param roles 最新角色列表
     */
    void refreshUserAuthorization(String username, List<String> permissions, List<String> roles);
}
