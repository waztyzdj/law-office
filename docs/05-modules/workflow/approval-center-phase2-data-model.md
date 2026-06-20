# 审批中心二期数据模型设计

## 文档目的

本文用于把审批中心二期需求落到数据模型层，明确现有表扩展、新增表、枚举定稿、关键状态流转、索引和一致性规则。后续修改 `sql/建表脚本.sql`、后端实体、前端常量和接口文档时，以本文为二期建模依据。

关联文档：

- [审批中心二期设计文档](approval-center-phase2-design.md)
- [审批中心二期需求明细](approval-center-phase2-requirements.md)
- [审批中心一期设计文档](approval-center-phase1-design.md)
- [文档中心](../document/center.md)
- [站内消息模块](../message.md)

## 设计结论

- 二期优先扩展一期已有 `wf_*` 表，新增少量专项表。
- 条件分支、抄送配置、超时配置、附件权限配置属于流程模型版本配置，随流程发布冻结。
- 会签和或签属于节点办理策略，落在 `wf_process_node_config.approval_mode`。
- 二期会签只做并行会签；串行会签使用多个普通审批节点表达。
- 撤回建议新增实例状态 `withdrawn`，和 `terminated` 区分，便于列表、消息和审批记录展示。
- 抄送使用独立 `wf_cc_record` 表，不写入 `wf_task`，避免把查看权误当成办理权。
- 催办和超时提醒使用统一 `wf_reminder_record` 表，便于防重复和详情展示。
- 审批附件使用独立 `wf_attachment` 表记录审批语义，同时复用 `sys_files` 和 `sys_file_relation`。
- 消息通知复用 `sys_message`、`sys_message_receiver`、`sys_message_action`、`sys_message_send_record`，工作流只保存消息业务关联字段或发送日志，不重复建消息表。
- 流程图高亮不单独落表，后端基于流程模型快照、任务、候选人、审批记录和分支命中记录运行时组装。

## 公共字段约定

二期新增 `wf_*` 表统一包含：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `varchar(64)` | 无 | 主键 |
| `tenant_id` | `varchar(64)` | `'0'` | 租户 ID |
| `create_by` | `varchar(64)` | `NULL` | 创建人 |
| `create_time` | `datetime` | `NULL` | 创建时间 |
| `update_by` | `varchar(64)` | `NULL` | 更新人 |
| `update_time` | `datetime` | `NULL` | 更新时间 |
| `delete_flag` | `int` | `0` | 删除标志：`0` 未删除，`1` 已删除 |
| `delete_time` | `datetime` | `NULL` | 删除时间 |
| `delete_by` | `varchar(64)` | `NULL` | 删除人 |

规则：

- 后端实体默认继承 `BaseTenantEntity`。
- 删除默认逻辑删除，不能物理删除业务记录。
- 查询默认过滤 `delete_flag = 0`。
- 业务唯一约束必须包含 `tenant_id` 和 `delete_flag`。
- JSON 字段优先使用 `json`；如数据库兼容性不足，降级为 `longtext`，但后端仍必须按 JSON 校验。

## 现有表扩展

### `wf_process_node_config`

流程节点配置表需要承载二期节点策略和运行配置。

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `approval_mode` | `varchar(20)` | `'single'` | 办理策略：`single` 单人审批、`countersign` 并行会签、`orsign` 或签 |
| `assignee_resolve_mode` | `varchar(20)` | `'select'` | 执行人确定方式：`all` 发送给全部解析人员、`select` 由上一步办理人选择 |
| `reject_policy` | `varchar(32)` | `'terminate'` | 不通过策略：`terminate` 终止流程；二期会签/或签默认终止 |
| `branch_json` | `json` | `NULL` | 条件分支配置 JSON，仅条件节点或带分支的网关节点使用 |
| `cc_json` | `json` | `NULL` | 抄送配置 JSON |
| `timeout_json` | `json` | `NULL` | 超时提醒配置 JSON |
| `attachment_json` | `json` | `NULL` | 附件权限配置 JSON |

