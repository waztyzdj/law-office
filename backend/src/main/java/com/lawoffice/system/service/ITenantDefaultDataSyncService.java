package com.lawoffice.system.service;

/**
 * 租户默认数据同步服务。
 * <p>
 * 负责将默认租户 {@code 0} 的数据字典、字典项和通用类型补齐到业务租户。
 */
public interface ITenantDefaultDataSyncService {

    /**
     * 新建租户后，将默认租户基础数据同步到目标租户。
     *
     * @param tenantId 目标租户 ID
     * @param operator 操作人账号
     */
    void syncDefaultDataToTenant(String tenantId, String operator);

    /**
     * 默认租户新增或保存字典后，将该字典补齐到所有启用业务租户。
     *
     * @param dictId 默认租户字典 ID
     * @param operator 操作人账号
     */
    void syncDefaultDictToAllTenants(String dictId, String operator);

    /**
     * 默认租户新增或保存字典项后，将该字典项补齐到所有启用业务租户。
     *
     * @param dictItemId 默认租户字典项 ID
     * @param operator 操作人账号
     */
    void syncDefaultDictItemToAllTenants(String dictItemId, String operator);

    /**
     * 默认租户新增或保存通用类型后，将该通用类型补齐到所有启用业务租户。
     *
     * @param categoryId 默认租户通用类型 ID
     * @param operator 操作人账号
     */
    void syncDefaultCategoryToAllTenants(String categoryId, String operator);

    /**
     * 判断当前租户上下文是否为默认租户上下文。
     *
     * @return 当前上下文为空或为默认租户 {@code 0} 时返回 true
     */
    boolean isSystemTenantContext();
}
