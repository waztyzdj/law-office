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

## 接口

资源前缀：`/role`，支持通用 CRUD。

## 关键规则

- `role_code` 唯一。
- 角色授权变更后，需要确保用户重新获取权限或刷新登录态。
