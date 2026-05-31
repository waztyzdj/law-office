# 文件管理

## 功能说明

文件管理用于维护文件元数据和业务关联，并与 MinIO 对象存储能力配合。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/SysFilesController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/SysFilesServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/SysFiles.java`

## 数据表

- `sys_files`：文件元数据
- `sys_file_relation`：文件与业务对象的关联

## 接口前缀

- `/files`

## 关键规则

- 文件元数据和业务关联分离。
- 文件对象由服务端生成 key。
- 所有上传、查询和绑定操作都应校验租户与文件状态。
- 通用附件单文件大小上限为 50MB，超过限制时返回 413。
- 文件中心通用详情和下载接口仅允许上传人访问；受保护业务附件必须通过对应业务模块接口下载，并由业务模块校验成员、收件人、审批人等访问权限。
- 文件下载由后端按 `sys_files.file_name` 设置下载文件名，避免暴露对象存储 key 或产生中文乱码。
