# 数据库总体说明

数据库脚本位于 `sql/建表脚本.sql`，当前主要包含系统管理相关表。

## 表清单

| 表名 | 说明 |
| --- | --- |
| `sys_category` | 通用类型。 |
| `sys_depart` | 组织机构。 |
| `sys_depart_permission` | 部门权限关系。 |
| `sys_depart_role` | 部门角色。 |
| `sys_depart_role_permission` | 部门角色权限关系。 |
| `sys_depart_role_user` | 部门角色用户关系。 |
| `sys_dict` | 字典主表。 |
| `sys_dict_item` | 字典明细。 |
| `sys_files` | 文件元数据。 |
| `sys_file_acl` | 文件访问授权。 |
| `sys_file_relation` | 文件业务关联。 |
| `sys_permission` | 菜单和权限。 |
| `sys_role` | 角色。 |
| `sys_role_permission` | 角色权限关系。 |
| `sys_tenant` | 租户。 |
| `sys_user` | 用户。 |
| `sys_user_depart` | 用户部门关系。 |
| `sys_user_role` | 用户角色关系。 |
| `sys_user_tenant` | 用户租户关系。 |
| `sys_log` | 操作日志。 |
| `home_workbench_card` | 工作台卡片配置。 |
| `home_workbench_user_card` | 工作台用户卡片布局。 |
| `home_workbench_quick_entry` | 工作台快捷入口。 |

## 通用字段

多数业务表包含：

- `id`
- `create_by`
- `create_time`
- `update_by`
- `update_time`
- `delete_flag`
- `tenant_id`

## 设计约定

- 默认使用逻辑删除。
- 多租户表通过 `tenant_id` 隔离。
- 用户、角色、权限等核心表建立必要唯一索引和查询索引。
- 字段变更时要同步 Entity、Req、VO、前端表单、接口文档和 SQL。
