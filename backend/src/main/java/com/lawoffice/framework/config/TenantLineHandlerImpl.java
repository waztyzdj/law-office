package com.lawoffice.framework.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.stereotype.Component;

/**
 * 多租户处理器
 * 自动在SQL中添加租户ID过滤条件
 */
@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    /**
     * 获取当前租户ID
     * 从ThreadLocal或请求上下文中获取
     */
    @Override
    public Expression getTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        
        // 如果租户ID为空，使用默认值"0"
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "0";
        }
        
        return new StringValue(tenantId);
    }

    /**
     * 判断是否需要忽略租户过滤
     * 以下表不需要租户隔离：
     * 1. 租户相关表：sys_tenant, sys_user_tenant
     * 2. 用户相关表：sys_user
     * 3. 权限菜单表：sys_permission
     * 4. 日志表：sys_log
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 不需要租户隔离的表
        return "sys_tenant".equalsIgnoreCase(tableName)                    // 租户表
            || "sys_user".equalsIgnoreCase(tableName)                      // 用户表
            || "sys_user_tenant".equalsIgnoreCase(tableName)               // 用户租户关系表
            || "sys_permission".equalsIgnoreCase(tableName)                // 权限菜单表
            || "sys_log".equalsIgnoreCase(tableName);                      // 系统日志表
    }
}
