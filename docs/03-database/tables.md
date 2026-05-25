# 表结构说明

本文件记录核心表的业务用途和重点字段。完整字段以 `sql/建表脚本.sql` 为准。

## `sys_user`

用户表。

重点字段：

- `id`：用户 ID。
- `username`：登录账号，唯一。
- `realname`：真实姓名。
- `password`：BCrypt 密码摘要，接口默认不返回。
- `email`：邮箱，唯一。
- `phone`：手机号，唯一。
- `status`：用户状态。
- `work_no`：工号，唯一。
- `delete_flag`：逻辑删除标记。

## `sys_role`

角色表。

重点字段：

- `role_code`：角色编码，同一租户内唯一。
- `role_name`：角色名称。
- `description`：角色描述。

## `sys_permission`

菜单和权限表。

重点字段：

- `name`：菜单或权限名称。
- `perms`：权限码。
- `menu_type`：菜单类型，取值以建表脚本字段注释为准：`0` 一级菜单、`1` 子菜单、`2` 按钮权限。
- `parent_id`：父级菜单。
- `path`：前端路由路径。
- `component`：前端组件路径。
- `sort_no`：排序号。
- `status`：状态。
- `hidden`：是否隐藏。

## `sys_role_permission`

角色权限关系表。

重点字段：

- `role_id`
- `permission_id`
- `tenant_id`

## `sys_depart`

组织机构表。

重点字段：

- `parent_id`：父级机构。
- `depart_name`：机构名称。
- `org_code`：机构编码，唯一。
- `depart_order`：排序。

## `sys_tenant`

租户表。

重点字段：

- `name`：租户名称。
- `status`：租户状态。
- `begin_date`、`end_date`：租户有效期。

## `sys_dict` / `sys_dict_item`

字典主表和字典明细。

重点字段：

- `dict_code`：字典编码，同一租户内唯一。
- `item_text`：字典项文本。
- `item_value`：字典项值。
- `sort_order`：排序。
- `status`：状态。

## `sys_files`

文件元数据表。

重点字段：

- `file_name`
- `file_url`
- `file_size`
- `file_type`
- `bucket_name`
- `object_name`

## `sys_log`

操作日志表。

重点字段：

- `log_type`
- `operate_type`
- `userid`
- `username`
- `ip`
- `request_url`
- `request_param`
- `create_time`

## 初始化脚本

- `sql/建表脚本.sql`：基础表结构。
- `sql/系统权限初始化.sql`：系统管理菜单、按钮权限和管理员角色授权示例。
