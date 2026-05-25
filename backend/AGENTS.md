# 后端 AI 编码规范

> 适用范围：本文件约束 `backend/` 下所有 Java + Spring Boot 代码。后续 AI 或开发者修改后端前，必须先阅读并遵守本文件。

## 维护原则

- 本规范是后端主规范，后续后端编码约定统一维护在此文件。
- 先读项目现有实现，再写新代码。优先复用 `framework`、`system`、`BaseController`、`BaseServiceImpl`、`BaseResult`、`QueryWrapperBuilderUtils`、`RequestContextUtils` 等既有能力。
- 当前技术基线参考 `pom.xml`：Java 17、Spring Boot 3.2.5、Spring Framework 6、Jakarta Servlet/Validation、MyBatis-Plus 3.5.x、Shiro 2、JWT、Redis、MinIO、EasyExcel。
- 官方实践参考：
  - Spring Boot Reference: https://docs.spring.io/spring-boot/index.html
  - Spring Framework Validation: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
  - Spring Framework Exceptions: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html
  - Spring Boot Testing: https://docs.spring.io/spring-boot/reference/testing/

## AI 工作流

1. 修改前先用 `rg` / `rg --files` 查找同类 Controller、Service、Entity、Req、VO、Mapper 实现。
2. 优先做最小范围修改。不要重写框架基类、认证链路、租户链路或全局配置，除非任务明确要求。
3. 涉及接口时同步检查：请求校验、权限、租户隔离、审计字段、异常响应、日志、前端协议兼容性。
4. 涉及数据库时同步检查：逻辑删除、唯一性、索引、事务、批量性能、数据迁移 SQL。
5. 涉及安全时同步检查：密码、token、权限码、租户 ID、文件路径、日志脱敏。
6. 完成后至少运行 Maven 编译；影响核心框架、认证、权限、SQL 或公共工具时补充相关测试或人工验证说明。
7. 如果发现稳定的新项目约定，及时补充到本文件。

## 目录边界

- `com.lawoffice.framework`：通用框架层。放 BaseController/BaseService、统一返回、异常处理、租户、日志、查询构建、通用 DTO/Req/VO。禁止依赖具体业务模块。
- `com.lawoffice.system`：系统管理域。包含用户、角色、菜单、权限、字典、部门、租户、文件、认证等。
- `com.lawoffice.case`、`home`、`oa`：业务域。新业务按领域建包，保持 `controller/entity/mapper/req/service/vo` 结构。
- `com.lawoffice.util`：跨模块工具。工具类必须无状态、线程安全；有业务语义的逻辑优先放 Service。
- `src/main/resources`：配置和资源。环境差异放 `application-dev.yml`、`application-prod.yml`，禁止提交真实密钥。
- `sql/`：建表、初始化权限、菜单、字典等 SQL。后端字段或权限变更涉及数据库时必须同步。

## 分层规则

- Controller 只处理 HTTP 协议、参数接收、权限注解、结果包装，不写复杂业务逻辑。
- Service 承载业务规则、事务边界、校验、数据组装、跨 Mapper 协作。
- Mapper 只做数据库访问。复杂 SQL 可以放 Mapper XML 或注解，但必须可读、可解释、可索引。
- Entity 映射数据库表，不直接作为复杂接口响应对象扩散到前端。
- Req 接收前端请求并承担入参校验；VO 返回前端展示数据；DTO 用于服务层内部上下文传递。
- 业务扩展优先重写 `BaseController` / `BaseServiceImpl` 的 `doBeforeXxx`、`doAfterXxx` 钩子，避免复制整套 CRUD。

## 命名规范

- 包名全部小写，按业务域分层：`com.lawoffice.system.controller`。
- Controller 以 `Controller` 结尾；Service 接口沿用项目约定以 `I` 开头，实现以 `Impl` 结尾。
- Entity、Req、VO、DTO 使用 PascalCase：`User`、`UserReq`、`UserVO`。
- Mapper 以 `Mapper` 结尾，并继承 MyBatis-Plus `BaseMapper<E>`。
- 方法名表达业务动作：`assignRoles`、`getCurrentUserDetailInfo`、`validateUnique`。
- 常量使用 `private static final`，命名为 UPPER_SNAKE_CASE。魔法数字和状态码必须抽成常量或枚举。
- 权限码使用 `模块:动作`，如 `user:view`、`user:edit`，并与前端 `permissionCodes` 保持一致。

