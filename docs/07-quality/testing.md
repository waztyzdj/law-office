# 测试策略

## 后端

推荐测试类型：

- Service 单元测试：验证业务规则。
- Controller/MockMvc 测试：验证接口协议。
- Mapper/集成测试：验证 SQL、事务和数据库约束。

常用命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd -q -DskipTests compile
```

## 前端

当前项目保留构建和类型检查脚本：

```powershell
pnpm typecheck
pnpm build
```

后续如恢复 Vitest 或 Playwright，需要补充：

- 测试目录。
- 命名规范。
- 覆盖范围。
- 本地和 CI 运行方式。

## 验证最低要求

- 文档-only 变更：无需编译，但要检查链接和路径。
- 后端代码变更：至少编译。
- 前端代码变更：至少类型检查；UI 变更建议浏览器验证。
- 数据库变更：验证脚本可执行，并同步代码和文档。
