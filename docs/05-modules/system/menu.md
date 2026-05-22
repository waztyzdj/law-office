# 菜单与权限

## 功能说明

菜单与权限模块维护前端路由菜单、按钮权限和接口权限码。

## 代码入口

- 菜单 Controller：`backend/src/main/java/com/lawoffice/system/controller/MenuController.java`
- 权限 Controller：`backend/src/main/java/com/lawoffice/system/controller/PermissionController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/MenuServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/Permission.java`
- 前端页面：`frontend/src/views/system/menu/index.vue`
- 前端权限常量：`frontend/src/constants/permissions.ts`

## 数据表

- `sys_permission`
- `sys_role_permission`
- `sys_depart_permission`

## 接口

- `GET /menu/all`：获取当前用户菜单树。
- `/permission/*`：权限资源通用 CRUD。

## 关键规则

- 权限码与前端按钮控制保持一致。
- 菜单树由权限数据构建。
- 新增按钮权限时必须同步前端常量和后端权限数据。