字段说明：

- `approval_mode=single`：保持一期语义，执行人确定方式固定为 `select`。多人解析时仍按一期规则由上一环节选择单人，除非节点显式配置为会签或或签。
- `approval_mode=countersign`：并行会签。为每个有效审批人生成待办，所有待办通过后进入下一节点。
- `approval_mode=orsign`：或签。为多个有效审批人生成可办理任务，任一人完成后取消其它同组任务并进入下一节点。
- `assignee_resolve_mode=all`：运行时不要求上一步选择，按审批人配置解析出的全部有效人员创建任务；该模式仅用于会签/或签。
- `assignee_resolve_mode=select`：运行时先让上一步办理人在候选范围内选择实际执行人；单人审批必须选择 1 人，会签/或签可选择 1 个或多个人。
- `reject_policy=terminate`：任一办理人不通过则流程标记 `rejected` 并取消其它待办。
- 二期不实现 `reject_policy=continue` 或 `reject_policy=vote`，字段先保留扩展空间。

`branch_json` 建议结构：

```json
{
  "defaultTargetNodeId": "approve_default",
  "branches": [
    {
      "branchId": "branch_amount_high",
      "branchName": "金额大于10000",
      "priority": 10,
      "targetNodeId": "approve_partner",
      "conditions": [
        {
          "sourceType": "form_field",
          "fieldKey": "amount",
          "valueType": "number",
          "operator": "gt",
          "value": 10000
        }
      ],
      "logic": "and"
    }
  ]
}
```

`cc_json` 建议结构：

```json
{
  "events": ["start", "node_approved", "process_finished"],
  "targets": [
    {
      "targetType": "user",
      "targetIds": ["..."]
    }
  ],
  "allowRuntimeSelect": false
}
```

`timeout_json` 建议结构：

```json
{
  "enabled": true,
  "durationMinutes": 1440,
  "remindIntervalMinutes": 1440,
  "maxRemindCount": 3
}
```

`attachment_json` 建议结构：

```json
{
  "allowUpload": true,
  "allowDeleteOwn": true,
  "allowDeleteAfterComplete": false,
  "required": false,
  "maxCount": 10
}
```

索引调整：

- 保持一期 `uk_wfpnc_tenant_model_node_active`。
- 可追加普通索引 `idx_wfpnc_tenant_model_mode(tenant_id, process_model_id, approval_mode, delete_flag)`，用于发布校验和设计器查询。

### `wf_process_model`

流程模型版本表保存简单设计器 JSON 和 BPMN XML。二期建议增加安全校验摘要字段，便于审计发布结果。

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `bpmn_security_status` | `varchar(20)` | `NULL` | BPMN 安全校验状态：`passed`、`failed` |
| `bpmn_security_message` | `varchar(1000)` | `NULL` | BPMN 安全校验摘要 |

规则：

- 发布流程时必须执行 BPMN 白名单校验，校验通过后写 `passed`。
- 校验失败的草稿可以保存，但不能发布，失败原因写入 `bpmn_security_message`。
- 简单设计器发布时转换出的 BPMN XML 同样必须校验。

### `wf_process_instance`

流程实例扩展表需要区分撤回状态。

字段调整：

| 字段 | 调整 | 说明 |
| --- | --- | --- |
| `status` | 枚举新增 `withdrawn` | 撤回。区别于系统终止 `terminated` |

状态说明：

- `draft`：草稿待提交。
- `running`：流转中。
- `approved`：已通过。
- `rejected`：不通过。
- `withdrawn`：发起人撤回。
- `terminated`：系统或管理动作终止，二期主要保留既有语义。

规则：

- 撤回成功后设置 `status=withdrawn`，`end_time=当前时间`。
- 撤回后是否生成发起人待提交任务留给接口设计确认；如果生成，则新实例或复用原实例必须另行明确，数据层不建议在同一个 `withdrawn` 实例继续流转。
- 已撤回实例只读展示，不再产生普通审批待办。

### `wf_task`

