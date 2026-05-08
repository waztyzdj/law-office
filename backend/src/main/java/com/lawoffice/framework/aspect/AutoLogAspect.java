package com.lawoffice.framework.aspect;

import com.alibaba.fastjson2.JSON;
import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.SysLog;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.framework.service.ILogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class AutoLogAspect {

    @Autowired
    private ILogService sysLogService;

    @Around("@annotation(autoLog)")
    public Object aroundWithAnnotation(ProceedingJoinPoint joinPoint, AutoLog autoLog) throws Throwable {
        return executeWithLog(joinPoint, autoLog.logType(), autoLog.operateType(), autoLog.value());
    }

    @Around("execution(* com.lawoffice..controller.BaseController.*(..)) && !@annotation(com.lawoffice.framework.annotation.AutoLog)")
    public Object aroundBaseController(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        
        LogType logType = LogType.OPERATION;
        OperateType operateType = getOperateTypeByMethodName(methodName);
        String customValue = "";
        
        return executeWithLog(joinPoint, logType, operateType, customValue);
    }

    private Object executeWithLog(ProceedingJoinPoint joinPoint, LogType logType, OperateType operateType, String customValue) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        SysLog sysLog = new SysLog();
        sysLog.setId(UUID.randomUUID().toString().replace("-", ""));
        sysLog.setLogType(logType.getCode());
        sysLog.setOperateType(operateType.getCode());
        
        String moduleName = extractModuleName(joinPoint);
        String operationName = getOperationName(joinPoint, customValue);
        sysLog.setLogContent(moduleName + "-" + operationName);
        
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                sysLog.setRequestUrl(request.getRequestURI());
                sysLog.setRequestType(request.getMethod());
                sysLog.setIp(getIpAddress(request));
                sysLog.setMethod(extractActualMethodName(joinPoint));
                
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    try {
                        sysLog.setRequestParam(serializeParams(args));
                    } catch (Exception e) {
                        log.warn("参数序列化失败", e);
                        sysLog.setRequestParam("参数序列化失败: " + e.getMessage());
                    }
                }
                
                sysLog.setUserid(getCurrentUserId(request));
                sysLog.setUsername(getCurrentUsername(request));
                sysLog.setClientType(getClientType(request));
            }
            
            sysLog.setCreateTime(LocalDateTime.now());
            sysLog.setCreateBy(sysLog.getUsername());
            
            Object result = joinPoint.proceed();
            
            long costTime = System.currentTimeMillis() - startTime;
            sysLog.setCostTime(costTime);
            
            sysLogService.saveLogAsync(sysLog);
            
            return result;
        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            sysLog.setCostTime(costTime);
            sysLog.setLogContent(sysLog.getLogContent() + " [异常: " + e.getMessage() + "]");
            
            sysLogService.saveLogAsync(sysLog);
            
            throw e;
        }
    }

    /**
     * 提取实际调用的 Controller 方法名（包含完整包路径）
     */
    private String extractActualMethodName(ProceedingJoinPoint joinPoint) {
        try {
            Class<?> controllerClass = joinPoint.getTarget().getClass();
            if (controllerClass.getName().contains("$$")) {
                controllerClass = controllerClass.getSuperclass();
            }
            
            String methodName = joinPoint.getSignature().getName();
            return controllerClass.getName() + "." + methodName;
        } catch (Exception e) {
            log.debug("提取实际方法名失败", e);
            return joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        }
    }

    /**
     * 序列化请求参数，过滤掉无法序列化的对象
     */
    private String serializeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        
        try {
            // 收集所有可序列化的参数
            java.util.List<Object> serializableArgs = new java.util.ArrayList<>();
            
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                
                String className = arg.getClass().getName();
                // 过滤掉 Servlet 相关对象
                if (className.contains("HttpServletRequest") || 
                    className.contains("HttpServletResponse") ||
                    className.contains("Servlet") ||
                    className.contains("jakarta.servlet")) {
                    continue;
                }
                
                serializableArgs.add(arg);
            }
            
            // 如果没有可序列化的参数，返回空对象
            if (serializableArgs.isEmpty()) {
                return "{}";
            }
            
            // 如果只有一个参数，直接序列化
            if (serializableArgs.size() == 1) {
                return JSON.toJSONString(serializableArgs.get(0));
            }
            
            // 如果有多个参数，序列化为数组
            return JSON.toJSONString(serializableArgs);
        } catch (Exception e) {
            log.warn("参数序列化异常", e);
            return "{}";
        }
    }

    private OperateType getOperateTypeByMethodName(String methodName) {
        switch (methodName) {
            case "list":
            case "page":
                return OperateType.QUERY;
            case "getById":
                return OperateType.GET_BY_ID;
            case "save":
                return OperateType.SAVE;
            case "batchSave":
                return OperateType.BATCH_SAVE;
            case "delete":
                return OperateType.DELETE;
            case "batchDelete":
                return OperateType.BATCH_DELETE;
            case "export":
                return OperateType.EXPORT;
            case "importExcel":
                return OperateType.IMPORT;
            default:
                return OperateType.CUSTOM;
        }
    }

    private String extractModuleName(ProceedingJoinPoint joinPoint) {
        try {
            Object target = joinPoint.getTarget();
            Class<?> controllerClass = target.getClass();
            
            if (controllerClass.getName().contains("$$")) {
                controllerClass = controllerClass.getSuperclass();
            }
            
            try {
                java.lang.reflect.Field moduleNameField = controllerClass.getSuperclass().getDeclaredField("moduleName");
                moduleNameField.setAccessible(true);
                String moduleName = (String) moduleNameField.get(target);
                
                if (moduleName != null && !moduleName.isEmpty()) {
                    return moduleName;
                }
            } catch (NoSuchFieldException e) {
                log.debug("BaseController中没有moduleName字段");
            }
            
            java.lang.reflect.Type genericSuperclass = controllerClass.getGenericSuperclass();
            
            if (genericSuperclass instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.Type[] types = ((java.lang.reflect.ParameterizedType) genericSuperclass).getActualTypeArguments();
                
                if (types != null && types.length > 1 && types[1] instanceof Class) {
                    Class<?> entityClass = (Class<?>) types[1];
                    ModuleInfo moduleInfo = entityClass.getAnnotation(ModuleInfo.class);
                    if (moduleInfo != null) {
                        return moduleInfo.name();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取模块名称失败: {}", e.getMessage());
        }
        
        return "系统操作";
    }

    private String getOperationName(ProceedingJoinPoint joinPoint, String customValue) {
        if (customValue != null && !customValue.isEmpty()) {
            return customValue;
        }
        
        String methodName = joinPoint.getSignature().getName();
        switch (methodName) {
            case "list":
                return "列表查询";
            case "page":
                return "分页查询";
            case "save":
                return "保存数据";
            case "delete":
                return "删除数据";
            case "batchDelete":
                return "批量删除";
            case "export":
                return "导出Excel";
            case "importExcel":
                return "导入Excel";
            case "getById":
                return "根据ID查询";
            case "batchSave":
                return "批量保存";
            default:
                return methodName;
        }
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index).trim();
            } else {
                return ip.trim();
            }
        }
        
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        ip = request.getRemoteAddr();
        
        // 1. 处理本地 IPv6 回环地址，强制转为 IPv4 格式
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        
        // 2. 如果是其他 IPv6 地址，尝试检查是否可以通过 DNS 解析为 IPv4（可选）
        // 注意：公网 IPv6 地址无法直接转换为 IPv4，除非有映射关系。
        // 如果你的网络环境支持 IPv4 映射的 IPv6 地址 (如 ::ffff:192.168.1.1)，可以这样处理：
        if (ip != null && ip.contains(":") && ip.startsWith("::ffff:")) {
            return ip.substring(7); // 提取出后面的 IPv4 地址
        }
        
        return ip;
    }

    private String getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? userId.toString() : "anonymous";
    }

    private String getCurrentUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : "anonymous";
    }

    private String getClientType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
                return "app";
            } else if (userAgent.contains("MicroMessenger")) {
                return "h5";
            }
        }
        return "pc";
    }
}
