# 系统介绍

LawOffice 是面向律师事务所的业务管理系统，采用前后端分离架构，当前重点建设系统管理、认证授权、多租户、日志审计、字典、组织机构、文件与后续业务模块基础能力。

## 系统目标

- 统一管理律所用户、角色、权限、部门、租户和菜单。
- 为案件、OA、知识库等业务模块提供可复用的认证、权限、租户、日志和 CRUD 框架。
- 通过前端 Vben Admin 与后端 Spring Boot 服务，提供可扩展的后台管理能力。
- 保持接口、数据库和权限模型的清晰约定，方便后续 AI 和开发者持续演进。

## 当前技术栈

- 前端：Vue 3、TypeScript、Ant Design Vue、Vben Admin、Pinia、Vue Router、Vite。
- 后端：Java 17、Spring Boot 3.2.5、MyBatis-Plus、Shiro、JWT、Redis、MinIO、EasyExcel。
- 数据库：MySQL，建表脚本位于 `sql/建表脚本.sql`。

## 核心能力

- 认证登录：用户名密码登录、JWT token、Redis token 存储、登出、修改密码。
- 权限控制：菜单权限、角色权限、按钮权限、接口权限注解。
- 多租户：租户上下文、租户字段、MyBatis-Plus 租户处理。
- 通用 CRUD：后端 `BaseController` 与 `BaseServiceImpl`，前端 `BaseApi` 对接。
- 日志审计：AOP 自动记录操作日志，日志表为 `sys_log`。
- 数据字典：字典主表和字典明细表。
- 文件能力：文件元数据、MinIO 配置、Excel 导入导出。

## 代码入口

- 前端应用：`frontend/`
- 后端服务：`backend/`
- 数据库脚本：`sql/`
- 前端编码规范：`frontend/AGENTS.md`
- 后端编码规范：`backend/AGENTS.md`