任务扩展表需要支持会签/或签同组任务、超时扫描和撤回取消。

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `approval_mode` | `varchar(20)` | `'single'` | 任务所属节点办理策略：`single`、`countersign`、`orsign` |
| `task_group_id` | `varchar(64)` | `NULL` | 同一节点同一批会签/或签任务组 ID |
| `group_total` | `int` | `NULL` | 同组任务总数 |
| `group_completed` | `int` | `NULL` | 同组已完成数，冗余摘要，可由服务维护 |
| `due_time` | `datetime` | `NULL` | 超时截止时间 |
| `last_remind_time` | `datetime` | `NULL` | 最近一次超时提醒时间 |
| `remind_count` | `int` | `0` | 已超时提醒次数 |

字段说明：

- `task_group_id` 用于会签/或签批量取消、完成判断和流程图展示。
- `group_completed` 是摘要字段，真实完成判断以同组 `wf_task` 状态为准，避免并发下只信冗余值。
- `due_time` 由节点 `timeout_json.durationMinutes` 和任务创建时间计算。
- `last_remind_time`、`remind_count` 只用于超时提醒防重复；催办记录在 `wf_reminder_record`。

`task_type` 枚举扩展：

- 保留一期：`start_draft`、`normal`、`transfer`、`add_sign`。
- 二期新增：`countersign`、`orsign`。

`status` 枚举扩展：

- 保留一期：`todo`、`done`、`transferred`、`returned`、`canceled`。
- 二期建议新增：`withdrawn`，用于撤回导致任务取消时更明确地区分于普通取消。

索引调整：

- 新增 `idx_wft_tenant_group_status(tenant_id, task_group_id, status, delete_flag)`：同组任务查询。
- 新增 `idx_wft_tenant_due_status(tenant_id, status, due_time, delete_flag)`：超时扫描。
- 保留 `idx_wft_tenant_assignee_status_time` 和 `idx_wft_tenant_instance_status`。

### `wf_task_candidate`

任务候选人表继续支撑候选任务和多人可办理任务。

字段调整：

| 字段 | 调整 | 说明 |
| --- | --- | --- |
| `source_type` | 枚举可继续复用现有审批人类型 | 会签/或签不作为审批人来源类型 |
| `status` | 保持 `active`、`claimed`、`canceled` | 或签完成、撤回、退回、不通过时取消未处理候选 |

规则：

- 会签二期建议直接生成多个 `wf_task`，不使用候选抢办表达会签。
- 或签可以选择多个 `wf_task` 或一个候选任务实现；为了列表和取消语义一致，二期建议也生成多个 `wf_task`，每个任务一个处理人。
- 如节点审批人解析为空，仍按一期审批人自选兜底。

### `wf_process_instance_assignee`

流程实例节点审批人快照表已有 `select_type`，二期可复用并扩展状态语义。

字段调整：

| 字段 | 调整 | 说明 |
| --- | --- | --- |
| `select_type` | 使用 `single`、`multiple` | 会签/或签选择多人时写 `multiple` |
| `status` | 保持 `active`、`canceled` | 撤回、退回或节点取消时可取消未使用快照 |

规则：

- 会签/或签节点的多人结果可以写入多条快照，`select_type=multiple`。
- 任务创建时优先读取实例审批人快照，避免组织关系变化影响已流转实例。
- 快照只表示“本实例该节点选定过这些人”，不表示待办状态；待办状态以 `wf_task` 为准。

### `wf_approval_record`

审批记录表继续作为详情时间线来源。二期扩展动作枚举，不建议为每个动作新增记录表，除非需要防重复或查询优化。

`action` 枚举新增：

| 枚举 | 含义 |
| --- | --- |
| `withdraw` | 撤回 |
| `cc` | 抄送 |
| `urge` | 催办 |
| `timeout_remind` | 超时提醒 |
| `task_cancel` | 系统取消任务 |
| `branch_match` | 条件分支命中 |
| `attachment_upload` | 上传附件 |
| `attachment_delete` | 删除附件 |

字段复用：

