# 角色与权限说明

## 权限模型

系统采用用户、角色、权限三层模型：

- 用户：`sys_user`
- 角色：`sys_role`
- 用户角色关系：`sys_user_role`
- 权限：`sys_permission`
- 角色权限关系：`sys_role_permission`

权限码采用 `module:action` 格式，例如：

- `user:view`
- `user:edit`
- `role:view`
- `role:edit`

## 后端控制

- 标准 CRUD 接口由 `PermissionAspect` 根据 `@ModuleInfo` 和方法语义进行自动权限控制。
- 自定义接口使用 `@RequiresPermission` 指定权限。
- 当前用户权限在登录后写入 token/Redis 相关上下文，前端通过 `getAccessCodesApi` 获取权限码。

## 前端控制

- 权限码统一维护在 `frontend/src/constants/permissions.ts`。
- 按钮权限使用 `v-access:code`。
- 逻辑判断使用 Vben 的 `useAccess`。

## 新增权限流程

1. 后端新增或确认 `sys_permission` 权限数据。
2. 前端同步 `permissionCodes` 常量。
3. 页面按钮或操作列添加权限控制。
4. 后端接口使用自动权限或 `@RequiresPermission`。
5. 更新对应模块文档。
