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

## `sys_user_depart`

用户部门关系表，也是审批中心组织关系解析的基础表。

重点字段：

- `user_id`：用户 ID。
- `dep_id`：部门 ID。
- `primary_depart_flag`：是否主部门，取值 `0` 否、`1` 是。
- `depart_leader_flag`：是否部门负责人，取值 `0` 否、`1` 是。
- `supervisor_user_id`：直属上级用户 ID，按当前部门维度维护。
- `tenant_id`：租户 ID。

关键索引：

- `idx_sud_tenant_user_primary`：按用户查询主部门。
- `idx_sud_tenant_dep_leader`：按部门解析唯一负责人。
- `idx_sud_tenant_supervisor`：按直属上级反查或校验组织关系。

## `sys_depart_role`

部门角色表，审批中心一期复用为部门岗位定义。

重点字段：

- `depart_id`：部门 ID。
- `role_name`：部门角色名称。
- `role_code`：部门角色编码。
- `workflow_enabled`：是否可作为审批岗位，取值 `0` 否、`1` 是。
- `tenant_id`：租户 ID。

## `sys_depart_role_user`

部门角色用户关系表，审批中心一期复用为部门岗位人员关系。

重点字段：

- `drole_id`：部门角色 ID。
- `user_id`：用户 ID。
- `tenant_id`：租户 ID。

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
- `target_id`：授权目标 ID；`target_type=tenant` 时使用当前租户 ID。
- `permission`：授权权限，取值 `read`、`download`、`update`、`manage`。
- `expire_time`：授权过期时间。
- `tenant_id`：租户 ID。
- 关键索引：`idx_sfa_tenant_target` 支撑共享给我、租户共享、部门共享等按授权目标查询；`idx_sfa_tenant_file` 支撑按文件批量判断是否已共享；`idx_sfa_tenant_creator_active_file` 支撑我的共享按创建人查询共享记录。

## `sys_file_relation`

文件业务关联表，用于业务模块附件绑定，也复用为文档中心个人归类关系。

重点字段：

- `file_id`：文件 ID。
- `biz_type`：业务类型；审批中心附件使用 `workflow_approval`；文档中心内部使用 `document_shared:<userId>` 记录“共享给我”个人归类；历史数据中可能存在 `document_business:<userId>` 业务文档个人归类，仅兼容只读展示。
- `biz_id`：业务数据 ID；文档中心个人归类场景下为目标整理文件夹 ID。
- `relation_type`：关系类型，`1` 表示业务附件，`2` 表示“共享给我”个人整理；历史数据中可能存在 `3` 表示旧版“业务文档”个人整理。
- `tenant_id`：租户 ID。

## 审批中心表

审批中心业务表统一使用 `wf_*` 前缀，Flowable 自带 `ACT_*` 表只作为流程运行时数据，不直接暴露给前端。完整字段、枚举和索引以 `sql/建表脚本.sql` 为准，模块设计以 `docs/05-modules/workflow/approval-center-phase1-design.md` 和 `docs/05-modules/workflow/approval-center-phase2-data-model.md` 为准。

### `wf_process_category`

流程分类表。

重点字段：

- `category_code`：分类编码，同一租户内软删范围唯一。
- `category_name`：分类名称。
- `status`：状态，取值 `enabled`、`disabled`。

### `wf_form_definition`

FormCreate 表单定义版本表。

重点字段：

- `form_key`：表单编码。
- `version`：表单版本号。
- `schema_json`：FormCreate 规则 JSON。
- `option_json`：FormCreate option JSON。
- `status`：状态，取值 `draft`、`published`、`disabled`。

### `wf_form_instance`

FormCreate 表单实例表，用于保存发起后的表单数据、schema 快照和 option 快照。

重点字段：

- `process_instance_id`：审批实例 ID，发起后写入。
- `form_definition_id`：表单定义版本 ID。
- `form_key`、`form_name`、`form_version`：表单定义快照信息。
- `form_data_json`：当前表单数据 JSON。
- `form_schema_snapshot_json`：发起时 FormCreate 规则快照。
- `form_option_snapshot_json`：发起时 FormCreate option 快照。
- `status`：状态，取值 `draft`、`active`、`archived`。

