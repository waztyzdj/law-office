package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.BaseTenantEntity;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.framework.vo.BaseVO;
import com.lawoffice.util.EntityFillUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 工作流配置类 Service 的公共校验能力。
 */
abstract class AbstractWorkflowConfigServiceImpl<M extends BaseMapper<E>, E extends BaseTenantEntity, V extends BaseVO>
        extends BaseServiceImpl<M, E, V> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected void prepareTenant(E entity, BaseDTO<E> dto) {
        if (entity == null) {
            throw new IllegalArgumentException("保存数据不能为空");
        }
        String tenantId = resolveTenantId(entity, dto == null ? null : dto.getContext());
        entity.setTenantId(tenantId);
    }

    protected String resolveTenantId(BaseTenantEntity entity, RequestContext context) {
        if (entity != null && StringUtils.hasText(entity.getTenantId())) {
            return entity.getTenantId();
        }
        if (context != null && StringUtils.hasText(context.getTenantId())) {
            return context.getTenantId();
        }
        throw new IllegalArgumentException("租户ID不能为空");
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

    protected void validateIn(String value, String message, String... allowedValues) {
        for (String allowedValue : allowedValues) {
            if (Objects.equals(value, allowedValue)) {
                return;
            }
        }
        throw new IllegalArgumentException(message);
    }

    protected void validateJson(String json, String fieldName, boolean required) {
        if (!StringUtils.hasText(json)) {
            if (required) {
                throw new IllegalArgumentException(fieldName + "不能为空");
            }
            return;
        }
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "不是合法JSON");
        }
    }

    protected void validateUnique(E entity, String message, Object... columnValues) {
        QueryWrapper<E> wrapper = new QueryWrapper<>();
        wrapper.eq("delete_flag", 0)
                .eq("tenant_id", entity.getTenantId());
        for (int i = 0; i < columnValues.length; i += 2) {
            wrapper.eq(String.valueOf(columnValues[i]), columnValues[i + 1]);
        }
        if (StringUtils.hasText(entity.getId())) {
            wrapper.ne("id", entity.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    protected <T extends BaseTenantEntity> T requireActiveById(BaseMapper<T> mapper, String id, String tenantId, String message) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException(message);
        }
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        T entity = mapper.selectOne(wrapper);
        if (entity == null) {
            throw new IllegalArgumentException(message);
        }
        return entity;
    }

    protected <T> long countActive(BaseMapper<T> mapper, String tenantId, Object... columnValues) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("delete_flag", 0)
                .eq("tenant_id", tenantId);
        for (int i = 0; i < columnValues.length; i += 2) {
            wrapper.eq(String.valueOf(columnValues[i]), columnValues[i + 1]);
        }
        return mapper.selectCount(wrapper);
    }

    protected E requireCurrent(String id, String tenantId, String message) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("ID不能为空");
        }
        E entity = requireActiveById(baseMapper, id, tenantId, message);
        return entity;
    }

    protected void fillUpdate(BaseTenantEntity entity, RequestContext context) {
        EntityFillUtils.fillAuditFields(entity, context, false);
    }

    protected List<String> resolveDeleteIds(BaseDTO<E> deleteDTO) {
        List<String> ids = new ArrayList<>();
        if (deleteDTO == null) {
            return ids;
        }
        if (StringUtils.hasText(deleteDTO.getId())) {
            ids.add(deleteDTO.getId());
        }
        if (deleteDTO.getDeleteIds() != null) {
            ids.addAll(deleteDTO.getDeleteIds().stream()
                    .filter(StringUtils::hasText)
                    .toList());
        }
        return ids;
    }
}
