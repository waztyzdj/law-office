# 术语表

| 术语 | 说明 |
| --- | --- |
| 用户 | 系统登录主体，对应 `sys_user`。 |
| 角色 | 权限集合，对应 `sys_role`。 |
| 权限 | 菜单、按钮或接口权限，对应 `sys_permission`。 |
| 菜单 | 前端路由和导航入口，当前由权限数据转换为菜单树。 |
| 租户 | 数据隔离单位，对应 `sys_tenant`。 |
| 部门 | 组织机构节点，对应 `sys_depart`。 |
| 数据字典 | 系统枚举和配置项集合，对应 `sys_dict`、`sys_dict_item`。 |
| 逻辑删除 | 通过 `delete_flag` 标记删除，查询默认过滤已删除数据。 |
| BaseController | 后端通用 CRUD 控制器基类。 |
| BaseServiceImpl | 后端通用 CRUD 服务实现基类。 |
| BaseResult | 后端统一响应结构。 |
| BaseApi | 前端通用 CRUD API 封装。 |
| Req | 请求对象，用于接收前端参数。 |
| VO | 响应对象，用于返回前端展示数据。 |
| DTO | 服务层内部传递对象。 |
