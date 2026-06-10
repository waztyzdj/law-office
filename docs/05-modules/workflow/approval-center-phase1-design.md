# 审批中心一期设计文档

## 文档目的

本文用于固定审批中心一期的技术路径、范围边界、数据模型、状态流转、接口清单、页面清单和 Flowable 集成方式。后续开发必须先对齐本文，再开始建表和代码实现，避免在实现过程中发散。

## 工作原则

- 先文档，后建表，再编码。
- 一期只做审批中心闭环，不提前实现二期能力。
- Flowable 只作为流程运行时引擎，不把 `ACT_*` 表直接暴露给前端。
- 系统自己的审批业务状态以 `wf_*` 扩展表为准，Flowable 表只保存引擎运行数据。
- 简单流程设计器和 BPMN 设计器最终都产出 BPMN XML，由 Flowable 部署和执行。
- 表单定义必须版本化。流程发布时绑定表单版本，历史实例使用发布时的表单快照。
- 字段权限按节点配置，并在审批详情渲染时生效。
- 发现非一期但有价值的需求，先记录到本文“后续完善清单”，不插入当前开发范围。

## 技术路径

### 后端

- 使用 Flowable 作为 BPMN 流程引擎。
- 后端接入 Flowable Spring Boot 能力，由业务服务封装 `RepositoryService`、`RuntimeService`、`TaskService`、`HistoryService` 等引擎 API。
- 审批中心业务包建议使用 `backend/src/main/java/com/lawoffice/workflow`。
- 审批中心接口前缀建议使用 `/workflow`。
- Flowable 用户、角色、部门不作为系统主数据来源。审批人解析统一使用现有系统用户、角色、部门和租户数据。

### 前端

- 前端审批中心建议使用 `frontend/src/views/workflow`。
- 表单设计和渲染使用 FormCreate，优先使用 Vue3 + Ant Design Vue 版本。
- 简单流程设计器使用仿钉钉/飞书节点式体验，保存自定义 `node_json`。
- 复杂流程设计器使用 bpmn-js，保存 BPMN XML。
- 两种设计器统一发布到 Flowable：简单设计器先转换为 BPMN XML，复杂设计器直接使用 BPMN XML。

### 官方资料

- Flowable Open Source Documentation: https://www.flowable.com/open-source/docs/index.html
- Flowable Spring Boot Documentation: https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot/
- Flowable API Documentation: https://www.flowable.com/open-source/docs/bpmn/ch04-API/
- bpmn-js Toolkit: https://bpmn.io/toolkit/bpmn-js
- bpmn-js Walkthrough: https://bpmn.io/toolkit/bpmn-js/walkthrough
- FormCreate Vue3 Guide: https://www.form-create.com/en/v3/guide/
- FormCreate Ant Design Vue Designer: https://view.form-create.com/en/antd/start

## 一期范围

一期目标是完成审批中心最小可用闭环：

1. 流程分类
2. 表单设计
3. 流程设计
4. 流程发布
5. 发起申请
6. 我的待办
7. 我的已办
8. 我发起的
9. 审批详情
10. 审批记录
11. 通过
12. 拒绝
13. 退回
14. 转办
15. 加签
16. 字段权限

## 非一期范围

以下能力不进入一期实现，只记录为后续完善项：

- 会签
- 或签
- 条件分支
- 抄送
- 撤回
- 催办
- 超时提醒
- 移动端审批
- 流程监控大屏
- 流程统计报表
- 审批附件模板化
- 打印、导出和归档
- 外部系统回调

## 一期业务闭环

```text
流程分类
  -> 表单设计
  -> 流程设计
  -> 发布流程
  -> 发起申请
  -> 生成流程实例
  -> 生成待办任务
  -> 审批人处理
  -> 写审批记录
  -> 更新实例状态
  -> 进入已办/结束
```

## 设计边界

### Flowable 边界

Flowable 负责：

- BPMN 部署
- 流程定义版本
- 流程实例运行
- 用户任务流转
- 历史任务和历史实例查询
- 流程变量存取

系统业务表负责：

- 流程分类
- 表单定义和版本
- 流程模型元数据
- 流程发布记录
- 审批标题、摘要、发起人、租户、业务状态
- 流程发起权限
- 审批记录
- 字段权限
- 前端列表缓存所需的业务字段

### 表单边界

- `wf_form_definition` 保存表单设计稿和发布版本。
- `wf_form_instance` 保存发起后的表单实例数据、表单 schema 快照和 option 快照。
- `wf_process_instance` 只保存流程实例摘要和 `form_instance_id`，不直接承载表单大 JSON。
- 审批中修改表单字段时，更新 `wf_form_instance.form_data_json`。
- 历史审批详情默认读取表单实例快照，避免表单后续变更影响历史数据。

### 流程设计边界

- 简单设计器只支持顺序审批节点、审批人配置和字段权限。
- 简单设计器的一期不做条件分支、会签、或签。
- 复杂设计器允许编辑 BPMN XML，但一期只承诺用户任务、开始、结束和顺序流的基本可用。
- 发布前必须做 BPMN 校验和节点配置校验。

## 数据模型

### 数据建模统一约定

- 一期所有 `wf_*` 业务表默认都是租户内数据，必须包含 `tenant_id`，后端实体默认继承 `BaseTenantEntity`；如果后续出现全局字典或全局模板，必须在表设计中单独说明并改为继承 `BaseEntity`。
- 主键 `id` 按项目现有字符串 ID 约定设计，SQL 字段建议使用 `varchar(64)`。
- 所有 `wf_*` 业务表必须包含完整审计与逻辑删除字段：`create_time`、`create_by`、`update_time`、`update_by`、`delete_flag`、`delete_time`、`delete_by`。
- 审计字段类型按现有 SQL 习惯保持一致：时间字段使用 `datetime`，操作人字段使用 `varchar(64)`，`delete_flag` 使用 `int`，默认 `0` 表示未删除，`1` 表示已删除。
- 后端新增、更新、删除必须复用项目已有审计填充约定；逻辑删除必须写入 `delete_flag/delete_time/delete_by`，不能只改 `delete_flag`。
- 查询列表和详情默认只查询 `delete_flag = 0` 的数据；确需查看已删除数据时必须作为明确的管理场景单独设计。
- 业务唯一约束需要考虑软删恢复和重建场景：非版本化业务键建议使用 `tenant_id + 业务键 + delete_flag`；表单、流程等版本化业务键建议使用 `tenant_id + 业务键 + version + delete_flag`。
- 常用列表索引需要优先覆盖 `tenant_id`、`delete_flag`、`status`、分类/发起人/处理人、更新时间或创建时间等查询条件。
- JSON 字段保存 FormCreate schema/option、节点配置、表单快照和表单数据。SQL 类型在建表阶段结合当前 MySQL 版本确认，优先使用 `json`；如果兼容性不足，使用 `longtext` 并在后端做 JSON 校验。
- 状态和动作枚举必须使用固定值，并在后续建表时同步 SQL 字段注释、后端 enum、前端常量和接口文档。
- 字段权限必须按“流程模型版本 + 流程节点 + 表单字段”配置；`node_id` 对应 BPMN `taskDefinitionKey`，`field_key` 对应 FormCreate 字段 `field`。
- Flowable 自带 `ACT_*` 表不纳入上述审计字段要求；系统业务状态、租户过滤和前端列表展示以 `wf_*` 扩展表为准。

