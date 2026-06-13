# 审批中心组织关系增强设计

## 文档目的

本文用于固定审批中心审批人解析所依赖的组织关系底座。审批中心一期要支持指定人员、指定角色、部门负责人、部门岗位、发起人直属上级、审批人自选等审批人配置方式，不能依赖模糊字段或前端输入 ID 硬配置。

本次设计优先复用现有组织表，通过增强字段落地，不新增 `sys_depart_leader`、`sys_user_supervisor` 独立关系表。

## 背景与结论

当前系统已有以下组织基础：

- `sys_depart`：部门树。
- `sys_user_depart`：用户部门关系。
- `sys_role`、`sys_user_role`：系统角色和用户角色关系。
- `sys_depart_role`、`sys_depart_role_user`：部门角色和部门角色人员关系。
- `sys_user.user_identity`、`sys_user.depart_ids`：历史上用于标记上级和负责部门，但语义较粗，不适合作为审批人解析的正式依据。
- `sys_user.post`：字段注释为职务，但当前缺少完整岗位主数据和用户岗位关系，不适合作为审批中心一期的部门岗位解析依据。

结论：

- 指定人员、指定角色可以直接基于现有用户和角色体系实现。
- 部门负责人复用 `sys_user_depart`，在用户-部门关系上标记唯一负责人。
- 发起人直属上级复用 `sys_user_depart`，按用户所在部门维护直属上级。
- 部门岗位复用 `sys_depart_role`、`sys_depart_role_user`，通过字段标记哪些部门角色可作为审批岗位。
- 审批人自选不配置可选范围；运行时由上一环节办理人从本租户有效用户中选择下一节点审批人。

## 设计原则

- 组织关系属于系统基础能力，审批中心只消费，不在审批中心表里维护组织主数据。
- 优先复用当前组织关系表，通过补字段表达审批所需关系，避免为单一场景额外开表。
- 审批任务创建时解析审批人，并把解析结果快照到 `wf_task`、`wf_task_candidate`，后续组织关系变化不影响已生成任务。
- 所有解析结果必须落在当前租户内，且用户必须是当前租户有效成员。
- 流程运行中解析下一节点审批人为空时，提示当前办理人并以“审批人自选”兜底选择本租户有效用户，不自动跳过节点。
- 多人候选任务进入候选待办，提交审批时自动认领。
- 接口遵循系统规范，使用 `POST + body`，不新增 RESTful path 参数接口。

## 数据库增强方案

### `sys_user_depart`

`sys_user_depart` 是用户与部门的关系表，天然适合承载“某人在某部门内是否负责人、直属上级是谁”等部门维度组织关系。

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `primary_depart_flag` | `tinyint(1)` | `0` | 是否主部门：`0` 否，`1` 是 |
| `depart_leader_flag` | `tinyint(1)` | `0` | 是否部门负责人：`0` 否，`1` 是 |
| `supervisor_user_id` | `varchar(64)` | `NULL` | 直属上级用户 ID，按当前部门维度维护 |

新增索引：

- `idx_sud_tenant_user_primary(tenant_id, user_id, primary_depart_flag, delete_flag)`：按用户查询主部门。
- `idx_sud_tenant_dep_leader(tenant_id, dep_id, depart_leader_flag, delete_flag)`：按部门解析唯一负责人。
- `idx_sud_tenant_supervisor(tenant_id, supervisor_user_id, delete_flag)`：反查直属下级或校验上级关系。

业务规则：

- 一个用户在一个租户内建议只允许一个主部门，由 Service 校验。
- 一个部门只允许一个负责人，由 Service 校验。
- `depart_leader_flag=1` 表示该用户是该部门负责人。
- `supervisor_user_id` 表示该用户在当前部门关系下的直属上级。
- `supervisor_user_id` 不能等于 `user_id`。
- 直属上级必须是当前租户有效成员；是否必须属于同部门由业务规则控制，一期建议要求属于同部门或上级部门。
- 保存直属上级时必须校验不能形成上下级循环。

### `sys_depart_role`

`sys_depart_role` 继续作为部门角色表，审批中心一期将“部门岗位”定义为“可用于审批的部门角色”。

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `workflow_enabled` | `tinyint(1)` | `0` | 是否可作为审批岗位：`0` 否，`1` 是 |

新增索引：

- `idx_sdr_tenant_depart_workflow(tenant_id, depart_id, workflow_enabled, delete_flag)`：按部门查询可用于审批的岗位角色。

