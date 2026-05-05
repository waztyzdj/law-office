package com.lawoffice.framework.annotation;

import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoLog {
    
    String value() default "";
    
    LogType logType() default LogType.OPERATION;
    
    OperateType operateType() default OperateType.CUSTOM;
    
    boolean enabled() default true;
}