### `wf_process_category`

流程分类表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `parent_id` | 父分类 ID，一期可为空 |
| `category_code` | 分类编码，同租户唯一 |
| `category_name` | 分类名称 |
| `sort_order` | 排序 |
| `status` | 状态：`enabled`、`disabled` |
| `remark` | 备注 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 分类编码唯一键建议使用 `tenant_id + category_code + delete_flag`。

### `wf_form_definition`

表单定义表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `category_id` | 所属流程分类 |
| `form_key` | 表单编码，同租户内作为业务编码，需配合版本唯一 |
| `form_name` | 表单名称 |
| `version` | 表单版本号 |
| `schema_json` | FormCreate 规则 JSON |
| `option_json` | FormCreate option JSON |
| `status` | 状态：`draft`、`published`、`disabled` |
| `published_time` | 发布时间 |
| `remark` | 备注 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 同一个 `form_key` 可以有多个版本。
- 表单版本唯一键建议使用 `tenant_id + form_key + version + delete_flag`。
- 只有 `published` 表单版本可以绑定到已发布流程。
- 编辑已发布表单时，应复制新版本，不直接覆盖历史版本。

### `wf_form_instance`

表单实例表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_instance_id` | 审批实例 ID，发起后写入 |
| `form_definition_id` | 表单定义版本 ID |
| `form_key` | 表单编码快照 |
| `form_name` | 表单名称快照 |
| `form_version` | 表单版本号快照 |
| `form_data_json` | 当前表单数据 JSON |
| `form_schema_snapshot_json` | 发起时表单 schema 快照 |
| `form_option_snapshot_json` | 发起时 FormCreate option 快照 |
| `status` | 状态：`draft`、`active`、`archived` |
| `submitted_time` | 提交时间 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 一期一个流程实例对应一个表单实例。
- 表单实例保存发起时的表单定义快照，历史详情不再读取最新表单定义。
- 审批中可编辑字段变更时，只更新 `wf_form_instance.form_data_json`。

### `wf_process_model`

流程模型表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `category_id` | 流程分类 ID |
| `form_definition_id` | 绑定表单定义版本 ID |
| `process_key` | 流程编码，同租户内作为业务编码，需配合版本唯一 |
| `process_name` | 流程名称 |
| `version` | 模型版本 |
| `designer_type` | 设计器类型：`simple`、`bpmn` |
| `node_json` | 简单设计器节点 JSON |
| `bpmn_xml` | BPMN XML |
| `status` | 状态：`draft`、`published`、`disabled` |
| `start_scope_type` | 发起范围：`all`、`specified` |
| `flowable_deployment_id` | Flowable 部署 ID |
| `flowable_process_definition_id` | Flowable 流程定义 ID |
| `published_time` | 发布时间 |
| `remark` | 备注 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 发布时生成 Flowable deployment。
- 流程模型版本唯一键建议使用 `tenant_id + process_key + version + delete_flag`。
- 发布后不直接修改当前版本，编辑时复制新版本。
- 前端默认加载最新 `published` 版本发起申请。

### `wf_process_start_permission`

流程发起权限表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_model_id` | 流程模型版本 ID |
| `target_type` | 授权目标类型：`user`、`role`、`depart`、`tenant` |
| `target_id` | 授权目标 ID，租户范围使用当前租户 ID |
| `status` | 状态：`enabled`、`disabled` |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- `start_scope_type=all` 时，当前租户用户均可发起。
- `start_scope_type=specified` 时，必须命中 `wf_process_start_permission` 才可发起。
- `available/page` 只返回当前用户有发起权限的已发布流程。

### `wf_process_node_config`

流程节点配置表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_model_id` | 流程模型 ID |
| `node_id` | 节点 ID，对应 BPMN `taskDefinitionKey` |
| `node_name` | 节点名称 |
| `node_type` | 节点类型：`start`、`approver`、`end` |
| `assignee_type` | 审批人类型：`user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select`、`starter` |
| `assignee_json` | 审批人配置 JSON |
| `allow_transfer` | 是否允许转办 |
| `allow_add_sign` | 是否允许加签 |
| `allow_return` | 是否允许退回 |
| `sort_order` | 节点顺序 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 节点配置唯一键建议使用 `tenant_id + process_model_id + node_id + delete_flag`。
- `assignee_type=user` 时，`assignee_json` 使用 `{ "userIds": ["..."] }`。
- `assignee_type=role` 时，`assignee_json` 使用 `{ "roleIds": ["..."] }`。
- `assignee_type=depart_leader` 时，基于 `sys_user_depart` 中的部门负责人字段解析；配置规则以 [审批中心组织关系增强设计](approval-center-org-relation-design.md) 为准。
- `assignee_type=depart_role` 时，基于部门角色解析部门岗位人员；配置规则以 [审批中心组织关系增强设计](approval-center-org-relation-design.md) 为准。
- `assignee_type=starter_supervisor` 时，基于 `sys_user_depart.supervisor_user_id` 解析发起人直属上级；配置规则以 [审批中心组织关系增强设计](approval-center-org-relation-design.md) 为准。
- `assignee_type=starter_select` 时，发起人按管理员配置的范围自选审批人；配置规则以 [审批中心组织关系增强设计](approval-center-org-relation-design.md) 为准。
- `assignee_type=starter` 时，`assignee_json` 可为空，运行时直接解析为发起人本人。

### `wf_field_permission`

字段权限表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_model_id` | 流程模型 ID |
| `node_id` | 节点 ID |
| `field_key` | 表单字段 key |
| `permission` | 权限：`hidden`、`readonly`、`editable` |
| `required_flag` | 该节点是否必填 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 字段权限唯一键建议使用 `tenant_id + process_model_id + node_id + field_key + delete_flag`。
- 发起节点默认字段可编辑。
- 审批节点默认字段只读。
- 隐藏字段后端也不能信任前端提交值，保存时必须按字段权限清洗。

### `wf_process_instance`

