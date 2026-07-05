# 工作台接口

## 权限

- `home:workbench:view`：访问工作台页面、查询当前用户工作台布局、保存个人布局和查询已授权卡片数据。
- `home:card:todo`：查看我的待办卡片。
- `home:card:cc`：查看我的抄送卡片。
- `home:card:quick-entry`：查看快捷入口卡片。
- `home:card:message`：查看我的消息卡片。
- `home:card:favorite`：查看我的收藏卡片。
- `home:card:metrics`：查看指标概览卡片。
- `home:card:manage`：管理工作台卡片配置和系统默认快捷入口。

说明：

- 卡片未配置 `permissionCode` 时，拥有 `home:workbench:view` 的用户默认可见。
- 卡片配置了 `permissionCode` 时，用户必须同时拥有 `home:workbench:view` 和对应卡片权限。
- 前端隐藏卡片不等于后端放行；卡片数据接口仍必须校验当前用户、当前租户、卡片启用状态、卡片权限和业务数据访问权。

## 用户端接口

工作台用户端接口统一使用 `/home/workbench` 前缀，要求登录态和当前租户上下文，请求参数使用 `POST + RequestBody`。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/home/workbench/layout` | `home:workbench:view` | 查询当前用户可见卡片、默认布局和用户个性化布局。 |
| POST | `/home/workbench/layout/save` | `home:workbench:view` | 保存当前用户卡片显隐、栅格位置、尺寸和用户级配置。 |
| POST | `/home/workbench/layout/reset` | `home:workbench:view` | 恢复当前用户默认布局。 |
| POST | `/home/workbench/card/data` | `home:workbench:view` + 卡片权限 | 查询单个卡片数据。 |
| POST | `/home/workbench/quick-entry/list` | `home:workbench:view` + `home:card:quick-entry` | 查询当前用户快捷入口。 |
| POST | `/home/workbench/quick-entry/save` | `home:workbench:view` + `home:card:quick-entry` | 保存当前用户个人快捷入口。 |
| POST | `/home/workbench/quick-entry/delete` | `home:workbench:view` + `home:card:quick-entry` | 删除当前用户个人快捷入口。 |

### 查询工作台布局

请求体：

```json
{}
```

响应数据：

```json
{
  "cards": [
    {
      "cardCode": "todo",
      "cardName": "我的待办",
      "componentKey": "WorkbenchTodoCard",
      "permissionCode": "home:card:todo",
      "visible": true,
      "sortNo": 10,
      "size": "large",
      "refreshInterval": 60,
      "config": {
        "limit": 8
      },
      "systemVisible": true,
      "userCustomized": false
    }
  ],
  "hiddenCards": [
    {
      "cardCode": "metrics",
      "cardName": "指标概览",
      "componentKey": "WorkbenchMetricsCard",
      "permissionCode": "home:card:metrics",
      "visible": false,
      "sortNo": 50,
      "size": "large"
    }
  ]
}
```

字段说明：

- `cards`：当前用户最终展示的卡片。
- `hiddenCards`：当前用户有权查看但被系统默认隐藏或用户个性化隐藏的卡片，可在个性化设置中重新打开。
- `sortNo`：服务端内部排序值，用于响应兜底排序和兼容旧布局；用户端保存布局时不提交该字段。
- `systemVisible`：系统默认是否显示。
- `userCustomized`：当前卡片是否存在用户个性化配置。

### 保存工作台布局

请求体：

```json
{
  "cards": [
    {
      "cardCode": "todo",
      "visible": true,
      "size": "large",
      "gridX": 0,
      "gridY": 0,
      "gridW": 6,
      "gridH": 4,
      "config": {
        "limit": 8
      }
    },
    {
      "cardCode": "metrics",
      "visible": false,
      "size": "large",
      "gridX": 6,
      "gridY": 0,
      "gridW": 6,
      "gridH": 4
    }
  ]
}
```

约束：

- 只能保存当前用户有权查看且系统已启用的卡片。
- `cardCode` 必须来自有效的工作台卡片配置。
- `size` 取值为 `small`、`medium`、`large`、`full`。
- `gridX`、`gridY` 从 `0` 开始，表示卡片在工作台 12 列栅格中的横向和纵向位置。
- `gridW` 表示卡片宽度，按 12 列栅格计；`gridH` 表示卡片高度，按工作台行高单位计。
- 卡片排序由后端根据 `gridY`、`gridX` 自动派生，前端不需要也不允许提交手工排序值。
- 后端按当前用户和当前租户覆盖保存，不允许前端传入 `userId` 或 `tenantId`。

### 恢复默认布局

请求体：

```json
{}
```

说明：

- 清理当前用户在当前租户下的卡片布局个性化记录。
- 不影响管理员卡片配置和个人快捷入口。

### 查询卡片数据

请求体：

```json
{
  "cardCode": "todo",
  "limit": 8,
  "timeRange": "week",
  "params": {
    "todoType": "workflow"
  }
}
```

响应数据：

```json
{
  "cardCode": "todo",
  "summary": {
    "total": 12,
    "urgent": 2
  },
  "items": [
    {
      "id": "taskId",
      "title": "合同审批",
      "type": "workflow",
      "status": "todo",
      "priority": "normal",
      "occurTime": "2026-07-03 10:00:00",
      "targetType": "route",
      "targetPath": "/workflow/todo",
      "bizId": "taskId"
    }
  ]
}
```

约束：

- `cardCode` 必填。
- `limit` 默认由卡片配置决定。对列表型卡片表示工作台卡片内每页显示行数，不表示业务数据只查询或只返回该数量。
- `timeRange` 第一版支持 `today`、`week`、`month`，卡片不需要时间范围时可忽略。
- 后端按卡片类型路由到对应卡片数据 Provider，不允许前端指定任意 Provider 或 SQL。

一期已接入 Provider：

- `todo`：复用审批中心 `pageTodo` 和 `pageDone`，返回当前用户待办、已办总数和明细。
- `cc`：复用审批中心 `pageCc`，返回当前用户未读抄送、已读抄送总数和明细，均按到达时间倒序排列。
- `quick-entry`：复用工作台快捷入口服务，返回当前用户可访问的系统默认入口和个人入口。
- `message`：复用消息中心收件箱，返回当前用户未读消息、已读消息总数和明细；存在未读催办或未读超时提醒时，额外返回对应动态 Tab 统计和明细。
- `favorite`：返回当前用户收藏文件明细，并展开收藏文件夹下的真实文件；卡片不展示文件夹本身，按收藏时间倒序排列。
- `metrics`：聚合我的待办、我的抄送和我的消息数量，作为工作台轻量指标，不替代统计报表中心。

### 查询快捷入口

请求体：

```json
{
  "includeSystem": true
}
```

响应数据：

```json
{
  "entries": [
    {
      "id": "entryId",
      "entryCode": "workflow-start",
      "entryName": "发起审批",
      "entryType": "menu",
      "menuId": "menuId",
      "path": "/workflow/runtime/start",
      "permissionCode": "",
      "icon": "lucide:file-plus",
      "sortNo": 10,
      "ownerType": "system"
    }
  ]
}
```

约束：

- 系统默认入口和用户个人入口合并返回。
- 入口配置了 `permissionCode` 或 `menuId` 时，必须过滤当前用户无权访问的入口。

### 保存个人快捷入口

请求体：

```json
{
  "id": "entryId，可选",
  "entryName": "文档中心",
  "entryType": "menu",
  "menuId": "menuId",
  "path": "/document/files",
  "icon": "lucide:folder",
  "sortNo": 20,
  "config": {}
}
```

约束：

- 用户端只能维护 `ownerType=user` 且 `ownerUserId` 为当前用户的快捷入口。
- `entryType=menu` 时必须选择当前用户有权访问的菜单。
- `entryType=link` 时必须填写以 `http://` 或 `https://` 开头的外部链接，前端以新窗口打开。

