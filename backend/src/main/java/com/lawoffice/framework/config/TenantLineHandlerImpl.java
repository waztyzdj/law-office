package com.lawoffice.framework.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus tenant handler.
 */
@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "0";
        }
        return new StringValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return "sys_tenant".equalsIgnoreCase(tableName)
                || "sys_user".equalsIgnoreCase(tableName)
                || "sys_user_tenant".equalsIgnoreCase(tableName)
                || "sys_permission".equalsIgnoreCase(tableName);
    }
}