- 抄送动作使用 `target_user_id/target_username/target_realname` 表示抄送人；批量抄送可写多条记录或只写摘要记录，抄送明细以 `wf_cc_record` 为准。
- 催办和超时提醒使用 `target_user_id` 表示提醒对象；批量提醒明细以 `wf_reminder_record` 为准。
- 条件分支命中使用 `target_node_id/target_node_name` 表示命中目标节点，`comment` 存分支名称。
- 附件动作可在 `comment` 存附件名称，附件明细以 `wf_attachment` 为准。

索引调整：

- 保持 `idx_wfar_tenant_instance_time`。
- 可追加 `idx_wfar_tenant_action_time(tenant_id, action, delete_flag, operate_time)`，用于后续审计查询。

## 新增表设计

### `wf_cc_record`

审批抄送记录表。用于“我的抄送”、审批详情访问权和抄送已读状态。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `varchar(64)` | 是 | 主键 |
| `tenant_id` | `varchar(64)` | 否 | 租户 ID |
| `process_instance_id` | `varchar(64)` | 是 | 审批实例 ID |
| `process_model_id` | `varchar(64)` | 是 | 流程模型版本 ID |
| `task_id` | `varchar(64)` | 否 | 触发抄送的任务 ID |
| `node_id` | `varchar(100)` | 否 | 触发节点 ID |
| `node_name` | `varchar(100)` | 否 | 触发节点名称 |
| `trigger_action` | `varchar(32)` | 是 | 触发动作：`start`、`approve`、`process_finished`、`manual` |
| `source_type` | `varchar(32)` | 否 | 抄送来源：`user`、`role`、`depart`、`starter_supervisor`、`runtime_select` |
| `source_id` | `varchar(64)` | 否 | 来源 ID |
| `receiver_user_id` | `varchar(64)` | 是 | 接收人用户 ID |
| `receiver_username` | `varchar(64)` | 否 | 接收人账号 |
| `receiver_realname` | `varchar(100)` | 否 | 接收人姓名 |
| `status` | `varchar(20)` | 是 | 状态：`unread`、`read`、`canceled` |
| `read_time` | `datetime` | 否 | 阅读时间 |
| `message_id` | `varchar(64)` | 否 | 关联站内消息 ID |
| `remark` | `varchar(500)` | 否 | 备注 |
| 公共字段 |  |  | 审计与逻辑删除 |

唯一键和索引：

- 唯一键：`uk_wfcc_tenant_instance_receiver_active(tenant_id, process_instance_id, receiver_user_id, delete_flag)`，避免同一实例给同一用户产生多条有效抄送。
- 普通索引：`idx_wfcc_tenant_receiver_status_time(tenant_id, receiver_user_id, status, delete_flag, create_time)`，用于我的抄送。
- 普通索引：`idx_wfcc_tenant_instance(tenant_id, process_instance_id, delete_flag)`，用于详情访问权。

规则：

- 抄送不是任务，不写入 `wf_task`。
- 抄送记录是审批详情访问权来源之一。
- 重复抄送同一实例给同一用户时，更新已有记录或保持一条有效记录，不新增重复未读记录。

### `wf_reminder_record`

催办和超时提醒记录表。用于防重复提醒、消息追踪和审批详情展示。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `varchar(64)` | 是 | 主键 |
| `tenant_id` | `varchar(64)` | 否 | 租户 ID |
| `process_instance_id` | `varchar(64)` | 是 | 审批实例 ID |
| `task_id` | `varchar(64)` | 是 | 任务扩展 ID |
| `flowable_task_id` | `varchar(128)` | 否 | Flowable 任务 ID |
| `remind_type` | `varchar(32)` | 是 | 提醒类型：`urge`、`timeout` |
| `sender_user_id` | `varchar(64)` | 否 | 发送人用户 ID；系统超时提醒为空或 `system` |
| `sender_username` | `varchar(64)` | 否 | 发送人账号 |
| `sender_realname` | `varchar(100)` | 否 | 发送人姓名 |
| `receiver_user_id` | `varchar(64)` | 是 | 接收人用户 ID |
| `receiver_username` | `varchar(64)` | 否 | 接收人账号 |
| `receiver_realname` | `varchar(100)` | 否 | 接收人姓名 |
| `message_id` | `varchar(64)` | 否 | 关联站内消息 ID |
| `remind_round` | `int` | 否 | 第几轮提醒，催办默认为 1 |
| `operate_time` | `datetime` | 是 | 提醒时间 |
| `remark` | `varchar(500)` | 否 | 备注 |
| 公共字段 |  |  | 审计与逻辑删除 |

