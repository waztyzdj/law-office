# 功能清单

## 已有基础功能

| 模块 | 功能 | 当前说明 |
| --- | --- | --- |
| 认证管理 | 登录 | `POST /auth/login`，返回 token 和默认租户。 |
| 认证管理 | 登出 | `POST /auth/logout`，清理 Redis token。 |
| 认证管理 | 修改密码 | `POST /auth/changePassword`，校验旧密码后重置。 |
| 用户管理 | 用户列表 | 支持分页、筛选、排序、导入导出。 |
| 用户管理 | 新增/编辑用户 | 支持用户名、姓名、密码、联系方式、状态等字段。 |
| 用户管理 | 当前用户信息 | `GET /user/info` 返回用户、角色、权限和首页路径。 |
| 角色管理 | 角色 CRUD | 基于通用 CRUD。 |
| 菜单权限 | 权限 CRUD | 维护菜单、按钮和权限码。 |
| 菜单管理 | 菜单树 | `GET /menu/all` 获取当前用户菜单树。 |
| 组织机构 | 部门 CRUD | 基于通用 CRUD。 |
| 租户管理 | 租户 CRUD | 基于通用 CRUD。 |
| 字典管理 | 字典和明细 | 基于 `sys_dict`、`sys_dict_item`。 |
| 文件管理 | 文件元数据 | 基于 `sys_files`，对接 MinIO 能力。 |
| 日志审计 | 操作日志 | AOP 写入 `sys_log`。 |

## 规划/待完善业务域

| 模块 | 说明 |
| --- | --- |
| 案件管理 | 代码目录已预留 `backend/src/main/java/com/lawoffice/case`，详细功能待补充。 |
| OA 办公 | 代码目录已预留 `backend/src/main/java/com/lawoffice/oa`，详细功能待补充。 |
| 首页/工作台 | 代码目录已预留 `backend/src/main/java/com/lawoffice/home`，前端已有 dashboard 页面。 |

## 维护要求

- 新增功能时，同步更新本清单、模块文档、接口文档和权限说明。
- 涉及数据库新增表或字段时，同步更新 `03-database/`。
- 涉及前端页面时，同步更新模块文档中的页面入口。
