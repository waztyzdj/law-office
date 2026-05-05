package com.lawoffice.framework.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ModuleInfo {
    
    String value() default "";
    
    String name();
    
    String description() default "";
    
    String[] tags() default {};
}
