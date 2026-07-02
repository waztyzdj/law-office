# 审批中心接口

## 流程归档

流程归档接口统一放在管理端路径 `/workflow/admin/archive`，请求参数使用 `POST + RequestBody`。接口需要登录态和当前租户上下文。

权限：

- `workflow:archive:view`：查看归档树、已归档列表、未归档列表、归档详情和下载归档材料包。
- `workflow:archive:manage`：手动归档和批量归档已结束且未归档流程。

接口：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/workflow/admin/archive/tree` | `workflow:archive:view` | 查询流程归档左侧流程树。 |
| POST | `/workflow/admin/archive/archived-page` | `workflow:archive:view` | 分页查询已归档流程实例。 |
| POST | `/workflow/admin/archive/unarchived-page` | `workflow:archive:view` | 分页查询已结束且未归档流程实例。 |
| POST | `/workflow/admin/archive/detail` | `workflow:archive:view` | 查询流程归档详情，已归档实例和已结束未归档实例可查看。 |
| POST | `/workflow/admin/archive/diagram` | `workflow:archive:view` | 查询流程归档详情页流程图，只按归档查看范围放行，不复用普通运行时实例访问权。 |
| POST | `/workflow/admin/archive/attachment/list` | `workflow:archive:view` | 查询流程归档详情页附件，只读展示。 |
| POST | `/workflow/admin/archive/attachment/download` | `workflow:archive:view` | 下载单个流程归档附件，请求体使用 `id` 传审批附件 ID。 |
| POST | `/workflow/admin/archive/attachment/download-all` | `workflow:archive:view` | 打包下载流程归档详情页附件，不包含审批单 PDF。 |
| POST | `/workflow/admin/archive/archive` | `workflow:archive:manage` | 手动归档单个已结束且未归档流程实例。 |
| POST | `/workflow/admin/archive/batch-archive` | `workflow:archive:manage` | 批量归档请求中明确勾选的已结束且未归档流程实例。 |
| POST | `/workflow/admin/archive/batch-archive-by-query` | `workflow:archive:manage` | 按未归档列表查询条件批量归档已结束且未归档流程实例。 |
| POST | `/workflow/admin/archive/download` | `workflow:archive:view` | 下载已归档流程材料包。 |
| POST | `/workflow/admin/monitor/archive` | `workflow:monitor:manage` | 流程监控入口手动归档单个已结束且未归档流程实例，归档来源记录为流程监控。 |
| POST | `/workflow/admin/monitor/batch-archive` | `workflow:monitor:manage` | 流程监控入口批量归档已勾选的已结束且未归档流程实例，归档来源记录为流程监控。 |
| POST | `/workflow/admin/monitor/batch-archive-by-query` | `workflow:monitor:manage` | 流程监控入口按当前监控列表查询条件批量归档已结束且未归档流程实例。 |

`archive` 请求体：

```json
{
  "processInstanceId": "流程实例ID",
  "archiveReason": "归档说明，可选"
}
```

`batch-archive` 请求体：

```json
{
  "processInstanceIds": ["流程实例ID"],
  "archiveReason": "归档说明，可选"
}
```

`batch-archive-by-query` 请求体：

```json
{
  "categoryId": "流程分类ID，可选",
  "processKey": "流程定义编码，可选",
  "instanceTitle": "流程标题，可选",
  "instanceNo": "流水号，可选",
  "starterRealname": "发起人姓名，可选",
  "instanceStatus": "approved/rejected/terminated，可选",
  "processStartTimeGe": "流程发起开始时间，可选，yyyy-MM-dd HH:mm:ss",
  "processStartTimeLe": "流程发起结束时间，可选，yyyy-MM-dd HH:mm:ss",
  "processEndTimeGe": "流程结束开始时间，可选，yyyy-MM-dd HH:mm:ss",
  "processEndTimeLe": "流程结束结束时间，可选，yyyy-MM-dd HH:mm:ss",
  "archiveReason": "归档说明，可选"
}
```

流程监控入口的 `batch-archive-by-query` 使用流程监控列表查询条件，请求体字段与 `/workflow/admin/monitor/page` 保持一致，例如：

```json
{
  "categoryId": "流程分类ID，可选",
  "processKey": "流程定义编码，可选",
  "instanceTitle": "流程标题，可选",
  "instanceNo": "流水号，可选",
  "starterRealname": "发起人姓名，可选",
  "status": "approved/rejected/terminated，可选",
  "startTimeGe": "发起开始时间，可选，yyyy-MM-dd HH:mm:ss",
  "startTimeLe": "发起结束时间，可选，yyyy-MM-dd HH:mm:ss",
  "updateTimeGe": "更新时间开始，可选，yyyy-MM-dd HH:mm:ss",
  "updateTimeLe": "更新时间结束，可选，yyyy-MM-dd HH:mm:ss",
  "archiveReason": "归档说明，可选"
}
```

`download` 请求体：

```json
{
  "processInstanceId": "流程实例ID"
}
```

边界：

- 正常结束流程由运行时自动归档，不需要人工权限。
- 手动归档只允许已通过、已拒绝、已终止且尚未归档的流程实例；撤回、运行中、草稿不允许归档。
- 按查询条件批量归档复用未归档列表过滤条件，单次最多处理 1000 条，超过时需要缩小查询范围。
- 下载必须先存在有效归档记录；归档下载复用审批材料包生成能力，但由归档查看权限控制。
