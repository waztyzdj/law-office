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

## ✨ 新特性（v2.0）

### 1. 列宽拖拽调整 🆕
- ✅ **默认启用**: 所有通过 `defineTableColumn` 或 `defineTableColumns` 定义的列自动支持列宽拖拽
- ✅ **持久化存储**: 调整后的列宽自动保存到 localStorage，页面刷新后恢复
- ✅ **宽度限制**: 最小 60px，最大 800px
- ✅ **视觉反馈**: 鼠标移到列头右侧边缘时显示 `col-resize` 光标

### 2. 自动横向滚动 🆕
- ✅ **智能判断**: 当总列宽超过表格容器宽度时，自动启用横向滚动条
- ✅ **固定列兼容**: 自动计算非固定列 + 右侧固定列的总宽度，避免遮挡

### 3. 操作列自动冻结 🆕
- ✅ **自动识别**: 检测到 `dataIndex` 为 `'action'` 的列时，自动固定在右侧
- ✅ **可配置**: 支持自定义操作列标识和禁用自动冻结

### 4. SmartTable 智能表格组件 🆕
- ✅ **零配置**: 业务代码无需任何修改，自动享受所有新功能
- ✅ **自动处理**: 内部自动处理操作列冻结、横向滚动、列宽调整
- ✅ **完全兼容**: 保留 Ant Design Vue Table 的所有 API 和插槽
- ✅ **可覆盖**: 业务代码可传入自定义 `scroll` 配置覆盖默认行为

---

## SmartTable - 智能表格组件（推荐使用）

### 核心优势

**业务代码零修改**，只需将 `<Table>` 替换为 `<SmartTable>`，即可自动享受：
- 列宽拖拽调整
- 操作列自动冻结
- 智能横向滚动
- 固定列不遮挡内容

### 使用示例

#### 基础使用（推荐）

``vue
<script setup lang="ts">
import { computed } from 'vue';
import { SmartTable } from '#/composables/Table';
import { getUserColumns } from './hooks/useUserColumns';

const columns = computed(() => getUserColumns(filterState, emit, pagination));
</script>

<template>
  <!-- 只需替换 Table 为 SmartTable，其他完全一样 -->
  <SmartTable
    :columns="columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    row-key="id"
    bordered
  />
</template>
```

#### 保留自定义配置

如果业务代码需要自定义 `scroll` 配置，可以传入覆盖默认行为：

``vue
<template>
  <SmartTable
    :columns="columns"
    :scroll="{ x: 1500, y: 500 }"
    ...
  />
</template>
```

#### 使用所有 Ant Design Vue Table API