唯一键和索引：

- 普通索引：`idx_wfrr_tenant_task_type_time(tenant_id, task_id, remind_type, delete_flag, operate_time)`。
- 普通索引：`idx_wfrr_tenant_receiver_time(tenant_id, receiver_user_id, delete_flag, operate_time)`。
- 可选唯一键：`uk_wfrr_timeout_round(tenant_id, task_id, receiver_user_id, remind_type, remind_round, delete_flag)`，防止同一轮超时提醒重复。

规则：

- 催办和超时提醒不改变 `wf_task.status`。
- 超时扫描以 `wf_task.due_time`、`wf_task.status`、`wf_task.remind_count` 为主，以本表防重复。
- 催办防刷规则由业务服务按最近一条 `urge` 记录判断。

### `wf_attachment`

审批附件表。记录审批语义和权限，文件元数据仍在 `sys_files`。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `varchar(64)` | 是 | 主键 |
| `tenant_id` | `varchar(64)` | 否 | 租户 ID |
| `process_instance_id` | `varchar(64)` | 是 | 审批实例 ID |
| `task_id` | `varchar(64)` | 否 | 任务扩展 ID |
| `approval_record_id` | `varchar(64)` | 否 | 审批记录 ID |
| `node_id` | `varchar(100)` | 否 | 上传节点 ID |
| `node_name` | `varchar(100)` | 否 | 上传节点名称 |
| `file_id` | `varchar(64)` | 是 | `sys_files.id` |
| `file_relation_id` | `varchar(64)` | 否 | `sys_file_relation.id` |
| `attachment_source` | `varchar(32)` | 是 | 来源：`start`、`task`、`comment` |
| `uploader_user_id` | `varchar(64)` | 是 | 上传人用户 ID |
| `uploader_username` | `varchar(64)` | 否 | 上传人账号 |
| `uploader_realname` | `varchar(100)` | 否 | 上传人姓名 |
| `status` | `varchar(20)` | 是 | 状态：`active`、`deleted` |
| `sort_order` | `int` | 否 | 排序 |
| `remark` | `varchar(500)` | 否 | 备注 |
| 公共字段 |  |  | 审计与逻辑删除 |

唯一键和索引：

- 唯一键：`uk_wfatt_tenant_instance_file_active(tenant_id, process_instance_id, file_id, delete_flag)`。
- 普通索引：`idx_wfatt_tenant_instance(tenant_id, process_instance_id, status, delete_flag, sort_order)`。
- 普通索引：`idx_wfatt_tenant_task(tenant_id, task_id, delete_flag)`。
- 普通索引：`idx_wfatt_tenant_record(tenant_id, approval_record_id, delete_flag)`。

与文件中心关系：

- 上传仍写 `sys_files`。
- 绑定业务附件时同时写 `sys_file_relation`，建议：
  - `biz_type = workflow_approval`
  - `biz_id = process_instance_id`
  - `relation_type = 1`
- `wf_attachment.file_relation_id` 保存该关联 ID，便于解绑。

与文档中心关系：

- 工作流模块需要实现 `IBusinessDocumentProvider`，处理 `biz_type=workflow_approval`。
- `canAccess(...)` 必须按审批详情访问权判断：发起人、当前处理人、历史处理人、候选人、审批记录操作人、抄送人可访问。
- 文档中心中审批附件按只读业务文档展示，不允许从文档中心修改审批附件关系。

### `wf_branch_record`