流程实例扩展表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_model_id` | 流程模型 ID |
| `form_instance_id` | 表单实例 ID |
| `flowable_process_instance_id` | Flowable 流程实例 ID |
| `flowable_process_definition_id` | Flowable 流程定义 ID |
| `form_definition_id` | 表单定义版本 ID |
| `instance_no` | 审批编号 |
| `instance_title` | 实例标题 |
| `business_key` | 业务 key，一期可使用实例 ID |
| `starter_user_id` | 发起人用户 ID |
| `starter_username` | 发起人账号 |
| `starter_realname` | 发起人姓名 |
| `status` | 状态：`running`、`approved`、`rejected`、`terminated` |
| `start_time` | 发起时间 |
| `end_time` | 结束时间 |
| `current_task_names` | 当前节点名称摘要 |
| `current_assignee_names` | 当前处理人姓名摘要 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

### `wf_task`

任务扩展表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_instance_id` | 审批实例 ID |
| `parent_task_id` | 父任务扩展 ID，转办/加签任务关联原任务 |
| `flowable_task_id` | Flowable 任务 ID |
| `node_id` | 节点 ID |
| `task_name` | 任务名称 |
| `task_type` | 任务类型：`normal`、`transfer`、`add_sign` |
| `owner_user_id` | 原处理人用户 ID，转办/加签时记录 |
| `owner_username` | 原处理人账号 |
| `owner_realname` | 原处理人姓名 |
| `assignee_user_id` | 当前处理人用户 ID |
| `assignee_username` | 当前处理人账号 |
| `assignee_realname` | 当前处理人姓名 |
| `status` | 状态：`todo`、`done`、`transferred`、`returned`、`canceled` |
| `claim_time` | 认领时间，一期可为空 |
| `complete_time` | 完成时间 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- `wf_task` 用于前端待办/已办列表展示和租户过滤，不替代 Flowable 任务表。
- `flowable_task_id` 建议使用 `tenant_id + flowable_task_id + delete_flag` 做唯一约束，避免重复同步待办。
- 任务创建和完成时通过业务服务同步。
- 如果审批人解析结果是单人，写入 `assignee_user_id`；如果是多人候选，`assignee_user_id` 可为空，候选人写入 `wf_task_candidate`。

### `wf_task_candidate`

任务候选人表。用于指定角色、部门负责人、部门岗位、发起人自选等解析出多人时，支撑“我的待办”列表和认领/处理权限判断。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `task_id` | 任务扩展 ID |
| `flowable_task_id` | Flowable 任务 ID |
| `candidate_user_id` | 候选处理人用户 ID |
| `candidate_username` | 候选处理人账号 |
| `candidate_realname` | 候选处理人姓名 |
| `source_type` | 来源类型：`user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select` |
| `source_id` | 来源 ID，角色或部门等 |
| `status` | 状态：`active`、`claimed`、`canceled` |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

规则：

- 候选人唯一键建议使用 `tenant_id + task_id + candidate_user_id + delete_flag`。
- 任务由某个候选人提交审批时自动认领，其它候选记录状态改为 `canceled`，当前处理人写回 `wf_task.assignee_user_id`。

### `wf_approval_record`

审批记录表。

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `tenant_id` | 租户 ID |
| `process_instance_id` | 审批实例 ID |
| `task_id` | 任务扩展 ID |
| `flowable_task_id` | Flowable 任务 ID |
| `node_id` | 节点 ID |
| `node_name` | 节点名称 |
| `action` | 动作：`start`、`approve`、`reject`、`return`、`transfer`、`add_sign`、`system_complete` |
| `operator_user_id` | 操作人用户 ID |
| `operator_username` | 操作人账号 |
| `operator_realname` | 操作人姓名 |
| `target_user_id` | 目标用户 ID，转办/加签使用 |
| `target_username` | 目标账号 |
| `target_realname` | 目标姓名 |
| `target_node_id` | 目标节点 ID，退回使用 |
| `target_node_name` | 目标节点名称，退回使用 |
| `comment` | 审批意见 |
| `form_data_snapshot_json` | 操作时表单数据快照 |
| `operate_time` | 操作时间 |
| `create_time/create_by/update_time/update_by/delete_flag/delete_time/delete_by` | 审计与逻辑删除 |

## 数据库表设计定稿

本节作为一期建表依据。下一步生成 SQL 时，以本节字段、类型、枚举、唯一键和索引为准。

### 公共字段

除 Flowable 自带 `ACT_*` 表外，一期所有 `wf_*` 表统一包含以下公共字段：

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

JSON 字段一期 SQL 类型定为 `json`，后端实体可先按 `String` 接收和序列化；如果实际数据库版本不支持 `json`，建表前统一降级为 `longtext`，但字段语义和后端 JSON 校验不变。

### `wf_process_category`

流程分类表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `parent_id` | `varchar(64)` | 否 | 父分类 ID，一期允许为空 |
| `category_code` | `varchar(64)` | 是 | 分类编码 |
| `category_name` | `varchar(100)` | 是 | 分类名称 |
| `sort_order` | `int` | 否 | 排序，默认 `0` |
| `status` | `varchar(20)` | 是 | `enabled`、`disabled` |
| `remark` | `varchar(500)` | 否 | 备注 |

索引：

- 主键：`pk_wf_process_category(id)`。
- 唯一键：`uk_wfpc_tenant_code_active(tenant_id, category_code, delete_flag)`。
- 普通索引：`idx_wfpc_tenant_parent_sort(tenant_id, parent_id, delete_flag, sort_order)`。
- 普通索引：`idx_wfpc_tenant_status(tenant_id, status, delete_flag)`。

### `wf_form_definition`

表单定义版本表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `category_id` | `varchar(64)` | 否 | 所属流程分类 |
| `form_key` | `varchar(64)` | 是 | 表单编码，同租户内作为业务编码 |
| `form_name` | `varchar(100)` | 是 | 表单名称 |
| `version` | `int` | 是 | 表单版本号，从 `1` 开始递增 |
| `schema_json` | `json` | 是 | FormCreate rule/schema JSON |
| `option_json` | `json` | 否 | FormCreate option JSON |
| `status` | `varchar(20)` | 是 | `draft`、`published`、`disabled` |
| `published_time` | `datetime` | 否 | 发布时间 |
| `remark` | `varchar(500)` | 否 | 备注 |

索引：

- 主键：`pk_wf_form_definition(id)`。
- 唯一键：`uk_wffd_tenant_key_version_active(tenant_id, form_key, version, delete_flag)`。
- 普通索引：`idx_wffd_tenant_category_status(tenant_id, category_id, status, delete_flag)`。
- 普通索引：`idx_wffd_tenant_key_status(tenant_id, form_key, status, delete_flag)`。

### `wf_form_instance`

