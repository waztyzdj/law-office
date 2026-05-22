# 字典管理

## 功能说明

字典管理用于维护系统枚举、下拉选项和可配置状态项。

## 代码入口

- 字典 Controller：`backend/src/main/java/com/lawoffice/system/controller/SysDictController.java`
- 字典明细 Controller：`backend/src/main/java/com/lawoffice/system/controller/SysDictItemController.java`
- Entity：`SysDict`、`SysDictItem`
- 前端页面：`frontend/src/views/system/dict/index.vue`

## 数据表

- `sys_dict`
- `sys_dict_item`

## 接口

- `/dict/*`：字典主表通用 CRUD。
- `/dictItem/*`：字典明细通用 CRUD。

## 关键规则

- `dict_code` 唯一。
- 字典项通过 `dict_id` 关联字典。
- 前后端硬编码枚举应逐步沉淀为字典。