## Spring Boot 与 Java

- 新代码使用 Java 17 能力，但保持团队可读性。局部变量类型清晰时可用 `var`，公共 API 不使用 `var`。
- 类成员顺序必须清晰稳定：常量/静态字段、实例字段、构造器、公共方法、受保护方法、私有方法；禁止把属性字段穿插放在方法之间。
- 使用 Spring Boot 3 / Spring 6 的 `jakarta.*` 包，不引入 `javax.*` 旧依赖。
- 依赖注入优先使用构造器注入；新代码避免字段注入。必需依赖尽量声明为 `final`。
- 配置类使用 `@ConfigurationProperties` 绑定结构化配置；不要散落读取环境变量或硬编码配置值。
- Bean 命名和条件装配要明确。全局 Bean 修改前必须检查现有调用方。
- 只在事务边界方法上使用 `@Transactional(rollbackFor = Exception.class)`；只读查询可使用 `readOnly = true`。
- 异步方法必须通过 Spring Bean 调用才会生效，不能在同类内部自调用；线程池使用 `AsyncConfig` 中的配置。

## Controller 规范

- 标准 CRUD Controller 必须继承：

```java
public class UserController extends BaseController<IUserService, User, UserVO, UserReq> {
}
```

- 新业务 Controller 必须标注 `@RestController`、`@RequestMapping`、`@Tag`；需要自动权限时补充 `@ModuleInfo`。
- 自定义接口必须标注 `@Operation`，说明接口语义，不写误导性的描述。
- 请求体使用 `@Valid @RequestBody`；路径参数、查询参数需要校验时使用 Bean Validation 注解。
- 不在 Controller 中手动拼接大量业务数据；调用 Service 返回 VO 或简单结果。
- 自定义接口优先让异常交给 `GlobalExceptionHandler` 统一处理。不要把未知异常的 `e.getMessage()` 直接返回给前端。
- 文件下载/导出接口必须设置响应头、文件名编码、Content-Type，并处理写出失败日志。

## Service 规范

- 标准业务 Service 接口继承 `IBaseService<E, V>`，实现继承 `BaseServiceImpl<M, E, V>`。
- 业务 Service 必须按接口和实现拆分：接口放在 `service` 包并以 `I` 开头，实现放在 `service.impl` 包并以 `Impl` 结尾；调用方优先依赖接口。
- 非 CRUD 的业务协作服务也必须提供接口和实现，例如租户默认数据同步、用户-租户关系同步、授权范围校验、租户上下文执行等。
- 复杂业务必须拆成小的私有方法：规范化、校验、查询、组装、持久化分别命名。
- 写操作必须考虑事务。跨多张表写入、先删后插、批量导入、权限分配必须有事务。
- 校验失败抛出 `IllegalArgumentException` 或项目业务异常，由全局异常处理返回 400/业务码。
- 不在 Service 返回敏感字段，如密码、salt、token 原文。
- 批量操作避免逐条无事务写入；如必须逐条处理，要明确失败策略：全量回滚、跳过失败、返回失败明细。
- 认证、权限、租户、审计、逻辑删除属于核心规则，扩展时优先复用现有工具和上下文。

## 注释规范

- Service 接口的公共方法必须写 JavaDoc，说明业务语义、关键约束、参数和返回值。
- 实现类中不在接口里的 `private` 方法，凡是承载业务规则、数据同步、权限校验、租户上下文切换、软删恢复或复杂查询组装的，必须写方法级注释。
- 关键代码分支要说明“为什么这样做”，例如：为什么逻辑删除而不物理删除、为什么恢复已删除关系而不重复插入、为什么授权不能超过当前操作人权限、为什么默认数据同步不覆盖租户已有数据。
- 简单 getter/setter、纯粹一眼可读的局部变量赋值不需要机械注释；禁止写只复述代码的空泛注释。

## Entity / Req / VO / DTO

