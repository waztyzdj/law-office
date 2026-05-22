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

初始化脚本：`sql/系统权限初始化.sql`。

## 接口

- `GET /menu/all`：获取当前用户菜单树。
- `GET /permission/tree`：获取全部菜单和按钮权限树。
- `/permission/*`：权限资源通用 CRUD。

## 页面能力

- 支持维护一级菜单、子菜单、按钮权限三类权限，`menu_type` 取值为 `0` 一级菜单、`1` 子菜单、`2` 按钮权限。
- 支持设置父级、路径、组件、组件名、权限码、图标、排序、状态、隐藏和缓存。
- 支持在树形列表中新增下级、编辑和删除。

## 关键规则

- 权限码与前端按钮控制保持一致。
- 菜单树由权限数据构建。
- 新增按钮权限时必须同步前端常量和后端权限数据。
