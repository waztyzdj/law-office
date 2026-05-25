package com.lawoffice.system.service;

import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.Tenant;

import java.time.LocalDateTime;
import java.util.List;

public interface ITenantLifecycleService {

    /**
     * 初始化新租户的基础关系。
     * <p>
     * 包括幂等创建默认租户管理员角色、复制默认租户基础数据，并在传入管理员用户时同步管理员成员。
     *
     * @param tenant 新增或已保存的租户实体
     * @param adminUserIds 初始租户管理员用户 ID 列表，可为空
     * @param operator 操作人账号，缺省时由实现层兜底
     * @param operateTime 操作时间，缺省时由实现层使用当前时间
     */
    void initializeTenant(Tenant tenant, List<String> adminUserIds, String operator, LocalDateTime operateTime);

    /**
     * 查询租户下正常状态的用户 ID。
     *
     * @param tenantId 租户 ID
     * @return 用户 ID 列表
     */
    List<String> getTenantUserIds(String tenantId);

    /**
     * 覆盖同步租户成员关系。
     * <p>
     * 已存在的有效关系保持不变，缺失关系补齐，不在目标列表内的关系按逻辑删除处理。
     *
     * @param tenantId 租户 ID
     * @param userIds 目标用户 ID 列表
     * @param operator 操作人账号
     */
    void assignTenantUsers(String tenantId, List<String> userIds, String operator);

    /**
     * 查询租户默认管理员角色下的用户 ID。
     *
     * @param tenantId 租户 ID
     * @return 管理员用户 ID 列表
     */
    List<String> getTenantAdminUserIds(String tenantId);

    /**
     * 覆盖同步租户默认管理员角色成员。
     * <p>
     * 被设置为管理员的用户会先补齐租户成员关系，再同步默认管理员角色关系。
     *
     * @param tenantId 租户 ID
     * @param userIds 管理员用户 ID 列表
     * @param operator 操作人账号
     */
    void assignTenantAdmins(String tenantId, List<String> userIds, String operator);

    /**
     * 确保租户存在默认管理员角色。
     * <p>
     * 角色编码规则为 {@code ADMIN_ + 租户编码}，角色名称规则为 {@code 租户名称 + 管理员}。
     * 如果角色已被逻辑删除，实现层会恢复该角色。
     *
     * @param tenantId 租户 ID
     * @param operator 操作人账号
     * @param operateTime 操作时间
     * @return 默认租户管理员角色
     */
    Role ensureTenantAdminRole(String tenantId, String operator, LocalDateTime operateTime);

    /**
     * 删除租户时逻辑删除关联关系。
     * <p>
     * 包括租户用户关系、租户角色、角色权限关系和角色用户关系；不会删除用户主账号。
     *
     * @param tenantId 租户 ID
     * @param deleteBy 删除人账号
     */
    void deleteTenantRelations(String tenantId, String deleteBy);
}
