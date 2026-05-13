package com.lawoffice.framework.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 多租户基础实体类
 * 所有需要租户隔离的实体都应继承此类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseTenantEntity extends BaseEntity implements Serializable {

    /**
     * 租户ID
     * 注意：不需要特殊注解，租户隔离由 TenantLineInnerInterceptor 自动处理
     */
    private String tenantId;
}
