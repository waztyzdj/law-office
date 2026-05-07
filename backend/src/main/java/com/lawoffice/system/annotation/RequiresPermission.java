package com.lawoffice.system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 用于方法级别的权限控制
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    
    /**
     * 需要的权限编码
     * 支持多个权限，默认为AND关系（需要同时满足）
     */
    String[] value() default {};
    
    /**
     * 逻辑关系：AND-需要满足所有权限，OR-满足任一权限即可
     */
    Logical logical() default Logical.AND;
    
    enum Logical {
        AND, OR
    }
}
