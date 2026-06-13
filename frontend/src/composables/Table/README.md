# Table Composables

表格相关的组合式函数和辅助工具。

## 目录结构

```
composables/Table/
├── index.ts                    # 统一导出
├── useTable.ts                 # 通用表格列表逻辑
├── useTableHeaderFilter.ts     # 表头筛选功能（支持文本、日期、时间）
├── useTableHelper.ts           # 表格列定义辅助函数
└── README.md                   # 本文档
```

## 📖 使用指南

### 核心功能

本模块提供以下核心功能：

1. **useTable** - 通用表格列表管理（分页、筛选、删除、排序等）
2. **useTableHeaderFilter** - 表头高级筛选功能
3. **useTableHelper** - 表格列定义辅助函数（简化列配置）

---

## 📏 偏好设置行高配置使用指南

### 功能说明

[useTable](file://e:\project\law-office\frontend\src\composables\Table\useTable.ts) 会自动从系统偏好设置中读取表格行高配置，并转换为 Ant Design Vue Table 的 `size` 属性。

**配置位置**：偏好设置 → 扩展 → 表格行高（12-64px，默认 36px）

### 使用示例

#### 方式一：直接使用 tableSize（推荐）

``vue
<script setup lang="ts">
import { useTable } from '#/composables/Table';

const {
  dataSource,
  loading,
  pagination,
  tableSize, // ← 自动从偏好设置读取的行高配置
} = useTable({ /* 配置 */ });
</script>

<template>
  <Table
    :columns="columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :size="tableSize"  ← 应用表格尺寸
    row-key="id"
  />
</template>
```

### 尺寸映射规则

| 行高范围 | size 值 | 说明 |
|---------|---------|------|
| ≤ 32px | `small` | 紧凑模式 |
| 33-47px | `middle` | 默认模式 |
| ≥ 48px | `large` | 宽松模式 |

### 注意事项

1. **响应式更新**：当用户在偏好设置中修改行高时，`tableSize` 会自动更新，表格立即响应
2. **默认值**：如果未配置行高，默认使用 36px（对应 `size="middle"`）
3. **可覆盖**：业务代码可以手动传入自定义的 `size` 值覆盖 `tableSize`
4. **类型安全**：`tableSize` 的类型为 `Ref<'small' | 'middle' | 'large'>`

---

## useTable - 通用表格列表管理

### 核心功能

提供完整的表格列表管理功能，包括：
- ✅ 数据加载与分页
- ✅ 筛选条件管理（持久化到 localStorage）
- ✅ 排序功能
- ✅ 删除操作（单个/批量）
- ✅ 行选择功能（可选）
- ✅ 响应式表格尺寸（基于偏好设置）

### 基本使用

``vue
<script setup lang="ts">
import { useTable } from '#/composables/Table';
import { userApi } from '@/api/system/user';

const {
  dataSource,
  loading,
  pagination,
  activeFilters,
  tableSize,
  loadData,
  handleDelete,
  handleTableChange,
} = useTable({
  apiConfig: {
    fetchData: (params) => userApi.page(params),
    deleteItem: (id) => userApi.delete({ id }),
    batchDeleteItems: (ids) => userApi.batchDelete(ids),
  },
  storageConfig: {
    filtersKey: 'user_list_filters', // localStorage 键名
  },
  enableRowSelection: true, // 启用行选择
});

// 初始加载
loadData();
</script>

<template>
  <Table
    :columns="columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :size="tableSize"
    :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
    @change="handleTableChange"
    row-key="id"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'action'">
        <a @click="handleDelete(record)">删除</a>
      </template>
    </template>
  </Table>
</template>
```

### 配置接口

#### ApiConfig

``typescript
interface ApiConfig {
  /** 
   * 获取数据的 API 方法（必填）
   * 
   * 重要：必须使用箭头函数包装 BaseApi 方法以保持 this 上下文！
   * 
   * ✅ 正确示例：
   * fetchData: (params) => userApi.page(params)
   * 
   * ❌ 错误示例（会导致 this 丢失）：
   * fetchData: userApi.page
   */
  fetchData: (params: BasePageReq) => Promise<any>;
  /** 删除单个项目的 API 方法（可选） */
  deleteItem?: (id: string | number) => Promise<any>;
  /** 批量删除的 API 方法（可选） */
  batchDeleteItems?: (ids: (string | number)[]) => Promise<any>;
}
```

#### UseTableConfig

``typescript
interface UseTableConfig {
  /** API 配置（必填） */
  apiConfig: ApiConfig;
  /** localStorage 配置（可选） */
  storageConfig?: StorageConfig;
  /** 删除对话框配置（可选） */
  deleteConfig?: DeleteConfig;
  /** 是否启用行选择功能，默认为 false */
  enableRowSelection?: boolean;
}
```

### 返回值

``typescript
{
  dataSource: Ref<any[]>;           // 表格数据
  loading: Ref<boolean>;            // 加载状态
  pagination: TablePaginationConfig; // 分页配置
  selectedRowKeys?: Ref<(string | number)[]>; // 选中的行（仅启用行选择时）
  activeFilters: Ref<Record<string, any>>;    // 当前筛选条件
  tableSize: Ref<'small' | 'middle' | 'large'>; // 表格尺寸
  loadData: Function;               // 加载数据方法
  handleDelete: Function;           // 删除单个项目
  handleBatchDelete?: Function;     // 批量删除（仅启用行选择时）
  onSelectChange?: Function;        // 行选择变化（仅启用行选择时）
  handleTableChange: Function;      // 表格变化处理（分页、排序、筛选）
  clearAllFilters: Function;        // 清空所有筛选
  resetPagination: Function;        // 重置分页到第一页
}
```

### 高级用法

#### 额外搜索参数

``typescript
// 传递额外的搜索参数（如搜索表单的值）
loadData({ keyword: '张三', status: 1 });
```

#### 额外筛选条件

``typescript
// 传递额外的筛选条件（会合并到 activeFilters 并持久化）
loadData({}, { departmentId_eq: 1 });
```

#### 自定义删除确认

``typescript
useTable({
  apiConfig: { /* ... */ },
  deleteConfig: {
    title: '确认删除用户',
    content: (record) => `确定要删除用户 "${record.username}" 吗？`,
    batchTitle: '确认批量删除',
    batchContent: (count) => `确定要删除选中的 ${count} 个用户吗？`,
  },
});
```

---

## useTableHelper - 表格列定义辅助函数

### 核心功能

提供 `defineTableColumn` 和 `defineTableColumns` 辅助函数，简化表格列的定义。

**主要优势：**
- ✅ 自动处理通用的列属性（对齐、筛选、排序等）
- ✅ 减少重复代码，提升可维护性
- ✅ 支持多种列类型（文本、日期、时间、枚举、操作等）
- ✅ **自动启用单元格内容省略号显示和 Tooltip 提示**
- ✅ **列标题自动不换行并显示省略号**
- ✅ **默认启用列宽拖拽调整功能**
- ✅ 灵活的配置选项

### 使用示例

#### 1. 简单文本列（带自动筛选）

``typescript
import { defineTableColumn } from '#/composables/Table';

// 只需指定宽度和标题，其他属性自动添加
const column = defineTableColumn(
  'username',
  '用户名',
  { width: 120 },
  filterState,
  emit,
  pagination
);

// 生成的列配置包含：
// - align: 'center'
// - sorter: true
// - ellipsis: true (自动启用省略号)
// - customHeaderCell: 列标题不换行
// - customRender: 自动包装 Tooltip
// - filterDropdown: 自动添加高级筛选
// - filteredValue: 自动绑定筛选状态
// - onFilter: () => true
```

#### 2. 批量定义列（推荐）

``typescript
import { defineTableColumns } from '#/composables/Table';

export function getUserColumns(filterState, emit, pagination) {
  const columns = [
    { dataIndex: 'username', title: '用户名', options: { width: 120 } },
    { dataIndex: 'email', title: '邮箱', options: { width: 180 } },
    { dataIndex: 'phone', title: '电话', options: { width: 130 } },
  ];
  
  return defineTableColumns(columns, filterState, emit, pagination, {
    tableKey: 'system_user',
  });
}
```

#### 3. Select 类型列（多选筛选）

``typescript
{
  dataIndex: 'status',
  title: '状态',
  options: {
    width: 100,
    columnType: 'select' as const,
    selectOptions: [
      { label: '正常', value: 1, color: 'green' },
      { label: '冻结', value: 2, color: 'red' },
      { label: '待审核', value: 3, color: 'orange' },
    ],
  },
}

// 特性：
// - 自动显示多选下拉框
// - 使用 in 操作符进行筛选
// - 自动根据 selectOptions 渲染带颜色的标签
// - 支持自定义颜色显示
```

#### 4. DateTime 类型列（日期/时间切换）

``typescript
{
  dataIndex: 'createTime',
  title: '创建时间',
  options: {
    width: 180,
    columnType: 'datetime' as const,
  },
}

// 特性：
// - 支持日期/时间模式切换
// - 日期模式：自动拼接时分秒（00:00:00 或 23:59:59）
// - 时间模式：使用完整的时间值
// - 智能参数转换，适配后端查询需求
```

**日期模式运算符及参数转换：**
- **等于** → `between`: `[日期 00:00:00, 日期 23:59:59]`
- **大于** → `gt`: `日期 23:59:59`
- **大于等于** → `ge`: `日期 00:00:00`
- **小于** → `lt`: `日期 00:00:00`
- **小于等于** → `le`: `日期 23:59:59`
- **在两者之间** → `between`: `[开始日期 00:00:00, 结束日期 23:59:59]`

**时间模式运算符：**
- **大于** → `gt`: 直接使用时间值
- **小于** → `lt`: 直接使用时间值
- **在两者之间** → `between`: `[开始时间, 结束时间]`

#### 5. 日期列

``typescript
{
  dataIndex: 'createTime',
  title: '创建时间',
  options: {
    width: 180,
    columnType: 'date', // 日期类型
  },
}

// 特性：
// - 自动显示日期选择器
// - 筛选条件：等于、大于、小于、在两者之间
// - 格式化：YYYY-MM-DD
```

#### 6. 日期时间列

``typescript
{
  dataIndex: 'updateTime',
  title: '更新时间',
  options: {
    width: 180,
    columnType: 'datetime', // 日期时间类型
  },
}

// 特性：
// - 自动显示日期时间选择器（含时分秒）
// - 筛选条件：等于、大于、小于、在两者之间
// - 格式化：YYYY-MM-DD HH:mm:ss
```

#### 7. 枚举/状态列

``typescript
import { h } from 'vue';
import { Tag } from 'ant-design-vue';

{
  dataIndex: 'status',
  title: '状态',
  options: {
    width: 100,
    sorter: false, // 禁用排序
    filters: [
      { text: '正常', value: 1 },
      { text: '冻结', value: 2 },
    ],
    onFilter: (value, record) => record.status === value,
    customRender: ({ record }) => {
      const statusMap = {
        1: { text: '正常', color: 'green' },
        2: { text: '冻结', color: 'red' },
      };
      const status = statusMap[record.status];
      return h(Tag, { color: status.color }, () => status.text);
    },
  },
}
```

#### 8. 操作列（无筛选）

``typescript
import { h } from 'vue';
import { Space } from 'ant-design-vue';

{
  dataIndex: 'action',
  title: '操作',
  options: {
    width: 150,
    fixed: 'right' as const,
    hasFilter: false, // 禁用筛选
    customRender: ({ record }) => {
      return h(Space, { size: 'middle' }, {
        default: () => [
          h('a', { onClick: () => emit('edit', record) }, '编辑'),
          h('a', { onClick: () => emit('delete', record) }, '删除'),
        ],
      });
    },
  },
}
```

### TableColumnOptions 配置项

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| width | number | - | 列宽度 |
| sorter | boolean | true | 是否启用排序 |
| fixed | 'left' \| 'right' \| boolean | - | 固定列位置 |
| customRender | Function | - | 自定义渲染函数 |
| filters | Array | - | 枚举过滤选项 |
| onFilter | Function | - | 枚举过滤函数 |
| hasFilter | boolean | true | 是否启用筛选 |
| **columnType** | **'text' \| 'date' \| 'datetime' \| 'number' \| 'select'** | **'text'** | **列类型** |
| ...rest | any | - | 其他 Ant Design Vue 列配置 |

### 列类型说明

| 类型 | 筛选控件 | 筛选条件 | 格式 |
|------|----------|----------|------|
| text | 输入框 | 包含、等于、不等于、大于、小于等 | - |
| date | 日期选择器 | 等于、大于、小于、在两者之间 | YYYY-MM-DD |
| datetime | 日期/时间切换 | 日期模式：等于、大于、小于等<br/>时间模式：大于、小于、在两者之间 | YYYY-MM-DD HH:mm:ss |
| select | 多选下拉框 | in（在...之中） | 自动渲染标签 |
| number | 输入框 | 同 text | - |

### SelectOption 配置项

当 `columnType` 为 `select` 时，需要配置 `selectOptions`：

``typescript
interface SelectOption {
  label: string;   // 显示文本
  value: any;      // 值
  color?: string;  // 标签颜色（可选）
}
```

**颜色支持：**
- 预设颜色：`green`, `red`, `blue`, `orange`, `purple`, `cyan` 等
- 十六进制颜色：`#ff0000`, `#00ff00` 等
- 不配置 color 则显示普通文本

### 自动省略号和 Tooltip 功能

所有通过 `defineTableColumn` 或 `defineTableColumns` 定义的列都会自动获得以下特性：

1. **列标题不换行**：标题过长时自动显示省略号
2. **单元格内容不换行**：内容超出列宽时显示省略号
3. **鼠标悬停显示完整内容**：悬停在单元格上时，通过 Tooltip 显示完整文本

这些特性无需额外配置，自动应用于所有列。如果需要自定义行为，可以通过 `customRender` 覆盖默认渲染。

### 列宽持久化存储

- **存储位置**: `localStorage`
- **键名**: `table_columnWidths_v2`
- **格式**: `{ "system_user:username": 120, "system_user:email": 180, ... }`
- **隔离规则**: 使用 `defineTableColumns` 时必须传入稳定且模块唯一的 `tableKey`，列宽会按 `tableKey:dataIndex` 保存，避免不同列表的同名列互相污染。
- **清除方法**: `localStorage.removeItem('table_columnWidths_v2')`

### 宽度限制

- **最小宽度**: 60px（防止列过窄无法显示内容）
- **最大宽度**: 800px（防止单列过宽影响布局）

### 完整示例

查看 [`useUserColumns.ts`](../../views/system/user/hooks/useUserColumns.ts) 了解完整的实际应用，包括日期时间列的使用。

---

## 其他功能

- **useTableHeaderFilter**: 表头高级筛选功能（支持文本、日期、时间）