条件分支命中记录表。用于流程图高亮、审计和排查。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `varchar(64)` | 是 | 主键 |
| `tenant_id` | `varchar(64)` | 否 | 租户 ID |
| `process_instance_id` | `varchar(64)` | 是 | 审批实例 ID |
| `process_model_id` | `varchar(64)` | 是 | 流程模型版本 ID |
| `source_node_id` | `varchar(100)` | 是 | 分支来源节点 ID |
| `source_node_name` | `varchar(100)` | 否 | 分支来源节点名称 |
| `branch_id` | `varchar(100)` | 是 | 命中分支 ID，默认分支可使用 `default` |
| `branch_name` | `varchar(100)` | 否 | 命中分支名称 |
| `target_node_id` | `varchar(100)` | 是 | 目标节点 ID |
| `target_node_name` | `varchar(100)` | 否 | 目标节点名称 |
| `condition_snapshot_json` | `json` | 否 | 判断条件快照 |
| `form_data_snapshot_json` | `json` | 否 | 判断时表单数据快照 |
| `matched_time` | `datetime` | 是 | 命中时间 |
| 公共字段 |  |  | 审计与逻辑删除 |

索引：

- 普通索引：`idx_wfbr_tenant_instance_time(tenant_id, process_instance_id, delete_flag, matched_time)`。
- 普通索引：`idx_wfbr_tenant_source(tenant_id, process_model_id, source_node_id, delete_flag)`。

规则：

- 每次条件网关判断必须写一条命中记录。
- 默认分支也必须写记录。
- 流程图高亮实际路径时读取本表。

## 枚举定稿

### 节点类型 `node_type`

一期：

- `start`
- `approver`
- `end`

二期新增：

- `gateway`：网关节点，承载条件分支或并行结构的逻辑节点。

说明：

- 简单设计器可继续把条件配置挂在审批节点或网关节点上；发布到 BPMN 时转换为排他网关。
- 会签/或签不新增节点类型，使用审批节点 `approval_mode` 表达。

### 办理策略 `approval_mode`

- `single`：单人审批。
- `countersign`：并行会签。
- `orsign`：或签。

### 执行人确定方式 `assignee_resolve_mode`

- `all`：进入节点时发送给全部解析人员。
- `select`：进入节点前由上一步办理人在候选范围内选择实际执行人。
- 单人审批不开放该配置，固定按 `select` 处理。

### 流程实例状态 `wf_process_instance.status`

- `draft`：草稿待提交。
- `running`：流转中。
- `approved`：已通过。
- `rejected`：不通过。
- `withdrawn`：已撤回。
- `terminated`：已终止。

### 任务类型 `wf_task.task_type`

- `start_draft`：发起草稿待提交。
- `normal`：普通审批。
- `transfer`：转办。
- `add_sign`：加签。
- `countersign`：会签任务。
- `orsign`：或签任务。

### 任务状态 `wf_task.status`

- `todo`：待办。
- `done`：已办。
- `transferred`：已转办。
- `returned`：已退回。
- `canceled`：已取消。
- `withdrawn`：因撤回取消。

### 候选人状态 `wf_task_candidate.status`

- `active`：有效。
- `claimed`：已认领。
- `canceled`：已取消。

### 审批动作 `wf_approval_record.action`

一期：

- `save_draft`
- `start`
- `approve`
- `reject`
- `return`
- `transfer`
- `add_sign`
- `system_complete`

二期新增：

- `withdraw`
- `cc`
- `urge`
- `timeout_remind`
- `task_cancel`
- `branch_match`
- `attachment_upload`
- `attachment_delete`

### 抄送状态 `wf_cc_record.status`

- `unread`：未读。
- `read`：已读。
- `canceled`：已取消。

### 提醒类型 `wf_reminder_record.remind_type`

- `urge`：人工催办。
- `timeout`：超时提醒。

### 附件状态 `wf_attachment.status`

- `active`：有效。
- `deleted`：已删除。

### 附件来源 `wf_attachment.attachment_source`

