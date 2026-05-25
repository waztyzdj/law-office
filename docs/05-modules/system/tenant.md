# 租户管理

## 功能说明

租户管理用于维护多租户信息，并为用户、角色、权限和业务数据提供隔离边界。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/TenantController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/TenantServiceImpl.java`
- 生命周期协作接口：`backend/src/main/java/com/lawoffice/system/service/ITenantLifecycleService.java`
- 生命周期协作实现：`backend/src/main/java/com/lawoffice/system/service/impl/TenantLifecycleServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/Tenant.java`
- 前端页面：`frontend/src/views/system/tenant/index.vue`

## 后端职责边界

- `TenantServiceImpl`：承接租户管理页面的用例入口，包括租户保存、删除、设置用户、设置管理员和管理员授权。
- `TenantLifecycleServiceImpl`：承接租户生命周期协作流程，包括默认管理员角色、默认数据复制、租户管理员成员同步和删除租户时的关联关系逻辑删除。
- `ITenantDefaultDataSyncService` / `TenantDefaultDataSyncServiceImpl`：承接默认租户 `0` 的数据字典、字典项和通用类型复制，不处理租户成员或角色授权。
- 用户-租户关系、角色-权限关系和租户上下文切换后续应继续抽成独立协作服务，避免在多个业务 Service 中重复写底层同步逻辑。

## 数据表

- `sys_tenant`
- `sys_user_tenant`
- `sys_role`
- `sys_user_role`

## 接口

资源前缀：`/tenant`，支持通用 CRUD。

扩展接口：

- `POST /tenant/userIds`：获取租户已分配用户 ID。
- `POST /tenant/assignUsers`：差量同步租户用户。
- `POST /tenant/adminUserIds`：获取租户管理员角色下的用户 ID。
- `POST /tenant/assignAdmins`：差量同步租户管理员角色成员。
- `POST /tenant/adminPermissionIds`：获取租户管理员角色已授权限 ID。
- `POST /tenant/assignAdminPermissions`：覆盖保存租户管理员角色权限。

## 页面能力

- 租户列表操作列支持设置用户。
- 租户用户设置使用穿梭框展示可选用户和已选用户，一个用户可以关联多个租户；保存时保留已存在关系，只新增或删除发生变化的关系。
- 租户列表操作列支持设置管理员，管理员从该租户已分配用户中选择。

## 关键规则

- 用户登录时会解析默认租户。
- 用户可在已分配的正常租户之间切换，切换后后端签发新的租户 token。
- 租户编码新增后不能修改。
- 保存租户后会幂等创建默认管理员角色；角色编码采用 `ADMIN_ + 租户编码`，角色名称采用 `租户名称 + 管理员`。
- 租户保存与初始化在同一事务边界内完成，初始化包括默认管理员角色、默认数据复制和初始管理员成员同步。
- 新建租户时可指定租户管理员；平台管理员不会自动加入新租户管理员角色。
- 租户管理员角色默认不自动授权，需要在租户管理的“管理员授权”入口维护。
- 设置租户管理员时会同步默认管理员角色成员；被设置为管理员的用户会自动补齐该租户用户关系。
- 删除租户时会逻辑删除该租户的用户关系、租户角色、角色权限和角色用户关系；不会删除用户主账号。
- 新建租户时会复制默认租户 `0` 的数据字典、字典项和通用类型；默认租户 `0` 新增这些数据时会补齐到所有启用租户，已存在数据不覆盖，已逻辑删除的同编码数据会恢复。
- 多租户业务表需要包含 `tenant_id`。
- 租户上下文不得由前端任意传入覆盖。

## 数据库迁移

历史库需要将以下唯一约束调整为租户内唯一：

- `sys_role`：`uniq_sys_role_role_code` -> `uniq_sys_role_tenant_role_code (tenant_id, role_code)`。
- `sys_dict`：`uk_sd_dict_code` -> `uk_sd_tenant_dict_code (tenant_id, dict_code)`。
- `sys_category`：`index_code` -> `uk_sc_tenant_code (tenant_id, code)`。

## 补充规则

- 租户管理列表提供“管理员授权”，平台管理员无需切换到目标租户即可维护该租户默认管理员角色权限。
- 租户管理员权限保存时会覆盖默认管理员角色原权限；授权范围不能超过当前操作人自身权限，且不允许下放租户管理、权限管理等平台级能力。
- 角色编码为 `ADMIN` 的超级管理员可在顶部租户切换入口中切换到任一启用租户，用于跨租户运维；切换后默认叠加目标租户 `ADMIN_ + 租户编码` 角色权限，普通用户仍只能切换到已分配且启用的租户。
