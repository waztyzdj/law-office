# 日志审计

## 功能说明

日志审计记录系统操作行为，支持后续审计、排障和安全追踪。

## 代码入口

- Aspect：`backend/src/main/java/com/lawoffice/framework/aspect/AutoLogAspect.java`
- Service：`backend/src/main/java/com/lawoffice/framework/service/impl/LogServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/framework/entity/SysLog.java`
- 前端页面：`frontend/src/views/system/log/index.vue`

## 数据表

- `sys_log`

## 关键规则

- 日志记录由 AOP 统一处理。
- 敏感参数必须脱敏。
- 异常日志保留堆栈，接口响应不泄露内部细节。