### 删除个人快捷入口

请求体：

```json
{
  "id": "entryId"
}
```

说明：

- 只能删除当前用户自己的快捷入口。
- 系统默认入口不能通过用户端删除，只能在个性化设置中隐藏或由管理员停用。

## 管理端接口

工作台管理端接口统一使用 `/home/admin/workbench` 前缀，请求参数使用 `POST + RequestBody`。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/home/admin/workbench/card/page` | `home:card:manage` | 分页查询工作台卡片配置。 |
| POST | `/home/admin/workbench/card/detail` | `home:card:manage` | 查询工作台卡片配置详情。 |
| POST | `/home/admin/workbench/card/save` | `home:card:manage` | 新增或编辑工作台卡片配置。 |
| POST | `/home/admin/workbench/card/status` | `home:card:manage` | 启用或停用工作台卡片。 |
| POST | `/home/admin/workbench/card/sort` | `home:card:manage` | 调整工作台卡片默认排序。 |
| POST | `/home/admin/workbench/quick-entry/page` | `home:card:manage` | 分页查询系统默认快捷入口。 |
| POST | `/home/admin/workbench/quick-entry/save` | `home:card:manage` | 新增或编辑系统默认快捷入口。 |
| POST | `/home/admin/workbench/quick-entry/status` | `home:card:manage` | 启用或停用系统默认快捷入口。 |

### 分页查询卡片配置

请求体：

```json
{
  "cardName": "待办",
  "cardCode": "todo",
  "componentKey": "WorkbenchTodoCard",
  "status": "enabled",
  "pageNum": 1,
  "pageSize": 10
}
```

### 保存卡片配置

请求体：

```json
{
  "id": "cardId，可选",
  "cardCode": "todo",
  "cardName": "我的待办",
  "componentKey": "WorkbenchTodoCard",
  "permissionCode": "home:card:todo",
  "status": "enabled",
  "defaultVisible": 1,
  "defaultSort": 10,
  "defaultSize": "large",
  "defaultRefreshInterval": 60,
  "config": {
    "limit": 8
  },
  "remark": "展示当前用户待办和已办事项"
}
```

约束：

- `cardCode` 在同一租户有效数据内唯一。
- `componentKey` 必须是前端预置注册表中的合法值。
- `permissionCode` 可为空；不为空时必须是系统已有按钮权限码。
- `defaultSize` 取值为 `small`、`medium`、`large`、`full`。
- `defaultRefreshInterval` 单位为秒，第一版不允许小于 30 秒。
- 停用卡片后所有用户不可见，但保留用户个性化记录。

### 启用或停用卡片

请求体：

```json
{
  "id": "cardId",
  "status": "disabled"
}
```

### 调整卡片默认排序

请求体：

```json
{
  "items": [
    {
      "id": "cardId",
      "defaultSort": 10
    }
  ]
}
```

### 保存系统默认快捷入口

请求体：

```json
{
  "id": "entryId，可选",
  "entryCode": "workflow-start",
  "entryName": "发起审批",
  "entryType": "menu",
  "menuId": "menuId",
  "path": "/workflow/runtime/start",
  "permissionCode": "",
  "icon": "lucide:file-plus",
  "sortNo": 10,
  "status": "enabled",
  "config": {}
}
```

约束：

- 管理端只维护 `ownerType=system` 的快捷入口。
- `entryCode` 在同一租户、同一 `ownerType` 有效数据内唯一。
- `entryType=menu` 时必须绑定有效菜单或内部路由。
- `entryType=link` 时必须填写以 `http://` 或 `https://` 开头的外部链接。

