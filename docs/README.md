# LawOffice 文档中心

本文档目录用于集中维护项目说明、需求、架构、数据库、接口、模块设计、部署和质量规范。

## 阅读顺序

1. `00-overview/`：先了解系统目标、术语和整体边界。
2. `01-requirements/`：查看功能清单、角色权限和关键业务规则。
3. `02-architecture/`：理解前后端、后端分层、认证权限、租户、日志等设计。
4. `03-database/`：查看表结构、表关系、字典和 SQL 变更规则。
5. `04-api/`：查看接口协议、通用 CRUD、认证和系统管理接口。
6. `05-modules/`：按模块查看功能说明、代码入口、数据表和接口关联。
7. `06-deployment/`：本地开发、配置、部署和排障。
8. `07-quality/`：编码规范、测试策略和发布检查。

## 维护规则

- 代码改动涉及接口、表结构、权限、配置、功能行为时，必须同步更新对应文档。
- 模块文档优先写在 `05-modules/<domain>/<module>.md`，公共协议和规范写在前面的专题目录。
- 文档要写当前事实，不记录已删除路径、废弃方案或无执行价值的历史。
- Mermaid 图可以直接写在 Markdown 中；导出的图片放在 `assets/images/`。

## 文档地图

- 系统介绍：[00-overview/system-introduction.md](00-overview/system-introduction.md)
- 术语表：[00-overview/glossary.md](00-overview/glossary.md)
- 功能清单：[01-requirements/feature-list.md](01-requirements/feature-list.md)
- 代码结构：[02-architecture/code-structure.md](02-architecture/code-structure.md)
- 系统架构：[02-architecture/system-architecture.md](02-architecture/system-architecture.md)
- 数据库说明：[03-database/database-overview.md](03-database/database-overview.md)
- 接口总览：[04-api/api-overview.md](04-api/api-overview.md)
- 系统管理模块：[05-modules/system/README.md](05-modules/system/README.md)
- 文档中心模块：[05-modules/document/README.md](05-modules/document/README.md)
- 本地开发：[06-deployment/local-development.md](06-deployment/local-development.md)
- 编码规范：[07-quality/coding-standards.md](07-quality/coding-standards.md)
