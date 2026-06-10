# 组织机构

## 功能说明

组织机构模块维护部门树、部门成员、部门负责人、部门内直属上级和部门相关权限、角色关系。

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

- `POST /depart/userIds`：查询部门成员 ID。
- `POST /depart/users`：查询部门成员。
- `POST /depart/assignUsers`：覆盖保存部门成员，并同步默认部门角色。
- `POST /depart/roleIds`：查询部门角色 ID。
- `POST /depart/roles`：查询部门角色。
- `POST /depart/assignRoles`：绑定部门角色。
- `POST /depart/permissionIds`：查询部门直接权限 ID。
- `POST /depart/permissions`：查询部门直接权限。
- `POST /depart/permissionSources`：查询部门权限来源。
- `GET /depart/grantablePermissionTree`：查询当前用户可授予部门角色的权限树。
- `POST /depart/assignPermissions`：覆盖保存部门直接权限。
- `POST /departRole/permissionIds`：查询部门角色权限 ID。
- `POST /departRole/permissions`：查询部门角色权限。
- `POST /departRole/assignPermissions`：覆盖保存部门角色权限。
- `POST /departRole/userIds`：查询部门角色用户 ID。
- `POST /departRole/users`：查询部门角色用户。
- `POST /departRole/assignUsers`：覆盖保存部门角色用户。
- `POST /departRole/save`：新增或编辑普通部门角色。
- `POST /departRole/delete`：删除普通部门角色，并清理角色权限、角色人员关系。

## 关键规则

- 部门通过 `parent_id` 构建树。
- 根部门 `parent_id` 统一保存为 `NULL`。
- 父部门必须存在且未删除，编辑时禁止把父部门设置为自身或子孙部门。
- 同一租户内 `org_code` 不能重复。
- 删除部门前必须确认没有下级部门、用户部门关系、部门直接权限、部门角色权限和部门角色用户关系。
- 部门保存后会自动维护默认部门角色，并同步 `iz_leaf` 叶子节点标记。
- 默认部门角色编码为 `DEPART_租户编号_部门编号`，其中部门编号对应 `org_code`。
- 部门成员默认拥有本部门默认角色；成员从部门移除时同步移除该部门下的角色用户关系。
- 默认部门角色由系统维护，不能修改、删除，也不能手动分配人员；默认角色人员来自部门成员。
- 默认部门角色允许授权权限，普通部门角色允许新增、编辑、删除、授权和分配人员。
- 用户部门关系 `sys_user_depart` 承载主部门、部门唯一负责人和部门维度直属上级。
- 同一租户内一个用户建议只设置一个主部门；同一部门只允许设置一个负责人，均由 Service 校验。
- 直属上级按用户所在部门维护，保存时必须避免自己作为自己的上级，并避免形成上下级循环。
- 部门角色可通过 `workflow_enabled=1` 标记为审批中心可选的部门岗位；默认部门角色不建议作为审批岗位。
- 涉及用户部门、部门权限和部门角色关系时要避免孤立关系数据。

## 数据库约束

- `sys_depart` 使用 `tenant_id + org_code + delete_flag` 约束部门编码。
- 关系表使用租户 ID、业务外键和 `delete_flag` 的复合唯一索引避免重复绑定。
- `sys_user_depart` 使用 `idx_sud_tenant_dep_leader` 支撑按部门解析负责人，使用 `idx_sud_tenant_supervisor` 支撑直属上级关系查询。
- `sys_depart_role` 使用 `idx_sdr_tenant_depart_workflow` 支撑审批岗位角色查询。
