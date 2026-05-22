# 文件与导入导出接口

## 通用 Excel 导入

- 方法：`POST`
- 路径：`/{resource}/import`
- Content-Type：`multipart/form-data`
- 字段：`file`

说明：继承 `BaseController` 的资源默认支持 Excel 导入。导入逻辑由 `ExcelUtils` 和对应 Service 处理。

## 通用 Excel 导出

- 方法：`POST`
- 路径：`/{resource}/export`
- 说明：根据查询条件导出 Excel。

## 文件管理

文件资源前缀为 `/files`，标准 CRUD 管理文件元数据。对象存储能力由 MinIO 配置和工具类提供。

## 安全要求

- 上传必须校验文件大小、类型和文件名。
- 下载和导出不得泄露敏感字段。
- 对象存储 key 由服务端生成。
