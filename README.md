# LawOffice - 律所业务管理系统

## 1. 系统介绍
LawOffice 是一款专为律师事务所设计的综合业务管理平台。旨在通过数字化手段提升律所的案件管理效率、优化行政办公流程，并保障客户数据的安全性。系统采用前后端分离架构，支持多租户模式，具备完善的权限控制与操作日志审计功能。

**核心功能模块：**
*   **案件管理**：案件全生命周期跟踪、当事人信息管理、案件状态流转。
*   **系统管理**：用户管理、角色权限分配、部门组织架构。
*   **OA 办公**：日常审批、文档管理、工作日程安排。
*   **日志审计**：基于 AOP 的全链路操作日志记录，支持异步高性能写入。

---

## 2. 使用技术栈

### 后端技术
*   **核心框架**：Spring Boot 3.x (Java 17)
*   **持久层**：MyBatis-Plus (简化 CRUD，支持 Lambda 查询与逻辑删除)
*   **数据库**：MySQL 8.0+
*   **连接池**：Druid (提供强大的监控和扩展功能)
*   **安全认证**：Spring Security + JWT (无状态身份验证)
*   **工具库**：Lombok, FastJSON2, EasyExcel (导入导出), Jasypt (配置加密)
*   **接口文档**：Swagger / OpenAPI 3.0

### 前端技术
*   **核心框架**：Vue 3 (Composition API) + Vite
*   **UI 组件库**：Ant Design Vue 4.x
*   **状态管理**：Pinia
*   **网络请求**：Axios (封装拦截器处理 Token 与全局错误)
*   **路由管理**：Vue Router 4

---

## 3. 后端框架介绍及代码规范

### 3.1 模块化分层架构
系统采用标准的 MVC 分层架构，以 **用户管理模块 (`system`)** 为例，各层级职责与命名规范如下：

| 层级 | 包路径示例 | 命名规范 | 职责描述 |
| :--- | :--- | :--- | :--- |
| **实体层** | `com.lawoffice.system.entity` | `User.java` | 对应数据库表结构，继承 `BaseEntity`，使用 `@TableName` 映射。 |
| **控制层** | `com.lawoffice.system.controller` | `UserController.java` | 接收 HTTP 请求，继承 `BaseController`，使用 `@Tag` 定义 Swagger 分组。 |
| **服务接口** | `com.lawoffice.system.service` | `IUserService.java` | 定义业务接口，继承 `IBaseService<User>`。 |
| **服务实现** | `com.lawoffice.system.service.impl` | `UserServiceImpl.java` | 实现具体业务逻辑，继承 `BaseServiceImpl<User>`。 |
| **数据访问** | `com.lawoffice.system.mapper` | `UserMapper.java` | 继承 `BaseMapper<User>`，负责底层 SQL 交互。 |

### 3.2 基础框架设计规范
为了减少重复代码并统一行为，所有业务模块需遵循以下基类约束：

1.  **Controller 规范**：
    *   必须继承 `BaseController<S, E>`。
    *   通过构造函数注入 Service：`public UserController(IUserService userService) { this.baseService = userService; }`。
    *   在实体类上添加 `@ModuleInfo(name = "用户管理")`，系统将自动提取该名称用于日志记录和 Swagger 文档。

2.  **Service 规范**：
    *   实现类必须继承 `BaseServiceImpl<E>` 并传入 Mapper 和实体 Class。
    *   利用钩子方法（如 `doBeforeSave`, `doAfterList`）在不修改父类代码的前提下扩展业务逻辑。

3.  **DTO 与 查询规范**：
    *   列表/分页查询统一使用 `BaseDTO<E>` 或 `BasePageDTO<E>` 封装。
    *   查询条件通过 `QueryWrapper` 传递，禁止在 Controller 中直接拼接 SQL。
    *   返回结果统一包装为 `BaseResult<T>`，确保前端接收格式一致。

### 3.3 编码与命名细节
*   **方法命名**：
    *   查询单个：`getById`, `getByUsername`
    *   查询列表：`list`, `page`
    *   新增/修改：`save` (根据 ID 是否存在自动判断)
    *   删除：`delete` (逻辑删除), `batchDelete` (批量逻辑删除)
*   **变量命名**：
    *   布尔类型字段建议以 `is` 或 `has` 开头（如 `isDeleted`），但在数据库字段中统一使用 `delete_flag`。
    *   常量全部大写，单词间用下划线分隔。
*   **注释要求**：
    *   所有 Controller 方法必须包含 JavaDoc 注释。
    *   复杂业务逻辑必须在 `ServiceImpl` 中添加行内注释说明意图。

### 3.4 异步日志与性能优化
*   系统通过 `AutoLogAspect` 自动记录操作日志。
*   日志写入采用 **异步线程池 (`taskExecutor`)** 处理，确保高并发下日志记录不会阻塞主业务流程。
*   IP 地址获取已做兼容处理，优先从 `X-Forwarded-For` 获取，并强制转换为 IPv4 格式存储。

---

## 4. 数据库设计规范

### 4.1 表结构与字段规范
1.  **表名命名**：采用 `模块前缀_功能英文名` 的小写蛇形命名法（如 `sys_user`, `case_info`）。
2.  **主键规范**：
    *   统一使用字段名 `id`。
    *   类型为 `VARCHAR(64)`，存储去除横杠的 UUID 或雪花算法 ID。
3.  **外键规范**：
    *   命名格式为 `关联表名单数_id`（如 `user_id`, `dept_id`）。
    *   类型统一为 `VARCHAR(64)`，与关联表主键保持一致。
4.  **字符串长度**：
    *   除主键和外键外，其他 `VARCHAR` 字段的长度必须是 **2 的 N 次幂**（如 16, 32, 64, 128, 255 等），以优化数据库存储空间。
5.  **必备审计字段**：每张表必须包含以下字段（继承自 `BaseEntity`）：
    *   `create_time`: 创建时间
    *   `create_by`: 创建人
    *   `update_time`: 更新时间
    *   `update_by`: 更新人
    *   `delete_flag`: 逻辑删除标识 (0: 未删除, 1: 已删除)
    *   `tenant_id`: 租户 ID (用于多租户隔离)

### 4.2 索引设计规范
1.  **主键索引**：命名为 `pk_表名`（如 `pk_sys_user`）。
2.  **唯一索引**：命名为 `uk_表名_字段名`（如 `uk_sys_user_username`）。
3.  **普通索引**：命名为 `idx_表名_字段名`（如 `idx_case_info_status`）。
4.  **外键索引**：所有作为查询条件的 `xxxx_id` 字段必须建立普通索引。

### 4.3 约束与性能
1.  **物理外键**：数据库中**不建立**物理外键约束，关联关系由应用程序逻辑维护，以提高读写性能和分布式扩展性。
2.  **数据类型**：
    *   金额字段统一使用 `DECIMAL(10, 2)`。
    *   布尔值/状态位使用 `TINYINT(1)`。
    *   大文本内容使用 `TEXT` 类型，若内容过大建议独立分表存储。

### 4.4 逻辑删除
*   系统全面启用 MyBatis-Plus 的逻辑删除功能。
*   所有查询操作会自动拼接 `WHERE delete_flag = 0`，确保已删除数据不会出现在业务列表中。

---

> **提示**：在本地开发前，请确保已配置好 `application-dev.yml` 中的数据库连接信息，并执行 `sql/init.sql` 初始化表结构。
