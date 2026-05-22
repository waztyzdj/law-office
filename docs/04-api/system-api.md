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
- 说明：覆盖保存角色菜单和按钮权限。

## 用户扩展接口

### 当前用户详情

- 方法：`GET`
- 路径：`/user/info`
- 说明：返回当前用户、角色和权限。

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