表单实例表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_instance_id` | `varchar(64)` | 否 | 审批实例 ID，发起后写入 |
| `form_definition_id` | `varchar(64)` | 是 | 表单定义版本 ID |
| `form_key` | `varchar(64)` | 是 | 表单编码快照 |
| `form_name` | `varchar(100)` | 是 | 表单名称快照 |
| `form_version` | `int` | 是 | 表单版本号快照 |
| `form_data_json` | `json` | 否 | 当前表单数据 JSON |
| `form_schema_snapshot_json` | `json` | 否 | 发起时 FormCreate schema 快照 |
| `form_option_snapshot_json` | `json` | 否 | 发起时 FormCreate option 快照 |
| `status` | `varchar(20)` | 是 | `draft`、`active`、`archived` |
| `submitted_time` | `datetime` | 否 | 提交时间 |

索引：

- 主键：`pk_wf_form_instance(id)`。
- 唯一键：`uk_wffi_tenant_process_active(tenant_id, process_instance_id, delete_flag)`。
- 普通索引：`idx_wffi_tenant_form_status(tenant_id, form_definition_id, status, delete_flag)`。
- 普通索引：`idx_wffi_tenant_key_version(tenant_id, form_key, form_version, delete_flag)`。

### `wf_process_model`

流程模型版本表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `category_id` | `varchar(64)` | 否 | 流程分类 ID |
| `form_definition_id` | `varchar(64)` | 是 | 绑定表单定义版本 ID |
| `process_key` | `varchar(64)` | 是 | 流程编码，同租户内作为业务编码 |
| `process_name` | `varchar(100)` | 是 | 流程名称 |
| `version` | `int` | 是 | 流程模型版本号，从 `1` 开始递增 |
| `designer_type` | `varchar(20)` | 是 | `simple`、`bpmn` |
| `node_json` | `json` | 否 | 简单设计器节点 JSON |
| `bpmn_xml` | `longtext` | 否 | BPMN XML，发布时必须存在 |
| `status` | `varchar(20)` | 是 | `draft`、`published`、`disabled` |
| `start_scope_type` | `varchar(20)` | 是 | `all`、`specified` |
| `flowable_deployment_id` | `varchar(128)` | 否 | Flowable 部署 ID |
| `flowable_process_definition_id` | `varchar(128)` | 否 | Flowable 流程定义 ID |
| `published_time` | `datetime` | 否 | 发布时间 |
| `remark` | `varchar(500)` | 否 | 备注 |

索引：

- 主键：`pk_wf_process_model(id)`。
- 唯一键：`uk_wfpm_tenant_key_version_active(tenant_id, process_key, version, delete_flag)`。
- 普通索引：`idx_wfpm_tenant_category_status(tenant_id, category_id, status, delete_flag)`。
- 普通索引：`idx_wfpm_tenant_form_status(tenant_id, form_definition_id, status, delete_flag)`。
- 普通索引：`idx_wfpm_flowable_definition(flowable_process_definition_id)`。

### `wf_process_start_permission`

流程发起权限表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_model_id` | `varchar(64)` | 是 | 流程模型版本 ID |
| `target_type` | `varchar(32)` | 是 | `user`、`role`、`depart`、`tenant` |
| `target_id` | `varchar(64)` | 是 | 授权目标 ID，`target_type=tenant` 时使用当前租户 ID |
| `status` | `varchar(20)` | 是 | `enabled`、`disabled` |

索引：

- 主键：`pk_wf_process_start_permission(id)`。
- 唯一键：`uk_wfpsp_tenant_model_target_active(tenant_id, process_model_id, target_type, target_id, delete_flag)`。
- 普通索引：`idx_wfpsp_tenant_target_status(tenant_id, target_type, target_id, status, delete_flag)`。
- 普通索引：`idx_wfpsp_tenant_model_status(tenant_id, process_model_id, status, delete_flag)`。

### `wf_process_node_config`

流程节点配置表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_model_id` | `varchar(64)` | 是 | 流程模型版本 ID |
| `node_id` | `varchar(100)` | 是 | 节点 ID，对应 BPMN `taskDefinitionKey` |
| `node_name` | `varchar(100)` | 是 | 节点名称 |
| `node_type` | `varchar(20)` | 是 | `start`、`approver`、`end` |
| `assignee_type` | `varchar(32)` | 否 | `user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select`、`starter` |
| `assignee_json` | `json` | 否 | 审批人配置 JSON |
| `allow_transfer` | `tinyint(1)` | 是 | 是否允许转办，`0` 否，`1` 是 |
| `allow_add_sign` | `tinyint(1)` | 是 | 是否允许加签，`0` 否，`1` 是 |
| `allow_return` | `tinyint(1)` | 是 | 是否允许退回，`0` 否，`1` 是 |
| `sort_order` | `int` | 否 | 节点顺序，默认 `0` |

索引：

- 主键：`pk_wf_process_node_config(id)`。
- 唯一键：`uk_wfpnc_tenant_model_node_active(tenant_id, process_model_id, node_id, delete_flag)`。
- 普通索引：`idx_wfpnc_tenant_model_sort(tenant_id, process_model_id, delete_flag, sort_order)`。

### `wf_field_permission`

节点字段权限表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_model_id` | `varchar(64)` | 是 | 流程模型版本 ID |
| `node_id` | `varchar(100)` | 是 | 节点 ID |
| `field_key` | `varchar(100)` | 是 | FormCreate 字段 `field` |
| `permission` | `varchar(20)` | 是 | `hidden`、`readonly`、`editable` |
| `required_flag` | `tinyint(1)` | 是 | 当前节点是否必填，`0` 否，`1` 是 |

索引：

- 主键：`pk_wf_field_permission(id)`。
- 唯一键：`uk_wffp_tenant_model_node_field_active(tenant_id, process_model_id, node_id, field_key, delete_flag)`。
- 普通索引：`idx_wffp_tenant_model_node(tenant_id, process_model_id, node_id, delete_flag)`。

### `wf_process_instance`