业务规则：

- 默认部门角色不建议标记为审批岗位。
- 普通部门角色可标记为审批岗位，例如“财务审批人”“行政审批人”“部门内勤”。
- 审批节点配置“部门岗位”时，只允许选择 `workflow_enabled=1` 的部门角色。
- 部门岗位人员仍由 `sys_depart_role_user` 维护。

## 审批人类型

审批中心一期建议统一支持以下审批人类型：

| 类型 | 含义 | 配置阶段 | 运行时解析依据 |
| --- | --- | --- | --- |
| `user` | 指定人员 | 选择具体用户 | `sys_user` + `sys_user_tenant` |
| `role` | 指定角色 | 选择系统角色 | `sys_role` + `sys_user_role` |
| `depart_leader` | 部门负责人 | 固定取发起人当前部门 | `sys_user_depart.depart_leader_flag` |
| `depart_role` | 部门岗位 | 选择可用于审批的部门角色 | `sys_depart_role.workflow_enabled` + `sys_depart_role_user` |
| `starter_supervisor` | 发起人直属上级 | 无需选择具体人 | `sys_user_depart.supervisor_user_id` |
| `starter_select` | 审批人自选 | 无需配置范围 | 到达上一环节办理动作时，由当前办理人从本租户有效用户中选择 |

说明：

- 现有 `starter` 表示“发起人本人”，可作为保留能力。
- `depart_role` 在界面上显示为“部门岗位”，降低业务用户理解成本；后端仍复用部门角色体系。
- 后续如果引入正式岗位主数据，可新增岗位解析器，但一期不依赖 `sys_user.post`。

## 配置 JSON

### 部门负责人

```json
{
  "departSource": "starter",
  "departIds": []
}
```

字段说明：

- 一期固定取发起人当前部门负责人；如果发起人有主部门，优先取主部门。
- 流程设计阶段不开放指定部门负责人范围，避免把其它部门负责人错误解析为当前审批人。

### 部门岗位

```json
{
  "departSource": "starter",
  "departIds": [],
  "departRoleIds": ["..."]
}
```

字段说明：

- `departSource=starter`：在发起人所在部门内解析岗位角色人员。
- `departSource=specified`：在指定部门内解析岗位角色人员。
- `departRoleIds` 只能选择 `workflow_enabled=1` 的部门角色。

### 发起人直属上级

```json
{
  "departSource": "starter",
  "emptyStrategy": "error"
}
```

字段说明：

- `departSource=starter`：取发起人主部门或发起时选择的部门下的 `supervisor_user_id`。
- `emptyStrategy=error`：找不到直属上级时阻断流程。

### 审批人自选

`starter_select` 的 `assignee_json` 为空，不在设计阶段保存选择范围。

规则：

- `selectMode=single`：当前办理人只能选择 1 个审批人。
- `selectMode=multiple`：当前办理人可选择多个人；一期建议先限制为 `single`。
- `starter_select` 不配置 `scopeRules`，运行时复用公共人员选择器选择本租户有效用户。
- 发起或审批提交页面只允许通过人员选择器选择用户，不允许手输用户 ID。
- 提交时，后端必须校验所选用户仍为当前租户有效成员，不能信任前端。

## 审批人解析流程

```text
读取节点配置
  -> 按 assignee_type 选择解析器
  -> 解析组织关系和人员范围
  -> 过滤当前租户无效用户、冻结用户、已删除用户
  -> 去重并生成候选结果
  -> 指定角色/部门岗位多人时由当前环节办理人选择下一审批人
  -> 当前环节选择结果写入 wf_process_instance_assignee 快照
  -> 任务创建时优先读取实例快照写入 wf_task.assignee_*
  -> 未要求发起人选择且解析为单人时写入 wf_task.assignee_*
  -> 仅候选池策略写入 wf_task_candidate
  -> 同步 Flowable assignee 或 candidate user
```

解析器建议：

- `UserAssigneeResolver`：指定人员。
- `RoleAssigneeResolver`：指定角色。
- `DepartLeaderAssigneeResolver`：部门负责人。
- `DepartRoleAssigneeResolver`：部门岗位。
- `StarterSupervisorAssigneeResolver`：发起人直属上级。
- `StarterSelectAssigneeResolver`：审批人自选。

解析结果建议统一 DTO：