[SmartTable](file://e:\project\law-office\frontend\src\composables\Table\TableWrapper.vue) 完全透传 Ant Design Vue Table 的所有属性和事件：

``vue
<template>
  <SmartTable
    :columns="columns"
    :data-source="dataSource"
    :row-selection="rowSelection"
    :custom-row="customRow"
    :custom-header-row="customHeaderRow"
    @expand="handleExpand"
    @resize-column="handleResize"
  >
    <!-- 支持所有插槽 -->
    <template #bodyCell="{ column, record }">
      <!-- 自定义单元格渲染 -->
    </template>
  </SmartTable>
</template>
```

---

## 📖 使用指南

### 零配置使用（推荐）

**好消息！** 如果你已经在使用 `defineTableColumn` 或 `defineTableColumns`，那么：

✅ **列宽拖拽调整已自动启用** - 无需任何修改  
✅ **单元格省略号和 Tooltip 已自动启用** - 无需任何修改  
✅ **列标题不换行已自动启用** - 无需任何修改  

``vue
<!-- 现有代码保持不变，即可享受新功能 -->
<script setup lang="ts">
import { getUserColumns } from './hooks/useUserColumns';

const columns = computed(() => getUserColumns(filterState, emit, pagination));
</script>

<template>
  <Table :columns="columns" ... />
</template>
```

### 可选增强配置

如果你需要以下功能，可以添加少量配置：

#### 1. 自动冻结操作列 + 智能横向滚动

``vue
<script setup lang="ts">
import { computed } from 'vue';
import { Table } from 'ant-design-vue';
import { 
  generateTableScroll, 
  autoFreezeActionColumn 
} from '#/composables/Table';
import { getUserColumns } from './hooks/useUserColumns';

// 基础列定义（来自 hooks）
const baseColumns = computed(() => 
  getUserColumns(filterState, emit, pagination)
);

// 自动处理操作列冻结
const columns = computed(() => 
  autoFreezeActionColumn(baseColumns.value)
);

// 生成智能 scroll 配置
const scrollConfig = computed(() => 
  generateTableScroll(columns.value, {
    enableScroll: true,        // 启用横向滚动（默认 true）
    minTableWidth: 800,        // 最小表格宽度（默认 800）
  })
);
</script>

<template>
  <Table
    :columns="columns"
    :scroll="scrollConfig"     <!-- 添加这一行 -->
    ...
  />
</template>
```

#### 2. 禁用自动冻结操作列

如果某些表格不需要自动冻结操作列：

``typescript
const columns = autoFreezeActionColumn(baseColumns.value, {
  autoFreezeActionColumn: false, // 禁用自动冻结
});
```

#### 3. 自定义操作列标识

如果你的操作列不是 `action`，而是其他名称（如 `operations`）：

``typescript
const columns = autoFreezeActionColumn(baseColumns.value, {
  actionColumnKey: 'operations', // 自定义操作列 key
});
```

#### 4. 手动控制操作列冻结

如果你希望在列定义中手动控制（保持原有行为）：

``typescript
{
  dataIndex: 'action',
  title: '操作',
  options: {
    width: 150,
    fixed: 'right' as const, // 手动指定，autoFreezeActionColumn 会跳过此列
    hasFilter: false,
  },
}
```

---

## 🎯 最佳实践建议

### 场景 1：简单列表页面
**推荐**: 零配置，直接使用现有代码

``vue
<Table :columns="columns" />
```

### 场景 2：复杂表格（多列、大数据量）
**推荐**: 启用自动冻结和智能滚动

``vue
<Table 
  :columns="autoFreezeActionColumn(columns)"
  :scroll="generateTableScroll(columns)"
/>
```

### 场景 3：需要完全自定义
**推荐**: 在列定义中手动配置所有选项

``typescript
{
  dataIndex: 'action',
  title: '操作',
  options: {
    width: 150,
    fixed: 'right',
    hasFilter: false,
    customRender: ...
  },
}
```

---

## 🔧 技术细节

### 列宽持久化存储

- **存储位置**: `localStorage`
- **键名**: `table_columnWidths`
- **格式**: `{ "username": 120, "email": 180, ... }`
- **清除方法**: `localStorage.removeItem('table_columnWidths')`

### 宽度限制

- **最小宽度**: 60px（防止列过窄无法显示内容）
- **最大宽度**: 800px（防止单列过宽影响布局）

### 操作列识别规则

[autoFreezeActionColumn](file://e:\project\law-office\frontend\src\composables\Table\useTable.ts#L121-L148) 会自动识别并冻结满足以下条件的列：
1. `dataIndex` 或 `key` 等于配置的 `actionColumnKey`（默认 `'action'`）
2. **且**未手动设置 `fixed` 属性

如果列已经设置了 `fixed`，则不会覆盖。

---

## ❓ 常见问题

### Q1: 为什么我调整的列宽刷新后没有保存？
A: 检查浏览器控制台是否有错误，确认 localStorage 未被禁用。

### Q2: 如何让某些列不支持拖拽调整？
A: 目前所有列都支持拖拽。如需禁用，可以在列定义中设置特殊的样式或事件处理。

### Q3: 操作列没有被自动冻结？
A: 检查列的 `dataIndex` 是否为 `'action'`，或者通过 `actionColumnKey` 配置自定义标识。

### Q4: 横向滚动条没有出现？
A: 确保总列宽超过了 `minTableWidth`（默认 800px），可以通过调整列宽或降低 `minTableWidth` 来触发。

---

## 📝 更新日志

### v2.0 (当前版本)
- ✨ 新增列宽拖拽调整功能
- ✨ 新增 `generateTableScroll` 智能滚动配置
- ✨ 新增 `autoFreezeActionColumn` 自动冻结操作列
- ✨ 新增 `calculateTableWidth` 计算表格总宽度
- 🔄 优化列定义流程，保持向后兼容

### v1.x
- 基础表格列定义功能
- 表头筛选功能
- 多种列类型支持

## useTable - 表格配置辅助函数

### 核心功能

提供表格配置相关的辅助函数，简化表格的 scroll 配置和操作列处理。

#### 1. calculateTableWidth - 计算表格总宽度

``typescript
import { calculateTableWidth } from '#/composables/Table';

const columns = [
  { dataIndex: 'username', width: 120 },
  { dataIndex: 'email', width: 180 },
];

const totalWidth = calculateTableWidth(columns); // 300
```

#### 2. generateTableScroll - 生成表格 scroll 配置

``typescript
import { generateTableScroll } from '#/composables/Table';

const columns = [
  { dataIndex: 'username', width: 120 },
  { dataIndex: 'email', width: 180 },
  { dataIndex: 'phone', width: 130 },
];

// 自动生成 scroll 配置
const scrollConfig = generateTableScroll(columns, {
  enableScroll: true,        // 是否启用横向滚动，默认 true
  minTableWidth: 800,        // 最小表格宽度，默认 800
});

// 在 Table 组件中使用
<Table :scroll="scrollConfig" ... />
```

#### 3. autoFreezeActionColumn - 自动冻结操作列

``typescript
import { autoFreezeActionColumn } from '#/composables/Table';

const columns = [
  { dataIndex: 'username', title: '用户名' },
  { dataIndex: 'action', title: '操作' }, // 会自动添加 fixed: 'right'
];

// 自动处理操作列冻结
const processedColumns = autoFreezeActionColumn(columns, {
  autoFreezeActionColumn: true,  // 是否自动冻结，默认 true
  actionColumnKey: 'action',      // 操作列的 key，默认 'action'
});
```

### 在业务代码中的使用示例

``vue
<script setup lang="ts">
import { computed } from 'vue';
import { Table } from 'ant-design-vue';
import { generateTableScroll, autoFreezeActionColumn } from '#/composables/Table';
import { getUserColumns } from './hooks/useUserColumns';

const columns = computed(() => {
  const baseColumns = getUserColumns(filterState, emit, pagination);
  
  // 自动冻结操作列
  return autoFreezeActionColumn(baseColumns);
});

// 生成 scroll 配置
const scrollConfig = computed(() => 
  generateTableScroll(columns.value)
);
</script>

<template>
  <Table
    :columns="columns"
    :scroll="scrollConfig"
    ...
  />
</template>
```

---

## useTableHelper - 表格列定义辅助函数

### 核心功能

提供 `defineTableColumn` 和 `defineTableColumns` 辅助函数，简化表格列的定义。

**主要优势：**
- ✅ 自动处理通用的列属性（对齐、筛选、排序等）
- ✅ 减少重复代码，提升可维护性
- ✅ 支持多种列类型（文本、日期、时间、枚举、操作等）
- ✅ **自动启用单元格内容省略号显示和 Tooltip 提示** 🆕
- ✅ **列标题自动不换行并显示省略号** 🆕
- ✅ **默认启用列宽拖拽调整功能** 🆕
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
  
  return defineTableColumns(columns, filterState, emit, pagination);
}
```

#### 3. Select 类型列（多选筛选）🆕

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

#### 4. DateTime 类型列（日期/时间切换）🆕

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

#### 5. 日期列 🆕

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

#### 6. 日期时间列 🆕

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
| **columnType** | **'text' \| 'date' \| 'datetime'** | **'text'** | **列类型（新增）** |
| ...rest | any | - | 其他 Ant Design Vue 列配置 |

### 列类型说明

| 类型 | 筛选控件 | 筛选条件 | 格式 |
|------|----------|----------|------|
| text | 输入框 | 包含、等于、不等于、大于、小于等 | - |
| date | 日期选择器 | 等于、大于、小于、在两者之间 | YYYY-MM-DD |
| **datetime** | **日期/时间切换** | **日期模式：等于、大于、小于等<br/>时间模式：大于、小于、在两者之间** | **YYYY-MM-DD HH:mm:ss** |
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

### 自动省略号和 Tooltip 功能 🆕

所有通过 `defineTableColumn` 或 `defineTableColumns` 定义的列都会自动获得以下特性：

1. **列标题不换行**：标题过长时自动显示省略号
2. **单元格内容不换行**：内容超出列宽时显示省略号
3. **鼠标悬停显示完整内容**：悬停在单元格上时，通过 Tooltip 显示完整文本

这些特性无需额外配置，自动应用于所有列。如果需要自定义行为，可以通过 `customRender` 覆盖默认渲染。

### 完整示例

查看 [`useUserColumns.ts`](../../views/system/user/hooks/useUserColumns.ts) 了解完整的实际应用，包括日期时间列的使用。

## 其他功能

- **useTable**: 通用表格列表逻辑（分页、筛选、删除等）
- **useTableHeaderFilter**: 表头高级筛选功能（支持文本、日期、时间）