流程实例业务扩展表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_model_id` | `varchar(64)` | 是 | 流程模型版本 ID |
| `form_instance_id` | `varchar(64)` | 是 | 表单实例 ID |
| `flowable_process_instance_id` | `varchar(128)` | 是 | Flowable 流程实例 ID |
| `flowable_process_definition_id` | `varchar(128)` | 是 | Flowable 流程定义 ID |
| `form_definition_id` | `varchar(64)` | 是 | 表单定义版本 ID |
| `instance_no` | `varchar(64)` | 是 | 审批编号 |
| `instance_title` | `varchar(200)` | 是 | 实例标题 |
| `business_key` | `varchar(64)` | 是 | 业务 key，一期使用实例 ID |
| `starter_user_id` | `varchar(64)` | 是 | 发起人用户 ID |
| `starter_username` | `varchar(64)` | 否 | 发起人账号 |
| `starter_realname` | `varchar(100)` | 否 | 发起人姓名 |
| `status` | `varchar(20)` | 是 | `running`、`approved`、`rejected`、`terminated` |
| `start_time` | `datetime` | 是 | 发起时间 |
| `end_time` | `datetime` | 否 | 结束时间 |
| `current_task_names` | `varchar(500)` | 否 | 当前节点名称摘要 |
| `current_assignee_names` | `varchar(500)` | 否 | 当前处理人姓名摘要 |

索引：

- 主键：`pk_wf_process_instance(id)`。
- 唯一键：`uk_wfpi_tenant_instance_no_active(tenant_id, instance_no, delete_flag)`。
- 唯一键：`uk_wfpi_tenant_flowable_instance_active(tenant_id, flowable_process_instance_id, delete_flag)`。
- 唯一键：`uk_wfpi_tenant_form_instance_active(tenant_id, form_instance_id, delete_flag)`。
- 普通索引：`idx_wfpi_tenant_starter_status_time(tenant_id, starter_user_id, status, delete_flag, start_time)`。
- 普通索引：`idx_wfpi_tenant_model_status_time(tenant_id, process_model_id, status, delete_flag, start_time)`。
- 普通索引：`idx_wfpi_tenant_business_key(tenant_id, business_key, delete_flag)`。

### `wf_task`

任务业务扩展表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_instance_id` | `varchar(64)` | 是 | 审批实例 ID |
| `parent_task_id` | `varchar(64)` | 否 | 父任务扩展 ID，转办/加签任务关联原任务 |
| `flowable_task_id` | `varchar(128)` | 是 | Flowable 任务 ID |
| `node_id` | `varchar(100)` | 是 | 节点 ID |
| `task_name` | `varchar(100)` | 是 | 任务名称 |
| `task_type` | `varchar(20)` | 是 | `normal`、`transfer`、`add_sign` |
| `owner_user_id` | `varchar(64)` | 否 | 原处理人用户 ID |
| `owner_username` | `varchar(64)` | 否 | 原处理人账号 |
| `owner_realname` | `varchar(100)` | 否 | 原处理人姓名 |
| `assignee_user_id` | `varchar(64)` | 否 | 当前处理人用户 ID，候选任务提交时自动写入 |
| `assignee_username` | `varchar(64)` | 否 | 当前处理人账号 |
| `assignee_realname` | `varchar(100)` | 否 | 当前处理人姓名 |
| `status` | `varchar(20)` | 是 | `todo`、`done`、`transferred`、`returned`、`canceled` |
| `claim_time` | `datetime` | 否 | 自动认领时间 |
| `complete_time` | `datetime` | 否 | 完成时间 |

索引：

- 主键：`pk_wf_task(id)`。
- 唯一键：`uk_wft_tenant_flowable_task_active(tenant_id, flowable_task_id, delete_flag)`。
- 普通索引：`idx_wft_tenant_assignee_status_time(tenant_id, assignee_user_id, status, delete_flag, create_time)`。
- 普通索引：`idx_wft_tenant_instance_status(tenant_id, process_instance_id, status, delete_flag)`。
- 普通索引：`idx_wft_tenant_node_status(tenant_id, node_id, status, delete_flag)`。
- 普通索引：`idx_wft_tenant_parent_task(tenant_id, parent_task_id, delete_flag)`。

### `wf_task_candidate`

任务候选人表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `task_id` | `varchar(64)` | 是 | 任务扩展 ID |
| `flowable_task_id` | `varchar(128)` | 是 | Flowable 任务 ID |
| `candidate_user_id` | `varchar(64)` | 是 | 候选处理人用户 ID |
| `candidate_username` | `varchar(64)` | 否 | 候选处理人账号 |
| `candidate_realname` | `varchar(100)` | 否 | 候选处理人姓名 |
| `source_type` | `varchar(32)` | 是 | `user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select` |
| `source_id` | `varchar(64)` | 否 | 来源 ID，角色或部门等 |
| `status` | `varchar(20)` | 是 | `active`、`claimed`、`canceled` |

索引：

- 主键：`pk_wf_task_candidate(id)`。
- 唯一键：`uk_wftc_tenant_task_user_active(tenant_id, task_id, candidate_user_id, delete_flag)`。
- 普通索引：`idx_wftc_tenant_user_status(tenant_id, candidate_user_id, status, delete_flag)`。
- 普通索引：`idx_wftc_tenant_flowable_task(tenant_id, flowable_task_id, delete_flag)`。

### `wf_approval_record`

审批记录表。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `process_instance_id` | `varchar(64)` | 是 | 审批实例 ID |
| `task_id` | `varchar(64)` | 否 | 任务扩展 ID |
| `flowable_task_id` | `varchar(128)` | 否 | Flowable 任务 ID |
| `node_id` | `varchar(100)` | 否 | 节点 ID |
| `node_name` | `varchar(100)` | 否 | 节点名称 |
| `action` | `varchar(32)` | 是 | `start`、`approve`、`reject`、`return`、`transfer`、`add_sign`、`system_complete` |
| `operator_user_id` | `varchar(64)` | 是 | 操作人用户 ID |
| `operator_username` | `varchar(64)` | 否 | 操作人账号 |
| `operator_realname` | `varchar(100)` | 否 | 操作人姓名 |
| `target_user_id` | `varchar(64)` | 否 | 目标用户 ID，转办/加签使用 |
| `target_username` | `varchar(64)` | 否 | 目标账号 |
| `target_realname` | `varchar(100)` | 否 | 目标姓名 |
| `target_node_id` | `varchar(100)` | 否 | 目标节点 ID，退回使用 |
| `target_node_name` | `varchar(100)` | 否 | 目标节点名称，退回使用 |
| `comment` | `varchar(1000)` | 否 | 审批意见 |
| `form_data_snapshot_json` | `json` | 否 | 操作时表单数据快照 |
| `operate_time` | `datetime` | 是 | 操作时间 |

索引：

- 主键：`pk_wf_approval_record(id)`。
- 普通索引：`idx_wfar_tenant_instance_time(tenant_id, process_instance_id, delete_flag, operate_time)`。
- 普通索引：`idx_wfar_tenant_operator_time(tenant_id, operator_user_id, delete_flag, operate_time)`。
- 普通索引：`idx_wfar_tenant_task(tenant_id, task_id, delete_flag)`。

### 一期枚举定稿

| 枚举 | 取值 |
| --- | --- |
| 流程分类状态 | `enabled`、`disabled` |
| 表单状态 | `draft`、`published`、`disabled` |
| 表单实例状态 | `draft`、`active`、`archived` |
| 流程模型状态 | `draft`、`published`、`disabled` |
| 设计器类型 | `simple`、`bpmn` |
| 发起范围 | `all`、`specified` |
| 发起权限目标类型 | `user`、`role`、`depart`、`tenant` |
| 发起权限状态 | `enabled`、`disabled` |
| 节点类型 | `start`、`approver`、`end` |
| 审批人类型 | `user`、`role`、`depart_leader`、`depart_role`、`starter_supervisor`、`starter_select`、`starter` |
| 字段权限 | `hidden`、`readonly`、`editable` |
| 流程实例状态 | `running`、`approved`、`rejected`、`terminated` |
| 任务状态 | `todo`、`done`、`transferred`、`returned`、`canceled` |
| 任务类型 | `normal`、`transfer`、`add_sign` |
| 候选人状态 | `active`、`claimed`、`canceled` |
| 审批动作 | `start`、`approve`、`reject`、`return`、`transfer`、`add_sign`、`system_complete` |

## 状态流转

### 流程模型状态

