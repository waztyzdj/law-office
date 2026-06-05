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
- `url`
- `file_size`
- `file_type`
- `store_type`：文件上传或虚拟整理类型，文档中心使用 `shared_view` 表示“共享给我”个人整理文件夹，`shared_by_me` 表示“我的共享”整理文件夹，`business_module_view` 和 `business_record_view` 表示业务文档接口返回的虚拟业务模块/业务数据目录；历史数据中可能存在的 `business_view` 表示旧版“业务文档”个人整理文件夹，仅兼容只读展示。
- `parent_id`
- `tenant_id`
- `iz_folder`
- `iz_star`
- `share_perms`
- `enable_down`
- `enable_updat`
- 关键索引：`idx_sf_tenant_parent_active_folder_time` 支撑文档中心按目录加载子级、左侧树只加载文件夹和列表默认排序；`idx_sf_tenant_owner_active_parent` 支撑本人目录、回收站和按所有者确认文件归属；`idx_sf_tenant_owner_store_parent_active` 支撑共享给我、我的共享、业务文档等个人整理文件夹查询。

## `sys_file_version`

文件历史版本表，用于保存 ONLYOFFICE 在线编辑产生的不可变历史快照。

重点字段：

- `file_id`：文件 ID。
- `version_no`：同一文件内递增版本号。
- `version_type`：版本类型，`upload` 表示上传初始版本，`final` 表示 ONLYOFFICE 最终保存，`restore` 表示从历史版本恢复生成。
- `object_name`：历史版本在 MinIO 中的不可变对象名。
- `changes_object_name`：ONLYOFFICE 变更包对象名，预留原生历史回放。
- `checksum`：内容 SHA-256 校验值，用于跳过未变化的最终保存。
- `history_json`：ONLYOFFICE 回调中的 `history` 原文，预留后续历史回放。
- `editor_id` / `editor_name`：编辑用户 ID 和姓名。
- `tenant_id`：租户 ID。

## `sys_file_acl`

文件访问授权表，用于文档中心共享给当前租户、当前租户内用户、部门或角色。

重点字段：

- `file_id`：文件 ID。
- `target_type`：授权目标类型，取值 `user`、`depart`、`role`、`tenant`。
- `target_id`：授权目标 ID。
- `permission`：授权权限，取值 `read`、`download`、`update`、`manage`。
- `expire_time`：授权过期时间。
- `tenant_id`：租户 ID。
- 关键索引：`idx_sfa_tenant_target` 支撑共享给我、租户共享、部门共享等按授权目标查询；`idx_sfa_tenant_file` 支撑按文件批量判断是否已共享；`idx_sfa_tenant_creator_active_file` 支撑我的共享按创建人查询共享记录。

## `sys_file_relation`

文件业务关联表，用于业务模块附件绑定，也复用为文档中心个人归类关系。

重点字段：

- `file_id`：文件 ID。
- `biz_type`：业务类型；文档中心内部使用 `document_shared:<userId>` 记录“共享给我”个人归类；历史数据中可能存在 `document_business:<userId>` 业务文档个人归类，仅兼容只读展示。
- `biz_id`：业务数据 ID；文档中心个人归类场景下为目标整理文件夹 ID。
- `relation_type`：关系类型，`1` 表示业务附件，`2` 表示“共享给我”个人整理；历史数据中可能存在 `3` 表示旧版“业务文档”个人整理。
- `tenant_id`：租户 ID。

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
