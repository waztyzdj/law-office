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

## 文档中心

文档中心接口复用 `/files/document` 前缀，基于 `sys_files` 保存文档和文件夹，基于 `sys_file_acl` 保存共享授权。

核心接口：

- `POST /files/document/page`：分页查询文档，`scope` 支持 `all`、`my`、`business`、`shared`、`sharedByMe`、`starred`、`trash`；`scope=all` 用于全局搜索本人有查询权限的未删除文件，`scope=business` 用于按“业务模块虚拟目录 -> 业务数据虚拟目录 -> 附件/个人整理文件夹”查询当前用户有业务访问权且仍有关联关系的业务文档，`scope=shared` 时可用 `shareTargetType`、`shareTargetId` 过滤租户共享或部门共享，并包含本人共享到该目标的文件；`scope=starred` 根目录返回本人收藏的未删除文件和文件夹，传 `parentId` 时浏览该收藏文件夹的直接子级；`scope=trash` 根目录只返回已删除文件夹树的顶层节点，可传 `parentId` 浏览已删除文件夹的直接子级。
- `POST /files/document/page` 可传 `folderOnly=true`，用于左侧树等只需要文件夹节点的场景；后端仅返回文件夹并按文件夹子节点计算 `hasChild`，普通右侧列表不传该参数。
- `POST /files/document/tree/batch`：批量加载左侧树多个节点的下一层文件夹。请求体为 `{ "items": [{ "key": "my", "scope": "my", "parentId": "...", "shareTargetType": "tenant", "shareTargetId": "..." }] }`；响应为 `{ [key]: DocumentFileVO[] }`。前端首次进入文档中心时用该接口一次加载多个根分类的首层目录，避免多个 `/document/page` 请求返回顺序不同造成树闪动。
- `POST /files/document/tree/prefetch`：批量预取左侧树多个父级目录的下一层文件夹。请求体为 `{ "parentIds": ["..."], "scope": "my", "shareTargetType": "tenant", "shareTargetId": "..." }`，一次最多处理 100 个父级目录；响应为 `{ [parentId]: DocumentFileVO[] }`。该接口复用文档分页查询的权限和 scope 规则，只用于前端树展开后的下一级缓存预热，不改变树的展开状态。
- `POST /files/document/upload`：上传文档中心文件。
- `POST /files/document/folder`：创建文件夹。
- `POST /files/document/rename`：重命名本人文档。
- `POST /files/document/move`：移动本人文档。
- `POST /files/document/batch-move`：批量移动本人文档或文件夹，任一文件校验失败时整体回滚。请求体：`{ "ids": ["..."], "parentId": "...", "scope": "my", "shareTargetType": "depart" }`。
- `POST /files/document/copy`：复制当前用户可下载的文档或文件夹到目标目录，文件内容会复制为新的对象存储文件。
- `POST /files/document/delete/{fileId}`：移入回收站。
- `POST /files/document/batch-delete`：批量移入回收站，任一文件校验失败时整体回滚。请求体：`{ "ids": ["..."] }`。
- `POST /files/document/restore/{fileId}`：从回收站恢复。
- `POST /files/document/batch-restore`：批量从回收站恢复，任一文件校验失败时整体回滚。请求体：`{ "ids": ["..."] }`。
- `POST /files/document/purge/{fileId}`：从回收站彻底删除本人拥有的文档及其子级，同时清理文件授权、个人归类关系、历史版本记录和对象存储文件。
- `POST /files/document/trash/clear`：清空本人回收站。
- `POST /files/document/star/{fileId}`：切换收藏状态。
- `POST /files/document/share`：覆盖保存共享目标。
- `GET /files/document/shares/{fileId}`：查询共享目标。
- `POST /files/document/share/revoke/{aclId}`：撤销单条共享。
- `GET /files/document/download/{fileId}`：按文档中心权限下载。

请求约定：
- `/upload`、`/folder`、`/move`、`/copy` 可传 `scope` 和 `shareTargetType` 标识当前文档中心根目录；`scope=shared` 且未传 `shareTargetType` 表示“共享给我”。
- “租户共享”和“部门共享”根目录允许上传和新建文件夹，前端在根目录创建后同步写入对应共享授权；“共享给我”不允许上传，但允许创建个人整理文件夹。
- “共享给我”的拖拽归类不修改原文件 `sys_files.parent_id`，而是用 `sys_file_relation` 记录当前用户自己的整理位置。
- “我的共享”根目录展示本人直接共享出去的文件/文件夹和本人创建的共享整理文件夹；进入已共享文件夹后按原文件夹层级展示直接子级，子文件不需要逐条写共享授权。
- 回收站中文件夹只允许恢复或彻底删除；文件只允许预览、下载、恢复或彻底删除。删除态文件的预览和下载仅允许文件所有者访问，在线编辑接口拒绝删除态文件。
- 租户共享、部门共享、共享给我、我的共享和我的收藏的前端右键菜单、拖拽、多选限制属于交互层规则；实际共享项、实际收藏项及其子级继承可见的区别见 `docs/05-modules/system/files.md`。
- “业务文档”是与“我的文档”平行的独立分类，不允许在文档中心上传、重命名、移动、复制或删除业务附件；消息附件等业务文档按业务模块访问权展示，例如消息发件人和收件人均可查看。业务文档根目录返回 `store_type=business_module_view` 的虚拟业务模块目录，模块下返回 `store_type=business_record_view` 的虚拟业务数据目录，业务数据目录下展示附件。历史数据中如存在 `store_type=business_view` 的个人整理文件夹和 `sys_file_relation` 的 `relation_type=3`、`biz_type=document_business:<userId>` 个人归类关系，仅用于兼容只读展示，不再通过文档中心新增或修改归类。业务附件在业务数据解除关联后才可由普通文档流程清理。
- 用户头像等系统内部附件不返回到业务文档分类，避免把非业务协作文档暴露在通用文档中心。
- 新业务模块接入业务文档时，附件仍通过 `sys_file_relation.biz_type/biz_id` 绑定；业务模块需要实现 `IBusinessDocumentProvider`，提供模块名称、业务数据目录名称和当前用户访问校验。文档中心核心服务只依赖该 Provider 接口，不为每个业务模块写定制分支。

## 安全要求

- 上传必须校验文件大小、类型和文件名；文档中心仅允许常规文档、图片和视频文件，禁止可执行文件、脚本和压缩包等高风险类型。
- 下载和导出不得泄露敏感字段。
- 对象存储 key 由服务端生成。
- 文档中心所有查询、下载、共享和恢复操作必须校验 `tenant_id`、文件状态和访问授权，租户共享使用 `target_type=tenant` 且 `target_id` 为当前租户 ID。
