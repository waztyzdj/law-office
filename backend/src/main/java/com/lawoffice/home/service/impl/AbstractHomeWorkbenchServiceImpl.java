package com.lawoffice.home.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.BaseTenantEntity;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.framework.vo.BaseVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.util.EntityFillUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

abstract class AbstractHomeWorkbenchServiceImpl<M extends BaseMapper<E>, E extends BaseTenantEntity, V extends BaseVO>
        extends BaseServiceImpl<M, E, V> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    protected final PermissionMapper permissionMapper;

    protected AbstractHomeWorkbenchServiceImpl(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    protected String requireTenantId(RequestContext context) {
        if (context != null && StringUtils.hasText(context.getTenantId())) {
            return context.getTenantId();
        }
        throw new IllegalArgumentException("租户ID不能为空");
    }

    protected String requireUserId(RequestContext context) {
        if (context != null && StringUtils.hasText(context.getUserId())) {
            return context.getUserId();
        }
        throw new IllegalArgumentException("当前用户不能为空");
    }

    protected String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    protected void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    protected void validateIn(String value, String message, Set<String> allowedValues) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    protected void validateJson(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "不是合法JSON");
        }
    }

    protected String normalizeConfigJson(String configJson, JsonNode config, String fieldName) {
        if (config != null && !config.isNull()) {
            try {
                return OBJECT_MAPPER.writeValueAsString(config);
            } catch (Exception e) {
                throw new IllegalArgumentException(fieldName + "不是合法JSON");
            }
        }
        String normalizedJson = trimToNull(configJson);
        validateJson(normalizedJson, fieldName);
        return normalizedJson;
    }

    protected JsonNode parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(configJson);
        } catch (Exception e) {
            return null;
        }
    }

    protected void validatePermissionCode(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return;
        }
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getPerms, permissionCode)
                .eq(Permission::getDeleteFlag, 0);
        if (permissionMapper.selectCount(wrapper) == 0) {
            throw new IllegalArgumentException("权限码不存在");
        }
    }

    protected boolean hasPermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return true;
        }
        try {
            Subject subject = SecurityUtils.getSubject();
            return subject != null && subject.isPermitted(permissionCode);
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean hasMenuAccess(String menuId, String explicitPermissionCode) {
        if (StringUtils.hasText(explicitPermissionCode) && !hasPermission(explicitPermissionCode)) {
            return false;
        }
        if (!StringUtils.hasText(menuId)) {
            return true;
        }
        Permission permission = permissionMapper.selectById(menuId);
        if (permission == null || permission.getDeleteFlag() == null || permission.getDeleteFlag() != 0) {
            return false;
        }
        return !StringUtils.hasText(permission.getPerms()) || hasPermission(permission.getPerms());
    }

    protected void requireActiveMenu(String menuId) {
        if (!StringUtils.hasText(menuId)) {
            return;
        }
        Permission permission = permissionMapper.selectById(menuId);
        if (permission == null || permission.getDeleteFlag() == null || permission.getDeleteFlag() != 0) {
            throw new IllegalArgumentException("菜单不存在或已删除");
        }
    }

    protected QueryWrapper<E> activeTenantWrapper(String tenantId) {
        QueryWrapper<E> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        return wrapper;
    }

    protected int pageNum(BasePageReq req) {
        if (req == null || req.getPageNum() <= 0) {
            return DEFAULT_PAGE_NUM;
        }
        return req.getPageNum();
    }

    protected int pageSize(BasePageReq req) {
        if (req == null || req.getPageSize() <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(req.getPageSize(), MAX_PAGE_SIZE);
    }

    protected void fillCreateOrUpdate(BaseTenantEntity entity, RequestContext context, boolean create) {
        EntityFillUtils.fillAuditFields(entity, context, create);
    }

    protected void fillUpdate(BaseTenantEntity entity, RequestContext context) {
        EntityFillUtils.fillAuditFields(entity, context, false);
    }

    protected String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    protected String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : HomeWorkbenchConstants.STATUS_ENABLED;
    }

    protected String defaultSize(String size) {
        return StringUtils.hasText(size) ? size : HomeWorkbenchConstants.SIZE_MEDIUM;
    }
}
