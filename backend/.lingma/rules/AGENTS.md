# 项目开发规范 (必须遵守)

## 后端代码规范
1. **分层架构**：严格遵循 Controller -> Service -> Mapper 结构。
2. **基类继承**：Controller 必须继承 `BaseController`，Service 实现类必须继承 `BaseServiceImpl`。
3. **命名规范**：接口以 `I` 开头，实现类以 `Impl` 结尾。实体类需添加 `@ModuleInfo` 注解。
4. **异步日志**：不要在 Controller 中手动记录日志，依赖 `AutoLogAspect` 自动处理。
5. **权限控制**：通过 `@ModuleInfo` 注解定义模块权限前缀，系统自动生成权限编码（模块名:操作类型）。
6. **钩子方法**：在 Service 层使用 `doBeforeXXX` 和 `doAfterXXX` 钩子方法进行业务扩展。
7. **异常处理**：统一使用 `BaseResult.error()` 返回错误信息，避免直接抛出异常。
8. **数据转换**：使用 `BeanUtil.copyProperties()` 进行对象属性复制。
9. **查询构建**：使用 `QueryWrapperBuilderUtils.build()` 构建动态查询条件。
10. **上下文传递**：通过 `RequestContext` 传递请求上下文信息。

## 数据库规范
1. **主键**：统一使用 `id`，类型 `VARCHAR(64)`。
2. **外键**：命名为 `xxxx_id`，类型 `VARCHAR(64)`。
3. **字符串长度**：必须是 2 的 N 次幂（16, 32, 64, 128...）。
4. **索引命名**：`pk_表名`, `uk_表名_字段`, `idx_表名_字段`。

## Framework 使用规范

### Controller 层规范
1. **继承 BaseController**：所有业务 Controller 必须继承 `BaseController<S, E, V, R>`
   - S: Service 接口类型
   - E: 实体类型（继承 BaseEntity）
   - V: VO 类型（继承 BaseVO）
   - R: Req 类型（继承 BaseReq）

2. **添加 @ModuleInfo 注解**：用于权限控制和模块标识
   ```java
   @ModuleInfo(value = "user", name = "用户管理", description = "系统用户信息管理")
   ```

3. **构造函数注入 Service**：
   ```java
   @Autowired
   public UserController(IUserService userService) {
       this.baseService = userService;
   }
   ```

4. **自定义方法**：对于非标准 CRUD 操作，可直接编写方法并添加相应注解

### Service 层规范
1. **接口定义**：继承 `IBaseService<E, V>`
2. **实现类**：继承 `BaseServiceImpl<M, E, V>`
3. **钩子方法重写**：根据业务需要重写以下方法：
   - `doBeforeSave()` / `doAfterSave()`
   - `doBeforeList()` / `doAfterList()`
   - `doBeforePage()` / `doAfterPage()`
   - `doBeforeDelete()` / `doAfterDelete()`
   - `doBeforeBatchSave()` / `doAfterBatchSave()`
   - `doBeforeImport()` / `doAfterImport()`
   - `doBeforeExport()` / `doAfterExport()`

### Entity 实体类规范
1. **继承 BaseEntity**：包含 id、createTime、createBy、updateTime、updateBy、deleteFlag 等字段
2. **添加 @ModuleInfo 注解**：用于权限控制
3. **使用 Lombok**：推荐使用 `@Data` 注解
4. **字段命名**：采用驼峰命名法，与数据库字段对应

### VO/DTO/Req 规范
1. **VO (View Object)**：继承 `BaseVO`，用于前端展示
2. **DTO (Data Transfer Object)**：继承 `BaseDTO`，用于服务间数据传输
3. **Req (Request)**：继承 `BaseReq`，用于接收前端请求参数
4. **分页请求**：使用 `BasePageReq`，包含 pageNum 和 pageSize

### 权限控制规范
1. **自动权限**：基于 `@ModuleInfo` 注解和方法名自动生成权限编码
   - 查看权限：`module:view` (getById, list, page, export)
   - 编辑权限：`module:edit` (save, batchSave, delete, batchDelete, import)
2. **手动权限**：使用 `@RequiresPermission` 注解指定具体权限
   ```java
   @RequiresPermission({"user:add", "user:edit"})
   ```

### 日志记录规范
1. **自动日志**：通过 `AutoLogAspect` 自动记录操作日志
2. **手动日志**：在必要时使用 `@AutoLog` 注解自定义日志内容
3. **日志级别**：合理使用 debug、info、warn、error 级别

### 异常处理规范
1. **统一返回**：使用 `BaseResult` 包装返回结果
2. **成功响应**：`BaseResult.success(data)` 或 `BaseResult.success()`
3. **错误响应**：`BaseResult.error(message)` 或 `BaseResult.error(code, message)`
4. **权限异常**：抛出 `PermissionDeniedException` 返回 403 状态码

### 数据导入导出规范
1. **Excel 导入**：实现 `importExcel()` 方法，处理数据验证和转换
2. **Excel 导出**：实现 `exportExcel()` 方法，支持动态查询条件
3. **文件上传**：使用 MultipartFile 处理文件上传

### 工具类使用规范
1. **Bean 复制**：使用 `BeanUtil.copyProperties()`
2. **查询构建**：使用 `QueryWrapperBuilderUtils.build()`
3. **Excel 处理**：使用 `ExcelUtils` 相关方法
4. **实体填充**：使用 `EntityFillUtils.fillAuditFields()` 自动填充审计字段
5. **上下文获取**：使用 `RequestContextUtils.buildContext()` 构建请求上下文
