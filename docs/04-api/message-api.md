# 站内消息接口

## 权限

- 个人消息能力面向所有登录用户开放，不使用菜单权限码拦截。
- 收件箱、发件箱、发送消息、消息详情、已读、收藏、删除和附件下载均由后端按当前用户、当前租户和逻辑删除状态过滤。
- 消息模板、全站公告、系统群发、发送记录审计等管理能力如后续实现，再单独使用权限码控制。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/message/send` | 发送站内消息，接收人必须属于当前租户 |
| POST | `/message/notifications/page` | 查询当前登录用户未读消息通知，不依赖消息菜单权限 |
| POST | `/message/notifications/read` | 将当前登录用户的一条通知标记为已读 |
| POST | `/message/notifications/clear` | 清空当前登录用户未读通知，消息仍保留在收件箱 |
| POST | `/message/inbox/page` | 分页查询当前用户收件箱 |
| POST | `/message/sent/page` | 分页查询当前用户发件箱 |
| POST | `/message/inbox/detail` | 查询收件消息详情，入参 `{ id }` 为收件消息 ID |
| POST | `/message/sent/detail` | 查询发件消息详情，入参 `{ id }` 为消息 ID |
| POST | `/message/read` | 标记收件消息已读 |
| POST | `/message/read/batch` | 批量标记选中的收件消息已读 |
| POST | `/message/read/all` | 当前用户当前租户下全部收件消息标记已读 |
| POST | `/message/star` | 切换收件消息收藏状态 |
| POST | `/message/inbox/delete` | 逻辑删除当前用户收件消息 |
| POST | `/message/inbox/delete/batch` | 批量逻辑删除当前用户选中的收件消息 |
| POST | `/message/recall` | 撤回当前用户发送的消息 |
| POST | `/message/sent/delete` | 逻辑删除当前用户发件消息 |
| POST | `/message/sent/delete/batch` | 批量逻辑删除当前用户选中的发件消息 |
| GET | `/message/attachment/download/{fileId}` | 下载消息附件，仅允许消息发件人或收件人访问 |

## 发送请求

```json
{
  "title": "消息标题",
  "content": "消息内容",
  "messageType": 1,
  "priority": 1,
  "receiverIds": ["userId"],
  "actions": [
    {
      "actionType": 2,
      "actionName": "查看详情",
      "externalUrl": "https://example.com",
      "openType": 2
    }
  ],
  "attachments": [
    {
      "fileId": "fileId",
      "fileName": "合同.pdf",
      "fileType": "pdf",
      "fileSize": 122880
    }
  ]
}
```

## 业务约束

- 外部链接仅允许 `http://` 和 `https://`。
- 内部页面和待办动作必须至少配置内部路径或业务 ID。
- 附件统一通过文件中心上传结果中的 `fileId` 绑定，不再手工维护 `fileUrl`。
- 受保护业务附件不返回对象存储直连地址，下载必须走业务模块接口并执行业务权限校验。
- 删除均为逻辑删除。
