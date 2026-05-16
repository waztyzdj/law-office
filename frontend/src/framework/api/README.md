# API 模块说明

## 目录结构

```
src/api/
├── base/                  # 基础 API 工具类
│   └── base.api.ts        # 通用 CRUD API 工具类（核心）
├── request.ts             # HTTP 请求客户端配置
├── index.ts               # API 模块入口
└── system/                # 系统管理相关 API
    ├── user.ts            # 用户管理 API
    ├── auth.ts            # 认证相关 API
    └── menu.ts            # 菜单管理 API
```

## 核心文件：base.api.ts

`base.api.ts` 提供了基于后端 `BaseController` 的通用 CRUD API 工具类。

### 使用方法

```typescript
import { BaseApi } from '#/api/base';

// 创建 API 实例
const userApi = new BaseApi('/user');

// 调用方法
await userApi.page({ pageNum: 1, pageSize: 10 });
await userApi.save(userData);
await userApi.delete({ id: '123' });
```

### 提供的方法

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| `list(params?)` | 列表查询（不分页） | BaseQueryReq | Promise\<T[]\> |
| `page(params)` | 分页查询 | BasePageReq | Promise\<any\> |
| `getById(params)` | 根据ID查询 | BaseReq | Promise\<T\> |
| `save(data)` | 保存（新增/修改） | T | Promise\<T\> |
| `batchSave(dataList)` | 批量保存 | T[] | Promise\<T[]\> |
| `delete(params)` | 删除单个 | BaseReq | Promise\<void\> |
| `batchDelete(ids)` | 批量删除 | string[] | Promise\<void\> |
| `importExcel(file)` | 导入 Excel | File | Promise\<number\> |
| `exportExcel(params?)` | 导出 Excel | BaseQueryReq | Promise\<Blob\> |

## 业务模块示例

查看 `system/user.ts` 了解如何在业务模块中使用 `BaseApi`。

### 快速创建新模块 API

```typescript
// src/api/system/role.ts

import type { BaseListParams } from '#/composables/Table/useTable';
import { BaseApi } from '#/api/base';

export interface RoleInfo {
  id?: string;
  roleName?: string;
  // ... 其他字段
}

const roleApi = new BaseApi('/role');

export async function getRoleListApi(params: BaseListParams) {
  const backendParams = {
    pageNum: params.current || 1,
    pageSize: params.size || 10,
    queryParams: params.queryParams || {},
  };

  if (params.sortField) {
    Object.assign(backendParams, {
      sortField: params.sortField,
      sortOrder: params.sortOrder || 'desc',
    });
  }

  const response = await roleApi.page(backendParams);
  return {
    items: response.records || [],
    total: response.total || 0,
  };
}

export async function saveRoleApi(data: RoleInfo) {
  return roleApi.save(data);
}

export async function deleteRoleApi(id: string) {
  return roleApi.delete({ id });
}
```

就这么简单！