## 卡片数据口径

### 我的待办卡片

- 数据来自审批中心待办、消息中心待办提醒和后续业务模块待办 Provider。
- 只返回当前用户可办理或可查看的事项。
- 点击后进入对应模块办理页或详情页。

### 我的抄送卡片

- 数据来自审批中心我的抄送记录。
- 卡片内分为“未读抄送”和“已读抄送”两个 Tab。
- 两个 Tab 均以抄送到达时间倒序排列。
- 点击明细后进入审批抄送页面并打开对应流程详情。

### 快捷入口卡片

- 数据来自系统默认快捷入口和用户个人快捷入口。
- 只返回当前用户有权访问的菜单或动作。

### 我的消息卡片

- 数据来自消息中心收件箱。
- 卡片内默认分为“未读消息”和“已读消息”两个 Tab。
- 存在未读催办消息时显示“催办”Tab，存在未读超时提醒时显示“超时”Tab，顺序为“催办、超时、未读、已读”；无对应未读消息时隐藏动态 Tab。
- 默认选中优先级为“催办、超时、未读”；前一类存在时，后一类不作为默认 Tab。
- 各 Tab 均按消息到达时间倒序排列。
- 点击明细后进入消息中心或打开消息详情，标记已读等操作复用消息中心能力。

### 我的收藏卡片

- 数据来自通用文件元数据表 `sys_files` 的收藏数据，当前用户直接收藏的文件直接展示，收藏文件夹下的真实文件会铺开展示。
- 卡片不展示文件夹本身，只展示当前登录用户可访问、未删除的真实文件。
- 列表按收藏时间 `starTime` 倒序排列；收藏文件夹下铺开的文件使用对应收藏文件夹的收藏时间；历史收藏没有收藏时间时，后端以更新时间或创建时间兜底。
- 每条记录显示文件类型对应图标、文件名称、收藏时间和下载按钮。
- 右上角跳转进入文档中心“我的收藏”；明细双击直接调用文档中心预览能力，右侧下载按钮调用文档中心下载能力。

### 指标概览卡片

- 数据来自审批、消息、文档等模块的轻量统计接口或 Provider。
- 只展示当前用户维度或其有数据权限的范围。
- 不承担统计报表中心的复杂分析口径。

## 安全与性能

- 所有接口必须按当前登录用户和当前租户过滤数据。
- 卡片数据接口必须有服务端安全上限；列表型卡片的 `limit` 只控制前端每页行数，后端可返回多页所需数据并由工作台卡片分页展示。
- 工作台页面应支持先加载布局再分卡片加载数据；单个卡片失败不能影响其它卡片。
- 快捷入口和卡片配置中的 `config` 只允许保存展示参数，不允许保存可执行脚本、任意组件路径或 SQL。
- 后端不得返回当前用户无权访问的审批、消息、文档、日志或业务对象。
