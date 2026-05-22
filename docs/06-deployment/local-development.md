# 本地开发

## 后端

目录：

```text
backend/
```

编译：

```powershell
.\mvnw.cmd -q -DskipTests compile
```

配置文件：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

本地运行前需要准备：

- JDK 17。
- MySQL。
- Redis。
- MinIO，如使用文件能力。
- 执行 `sql/建表脚本.sql`。

## 前端

目录：

```text
frontend/
```

常用命令：

```powershell
pnpm install
pnpm dev
pnpm build
```

配置文件：

- `.env`
- `.env.development`
- `.env.production`

## 注意事项

- 不要提交真实密钥。
- 修改接口代理或后端地址后同步检查前端请求配置。
- 当前前端 `typecheck` 依赖 `vue-tsc`，如命令不可用需要先补齐依赖。
