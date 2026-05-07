package com.lawoffice.util;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 实体字段填充工具类
 * 用于自动填充审计字段（创建时间、更新时间等）
 */
public class EntityFillUtils {

    /**
     * 填充实体的审计字段
     * 
     * @param entity 实体对象
     * @param context 请求上下文
     * @param isCreate 是否为新增操作
     */
    public static void fillAuditFields(BaseEntity entity, RequestContext context, boolean isCreate) {
        if (entity == null) {
            return;
        }
        
        String username = context != null ? context.getUsername() : "system";
        
        if (isCreate) {
            entity.setCreateTime(LocalDateTime.now());
            entity.setCreateBy(username);
        } else {
            entity.setUpdateTime(LocalDateTime.now());
            entity.setUpdateBy(username);
        }
        
        if (entity.getDeleteFlag() == null) {
            entity.setDeleteFlag(0);
        }
    }

    /**
     * 填充逻辑删除字段
     * 
     * @param entity 实体对象
     * @param deleteBy 删除人
     */
    public static void fillDeleteFields(BaseEntity entity, String deleteBy) {
        if (entity == null) {
            return;
        }
        
        entity.setDeleteFlag(1);
        entity.setDeleteTime(LocalDateTime.now());
        entity.setDeleteBy(deleteBy != null ? deleteBy : "system");
    }
}
