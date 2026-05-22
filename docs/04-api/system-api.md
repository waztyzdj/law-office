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

## 用户扩展接口

### 当前用户详情

- 方法：`GET`
- 路径：`/user/info`
- 说明：返回当前用户、角色和权限。
