# 用户管理模块 - 代码组织结构

## 📁 目录结构

```
views/system/user/
├── index.vue                    # 主页面（布局与协调）
├── composables/                 # 组合式函数（业务逻辑）
│   ├── useUserSearch.ts        # 搜索表单逻辑
│   └── useUserList.ts          # 用户列表逻辑
└── components/                  # 子组件（UI展示）
    ├── UserSearchForm.vue      # 搜索表单组件
    └── UserTable.vue           # 用户表格组件
```

## 🎯 设计理念

### Vue 3 最佳实践

1. **关注点分离（Separation of Concerns）**
   - **Composables**: 封装业务逻辑和数据状态
   - **Components**: 负责UI展示和用户交互
   - **Index.vue**: 作为容器，组合各个部分

2. **单一职责原则（Single Responsibility Principle）**
   - 每个文件只负责一个明确的功能
   - 便于测试、维护和复用

3. **可组合性（Composability）**
   - Composables 可以在不同组件间复用
   - 组件通过 Props 和 Emits 进行通信

## 📋 各模块说明

### 1. Composables（组合式函数）

#### `useUserSearch.ts`
**职责**：管理搜索表单的状态和操作

**导出内容**：
- `searchForm`: 响应式搜索表单对象
- `resetSearchForm()`: 重置搜索表单
- `getSearchParams()`: 获取搜索参数（用于API调用）

**使用示例**：
```typescript
const { searchForm, resetSearchForm, getSearchParams } = useUserSearch();
```

#### `useUserList.ts`
**职责**：管理用户列表的数据加载、分页、删除等操作

**导出内容**：
- `dataSource`: 用户列表数据
- `loading`: 加载状态
- `pagination`: 分页配置
- `selectedRowKeys`: 选中的行
- `loadData()`: 加载数据
- `handleDelete()`: 删除单个用户
- `handleBatchDelete()`: 批量删除
- `onSelectChange()`: 选择行变化
- `handleTableChange()`: 分页变化

**使用示例**：
```typescript
const {
  dataSource,
  loading,
  pagination,
  selectedRowKeys,
  loadData,
  handleDelete,
  // ...
} = useUserList(getSearchParams);
```

### 2. Components（子组件）

#### `UserSearchForm.vue`
**职责**：渲染搜索表单UI

**Props**：
- `searchForm`: 搜索表单数据对象

**Emits**：
- `search`: 触发搜索
- `reset`: 重置搜索

**特点**：
- 纯展示组件，不包含业务逻辑
- 通过 Props 接收数据，通过 Emits 通知父组件

#### `UserTable.vue`
**职责**：渲染用户列表表格

**Props**：
- `dataSource`: 表格数据
- `loading`: 加载状态
- `pagination`: 分页配置
- `selectedRowKeys`: 选中的行键

**Emits**：
- `edit`: 编辑用户
- `delete`: 删除用户
- `change`: 分页变化
- `select-change`: 选择行变化
- `batch-delete`: 批量删除
- `add`: 新增用户

**特点**：
- 包含表格列定义和自定义渲染逻辑
- 所有操作通过 Emits 向上传递

### 3. Index.vue（主页面）

**职责**：
- 组合 Composables 和 Components
- 协调各组件之间的交互
- 处理顶层事件（如编辑、新增）

**特点**：
- 代码简洁，易于理解
- 只负责"胶水"逻辑，不包含具体实现

## 🔄 数据流

```
用户操作
   ↓
Component (emit event)
   ↓
Index.vue (handle event)
   ↓
Composable (update state / call API)
   ↓
Component (receive new props)
   ↓
UI 更新
```

## ✨ 优势

### 1. **可维护性**
- 每个文件职责清晰，修改时影响范围小
- 查找问题更快，定位更准确

### 2. **可测试性**
- Composables 可以独立单元测试
- Components 可以单独进行组件测试

### 3. **可复用性**
- Composables 可在其他页面复用（如角色管理、部门管理）
- Components 可作为通用组件使用

### 4. **可扩展性**
- 添加新功能只需新增对应的 Composable 或 Component
- 不影响现有代码结构

### 5. **团队协作**
- 不同开发者可以同时处理不同模块
- 减少代码冲突

## 🚀 后续扩展建议

### 1. 添加用户编辑对话框
```
components/
└── UserModal.vue  # 新增/编辑用户的模态框
```

### 2. 添加权限控制
```typescript
// 在 composable 中添加
import { useAccess } from '@vben/access';

const { hasAccess } = useAccess();
const canEdit = computed(() => hasAccess('user:edit'));
```

### 3. 添加数据导出功能
```typescript
// 在 useUserList.ts 中添加
const handleExport = async () => {
  // 导出逻辑
};
```

### 4. 添加高级搜索
```
components/
└── UserAdvancedSearch.vue  # 高级搜索面板
```

## 📝 注意事项

1. **避免在组件中直接调用 API**
   - ❌ 不要在 Component 中 import API
   - ✅ 应该在 Composable 中调用 API

2. **保持 Composables 纯净**
   - ❌ 不要在 Composable 中操作 DOM
   - ✅ 只处理数据和业务逻辑

3. **组件通信规范**
   - 父子通信：Props + Emits
   - 跨级通信：Provide/Inject 或 Pinia

4. **类型安全**
   - 所有 Props 和 Emits 都要定义 TypeScript 类型
   - Composables 的返回值也要明确类型

## 🔗 相关资源

- [Vue 3 Composition API](https://vuejs.org/guide/extras/composition-api-faq.html)
- [Vue 3 最佳实践](https://vuejs.org/style-guide/)
- [Ant Design Vue](https://antdv.com/)
