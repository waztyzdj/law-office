# 组织机构

## 功能说明

组织机构模块维护部门树和部门相关权限、角色关系。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/SysDepartController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/SysDepartServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/SysDepart.java`
- 前端页面：`frontend/src/views/system/depart/index.vue`

## 数据表

- `sys_depart`
- `sys_user_depart`
- `sys_depart_permission`
- `sys_depart_role`
- `sys_depart_role_permission`
- `sys_depart_role_user`

## 接口

资源前缀：`/depart`，支持通用 CRUD。

## 关键规则

- 部门通过 `parent_id` 构建树。
- `org_code` 唯一。
- 涉及用户部门关系时要避免孤立关系数据。
