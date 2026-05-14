# Table Composables

表格相关的组合式函数和辅助工具。

## 目录结构

```
composables/Table/
├── index.ts                    # 统一导出
├── useTable.ts                 # 通用表格列表逻辑
├── useTableHeaderFilter.ts     # 表头筛选功能（支持文本、日期、时间）
├── useTableHelper.ts           # 表格列定义辅助函数 ⭐ 新增
└── README.md                   # 本文档
```

## useTableHelper - 表格列定义辅助函数

### 核心功能

提供 `defineTableColumn` 和 `defineTableColumns` 辅助函数，简化表格列的定义。

**主要优势：**
- ✅ 自动处理通用的列属性（对齐、筛选、排序等）
- ✅ 减少重复代码，提升可维护性
- ✅ 支持多种列类型（文本、日期、时间、枚举、操作等）
- ✅ 灵活的配置选项

### 使用示例

#### 1. 简单文本列（带自动筛选）

```typescript
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
// - filterDropdown: 自动添加高级筛选
// - filteredValue: 自动绑定筛选状态
// - onFilter: () => true
```

#### 2. 批量定义列（推荐）

```typescript
import { defineTableColumns } from '#/composables/Table';

export function getUserColumns(filterState, emit, pagination) {
  const columns = [
    { dataIndex: 'username', title: '用户名', options: { width: 120 } },
    { dataIndex: 'email', title: '邮箱', options: { width: 180 } },
    { dataIndex: 'phone', title: '电话', options: { width: 130 } },
  ];
  
  return defineTableColumns(columns, filterState, emit, pagination);
}
```

#### 3. Select 类型列（多选筛选）🆕

```typescript
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

#### 4. 日期列 🆕

```typescript
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

#### 5. 日期时间列 🆕

```typescript
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

#### 6. 枚举/状态列

```typescript
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

#### 7. 操作列（无筛选）

```typescript
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
| **columnType** | **'text' \| 'date' \| 'datetime'** | **'text'** | **列类型（新增）** |
| ...rest | any | - | 其他 Ant Design Vue 列配置 |

### 列类型说明

| 类型 | 筛选控件 | 筛选条件 | 格式 |
|------|----------|----------|------|
| text | 输入框 | 包含、等于、不等于、大于、小于等 | - |
| date | 日期选择器 | 等于、大于、小于、在两者之间 | YYYY-MM-DD |
| datetime | 日期时间选择器 | 等于、大于、小于、在两者之间 | YYYY-MM-DD HH:mm:ss |
| **select** | **多选下拉框** | **in（在...之中）** | **自动渲染标签** |
| number | 输入框 | 同 text | - |

### SelectOption 配置项

当 `columnType` 为 `select` 时，需要配置 `selectOptions`：

```typescript
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

### 完整示例

查看 [`useUserColumns.ts`](../../views/system/user/hooks/useUserColumns.ts) 了解完整的实际应用，包括日期时间列的使用。

## 其他功能

- **useTable**: 通用表格列表逻辑（分页、筛选、删除等）
- **useTableHeaderFilter**: 表头高级筛选功能（支持文本、日期、时间）
