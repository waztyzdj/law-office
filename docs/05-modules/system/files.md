# 文件管理

## 功能说明

文件管理用于维护知识库文档或上传文件的元数据，并与 MinIO 对象存储能力配合。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/SysFilesController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/SysFilesServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/SysFiles.java`
- 工具类：`backend/src/main/java/com/lawoffice/util/MinioUtils.java`

## 数据表

- `sys_files`

## 接口

资源前缀：`/files`，支持通用 CRUD。

## 关键规则

- 文件元数据和对象内容分离。
- 对象存储 key 应由服务端生成。
- 上传下载必须做权限、租户和文件安全校验。
