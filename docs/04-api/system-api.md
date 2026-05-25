# 系统管理接口

## 通用 CRUD 资源

以下资源均继承通用 CRUD 接口：

| 资源 | 前缀 | Controller |
| --- | --- | --- |
| 用户 | `/user` | `UserController` |
| 角色 | `/role` | `RoleController` |
| 权限 | `/permission` | `PermissionController` |
| 部门 | `/depart` | `SysDepartController` |
| 租户 | `/tenant` | `TenantController` |
| 字典 | `/dict` | `SysDictController` |
| 字典明细 | `/dictItem` | `SysDictItemController` |
| 通用类型 | `/category` | `SysCategoryController` |
| 文件 | `/files` | `SysFilesController` |

每个资源默认支持：

- `POST /list`
- `POST /page`
- `POST /getById`
- `POST /save`
- `POST /batchSave`
- `POST /delete`
- `POST /batchDelete`
- `POST /import`
- `POST /export`

## 菜单接口

### 获取所有菜单

- 方法：`GET`
- 路径：`/menu/all`
- 说明：获取当前登录用户菜单列表，返回树形结构。

## 权限扩展接口

### 获取菜单权限树

- 方法：`GET`
- 路径：`/permission/tree`
- 说明：获取全部菜单和按钮权限树，用于菜单管理与角色授权。

## 角色扩展接口

### 获取角色权限 ID

- 方法：`POST`
- 路径：`/role/permissionIds`
- 请求体：`{ "id": "角色ID" }`
- 说明：获取角色已分配的权限 ID 列表。

### 分配角色权限

- 方法：`POST`
- 路径：`/role/assignPermissions`
- 请求体：`{ "id": "角色ID", "ids": ["权限ID"] }`
- 说明：覆盖保存角色菜单和按钮权限；角色编码为 `ADMIN` 的系统默认超级管理员角色可选择全部菜单权限。

## 用户扩展接口

### 当前用户详情

- 方法：`GET`
- 路径：`/user/info`
- 说明：返回当前用户、角色、权限和当前租户。

### 当前用户租户列表

- 方法：`POST`
- 路径：`/user/tenants`
- 说明：返回当前用户可切换的正常租户列表。

### 切换当前租户

- 方法：`POST`
- 路径：`/user/switchTenant`
- 请求体：`{ "tenantId": "租户ID" }`
- 说明：切换当前登录用户租户并返回新的 token。

### 获取用户角色 ID

- 方法：`POST`
- 路径：`/user/roleIds`
- 请求体：`{ "id": "用户ID" }`
- 说明：获取用户已分配的角色 ID 列表。

### 分配用户角色

- 方法：`POST`
- 路径：`/user/assignRoles`
- 请求体：`{ "id": "用户ID", "ids": ["角色ID"] }`
- 说明：覆盖保存用户角色。

## 租户扩展接口

### 获取租户用户 ID

- 方法：`POST`
- 路径：`/tenant/userIds`
- 请求体：`{ "id": "租户ID" }`
- 说明：获取租户已分配的用户 ID 列表。

### 分配租户用户

- 方法：`POST`
- 路径：`/tenant/assignUsers`
- 请求体：`{ "id": "租户ID", "ids": ["用户ID"] }`
- 说明：按提交的用户 ID 差量同步租户用户关系，已存在关系保持不变，取消勾选的关系会逻辑删除。

### 获取租户管理员用户 ID

- 方法：`POST`
- 路径：`/tenant/adminUserIds`
- 请求体：`{ "id": "租户ID" }`
- 说明：获取租户默认管理员角色下的用户 ID 列表；如果默认角色不存在，会自动补建。

### 分配租户管理员

- 方法：`POST`
- 路径：`/tenant/assignAdmins`
- 请求体：`{ "id": "租户ID", "ids": ["用户ID"] }`
- 说明：按提交的用户 ID 差量同步租户默认管理员角色成员；被设置为管理员的用户会自动补齐该租户用户关系。
## 租户与平台管理员补充规则

- `POST /user/tenants`：普通用户返回已分配且启用的租户；角色编码为 `ADMIN` 的超级管理员返回全部启用租户。
- `POST /user/switchTenant`：普通用户只能切换到已分配且启用的租户；角色编码为 `ADMIN` 的超级管理员可切换到任一启用租户。
- `POST /tenant/adminPermissionIds`：请求体 `{ "id": "租户ID" }`，返回该租户默认管理员角色已分配的权限 ID；默认管理员角色不存在时自动补建。
- `POST /tenant/assignAdminPermissions`：请求体 `{ "id": "租户ID", "ids": ["权限ID"] }`，覆盖保存该租户默认管理员角色权限；授权范围不能超过当前操作人自身权限，且不会下放租户管理和权限管理等平台级能力。

## 租户默认数据同步

- 新建租户时，会复制默认租户 `0` 的数据字典、字典项和通用类型。
- 默认租户 `0` 新增数据字典、字典项和通用类型时，会补齐到所有启用租户。
- 同步策略为缺失补齐，不覆盖租户已有数据；如果同编码数据已逻辑删除，会恢复该记录。
