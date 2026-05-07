package com.lawoffice.system.aspect;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.system.annotation.RequiresPermission;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 权限校验AOP切面
 * 支持两种权限控制方式：
 * 1. 拦截带有@RequiresPermission注解的方法（手动指定权限）
 * 2. 拦截BaseController中的方法，根据@ModuleInfo自动生成权限（自动权限控制）
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    /**
     * HTTP方法与权限操作的映射（用于BaseController自动权限）
     */
    private static final Map<String, String> METHOD_PERMISSION_MAP = new HashMap<>();

    static {
        // 查看权限
        METHOD_PERMISSION_MAP.put("getById", "view");
        METHOD_PERMISSION_MAP.put("list", "view");
        METHOD_PERMISSION_MAP.put("page", "view");
        METHOD_PERMISSION_MAP.put("export", "view");
        
        // 编辑权限
        METHOD_PERMISSION_MAP.put("save", "edit");
        METHOD_PERMISSION_MAP.put("batchSave", "edit");
        METHOD_PERMISSION_MAP.put("delete", "edit");
        METHOD_PERMISSION_MAP.put("batchDelete", "edit");
        METHOD_PERMISSION_MAP.put("import", "edit");
    }

    /**
     * 方式1：拦截带有@RequiresPermission注解的方法（手动指定权限）
     */
    @Before("@annotation(com.lawoffice.system.annotation.RequiresPermission)")
    public void checkPermissionByAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return;
        }

        String[] permissions = annotation.value();
        RequiresPermission.Logical logical = annotation.logical();

        if (permissions == null || permissions.length == 0) {
            log.warn("权限注解未配置权限编码: {}", method.getName());
            return;
        }

        Subject subject = SecurityUtils.getSubject();
        
        boolean hasPermission;
        if (logical == RequiresPermission.Logical.OR) {
            // OR逻辑：满足任一权限即可
            hasPermission = Arrays.stream(permissions)
                    .anyMatch(subject::isPermitted);
        } else {
            // AND逻辑：需要满足所有权限
            hasPermission = Arrays.stream(permissions)
                    .allMatch(subject::isPermitted);
        }

        if (!hasPermission) {
            log.warn("用户 {} 没有权限访问方法: {}, 需要的权限: {}", 
                    subject.getPrincipal(), method.getName(), Arrays.toString(permissions));
            throw new RuntimeException("没有权限访问该功能");
        }

        log.debug("用户 {} 权限校验通过: {}", subject.getPrincipal(), method.getName());
    }

    /**
     * 方式2：拦截BaseController中的所有公共方法，根据@ModuleInfo自动生成权限
     */
    @Before("execution(public * com.lawoffice.framework.controller.BaseController+.*(..))")
    public void checkPermissionForBaseController(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            
            // 获取目标类（实际的Controller类）
            Class<?> targetClass = joinPoint.getTarget().getClass();
            
            // 检查是否有 ModuleInfo 注解
            ModuleInfo moduleInfo = targetClass.getAnnotation(ModuleInfo.class);
            if (moduleInfo == null) {
                // 如果没有 ModuleInfo 注解，跳过权限校验
                log.debug("类 {} 没有 ModuleInfo 注解，跳过权限校验", targetClass.getSimpleName());
                return;
            }
            
            // 如果方法有 @RequiresPermission 注解，由 checkPermissionByAnnotation 处理，这里跳过
            if (method.isAnnotationPresent(RequiresPermission.class)) {
                return;
            }
            
            // 获取模块名称（转换为小写，作为权限前缀）
            String moduleName = moduleInfo.name();
            
            // 获取方法名
            String methodName = method.getName();
            
            // 生成权限编码
            String permissionCode = generatePermissionCode(moduleName, methodName);
            
            if (permissionCode == null) {
                log.debug("方法 {} 不需要权限校验", methodName);
                return;
            }
            
            // 执行权限校验
            Subject subject = SecurityUtils.getSubject();
            if (!subject.isPermitted(permissionCode)) {
                log.warn("用户 {} 没有权限 [{}] 访问方法: {}.{}", 
                        subject.getPrincipal(), permissionCode, 
                        targetClass.getSimpleName(), methodName);
                throw new RuntimeException("没有权限访问该功能，需要权限: " + permissionCode);
            }
            
            log.debug("用户 {} 权限 [{}] 校验通过", subject.getPrincipal(), permissionCode);
            
        } catch (RuntimeException e) {
            // 重新抛出运行时异常
            throw e;
        } catch (Exception e) {
            log.error("权限校验异常", e);
            throw new RuntimeException("权限校验失败: " + e.getMessage());
        }
    }

    /**
     * 生成权限编码
     * 规则：模块名:操作类型
     * 例如：user:view, role:add, permission:delete
     */
    private String generatePermissionCode(String moduleName, String methodName) {
        // 优先使用方法名映射
        String operation = METHOD_PERMISSION_MAP.get(methodName);
        
        // 如果还是没有，返回null（不校验权限）
        if (operation == null) {
            return null;
        }
        
        // 生成权限编码：模块名:操作
        return moduleName + ":" + operation;
    }
}