### `wf_process_model`

流程模型版本表，用于保存简单设计器和 BPMN 设计器的流程定义元数据。

重点字段：

- `process_key`：流程编码。
- `version`：流程模型版本号。
- `designer_type`：设计器类型，取值 `simple`、`bpmn`。
- `start_scope_type`：发起范围，取值 `all`、`specified`。
- `node_json`：简单设计器节点 JSON。
- `bpmn_xml`：BPMN XML，发布时必须存在。
- `bpmn_security_status`：BPMN 安全校验状态，取值 `passed`、`failed`。
- `bpmn_security_message`：BPMN 安全校验摘要。
- `flowable_deployment_id`：Flowable 部署 ID。
- `flowable_process_definition_id`：Flowable 流程定义 ID。

### `wf_process_start_permission`

流程发起权限表，用于控制哪些用户、角色、部门或租户范围可以发起某个已发布流程。

重点字段：

- `process_model_id`：流程模型版本 ID。
- `target_type`：授权目标类型，取值 `user`、`role`、`depart`、`tenant`。
- `target_id`：授权目标 ID。
- `status`：状态，取值 `enabled`、`disabled`。

### `wf_process_node_config`

流程节点配置表。

重点字段：

- `process_model_id`：流程模型版本 ID。
- `node_id`：节点 ID，对应 BPMN `taskDefinitionKey`。
- `node_type`：节点类型，取值 `start`、`approver`、`gateway`、`end`。
- `assignee_type`：审批人类型，取值 `user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select`、`starter`。
- `assignee_json`：审批人配置 JSON。
- `approval_mode`：办理策略，取值 `single`、`countersign`、`orsign`。
- `assignee_resolve_mode`：执行人确定方式，取值 `all`、`select`；`all` 表示发送给全部解析人员，`select` 表示由上一步办理人在候选范围内选择实际执行人；单人审批固定按 `select` 处理。
- `reject_policy`：不通过策略，二期取值 `terminate`。
- `branch_json`：条件分支配置 JSON。
- `cc_json`：抄送配置 JSON。
- `timeout_json`：超时提醒配置 JSON。
- `attachment_json`：附件权限配置 JSON。
- `allow_transfer`、`allow_add_sign`、`allow_return`：节点动作开关。

### `wf_field_permission`

节点字段权限表，用于控制某个流程节点下 FormCreate 字段隐藏、只读或可编辑。

重点字段：

- `process_model_id`：流程模型版本 ID。
- `node_id`：节点 ID。
- `field_key`：FormCreate 字段 `field`。
- `permission`：字段权限，取值 `hidden`、`readonly`、`editable`。
- `required_flag`：当前节点是否必填。

### `wf_process_instance`

流程实例业务扩展表。

重点字段：

- `flowable_process_instance_id`：Flowable 流程实例 ID，启动 Flowable 成功后回填，初始允许为空。
- `flowable_process_definition_id`：Flowable 流程定义 ID。
- `form_instance_id`：表单实例 ID。
- `form_definition_id`：表单定义版本 ID，用于列表查询和冗余展示。
- `instance_no`：审批编号，同一租户内软删范围唯一。
- `current_task_names`：当前节点名称摘要。
- `current_assignee_names`：当前处理人姓名摘要。
- `status`：状态，取值 `draft`、`running`、`approved`、`rejected`、`withdrawn`、`terminated`；`draft` 表示发起申请已保存但尚未提交到 Flowable，`withdrawn` 表示发起人撤回。

### `wf_task`

任务业务扩展表，用于待办、已办列表和租户过滤。

重点字段：

