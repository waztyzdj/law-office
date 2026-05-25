# 角色管理

## 功能说明

角色管理用于维护系统角色，并通过角色权限关系控制用户可访问的菜单、按钮和接口。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/RoleController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/RoleServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/Role.java`
- Req：`backend/src/main/java/com/lawoffice/system/req/RoleReq.java`
- VO：`backend/src/main/java/com/lawoffice/system/vo/RoleVO.java`
- 前端页面：`frontend/src/views/system/role/index.vue`

## 数据表

- `sys_role`
- `sys_role_permission`
- `sys_user_role`

初始化脚本：`sql/系统权限初始化.sql`。

## 接口

资源前缀：`/role`，支持通用 CRUD。

扩展接口：

- `POST /role/permissionIds`：获取角色已分配权限 ID。
- `POST /role/assignPermissions`：覆盖保存角色权限。

## 页面能力

- 角色列表支持分页、筛选、排序、新增、编辑、删除。
- 角色授权使用菜单权限树，可同时分配菜单权限和按钮权限。

## 关键规则

- `role_code` 在同一租户内唯一。
- 系统默认角色编码为 `ADMIN` 的角色是超级管理员角色；该角色对应的用户可切换任意启用租户。
- 给 `ADMIN` 角色授权菜单时可选择全部菜单权限，不受当前操作者已有权限范围限制。
- 角色编码以 `ADMIN` 开头的角色不允许删除。
- 角色授权变更后，需要确保用户重新获取权限或刷新登录态。
