# 代码结构说明

```text
law-office/
  backend/                 # Spring Boot 后端
  frontend/                # Vue 3 + Ant Design Vue + Vben 前端
  sql/                     # 数据库脚本
  docs/                    # 项目文档
```

## 后端结构

```text
backend/src/main/java/com/lawoffice/
  framework/               # 通用框架层
    annotation/            # AutoLog、ModuleInfo
    aspect/                # 操作日志 AOP
    config/                # CORS、Redis、MyBatis-Plus、租户、异常处理等配置
    controller/            # BaseController
    dto/                   # BaseDTO、BasePageDTO、RequestContext
    entity/                # BaseEntity、BaseTenantEntity、SysLog
    exception/             # 权限等异常
    mapper/                # 通用 Mapper
    req/                   # BaseReq、BaseQueryReq、BasePageReq
    result/                # BaseResult
    service/               # IBaseService、BaseServiceImpl
    util/                  # 查询构建、请求上下文
    vo/                    # BaseVO、PageVO、SysLogVO
  system/                  # 系统管理域
  case/                    # 案件业务域预留
  oa/                      # OA 业务域预留
  home/                    # 首页业务域预留
  util/                    # 跨模块工具类
```

## 前端结构

```text
frontend/
  src/
    adapter/               # Vben 与 Ant Design Vue 适配
    api/                   # 业务接口
    composables/           # 项目级组合函数
    constants/             # 权限码等常量
    framework/api/         # 请求基建
    layouts/               # 布局
    locales/               # 国际化
    router/                # 路由
    store/                 # Pinia store
    views/                 # 页面
  packages/                # Vben workspace packages
  internal/                # 构建和 lint 配置
```

## 代码规范入口

- 前端：`frontend/AGENTS.md`
- 后端：`backend/AGENTS.md`
