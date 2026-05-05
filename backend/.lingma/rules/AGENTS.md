# 项目开发规范 (必须遵守)

## 后端代码规范
1. **分层架构**：严格遵循 Controller -> Service -> Mapper 结构。
2. **基类继承**：Controller 必须继承 `BaseController`，Service 实现类必须继承 `BaseServiceImpl`。
3. **命名规范**：接口以 `I` 开头，实现类以 `Impl` 结尾。实体类需添加 `@ModuleInfo` 注解。
4. **异步日志**：不要在 Controller 中手动记录日志，依赖 `AutoLogAspect` 自动处理。

## 数据库规范
1. **主键**：统一使用 `id`，类型 `VARCHAR(64)`。
2. **外键**：命名为 `xxxx_id`，类型 `VARCHAR(64)`。
3. **字符串长度**：必须是 2 的 N 次幂（16, 32, 64, 128...）。
4. **索引命名**：`pk_表名`, `uk_表名_字段`, `idx_表名_字段`。
