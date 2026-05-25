# 通用类型

## 功能说明

通用类型模块用于维护系统分类或类型数据。

## 代码入口

- Controller：`backend/src/main/java/com/lawoffice/system/controller/SysCategoryController.java`
- Service：`backend/src/main/java/com/lawoffice/system/service/impl/SysCategoryServiceImpl.java`
- Entity：`backend/src/main/java/com/lawoffice/system/entity/SysCategory.java`
- 前端页面：`frontend/src/views/system/category/index.vue`

## 数据表

- `sys_category`

## 接口

资源前缀：`/category`，支持通用 CRUD。

## 关键规则

- `code` 在同一租户内唯一。
- 新建租户时会复制默认租户 `0` 的通用类型；默认租户 `0` 新增通用类型时，会补充复制到所有启用租户，已存在的租户数据不覆盖。
- 分类树或层级关系如后续启用，应同步补充本文件。