- `start`：发起申请上传。
- `task`：办理任务上传。
- `comment`：审批意见附件，二期可先预留。

## 关键状态流转

### 并行会签

```text
进入会签节点
  -> 解析审批人并去重
  -> 生成 task_group_id
  -> 为每个审批人生成 wf_task(task_type=countersign, approval_mode=countersign, status=todo)
  -> 所有会签任务完成前，流程实例保持 running
  -> 某一人 approve：该任务 done，记录审批意见
  -> 检查同组任务是否全部 done
  -> 全部 done：完成 Flowable 当前节点或并行聚合逻辑，生成下一节点待办
  -> 任一人 reject/return：取消同组其它 todo，按不通过或退回规则结束/回退
```

规则：

- 二期会签只做并行会签。
- 串行会签使用多个普通审批节点。
- 会签同组任务由 `task_group_id` 关联。
- `current_assignee_names` 汇总未完成会签任务处理人。

### 或签

```text
进入或签节点
  -> 解析审批人并去重
  -> 生成 task_group_id
  -> 为每个审批人生成 wf_task(task_type=orsign, approval_mode=orsign, status=todo)
  -> 任一人 approve：该任务 done，取消同组其它 todo/candidate，生成下一节点待办
  -> 任一人 reject：流程 rejected，取消同组其它 todo/candidate
  -> 后续重复提交同组已取消任务：后端拒绝或按已完成幂等返回
```

规则：

- 或签完成后必须立即取消同组其它待办。
- 被取消的或签任务不进入办理权限，只能查看详情。
- 审批记录必须能看出实际办理人和系统取消动作。

### 撤回

```text
发起人申请撤回
  -> 校验发起人、租户、实例状态 running
  -> 校验当前任务仍未被下一环节办理
  -> 终止 Flowable 流程实例
  -> wf_process_instance.status = withdrawn
  -> end_time = now
  -> 当前 todo task.status = withdrawn
  -> 当前 active candidate.status = canceled
  -> 写 wf_approval_record(action=withdraw)
  -> 给当前待办人和发起人发消息
```

规则：

- 二期撤回后实例进入只读终态，不在同一实例上继续流转。
- 如后续需要“撤回后重新提交”，建议从撤回实例复制为新的草稿实例，另行设计接口。

### 催办

```text
发起人催办
  -> 校验实例 running 且有当前待办
  -> 校验催办人有权限
  -> 校验防刷窗口
  -> 写 wf_reminder_record(remind_type=urge)
  -> 写 wf_approval_record(action=urge) 或详情摘要记录
  -> 发送站内消息给当前待办处理人/候选人
```

规则：

- 催办不改变任务状态和实例状态。
- 二期催办人范围建议先限定为发起人。

### 超时提醒

```text
定时任务扫描 wf_task
  -> 查询 status=todo 且 due_time <= now 的任务
  -> 根据 timeout_json、last_remind_time、remind_count 判断是否需要提醒
  -> 写 wf_reminder_record(remind_type=timeout)
  -> 更新 wf_task.last_remind_time/remind_count
  -> 发送站内消息
```

规则：

- 超时提醒不改变任务状态和实例状态。
- 任务已完成、取消、撤回、退回后不再提醒。
- 二期按自然时间计算，不引入工作日历。

### 抄送

```text
触发抄送事件
  -> 解析 cc_json 或运行时选择人
  -> 过滤当前租户有效用户并去重
  -> upsert wf_cc_record
  -> 写 wf_approval_record(action=cc) 摘要
  -> 发送站内消息
```

规则：

- 抄送只授予查看权，不产生办理任务。
- `wf_cc_record` 是我的抄送列表和详情访问权来源。

### 附件

```text
上传文件
  -> 复用文件模块写 sys_files
  -> 绑定 sys_file_relation(biz_type=workflow_approval, biz_id=process_instance_id)
  -> 写 wf_attachment
  -> 必要时写 wf_approval_record(action=attachment_upload)
```

规则：