| 字段 | 说明 |
| --- | --- |
| `userId` | 用户 ID |
| `username` | 账号 |
| `realname` | 姓名 |
| `sourceType` | 来源类型 |
| `sourceId` | 来源 ID，如角色 ID、部门 ID、部门角色 ID |
| `sourceName` | 来源名称，用于日志和前端展示 |

空审批人策略：

- 默认 `error`：阻断发布、发起或流转，并提示具体节点。
- 一期不做自动跳过，避免流程无审批直接通过。

## 与审批中心表的关系

`wf_process_node_config.assignee_type` 扩展为：

- `user`
- `role`
- `depart_leader`
- `depart_role`
- `starter_supervisor`
- `starter_select`
- `starter`

`wf_task_candidate.source_type` 同步扩展为：

- `user`
- `role`
- `depart_leader`
- `depart_role`
- `starter_supervisor`
- `starter_select`

`assignee_json` 保存配置快照，流程发布后随流程版本冻结；后续组织关系变更只影响新生成任务，不回写已发布流程配置。

## 页面与接口建议

### 组织管理入口

部门管理：

- 在部门成员抽屉中增加主部门、部门负责人、直属上级配置。
- 部门负责人只允许选择一个用户。
- 用户选择使用统一用户选择器，支持姓名、账号、部门搜索。

用户管理：

- 用户详情中可展示主部门和直属上级摘要。
- 如果后续需要在用户编辑页直接维护直属上级，本质仍回写 `sys_user_depart.supervisor_user_id`。

部门角色：

- 部门角色增加“可作为审批岗位”开关。
- 审批流程配置“部门岗位”时，只展示已开启该开关的部门角色。

### 系统接口

建议接口仍放在系统域，供审批中心消费。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/depart/member-relation/list` | 查询部门成员组织关系，包含负责人和直属上级 |
| `POST` | `/depart/member-relation/save` | 保存部门成员组织关系 |
| `POST` | `/depart/leader/list` | 查询部门负责人 |
| `POST` | `/depart/leader/save` | 覆盖保存部门负责人 |
| `POST` | `/user/supervisor/get` | 查询用户直属上级 |
| `POST` | `/user/supervisor/save` | 保存用户直属上级 |
| `POST` | `/workflow/assignee/preview` | 流程设计时预览节点审批人解析结果 |
| `POST` | `/system/picker/user/list` | 审批人自选时复用公共人员选择器查询本租户有效用户 |

说明：

- 组织关系维护接口归系统域。
- 审批人预览归工作流域，审批人自选复用系统域公共人员选择器。
- 所有接口参数放 body。

## 前端审批人配置组件

流程设计器中的审批人配置建议统一抽为组件：

```text
AssigneeConfigPanel
  -> UserPicker
  -> RolePicker
  -> DepartPicker
  -> DepartRolePicker
  -> SupervisorRuleConfig
  -> StarterSelectRuntimeTip
```

交互要求：

- 界面显示姓名、角色名、部门名和部门岗位名，不显示裸 ID。
- 指定人员支持搜索姓名、部门、账号。
- 指定角色选择系统角色。
- 部门负责人固定按发起人当前部门解析，不开放指定部门范围选择。
- 部门岗位可选择“发起人所在部门 + 部门岗位”或“指定部门 + 部门岗位”。
- 发起人直属上级无需选择具体人员，只显示规则说明。
- 审批人自选不配置选择范围，只显示运行时选择说明；实际办理时复用公共人员选择器。

## 实施顺序

建议按以下顺序实现，不直接跳到流程节点配置：

1. 更新 `sql/建表脚本.sql`，增强 `sys_user_depart`、`sys_depart_role` 字段，并扩展工作流审批人枚举注释。
2. 同步 `docs/03-database/tables.md`、系统部门文档和审批中心一期设计文档。
3. 后端更新实体、VO、Req、Mapper 查询和组织关系 Service。
4. 前端在部门管理中增加负责人、直属上级和审批岗位配置入口。
5. 前端实现审批人配置组件，先接入流程简单设计器。
6. 后端实现审批人解析器和候选人落库。
7. 发起申请、任务创建、任务提交时接入解析和校验。

## 待确认事项

- 部门负责人是否必须是本部门成员；当前设计天然要求负责人存在于 `sys_user_depart`，即属于该部门。
- 直属上级是否必须属于同部门；本文建议同部门或上级部门。
- 审批人自选一期只允许单选，后续如需要会签再扩展多选。
