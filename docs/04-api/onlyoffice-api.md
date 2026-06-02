# ONLYOFFICE 在线预览与编辑接口

本文记录文档中心接入 ONLYOFFICE Docs 的接口。当前支持 PDF、Word、Excel、PPT 在线预览，支持 Word、Excel、PPT 在线协同编辑；历史版本接口已预留，版本控制下一阶段实现。

## 生成编辑器配置

- 方法：`GET`
- 路径：`/files/document/onlyoffice/config/{fileId}`
- 查询参数：`mode=view|edit`，默认 `view`
- 认证：需要当前登录 JWT
- 说明：后端按文档中心权限生成 `DocsAPI.DocEditor` 初始化配置。

支持预览的扩展名：
- `pdf`
- `doc`
- `docx`
- `xls`
- `xlsx`
- `ppt`
- `pptx`

支持编辑的扩展名：
- `doc`
- `docx`
- `xls`
- `xlsx`
- `ppt`
- `pptx`

权限规则：
- `mode=view` 校验文档读取权限，并返回只读配置。
- `mode=edit` 校验文档更新权限，只有文件所有者或拥有共享 `update/manage` 权限且文件允许修改的用户可以获取编辑配置。
- PDF 仅支持预览，不开放在线编辑。
- 多用户编辑使用同一文件当前最终保存版本的 `document.key`，并启用 `editorConfig.coEditing.mode=fast`。`status=6` 强制保存只覆盖内容、不刷新版本时间；`status=2` 最终保存才刷新版本时间，避免同一协同会话被拆开，也避免最终保存后复用旧 key。

响应核心字段：
- `documentServerApiUrl`：前端动态加载的 ONLYOFFICE `api.js` 地址。
- `config`：传给 `new DocsAPI.DocEditor(...)` 的配置对象，编辑模式会包含 `editorConfig.callbackUrl`。

## 文件回源

- 方法：`GET`
- 路径：`/files/document/onlyoffice/download/{token}`
- 认证：不使用浏览器 JWT，仅接受后端生成的短期 token
- 调用方：ONLYOFFICE Document Server
- 说明：Document Server 使用该接口拉取受保护文件内容。token 包含文件 ID、租户 ID 和过期时间。

## 保存回调

- 方法：`POST`
- 路径：`/files/document/onlyoffice/callback/{token}`
- 认证：不使用浏览器 JWT，仅接受后端生成的编辑回调 token
- 调用方：ONLYOFFICE Document Server
- 响应：必须返回 ONLYOFFICE 约定 JSON，例如 `{"error":0}`

处理规则：
- `status=2`：文档可保存，后端下载回调 `url` 并覆盖原对象存储内容。
- `status=6`：强制保存，覆盖文件内容但不刷新版本时间，保证后续进入的用户仍加入当前协同会话。
- 其他状态当前直接返回成功，不修改文件。
- 保存前会重新校验当前用户是否仍有文档更新权限，防止打开后权限被撤销仍能写回。
- 回调下载地址必须与配置的 Document Server 地址同源，避免回调 token 被用于访问任意 URL。

## 历史版本预留

- 方法：`GET`
- 路径：`/files/document/onlyoffice/history/{fileId}`
- 认证：需要当前登录 JWT
- 当前行为：校验读取权限后返回空数组。
- 后续计划：落表保存 ONLYOFFICE 回调中的 `history`、`changesUrl`、版本号、编辑人和编辑时间，再扩展版本详情与恢复接口。
