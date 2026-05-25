# 用户管理

## 功能说明

用户管理用于维护系统登录用户，支持列表、分页、筛选、排序、新增、编辑、删除、批量删除、导入导出。

## 代码入口

- 后端 Controller：`backend/src/main/java/com/lawoffice/system/controller/UserController.java`
- 后端 Service：`backend/src/main/java/com/lawoffice/system/service/impl/UserServiceImpl.java`
- 后端 Entity：`backend/src/main/java/com/lawoffice/system/entity/User.java`
- 后端 Req：`backend/src/main/java/com/lawoffice/system/req/UserReq.java`
- 后端 VO：`backend/src/main/java/com/lawoffice/system/vo/UserVO.java`
- 前端 API：`frontend/src/api/system/user.ts`
- 前端页面：`frontend/src/views/system/user/index.vue`
- 前端表单：`frontend/src/views/system/user/components/UserFormDrawer.vue`
- 前端表格：`frontend/src/views/system/user/components/UserTable.vue`

## 数据表

- `sys_user`
- `sys_user_role`
- `sys_user_depart`
- `sys_user_tenant`

## 接口

继承通用 CRUD：

- `POST /user/page`
- `POST /user/getById`
- `POST /user/save`
- `POST /user/delete`
- `POST /user/batchDelete`
- `POST /user/import`
- `POST /user/export`

扩展接口：

- `GET /user/info`：获取当前用户详情、角色、权限和当前租户。
- `POST /user/tenants`：获取当前用户可切换租户列表。
- `POST /user/switchTenant`：切换当前用户租户并返回新 token。
- `POST /user/roleIds`：获取用户已分配角色 ID。
- `POST /user/assignRoles`：覆盖保存用户角色。

## 页面能力

- 用户列表操作列支持分配角色。
- 用户角色分配使用穿梭框展示可选角色和已选角色。

## 关键规则

- 新增用户必须填写用户名、真实姓名、密码。
- 密码需要满足复杂度要求。
- 编辑用户时保留用户名和密码，不允许通过普通编辑覆盖。
- 用户名、手机号、邮箱、工号、身份证号需要唯一。
- 密码、salt 不返回前端，不参与导出。
- 切换租户成功后后端重新签发 token，并把目标租户 ID 写入 token 和 Redis 会话。
- 角色编码为 `ADMIN` 的超级管理员切换到其他启用租户时，默认叠加目标租户 `ADMIN_ + 租户编码` 管理员角色权限。
- 切换租户后前端必须更新 token、刷新用户信息和菜单权限。
