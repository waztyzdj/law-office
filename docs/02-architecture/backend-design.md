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

## 系统管理服务职责边界

系统管理域的 Service 应按“业务用例入口”和“关系/规则协作服务”拆分，避免用户、租户、角色、权限的底层关系同步全部堆在单个 Service 中。

| 服务 | 职责边界 |
| --- | --- |
| `IUserService` | 用户管理用例入口，负责用户保存、编辑、删除、分配角色、登录态相关查询等面向用户模块的业务流程。 |
| `ITenantService` | 租户管理用例入口，负责租户 CRUD、租户成员维护、租户管理员维护和租户管理员授权入口。 |
| `ITenantLifecycleService` | 租户初始化和删除时的协作流程，负责默认管理员角色、初始管理员成员、默认数据复制和租户关联关系清理。 |
| `IRoleService` | 角色管理用例入口，负责角色权限覆盖保存、角色删除保护和授权后的会话失效处理。 |

后续重构时，用户-租户关系、用户-角色关系、权限授权范围校验、租户上下文切换等可沉淀为独立接口与实现。应用 Service 负责组织事务边界和业务流程，关系服务负责幂等同步、逻辑删除、恢复和归属判断，避免重复实现相同规则。

## 异常处理

全局异常处理位于 `framework/config/GlobalExceptionHandler.java`。新业务优先抛出语义明确的异常，由全局处理统一转换为 `BaseResult`。
