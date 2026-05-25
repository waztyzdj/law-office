package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.vo.TenantVO;

import java.util.List;

public interface ITenantService extends IBaseService<Tenant, TenantVO> {

    /**
     * 查询租户下正常状态的用户 ID。
     *
     * @param tenantId 租户 ID
     * @return 用户 ID 列表
     */
    List<String> getTenantUserIds(String tenantId);

    /**
     * 覆盖同步租户成员关系。
     *
     * @param tenantId 租户 ID
     * @param userIds 目标用户 ID 列表
     */
    void assignTenantUsers(String tenantId, List<String> userIds);

    /**
     * 查询租户默认管理员角色下的用户 ID。
     *
     * @param tenantId 租户 ID
     * @return 管理员用户 ID 列表
     */
    List<String> getTenantAdminUserIds(String tenantId);

    /**
     * 覆盖同步租户默认管理员角色成员。
     *
     * @param tenantId 租户 ID
     * @param userIds 管理员用户 ID 列表
     */
    void assignTenantAdmins(String tenantId, List<String> userIds);

    /**
     * 查询租户默认管理员角色已授权限 ID。
     *
     * @param tenantId 租户 ID
     * @return 权限 ID 列表
     */
    List<String> getTenantAdminPermissionIds(String tenantId);

    /**
     * 覆盖保存租户默认管理员角色权限。
     * <p>
     * 保存前会校验权限是否存在、补齐父级权限，并限制授权范围不能超过当前操作人自身权限。
     *
     * @param tenantId 租户 ID
     * @param permissionIds 目标权限 ID 列表
     * @param operatorUsername 当前操作人账号
     */
    void assignTenantAdminPermissions(String tenantId, List<String> permissionIds, String operatorUsername);
}
