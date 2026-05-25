package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.vo.TenantVO;

import java.util.List;

public interface ITenantService extends IBaseService<Tenant, TenantVO> {

    List<String> getTenantUserIds(String tenantId);

    void assignTenantUsers(String tenantId, List<String> userIds);

    List<String> getTenantAdminUserIds(String tenantId);

    void assignTenantAdmins(String tenantId, List<String> userIds);

    List<String> getTenantAdminPermissionIds(String tenantId);

    void assignTenantAdminPermissions(String tenantId, List<String> permissionIds, String operatorUsername);
}