- `flowable_task_id`：Flowable 任务 ID；发起草稿待办尚未进入 Flowable，使用 `draft:{task_id}` 本地标识。
- `node_id`：节点 ID。
- `parent_task_id`：父任务扩展 ID，转办或加签任务关联原任务。
- `task_type`：任务类型，取值 `start_draft`、`normal`、`transfer`、`add_sign`、`countersign`、`orsign`；`start_draft` 表示发起人待提交草稿。
- `approval_mode`：任务所属节点办理策略，取值 `single`、`countersign`、`orsign`。
- `task_group_id`：同一节点同一批会签或或签任务组 ID。
- `group_total`、`group_completed`：同组任务总数和已完成数摘要。
- `owner_user_id`：原处理人用户 ID，转办或加签时记录。
- `assignee_user_id`：当前处理人用户 ID；候选任务提交审批时自动认领并写入。
- `status`：状态，取值 `todo`、`done`、`transferred`、`returned`、`canceled`、`withdrawn`。
- `due_time`：超时截止时间。
- `last_remind_time`、`remind_count`：超时提醒防重复字段。
- `claim_time`：自动认领时间。
- `complete_time`：完成时间。

关键索引：

- `idx_wft_tenant_assignee_status_time(tenant_id, assignee_user_id, status, delete_flag, create_time)`：待办/已办按处理人和状态查询的基础索引。
- `idx_wft_tenant_instance_status(tenant_id, process_instance_id, status, delete_flag)`：按流程实例聚合任务状态。
- `idx_wft_tenant_assignee_status_complete(tenant_id, assignee_user_id, status, delete_flag, complete_time, update_time, create_time)`：我的已办按办理时间倒序查询。
- `idx_wft_tenant_instance_status_complete(tenant_id, process_instance_id, status, delete_flag, complete_time, update_time, create_time)`：我的已办按流程实例去重取最后办理任务。

### `wf_process_instance_assignee`

流程实例节点审批人快照表，用于保存运行时选择的审批人，避免组织关系变更影响已流转实例。

重点字段：

- `process_instance_id`：审批实例 ID。
- `process_model_id`：流程模型版本 ID。
- `node_id`、`node_name`：节点快照。
- `assignee_type`：审批人类型快照。
- `assignee_user_id`、`assignee_username`、`assignee_realname`：选定审批人快照。
- `source_type`、`source_id`：审批人来源。
- `select_type`：选择方式，取值 `single`、`multiple`。
- `status`：状态，取值 `active`、`canceled`。

### `wf_task_candidate`

任务候选人表，用于角色、部门负责人、部门岗位等解析出多人且明确启用候选池策略时，支撑“我的待办”和提交审批时自动认领。

重点字段：

- `task_id`：任务扩展 ID。
- `candidate_user_id`：候选处理人用户 ID。
- `source_type`：来源类型，取值 `user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select`。
- `status`：状态，取值 `active`、`claimed`、`canceled`。

### `wf_approval_record`

审批记录表。

重点字段：

- `process_instance_id`：审批实例 ID。
- `task_id`：任务扩展 ID。
- `action`：审批动作，取值 `save_draft`、`start`、`approve`、`reject`、`return`、`transfer`、`add_sign`、`withdraw`、`cc`、`urge`、`timeout_remind`、`task_cancel`、`branch_match`、`attachment_upload`、`attachment_delete`、`system_complete`。
- `operator_user_id`：操作人用户 ID。
- `target_user_id`：目标用户 ID，转办或加签时使用。
- `target_node_id`、`target_node_name`：退回目标节点。
- `form_data_snapshot_json`：操作时表单数据快照。
- `operate_time`：操作时间。

### `wf_admin_operation_record`

流程监控维护记录表，用于记录管理员改派、终止、补发通知等维护动作。该表不替代审批记录表，审批动作仍写入 `wf_approval_record`。

重点字段：

- `process_instance_id`：审批实例 ID。
- `task_id`：任务扩展 ID；终止流程等实例级维护动作可为空。
- `operation_type`：维护动作，取值 `reassign`、`terminate`、`resend_notice`。
- `operation_reason`：维护原因，必填。
- `before_snapshot_json`：动作前关键数据快照，例如原处理人、任务状态、流程状态。
- `after_snapshot_json`：动作后关键数据快照，例如新处理人、终止状态、通知结果。
- `operator_user_id`、`operator_username`、`operator_realname`：维护操作人快照。
- `operate_time`：维护时间。
- `status`：维护结果，取值 `success`、`failed`。
- `error_message`：失败原因。

### `wf_cc_record`

