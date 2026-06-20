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

## 文件扩展接口

### 下载文件

- 方法：`GET`
- 路径：`/files/download/{fileId}`
- 说明：上传人按文件 ID 下载文件，响应头 `Content-Disposition` 使用原始上传文件名并兼容中文文件名；业务附件应使用对应业务模块的下载接口。

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

### 获取角色用户 ID

- 方法：`POST`
- 路径：`/role/userIds`
- 请求体：`{ "id": "角色ID" }`
- 说明：获取角色已分配的用户 ID 列表。

### 分配角色用户

- 方法：`POST`
- 路径：`/role/assignUsers`
- 请求体：`{ "id": "角色ID", "ids": ["用户ID"] }`
- 说明：覆盖保存角色成员，仅允许选择当前租户下正常启用的成员用户。

### 获取个人中心基础资料

- 方法：`GET`
- 路径：`/user/profile`
- 说明：返回当前登录用户可查看和可编辑的基础资料。

### 保存个人中心基础资料

- 方法：`POST`
- 路径：`/user/profile`
- 请求体：`{ "realname": "姓名", "avatar": "头像地址", "email": "邮箱", "phone": "手机号", "telephone": "座机号", "post": "职务" }`
- 说明：仅允许当前用户修改自己的展示信息和联系方式，不允许修改账号、租户、部门、角色等权限边界字段。

### 上传个人头像

- 方法：`POST`
- 路径：`/user/profile/avatar`
- 请求体：`multipart/form-data`，字段名 `file`
- 说明：仅允许上传图片文件，文件大小不能超过 2MB；上传成功后同步保存为当前用户头像。

### 获取个人组织权限

- 方法：`GET`
- 路径：`/user/profile/organization`
- 说明：返回当前用户所属部门、系统角色、部门角色和当前租户下已授权菜单权限摘要；仅返回菜单权限中文名称和数量，不返回按钮权限编码。

### 获取当前租户用户

- 方法：`GET`
- 路径：`/user/profile/tenant-users`
- 说明：返回当前登录租户下启用且未删除的成员用户，个人中心相关业务使用；通用选人组件应优先使用 `/system/picker/users`。

### 获取当前租户角色用户

- 方法：`POST`
- 路径：`/user/profile/tenant-role-users`
- 请求体：`{ "id": "角色ID" }`
- 说明：返回当前登录租户下指定角色的启用成员用户，个人中心相关业务使用；通用选人组件应优先使用 `/system/picker/role-users`。

### 获取个人租户列表

- 方法：`GET`
- 路径：`/user/profile/tenants`
- 说明：返回当前用户可切换租户，并标记当前租户。

### 获取个人近期日志

- 方法：`GET`
- 路径：`/user/profile/logs`
- 说明：返回当前用户最近 20 条登录和操作日志。

## 部门扩展接口

### 获取部门成员组织关系

- 方法：`POST`
- 路径：`/depart/member-relation/list`
- 请求体：`{ "id": "部门ID" }`
- 说明：返回部门成员的主部门、部门负责人、直属上级及用户显示信息。

### 保存部门成员组织关系

- 方法：`POST`
- 路径：`/depart/member-relation/save`
- 请求体：`{ "departId": "部门ID", "members": [{ "userId": "用户ID", "primaryDepartFlag": 1, "departLeaderFlag": 0, "supervisorUserId": "直属上级用户ID" }] }`
- 说明：只维护已有部门成员的组织关系，不新增或移除部门成员；同一部门只允许一个负责人，直属上级不能是自己或形成循环。

### 获取部门负责人

- 方法：`POST`
- 路径：`/depart/leader/list`
- 请求体：`{ "id": "部门ID" }`
- 说明：返回指定部门负责人，当前规则下最多一条。

### 保存部门负责人

- 方法：`POST`
- 路径：`/depart/leader/save`
- 请求体：`{ "departId": "部门ID", "userId": "用户ID" }`
- 说明：覆盖保存指定部门唯一负责人；`userId` 为空时清空负责人，负责人必须是当前部门成员。

## 系统选择器接口

选择器接口只要求登录态和当前租户上下文，不配置菜单或按钮权限；接口内部必须按当前租户过滤数据，供审批选人、消息收件人等公共选择控件使用。

### 获取租户用户选择项

- 方法：`GET`
- 路径：`/system/picker/users`
- 说明：返回当前租户下启用且未删除的成员用户。

### 获取租户组织选择项

- 方法：`GET`
- 路径：`/system/picker/departs`
- 说明：返回当前租户下启用且未删除的组织机构。

### 获取租户角色选择项

- 方法：`GET`
- 路径：`/system/picker/roles`
- 说明：返回当前租户下未删除的系统角色。

### 获取部门用户选择项

- 方法：`POST`
- 路径：`/system/picker/depart-users`
- 请求体：`{ "id": "部门ID" }`
- 说明：返回当前租户下指定部门的有效成员；部门不属于当前租户时返回空列表。

### 获取角色用户选择项

- 方法：`POST`
- 路径：`/system/picker/role-users`
- 请求体：`{ "id": "角色ID" }`
- 说明：返回当前租户下指定角色的有效成员；角色不属于当前租户时返回空列表。

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