- Entity 继承 `BaseEntity` 或 `BaseTenantEntity`，使用 `@TableName` 指定表名。
- Entity 字段使用 Java 驼峰命名，数据库字段使用下划线命名，由 MyBatis-Plus 映射。
- 敏感字段必须限制查询或输出，如 `password` 使用 `@TableField(select = false)`，导出时使用 `@ExcelIgnore`。
- Req 继承 `BaseReq`，使用 Jakarta Bean Validation：`@NotBlank`、`@Size`、`@Pattern`、`@Email`、`@Min`、`@Max`。
- 新增和修改校验规则不一致时，优先使用校验分组或在 Service 中明确区分 create/update。
- VO 继承 `BaseVO`，只返回前端需要的字段。不要把 Entity 全量字段作为 VO 偷懒返回。
- DTO 用于内部流程和上下文传递，避免暴露给 HTTP 接口。
- 使用 Lombok 时保持克制：Entity/Req/VO 可用 `@Data`；有不变量、复杂构造或安全字段时手写方法更清晰。

## MyBatis-Plus 与数据库

- 普通查询优先使用 `LambdaQueryWrapper`，避免字符串字段名写错；动态筛选协议统一走 `QueryWrapperBuilderUtils.build()`。
- 所有列表/分页默认过滤 `delete_flag = 0`，不得返回逻辑删除数据。
- 多租户数据必须继承 `BaseTenantEntity` 或接入租户过滤，不得绕过 `TenantContextHolder`。
- 分页使用 MyBatis-Plus `Page`，前端协议保持 `pageNum`、`pageSize`、`records`、`total`。
- 排序字段必须白名单校验，禁止把前端传入字段直接拼进 SQL。
- 唯一性必须同时有应用层校验和数据库唯一索引，避免并发重复写入。
- 禁止 N+1 查询。需要关联数据时批量查出 ID 集合，再一次性查询并组装 Map。
- 禁止在 Wrapper `.last()` 中拼接用户输入；只有固定 SQL 片段才允许使用。

## 返回与异常

- HTTP 响应统一使用 `BaseResult<T>`，成功使用 `BaseResult.success(data)`。
- 参数错误返回 400，未登录/登录过期返回 401，无权限返回 403，资源不存在建议返回 404 或业务错误码，未知错误返回 500。
- 业务校验失败不要返回 500；抛 `IllegalArgumentException` 或业务异常并由 `GlobalExceptionHandler` 处理。
- 日志记录完整异常堆栈，但返回给前端的 500 消息必须泛化，避免泄露 SQL、路径、密钥、内部类名。
- 新增异常类型时同步扩展 `GlobalExceptionHandler`，不要在各 Controller 重复 catch。
- `BaseResult.error(String)` 默认是 500，业务校验不要直接使用这个重载。

## 权限、认证与租户

- 自动权限基于 `@ModuleInfo` 和方法语义；自定义权限使用 `@RequiresPermission`。
- 新增菜单/按钮权限时同步后端权限数据、前端权限码和路由/按钮控制。
- JWT 解析、Redis token、Shiro Realm、过滤器链路必须保持单一职责，不在业务 Service 中手动解析 token。
- 当前用户信息从 `RequestContext` 或请求过滤器写入属性中获取，避免信任前端传入的用户名、用户 ID、租户 ID。
- 登录日志不得输出密码、token 全文、身份证号、手机号全量等敏感信息。
- 密码必须使用 BCrypt 等安全哈希，不使用 MD5/SHA 明文摘要；修改密码要校验复杂度和旧密码。
- 租户上下文必须在请求结束清理，避免线程复用导致数据串租户。

## 日志与审计

- 使用 Lombok `@Slf4j`，禁止 `System.out.println`。
- Controller 不手写通用操作日志，优先依赖 `AutoLogAspect` 和 `@AutoLog`。
- 日志级别：`debug` 记录诊断细节，`info` 记录关键业务成功动作，`warn` 记录可预期失败，`error` 记录系统异常。
- 日志必须脱敏：密码、token、身份证、手机号、邮箱、密钥、文件绝对路径不能明文完整输出。
- 审计字段统一由 `EntityFillUtils` 和 `RequestContext` 填充，不在各业务里随意写死。

## 文件、Excel、MinIO