审批抄送记录表，用于“我的抄送”、审批详情访问权和抄送已读状态。

重点字段：

- `process_instance_id`：审批实例 ID。
- `process_model_id`：流程模型版本 ID。
- `task_id`：触发抄送的任务 ID。
- `node_id`、`node_name`：触发节点。
- `trigger_action`：触发动作，取值 `start`、`approve`、`process_finished`、`manual`。
- `source_type`、`source_id`：抄送来源。
- `receiver_user_id`、`receiver_username`、`receiver_realname`：抄送接收人。
- `status`：状态，取值 `unread`、`read`、`canceled`。
- `read_time`：阅读时间。
- `message_id`：关联站内消息 ID。

### `wf_reminder_record`

催办和超时提醒记录表，用于防重复提醒、消息追踪和审批详情展示。

重点字段：

- `process_instance_id`：审批实例 ID。
- `task_id`：任务扩展 ID。
- `flowable_task_id`：Flowable 任务 ID。
- `remind_type`：提醒类型，取值 `urge`、`timeout`。
- `sender_user_id`、`sender_username`、`sender_realname`：发送人；系统超时提醒可为空或 `system`。
- `receiver_user_id`、`receiver_username`、`receiver_realname`：接收人。
- `message_id`：关联站内消息 ID。
- `remind_round`：第几轮提醒。
- `operate_time`：提醒时间。

### `wf_attachment`

审批附件表，用于记录审批语义和权限，文件元数据仍以 `sys_files` 为准。

重点字段：

- `process_instance_id`：审批实例 ID。
- `task_id`：任务扩展 ID。
- `approval_record_id`：审批记录 ID。
- `node_id`、`node_name`：上传节点。
- `file_id`：文件 ID，对应 `sys_files.id`。
- `file_relation_id`：文件业务关联 ID，对应 `sys_file_relation.id`。
- `attachment_source`：来源，取值 `start`、`task`、`comment`。
- `uploader_user_id`、`uploader_username`、`uploader_realname`：上传人。
- `status`：状态，取值 `active`、`deleted`。

### `wf_branch_record`

条件分支命中记录表，用于流程图高亮、审计和排查。

重点字段：

- `process_instance_id`：审批实例 ID。
- `process_model_id`：流程模型版本 ID。
- `source_node_id`、`source_node_name`：分支来源节点。
- `branch_id`、`branch_name`：命中分支。
- `target_node_id`、`target_node_name`：目标节点。
- `condition_snapshot_json`：判断条件快照。
- `form_data_snapshot_json`：判断时表单数据快照。
- `matched_time`：命中时间。

### `wf_archive_record`

流程归档记录表，用于把正常结束或人工确认归档的流程实例纳入档案口径查询。归档记录保存列表查询所需快照，不改变流程实例原状态。

重点字段：

- `process_instance_id`：流程实例 ID，同一租户同一有效实例只允许一条归档记录。
- `process_model_id`：流程模型版本 ID。
- `category_id`、`category_name`：归档时流程分类快照，`category_id` 对应 `wf_process_category.id`。
- `process_key`、`process_name`、`process_version`：归档时流程定义编码、名称和版本快照。
- `form_instance_id`、`form_definition_id`：表单实例和表单定义版本 ID。
- `instance_no`、`instance_title`：归档时审批编号和实例标题快照。
- `starter_user_id`、`starter_username`、`starter_realname`：发起人快照。
- `instance_status`：归档时流程实例状态，对应 `wf_process_instance.status`，取值 `approved`、`rejected`、`terminated`；自动归档覆盖正常结束实例，手动归档覆盖已结束且未归档实例。
- `process_start_time`、`process_end_time`：流程发起和结束时间快照。
- `archive_source`：归档来源，取值 `auto`、`monitor_manual`、`archive_manual`。
- `archive_reason`：归档说明。
- `archiver_user_id`、`archiver_username`、`archiver_realname`：归档人快照，自动归档可为空或系统用户。
- `archive_time`：归档时间。

## `sys_log`

操作日志表。

重点字段：

- `tenant_id`
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
- `sql/审批中心权限初始化.sql`：审批中心菜单、按钮权限和管理员角色授权示例。
