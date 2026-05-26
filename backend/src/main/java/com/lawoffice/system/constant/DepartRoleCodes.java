package com.lawoffice.system.constant;

import org.springframework.util.StringUtils;

/**
 * 部门角色编码规则。
 */
public final class DepartRoleCodes {

    public static final String DEFAULT_DEPART_ROLE_PREFIX = "DEPART";
    public static final String SYSTEM_TENANT_ID = "0";

    private DepartRoleCodes() {
    }

    public static String buildDefaultRoleCode(String tenantId, String departCode) {
        String normalizedTenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : SYSTEM_TENANT_ID;
        String normalizedDepartCode = StringUtils.hasText(departCode) ? departCode.trim() : "";
        return DEFAULT_DEPART_ROLE_PREFIX + "_" + normalizedTenantId + "_" + normalizedDepartCode;
    }
}
