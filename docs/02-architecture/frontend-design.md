# 前端详细设计

## 应用结构

前端基于 Vben Admin 的 Vue 3 + TypeScript 项目，UI 使用 Ant Design Vue。

核心目录：

- `src/api`：接口定义。
- `src/framework/api`：请求客户端和通用 API 基础能力。
- `src/views`：页面。
- `src/composables/Table`：通用表格能力。
- `src/store`：认证等应用状态。
- `src/constants/permissions.ts`：权限码。

## 接口调用

系统管理模块优先使用 `BaseApi` 对接后端标准 CRUD。

```ts
export const userApi = new BaseApi('/user');
```

用户模块在 `src/api/system/user.ts` 中导出便捷方法：

- `pageUsers`
- `getUserById`
- `saveUser`
- `deleteUser`
- `batchDeleteUsers`
- `exportUsers`
- `importUsers`

## 页面结构

管理页推荐结构：

```text
views/system/user/
  index.vue
  components/
  hooks/
```

- `index.vue` 负责组装。
- `components` 负责 UI 表现。
- `hooks` 负责表格、筛选、列定义和业务流程。

## 权限控制

- 按钮权限使用 `v-access:code`。
- 逻辑权限判断使用 `useAccess`。
- 权限常量维护在 `src/constants/permissions.ts`。