```text
draft -> published
published -> disabled
disabled -> published
published -> draft copy
```

规则：

- 已发布版本不直接编辑。
- 禁用后不可发起新申请，不影响已发起实例继续流转。

### 表单定义状态

```text
draft -> published
published -> disabled
published -> draft copy
```

规则：

- 已发布表单版本不直接覆盖。
- 流程实例使用发起时的表单快照。

### 流程实例状态

```text
running -> approved
running -> rejected
running -> terminated
```

一期不支持撤回，因此不设计 `withdrawn` 状态。

### 任务状态

```text
todo -> done
todo -> transferred
todo -> returned
todo -> canceled
```

动作说明：

- `approve`：完成当前任务，流转到下一节点；若流程结束，实例变为 `approved`。
- `reject`：结束流程，实例变为 `rejected`。
- `return`：退回到指定目标节点，一期通过 Flowable change-state 跳转到目标用户任务节点。
- `transfer`：当前任务转给其他用户处理。
- `add_sign`：当前任务加签给其他用户处理；一期采用当前节点内加签子任务，加签人处理完成后回到原处理人。

## 接口清单

### 管理端接口

接口前缀：`/workflow/admin`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/category/page` | 分页查询流程分类 |
| `POST` | `/category/save` | 新增或编辑流程分类 |
| `POST` | `/category/delete` | 删除流程分类 |
| `POST` | `/form/page` | 分页查询表单 |
| `POST` | `/form/getById` | 查询表单详情 |
| `POST` | `/form/save` | 新增或编辑表单草稿 |
| `POST` | `/form/publish` | 发布表单版本，`id` 放在 body |
| `POST` | `/form/copy-as-draft` | 复制表单为下一版本草稿，`id` 放在 body |
| `POST` | `/form/disable` | 禁用表单版本，`id` 放在 body |
| `POST` | `/process/page` | 分页查询流程模型 |
| `POST` | `/process/getById` | 查询流程模型详情 |
| `POST` | `/process/save` | 新增或编辑流程草稿 |
| `POST` | `/process/publish` | 发布流程版本并部署到 Flowable，`id` 放在 body |
| `POST` | `/process/copy-as-draft` | 复制流程为下一版本草稿，`id` 放在 body |
| `POST` | `/node/page` | 分页查询节点配置 |
| `POST` | `/node/save` | 新增或编辑节点配置 |
| `POST` | `/node/delete` | 删除节点配置 |
| `POST` | `/start-permission/page` | 分页查询流程发起权限 |
| `POST` | `/start-permission/save` | 新增或编辑流程发起权限 |
| `POST` | `/start-permission/delete` | 删除流程发起权限 |
| `POST` | `/field-permission/page` | 分页查询字段权限 |
| `POST` | `/field-permission/save` | 新增或编辑字段权限 |
| `POST` | `/field-permission/delete` | 删除字段权限 |

### 管理配置层规则

- 流程分类、表单定义、流程模型、节点配置、字段权限和发起权限保存时必须校验租户、必填字段、枚举值和唯一键。
- 表单定义发布后不可直接修改或删除；如需调整，复制为下一版本草稿。
- 流程模型发布后不可直接修改或删除；节点配置和字段权限也随流程版本冻结。
- 表单发布必须校验 FormCreate `schema_json` 合法；流程发布必须校验 BPMN XML、绑定已发布表单版本、至少一个审批节点。
- `start_scope_type=specified` 的流程发布前必须配置发起权限。
- 流程发布时通过 `IFlowableService` 部署 BPMN XML，并回写 `flowable_deployment_id`、`flowable_process_definition_id`。
- 复制流程为下一版本草稿时，同时复制节点配置、字段权限和发起权限，并清空 Flowable 部署标识。

### 用户端接口

接口前缀：`/workflow`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/available/page` | 查询可发起流程，按当前用户发起权限过滤 |
| `POST` | `/start/form` | 获取发起表单，`processModelId` 放在 body |
| `POST` | `/start` | 发起申请，写入业务实例并启动 Flowable |
| `POST` | `/todo/page` | 我的待办，包含直接处理人与有效候选任务 |
| `POST` | `/done/page` | 我的已办 |
| `POST` | `/started/page` | 我发起的，按当前用户和租户过滤 |
| `POST` | `/instance/detail` | 审批详情，`id` 放在 body，返回实例、表单快照、当前任务和审批记录 |
| `POST` | `/instance/records` | 审批记录，`id` 放在 body，按操作时间升序返回 |
| `POST` | `/task/form` | 查询任务表单、字段权限、动作权限和可退回节点，`taskId` 放在 body |
| `POST` | `/task/approve` | 通过，`taskId` 放在 body，完成 Flowable 用户任务并同步后续待办 |
| `POST` | `/task/reject` | 拒绝，`taskId` 放在 body，终止 Flowable 流程实例并结束业务实例 |
| `POST` | `/task/return` | 退回，`taskId`、`targetNodeId` 放在 body |
| `POST` | `/task/transfer` | 转办，`taskId`、`targetUserId` 放在 body |
| `POST` | `/task/add-sign` | 加签，`taskId`、`targetUserId` 放在 body |

## 页面清单

### 管理端页面

建议目录：`frontend/src/views/workflow/admin`

| 页面 | 路由建议 | 说明 |
| --- | --- | --- |
| 流程分类 | `/workflow/category` | 分类 CRUD |
| 表单管理 | `/workflow/form` | FormCreate 表单设计、预览、发布 |
| 流程设计 | `/workflow/process` | 流程列表、简单设计器、BPMN 设计器、发布 |
| 字段权限 | 集成在流程设计详情中 | 节点字段权限配置 |

### 用户端页面

建议目录：`frontend/src/views/workflow/workbench`

| 页面 | 路由建议 | 说明 |
| --- | --- | --- |
| 发起申请 | `/workflow/start` | 选择流程并填写表单 |
| 我的待办 | `/workflow/todo` | 当前用户待处理任务 |
| 我的已办 | `/workflow/done` | 当前用户已处理任务 |
| 我发起的 | `/workflow/started` | 当前用户发起的实例 |
| 审批详情 | `/workflow/detail/:id` | 表单详情、流程图、审批记录、操作区 |

## Flowable 集成方式

### 集成决策

- Flowable 版本定为 `7.2.0`，匹配当前后端 Java 17、Spring Boot 3.2.5、Spring Framework 6 技术基线。
- 后端依赖只引入 `org.flowable:flowable-spring-boot-starter-process`，一期只使用 BPMN Process Engine，不引入 Flowable UI、REST Starter、Form Engine、DMN、CMMN。
- `ACT_*` 引擎表由 Flowable 通过 `flowable.database-schema-update=true` 自动管理，不写入 `sql/建表脚本.sql`。
- 系统业务表仍以 `wf_*` 为准，由 `sql/建表脚本.sql` 管理，并同步 `docs/03-database/`。
- 前端表单继续使用 FormCreate；表单定义和表单实例使用 `wf_form_definition`、`wf_form_instance`，不使用 Flowable Form Engine。

