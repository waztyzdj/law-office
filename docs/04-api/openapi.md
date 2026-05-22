# OpenAPI / Swagger

后端集成 SpringDoc OpenAPI，配置位于：

```text
backend/src/main/java/com/lawoffice/framework/config/SwaggerConfig.java
```

接口注解使用：

- `@Tag`
- `@Operation`
- `@Schema`

## 维护要求

- 新增 Controller 时补充 `@Tag`。
- 新增接口时补充 `@Operation`。
- Req/VO/Entity 关键字段补充 `@Schema`。
- 接口行为变更时同步更新 `docs/04-api/`。
