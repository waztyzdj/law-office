package com.lawoffice.system.service;

import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.Tenant;

import java.time.LocalDateTime;
import java.util.List;

public interface ITenantLifecycleService {

    void initializeTenant(Tenant tenant, List<String> adminUserIds, String operator, LocalDateTime operateTime);

    List<String> getTenantUserIds(String tenantId);

    void assignTenantUsers(String tenantId, List<String> userIds, String operator);

    List<String> getTenantAdminUserIds(String tenantId);

    void assignTenantAdmins(String tenantId, List<String> userIds, String operator);

    Role ensureTenantAdminRole(String tenantId, String operator, LocalDateTime operateTime);

    void deleteTenantRelations(String tenantId, String deleteBy);
}
