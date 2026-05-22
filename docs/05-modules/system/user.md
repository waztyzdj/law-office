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

- `GET /user/info`：获取当前用户详情、角色、权限。

## 关键规则

- 新增用户必须填写用户名、真实姓名、密码。
- 密码需要满足复杂度要求。
- 编辑用户时保留用户名和密码，不允许通过普通编辑覆盖。
- 用户名、手机号、邮箱、工号、身份证号需要唯一。
- 密码、salt 不返回前端，不参与导出。