### 发布流程

1. 管理员保存流程草稿。
2. 后端校验流程模型和节点配置。
3. 简单设计器模型转换为 BPMN XML；BPMN 设计器直接读取 BPMN XML。
4. 后端调用 Flowable `RepositoryService` 部署 BPMN XML，并写入当前系统 `tenant_id` 作为 Flowable tenant 标识。
5. 保存 `flowable_deployment_id` 和 `flowable_process_definition_id` 到 `wf_process_model`。
6. 流程状态变为 `published`。

发布校验规则：

- 简单设计器的 `node_json` 必须能转换成合法 BPMN XML。
- BPMN 设计器一期只允许开始事件、结束事件、用户任务和顺序流等白名单元素。
- 每个用户任务节点都必须能找到对应的 `wf_process_node_config`。
- 字段权限中的 `field_key` 必须能在绑定表单版本的 FormCreate `schema_json` 中找到。
- 已发布流程模型和字段权限不直接修改，调整时复制新版本。

### 发起流程

1. 用户选择已发布流程。
2. 后端读取绑定的表单版本、FormCreate schema/option 和字段权限。
3. 用户提交表单数据。
4. 后端校验表单数据和发起权限；`start_scope_type=specified` 时必须命中 `wf_process_start_permission`。
5. 后端写入 `wf_form_instance`，保存表单数据、schema 快照和 option 快照。
6. 后端生成 `instance_no`，写入 `wf_process_instance` 初始记录，并关联 `form_instance_id`。
7. 后端回写 `wf_form_instance.process_instance_id`。
8. 后端调用 Flowable `RuntimeService.startProcessInstanceById(...)`。
9. 后端同步当前 Flowable 活动任务摘要到 `wf_task`，并回写 `current_task_names`、`current_assignee_names`。
10. 后端写入 `wf_approval_record(action=start)`。

规则：

- 发起流程优先使用已保存的 `flowable_process_definition_id`，避免不同租户或不同版本使用相同 `process_key` 时误启动。
- Flowable 回调、监听器或异步任务回写业务表时，必须从 `wf_process_instance` 或流程变量中还原系统 `tenant_id`，不能依赖当前登录上下文。
- 当前发起运行时骨架提供 `POST /workflow/start/form` 和 `POST /workflow/start`，参数统一放在 body。
- 发起权限一期支持 `user`、`role`、`depart`、`tenant` 四类目标。
- 发起阶段先同步 Flowable 活动任务的基础摘要；候选人展开、多人候选自动认领、任务提交时字段权限清洗在任务办理骨架中实现。

### 发起入口与我发起的

- `POST /workflow/available/page` 只返回当前用户有发起权限的已发布流程。
- 可发起流程过滤规则：`start_scope_type=all` 对当前租户用户可见；`start_scope_type=specified` 必须命中 `wf_process_start_permission`，支持 `user`、`role`、`depart`、`tenant`。
- 可发起流程列表返回流程名称、流程版本、设计器类型、绑定表单名称、表单版本、发布时间等摘要。
- `POST /workflow/started/page` 只返回当前用户作为 `starter_user_id` 发起的审批实例。
- 我发起的列表支持按实例状态和标题筛选，按发起时间倒序返回实例编号、标题、流程名称、表单名称、当前节点和当前处理人摘要。

### 任务处理

1. 用户打开待办。
2. 后端校验任务归属、租户、状态。
3. 后端按节点字段权限清洗和保存表单数据。
4. 多人候选任务由候选人提交时自动认领，其它候选记录改为 `canceled`。
5. 通过时后端调用 Flowable `TaskService.complete(...)` 完成当前用户任务。
6. 拒绝时后端调用 Flowable `RuntimeService.deleteProcessInstance(...)` 终止流程实例，并将业务实例标记为 `rejected`。
7. 后端写入审批记录。
8. 后端同步任务扩展表、候选人状态、实例状态、当前节点摘要和当前处理人摘要。

当前任务办理运行时骨架：

- 提供 `POST /workflow/todo/page`、`POST /workflow/done/page`、`POST /workflow/task/form`。
- 提供 `POST /workflow/task/approve`、`POST /workflow/task/reject`、`POST /workflow/task/transfer`、`POST /workflow/task/return`、`POST /workflow/task/add-sign`，`taskId` 统一放在 body。
- 待办查询同时覆盖直接处理人任务和 `wf_task_candidate(status=active)` 候选任务。
- 已办查询返回当前用户直接完成或候选认领后完成的任务。
- 任务表单返回 FormCreate schema/option 快照、当前表单数据、当前节点字段权限、动作权限和可退回节点列表。
- 提交表单时仅 `editable` 字段允许覆盖 `wf_form_instance.form_data_json`；`readonly` 和 `hidden` 字段即使被提交也忽略。
- 完成任务后如果 Flowable 流程实例已结束，则业务实例标记为 `approved` 并归档表单实例。
- 拒绝动作直接结束流程实例，取消同一流程实例下其它待办任务和有效候选记录。
- 转办动作校验 `allow_transfer=1`，将当前 Flowable task assignee 改为目标用户，并更新当前 `wf_task` 的处理人，`owner_user_id` 记录原处理人。
- 退回动作校验 `allow_return=1`，目标节点必须来自 `task/form.returnNodes`，一期只允许退回到同一流程模型中顺序在当前节点之前的审批节点；通过 Flowable change-state 从当前节点跳转到目标节点，当前任务标记为 `returned`，再同步目标节点待办。
- 加签动作校验 `allow_add_sign=1`，创建 `task_type=add_sign` 的本地子任务并临时挂起原任务；加签子任务只允许通过完成，不允许拒绝、退回、转办或再次加签；加签任务通过后恢复原任务继续处理。

任务表单动作配置返回：

- `actionPermissions.allowApprove`：是否允许通过。普通任务和加签子任务均允许。
- `actionPermissions.allowReject`：是否允许拒绝。加签子任务不允许拒绝整个流程。
- `actionPermissions.allowTransfer`：是否允许转办，由当前节点 `allow_transfer` 控制，加签子任务固定不允许。
- `actionPermissions.allowAddSign`：是否允许加签，由当前节点 `allow_add_sign` 控制，加签子任务固定不允许。
- `actionPermissions.allowReturn`：是否允许退回，由当前节点 `allow_return` 且 `returnNodes` 非空共同决定。
- `returnNodes`：可退回节点列表，包含 `nodeId`、`nodeName`、`nodeType`、`sortOrder`，前端退回弹窗只能选择此列表中的节点。

### 审批详情与记录

