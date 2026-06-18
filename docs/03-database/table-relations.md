# 表关系说明

## 用户、角色、权限

```mermaid
erDiagram
  sys_user ||--o{ sys_user_role : has
  sys_role ||--o{ sys_user_role : assigned
  sys_role ||--o{ sys_role_permission : grants
  sys_permission ||--o{ sys_role_permission : included
```

## 用户、部门、租户

```mermaid
erDiagram
  sys_user ||--o{ sys_user_depart : belongs
  sys_depart ||--o{ sys_user_depart : contains
  sys_user ||--o{ sys_user_tenant : joins
  sys_tenant ||--o{ sys_user_tenant : owns
```

## 字典

```mermaid
erDiagram
  sys_dict ||--o{ sys_dict_item : contains
```

## 权限树

`sys_permission.parent_id` 指向同表 `id`，用于构建菜单和权限树。

## 部门树

`sys_depart.parent_id` 指向同表 `id`，用于构建组织机构树。

## 审批中心

```mermaid
erDiagram
  wf_process_category ||--o{ wf_form_definition : groups
  wf_process_category ||--o{ wf_process_model : groups
  wf_form_definition ||--o{ wf_process_model : binds
  wf_form_definition ||--o{ wf_form_instance : instantiates
  wf_process_model ||--o{ wf_process_start_permission : authorizes
  wf_process_model ||--o{ wf_process_node_config : configures
  wf_process_model ||--o{ wf_field_permission : controls
  wf_process_model ||--o{ wf_process_instance : starts
  wf_form_instance ||--|| wf_process_instance : binds
  wf_process_instance ||--o{ wf_task : creates
  wf_process_instance ||--o{ wf_process_instance_assignee : snapshots
  wf_task ||--o{ wf_task_candidate : candidates
  wf_process_instance ||--o{ wf_approval_record : records
  wf_task ||--o{ wf_approval_record : records
  wf_process_instance ||--o{ wf_cc_record : copies
  wf_process_instance ||--o{ wf_reminder_record : reminds
  wf_task ||--o{ wf_reminder_record : reminds
  wf_process_instance ||--o{ wf_attachment : attaches
  wf_task ||--o{ wf_attachment : attaches
  wf_approval_record ||--o{ wf_attachment : records
  wf_process_instance ||--o{ wf_branch_record : branches
```

`wf_field_permission.node_id` 与 `wf_process_node_config.node_id` 对应 BPMN `taskDefinitionKey`，用于控制某个节点下 FormCreate 字段的隐藏、只读和可编辑状态。

二期新增的 `wf_cc_record` 只授予审批详情查看权，不生成待办任务；`wf_reminder_record` 只记录催办和超时提醒，不改变流程状态；`wf_attachment` 记录审批附件语义，文件元数据仍在 `sys_files`，文件业务关系仍在 `sys_file_relation`；`wf_branch_record` 保存条件分支命中路径，用于流程图高亮和审计排查。
