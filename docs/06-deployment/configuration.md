# 配置项说明

## 后端配置

配置文件位于：

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-prod.yml`

重点配置：

- 数据库连接。
- Redis。
- MinIO。
- JWT。
- Druid。
- SpringDoc。

## 前端配置

配置文件位于：

- `frontend/.env`
- `frontend/.env.development`
- `frontend/.env.production`

重点配置：

- API 地址。
- 应用基础路径。
- 构建模式。

## 安全要求

- 生产密钥不提交到 Git。
- 环境差异通过 profile 或环境变量管理。
- 配置项新增后同步更新本文档。
