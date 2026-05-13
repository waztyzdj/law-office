package com.lawoffice.framework.config;

/**
 * 租户上下文持有者
 * 使用ThreadLocal存储当前请求的租户ID
 */
public class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前租户ID
     */
    public static void setCurrentTenantId(String tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
    }

    /**
     * 获取当前租户ID
     */
    public static String getCurrentTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    /**
     * 清除当前租户ID
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
    }
}
