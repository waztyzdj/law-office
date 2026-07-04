# 接口总览

## 基础约定

后端统一返回结构：

```json
{
  "code": 200,
  "message": "成功",
  "data": {}
}
```

分页返回通常位于 `data` 中：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10
}
```

## 通用 CRUD 接口

继承 `BaseController` 的资源默认拥有以下接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/{resource}/list` | 列表查询，不分页。 |
| POST | `/{resource}/page` | 分页查询。 |
| POST | `/{resource}/getById` | 根据 ID 查询。 |
| POST | `/{resource}/save` | 新增或修改。 |
| POST | `/{resource}/batchSave` | 批量保存。 |
| POST | `/{resource}/delete` | 逻辑删除单条。 |
| POST | `/{resource}/batchDelete` | 批量逻辑删除。 |
| POST | `/{resource}/import` | Excel 导入。 |
| POST | `/{resource}/export` | Excel 导出。 |

## 当前资源前缀

| 模块 | 前缀 | 说明 |
| --- | --- | --- |
| 用户 | `/user` | 用户管理，另有 `/user/info`。 |
| 角色 | `/role` | 角色管理。 |
| 权限 | `/permission` | 菜单权限管理。 |
| 菜单 | `/menu` | 菜单树，非标准 CRUD。 |
| 部门 | `/depart` | 组织机构管理。 |
| 租户 | `/tenant` | 多租户管理。 |
| 字典 | `/dict` | 字典管理。 |
| 字典明细 | `/dictItem` | 字典明细管理。 |
| 通用类型 | `/category` | 通用类型管理。 |
| 文件 | `/files` | 通用文件上传、绑定和下载。 |
| 文档中心 | `/document/files` | 文档中心浏览、共享、回收站和在线文档。 |
| 审批中心 | `/workflow`、`/workflow/admin` | 审批运行时、流程定义、流程监控和流程归档。 |
| 工作台 | `/home/workbench`、`/home/admin/workbench` | 工作台布局、卡片数据、快捷入口和卡片配置管理。 |
| 认证 | `/auth` | 登录、登出、修改密码。 |

## 错误码

| code | 说明 |
| --- | --- |
| 200 | 成功。 |
| 400 | 参数错误或业务校验失败。 |
| 401 | 未登录或登录过期。 |
| 403 | 无权限。 |
| 500 | 系统内部错误。 |
