# 后端详细设计

## 分层职责

| 层级 | 目录 | 职责 |
| --- | --- | --- |
| Controller | `controller` | HTTP 协议、参数接收、权限注解、结果返回。 |
| Service | `service` / `service.impl` | 业务规则、事务、校验、数据组装。 |
| Mapper | `mapper` | 数据库访问。 |
| Entity | `entity` | 数据库表映射。 |
| Req | `req` | 请求入参和校验。 |
| VO | `vo` | 响应前端的数据结构。 |

## BaseController

`BaseController` 使用泛型绑定 Service、Entity、VO、Req，统一提供标准 CRUD 接口。子类可以通过钩子方法扩展行为：

- `doBeforeList` / `doAfterList`
- `doBeforePage` / `doAfterPage`
- `doBeforeGetById` / `doAfterGetById`
- `doBeforeSave` / `doAfterSave`
- `doBeforeDelete` / `doAfterDelete`
- `doBeforeImport` / `doAfterImport`
- `doBeforeExport` / `doAfterExport`

## BaseServiceImpl

`BaseServiceImpl` 负责：

- 默认过滤 `delete_flag = 0`。
- Entity 与 VO 转换。
- 保存和批量保存。
- 逻辑删除。
- Excel 导入导出。
- 审计字段填充。

## 请求上下文

`RequestContextUtils` 从请求中构建上下文，Service 层通过 `BaseDTO.context` 获取当前用户、租户等信息。

## 异常处理

全局异常处理位于 `framework/config/GlobalExceptionHandler.java`。新业务优先抛出语义明确的异常，由全局处理统一转换为 `BaseResult`。