- 上传文件必须校验大小、后缀、MIME、空文件和文件名安全，不能信任原始文件名。
- MinIO object key 由服务端生成，避免路径穿越和覆盖。
- Excel 导入必须限制最大行数，逐行校验并返回清晰错误；大批量导入要考虑事务和内存。
- Excel 导出默认过滤逻辑删除和租户数据，避免导出敏感字段。
- 文件流必须使用 try-with-resources 或框架托管，避免泄漏。

## 配置与依赖

- 禁止提交真实数据库密码、Redis 密码、MinIO 密钥、JWT 密钥。生产配置使用环境变量、加密配置或部署平台注入。
- 新增依赖前先确认 JDK 17、Spring Boot 3、Jakarta 兼容性。
- 不引入与现有栈重复的框架，例如另一个 JSON 框架、另一个 ORM、另一个权限框架，除非任务明确要求。
- 配置属性要有默认值或启动期校验，避免运行到业务路径才报空指针。
- Profile 差异只放必要配置，不复制整份配置文件。

## 测试与验证

- 普通代码变更至少运行：

```powershell
.\mvnw.cmd -q -DskipTests compile
```

- 修改 Service 业务规则时补充单元测试；修改 Mapper/SQL/事务时补充集成测试。
- Spring Boot 3 推荐使用 `spring-boot-starter-test`、JUnit Jupiter、MockMvc；涉及真实数据库/Redis/MinIO 时优先使用 Testcontainers 或明确说明本地依赖。
- 测试命名表达行为：`shouldRejectDuplicateUsernameWhenCreateUser`。
- 测试数据必须可重复运行，不能依赖生产数据或个人本地环境。
- 无法运行验证时，最终回复必须说明原因和剩余风险。

## 数据库规范

- 主键统一使用 `id`，当前项目约定字符串 ID，长度与建表脚本保持一致。
- 外键字段命名为 `xxx_id`，即使不声明数据库外键，也要建立必要索引。
- 表必须包含审计字段和逻辑删除字段：`create_time`、`create_by`、`update_time`、`update_by`、`delete_flag`。
- 多租户表必须包含 `tenant_id` 并建立组合索引。
- 唯一索引命名 `uk_表名_字段`，普通索引命名 `idx_表名_字段`。
- 字段长度以业务真实上限为准，不机械追求 2 的幂；需要与前端校验、Req 校验、数据库三处一致。
- 枚举/状态字段要有注释，Java 侧要有常量或枚举表达。

## 项目约定

- 标准 CRUD 优先复用 `BaseController` 的 `/list`、`/page`、`/getById`、`/save`、`/batchSave`、`/delete`、`/batchDelete`、`/import`、`/export`。
- 树形管理页默认复用 `BaseController` 的 `/list` 返回扁平数据，由前端根据 `id`/`parentId` 组树；后端保留 `TreeEntity`/`TreeTenantEntity`、`TreeVO`、`TreeUtils` 和 `TreeServiceImpl` 中的父级校验、禁止循环引用、删除前子节点检查等 Service/Util 层能力。只有角色授权树、运行时菜单路由等确实需要服务端裁剪和组装的场景，才提供专用树接口。
- `BaseApi` 前端协议依赖后端分页返回字段 `records`、`total`、`pageNum`、`pageSize`，后端修改时必须同步前端。
- `BaseServiceImpl` 中的逻辑删除规则是项目默认行为，新查询不得绕过。
- 权限模块与前端 `src/constants/permissions.ts` 强绑定，新增权限必须双端同步。
- 涉及数据库字段枚举、状态码或字段含义时，必须先参考 `sql/建表脚本.sql` 对应字段注释；Java 常量/枚举、Req/VO 注释、Service 校验、初始化 SQL 和前端协议必须与建表脚本保持一致。
- 中文注释或文案出现乱码时，优先确认文件编码和终端编码，不要盲目整文件重写。

## 持续完善清单

- 引入业务异常体系后，在本文件补充异常类、错误码和返回规范。
- 完善测试基础设施后，补充测试目录、Testcontainers 配置和最低覆盖要求。
- 新增业务域时，把稳定的 Controller/Service/Mapper/SQL 模式沉淀到本文件。
- 调整认证、租户、权限、导入导出协议时，同步更新本文件、前端规范和相关 README。
