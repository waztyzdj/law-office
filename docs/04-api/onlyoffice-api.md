# ONLYOFFICE 在线预览接口

本文记录文档中心接入 ONLYOFFICE Docs 的第一阶段接口。当前阶段只支持只读预览，不支持编辑保存。

## 生成预览配置

- 方法：`GET`
- 路径：`/files/document/onlyoffice/config/{fileId}`
- 查询参数：`mode=view`，当前仅支持 `view`
- 认证：需要当前登录 JWT
- 说明：按文档中心读取权限生成 ONLYOFFICE `DocsAPI.DocEditor` 初始化配置。

支持的文件扩展名：

- `pdf`
- `doc`
- `docx`
- `xls`
- `xlsx`
- `ppt`
- `pptx`

响应数据核心字段：

- `documentServerApiUrl`：前端需要动态加载的 `api.js` 地址。
- `config`：传给 `new DocsAPI.DocEditor(...)` 的配置对象。

## 文件回源

- 方法：`GET`
- 路径：`/files/document/onlyoffice/download/{token}`
- 认证：不使用浏览器登录 JWT，仅接受后端生成的短期 token
- 调用方：ONLYOFFICE Document Server
- 说明：Document Server 使用该接口拉取受保护文件内容。token 包含文件 ID、租户 ID 和过期时间。

## 当前限制

- 当前只读预览按读取权限校验，不暴露 MinIO 直链。
- 当前不支持 `edit` 模式；传入非 `view` 会返回业务错误。
- 业务文档遵循现有业务文档访问规则。