- `POST /workflow/instance/detail` 返回流程实例扩展信息、表单实例快照、当前待办任务和审批记录。
- `POST /workflow/instance/records` 返回审批记录时间线，按 `operate_time`、`create_time` 升序排列。
- 审批详情和审批记录必须校验租户与访问权；允许查看的人包括发起人、当前或历史任务处理人、任务候选人、审批记录操作人。
- 审批详情默认读取 `wf_form_instance` 中的 schema/option/data 快照，不读取最新表单定义，避免历史实例被后续表单版本变更影响。
- 当前任务摘要以 `wf_task(status=todo)` 为准；流程已结束时当前任务列表为空。

动作落库规则：

- 转办任务必须记录 `owner_user_id`，并将 `task_type` 标记为 `transfer`，审批记录写入 `target_user_id`。
- 加签任务必须记录 `parent_task_id`，并将 `task_type` 标记为 `add_sign`，审批记录写入 `target_user_id`。
- 退回记录必须写入 `target_node_id` 和 `target_node_name`，便于审批记录和详情页准确展示。

### 审批人解析

一期支持：

- 指定人员
- 指定角色
- 部门负责人
- 部门岗位
- 发起人直属上级
- 发起人自选
- 发起人本人

解析规则：

- 解析结果必须落在当前租户内。
- 指定用户按 `sys_user_tenant(status=1)` 校验当前租户有效成员，并过滤冻结或已删除用户。
- 指定角色通过 `sys_user_role` 解析当前租户角色成员。
- 部门负责人不再通过 `sys_user.user_identity + depart_ids` 推断，必须通过 `sys_user_depart.depart_leader_flag` 解析；同一部门只允许一个负责人。
- 部门岗位一期复用部门角色和部门角色人员关系解析，不依赖 `sys_user.post`。
- 发起人直属上级必须通过 `sys_user_depart.supervisor_user_id` 解析，不能用部门负责人替代。
- 发起人自选必须由流程设计阶段配置可选范围，发起页面只允许从范围内选择，后端提交时再次校验。
- 审批人组织关系、配置 JSON、空审批人策略和实现顺序以 [审批中心组织关系增强设计](approval-center-org-relation-design.md) 为准。
- 角色、部门负责人、部门岗位等解析出多人时，一期采用候选待办策略：候选人都能在“我的待办”看到任务，首个提交审批的人成为当前处理人并自动认领任务，其它候选记录取消。
- 解析结果为单人时，写入 `wf_task.assignee_user_id`、`assignee_username`、`assignee_realname`，并同步 Flowable task assignee。
- 解析结果为多人时，`wf_task.assignee_user_id` 为空，候选人写入 `wf_task_candidate(status=active)`，并同步 Flowable candidate user。
- 找不到审批人时，发布流程或发起流程应失败，不允许生成悬空待办。

### 字段权限

渲染详情时：

- `hidden`：前端不展示，后端不接收该字段更新。
- `readonly`：前端展示只读，后端不允许修改该字段。
- `editable`：前端可编辑，后端允许保存。
- `required_flag=1`：当前节点提交时必填。

保存表单数据时：

- 后端以当前任务的 `node_id` 查询 `wf_field_permission`，不能只相信前端传来的 disabled/readonly 状态。
- `hidden` 字段从提交数据中剔除。
- `readonly` 字段如果提交值和实例当前值不同，后端忽略提交值，保留实例当前值。
- `editable` 字段才允许覆盖 `wf_form_instance.form_data_json`。
- 未配置字段权限时，一期默认按只读处理；发起节点默认可编辑。

## 简单设计器节点 JSON 初稿

```json
{
  "nodes": [
    {
      "id": "start",
      "type": "start",
      "name": "发起人"
    },
    {
      "id": "approve_1",
      "type": "approver",
      "name": "部门负责人审批",
      "assigneeType": "depart_leader",
      "assigneeConfig": {},
      "allowTransfer": true,
      "allowAddSign": true,
      "allowReturn": true
    },
    {
      "id": "end",
      "type": "end",
      "name": "结束"
    }
  ],
  "edges": [
    { "source": "start", "target": "approve_1" },
    { "source": "approve_1", "target": "end" }
  ]
}
```

一期仅支持顺序结构。后续条件分支、会签、或签再扩展 `node.type` 和 `edges.condition`。

## 后端包结构建议

```text
backend/src/main/java/com/lawoffice/workflow/
  controller/
  entity/
  mapper/
  req/
  service/
  service/impl/
  vo/
  dto/
  enums/
  converter/
```

协作服务建议：

- `IFormService`
- `IProcessModelService`
- `IRuntimeService`
- `ITaskService`
- `IOperationRecordService`
- `IFlowableService`
- `IAssigneeResolveService`
- `IFieldPermissionService`
- `IStartPermissionService`

## 前端目录建议

```text
frontend/src/views/workflow/
  admin/
    category/
    form/
    process/
  workbench/
    start/
    todo/
    done/
    started/
    detail/
  components/
    FormCreateRenderer.vue
    SimpleFlowDesigner.vue
    BpmnFlowDesigner.vue
    OperationRecordTimeline.vue
    ActionPanel.vue
  hooks/
  types.ts
```

API 文件建议：

```text
frontend/src/api/workflow.ts
```

## 实现计划

### 阶段 1：设计落地

- 完成本设计文档。
- 确认表字段、状态枚举、接口路径。
- 确认依赖版本和兼容性。

### 阶段 2：数据库和依赖

- 新增 Flowable 后端依赖。
- 新增审批中心业务表 SQL。
- Flowable `ACT_*` 表采用 `flowable.database-schema-update=true` 自动初始化和升级。
- 新增菜单和权限初始化 SQL。
- 同步 `docs/03-database/`、SQL 字段注释、后端 enum、前端常量和接口文档。

### 阶段 3：基础管理能力

- 流程分类 CRUD。
- 表单管理 CRUD。
- FormCreate 表单设计和预览。
- 表单发布版本。

### 阶段 4：流程设计和发布

- 简单流程设计器。
- BPMN 设计器基础接入。
- 简单节点 JSON 转 BPMN XML。
- 流程校验和发布。

### 阶段 5：审批运行闭环

- 发起申请。
- 我的待办。
- 我的已办。
- 我发起的。
- 审批详情。
- 审批记录。

### 阶段 6：审批动作和字段权限

- 通过。
- 拒绝。
- 退回。
- 转办。
- 加签。
- 字段权限渲染和后端清洗。

### 阶段 7：验证和收口

- 后端编译。
- 前端类型检查。
- 关键流程人工验证。
- 文档同步。

## 后续完善清单

以下内容发现后先记录，不进入一期：

- 会签、或签、条件分支。
- 抄送、撤回、催办、超时提醒。
- 审批消息通知。
- 审批附件和业务文档中心联动。
- 审批模板复制。
- 流程版本迁移。
- 流程实例管理员干预。
- 审批统计和报表。
- 移动端适配。
- 打印、导出、归档。
- 流程图高亮当前节点。
- BPMN XML 安全校验和白名单限制。

## 待确认事项

- 审批附件是否进入一期，本文建议先不进入。
- 流程发布权限和审批操作权限的权限码命名。