- 删除附件只逻辑删除 `wf_attachment` 并解绑或逻辑删除 `sys_file_relation`，不直接物理删除文件对象。
- 文档中心通过 `workflow_approval` Provider 校验访问权。

### 条件分支

```text
节点完成后进入网关判断
  -> 读取 branch_json
  -> 读取当前 form_data_json 和实例上下文
  -> 按 priority 判断条件
  -> 未命中则走 defaultTargetNodeId
  -> 写 wf_branch_record
  -> 写 wf_approval_record(action=branch_match) 摘要
  -> 生成目标节点待办
```

规则：

- 条件判断结果以后端为准。
- 分支记录用于流程图高亮实际路径。

## 消息模块复用

二期不新增工作流消息表。

建议消息业务字段：

- `biz_type = workflow`
- `biz_id = process_instance_id`
- 动作跳转到审批详情，参数包含 `processInstanceId`、`taskId` 或 `ccRecordId`。

消息触发场景：

- 新待办。
- 通过、不通过、退回、转办、加签。
- 抄送。
- 撤回。
- 催办。
- 超时提醒。
- 流程结束。

规则：

- 消息发送失败不能回滚审批主事务。
- 消息不是权限来源，跳转后仍由审批详情接口校验权限。
- `wf_cc_record.message_id`、`wf_reminder_record.message_id` 保存消息关联，便于排查。

## 文件与业务文档复用

建议统一业务类型：

- `sys_file_relation.biz_type = workflow_approval`
- `sys_file_relation.biz_id = process_instance_id`

工作流业务文档 Provider：

- 模块名称：审批中心。
- 业务数据目录名称：可使用流程名称、实例标题或审批编号组合。
- 访问权：发起人、当前任务处理人、候选人、历史任务处理人、审批记录操作人、抄送人。
- 权限：二期业务文档中心只读展示，下载和预览按 Provider 校验。

规则：

- 不在文档中心为审批中心写定制 `if/else`。
- 不从文档中心修改审批附件关系。
- 审批详情中的附件删除仍走工作流接口和权限校验。

## 流程图数据来源

二期不新增流程图状态表。

后端组装流程图时读取：

- `wf_process_model.node_json` 或 `bpmn_xml`：节点和边。
- `wf_process_node_config`：节点名称、类型、办理策略。
- `wf_task`：当前、已完成、已取消任务。
- `wf_task_candidate`：候选人摘要。
- `wf_approval_record`：审批动作时间线。
- `wf_branch_record`：条件分支命中路径。
- `wf_process_instance.status`：结束状态。

节点状态建议：

- `pending`：未到达。
- `current`：当前。
- `done`：已完成。
- `returned`：已退回。
- `canceled`：已取消。
- `end`：已结束。

## SQL 实施顺序

1. 扩展枚举注释：`wf_process_node_config`、`wf_process_instance`、`wf_task`、`wf_approval_record`。
2. 扩展 `wf_process_node_config` 二期配置字段。
3. 扩展 `wf_process_model` BPMN 安全字段。
4. 扩展 `wf_task` 会签/或签/超时字段。
5. 新增 `wf_cc_record`。
6. 新增 `wf_reminder_record`。
7. 新增 `wf_attachment`。
8. 新增 `wf_branch_record`。
9. 同步 `docs/03-database/tables.md` 和相关模块文档。
10. 同步后端实体、常量、Mapper、VO、Req 和前端常量。

## 待确认事项

- `wf_task.group_completed` 是否保留为冗余字段；如担心并发一致性，可删除，仅运行时聚合计算。
- 或签底层是否坚持多 `wf_task` 实现；本文建议多 `wf_task`，避免候选任务语义和待办展示不一致。
- 抄送重复记录采用更新原记录还是保持原创建时间；本文建议只保留一条有效记录并更新最近抄送时间，可在 SQL 设计时补 `last_cc_time`。
- 撤回后重新提交是否在二期实现；本文建议二期只做撤回终态，不做同实例重新提交。
- 附件权限是否增加节点级必传校验；本文预留 `attachment_json.required`，接口设计阶段再确认是否启用。
