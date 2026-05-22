# 租户管理

## 功能说明

租户管理用于维护多租户信息，并为用户、角色、权限和业务数据提供隔离边界。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/TenantController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/TenantServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/Tenant.java`
- 前端页面：`frontend/src/views/system/tenant/index.vue`

## 数据表

- `sys_tenant`
- `sys_user_tenant`

## 接口

资源前缀：`/tenant`，支持通用 CRUD。

## 关键规则

- 用户登录时会解析默认租户。
- 多租户业务表需要包含 `tenant_id`。
- 租户上下文不得由前端任意传入覆盖。
