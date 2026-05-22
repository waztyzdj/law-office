# 日志与审计设计

## 操作日志

系统通过 `AutoLogAspect` 自动记录操作日志，日志实体为 `SysLog`，数据库表为 `sys_log`。

## 日志表字段

重点字段包括：

- `log_type`：日志类型。
- `operate_type`：操作类型。
- `userid`：操作用户 ID。
- `username`：操作用户名。
- `ip`：客户端 IP。
- `request_url`：请求地址。
- `request_param`：请求参数。
- `create_time`：创建时间。

## 记录原则

- 通用操作日志优先使用 AOP 自动记录。
- 特殊场景使用 `@AutoLog` 补充语义。
- 敏感字段必须脱敏。
- 系统异常要记录完整堆栈，但返回前端的错误信息要泛化。
