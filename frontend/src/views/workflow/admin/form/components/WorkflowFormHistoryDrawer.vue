<script setup lang="ts">
import type { WorkflowFormDefinitionInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { computed, h, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Space, message } from 'ant-design-vue';

import { listWorkflowFormHistory } from '#/api/workflow';
import { BaseTable } from '#/components/BaseTable';
import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import { formDefinitionStatusOptions } from '../../../components/status';

interface Props {
  categoryMap: Record<string, string>;
}

interface HistoryTableFilter {
  apiCondition?: string;
  condition?: string;
  value?: unknown;
}

interface HistoryTableSorter {
  field?: unknown;
  order?: unknown;
}

type HistoryTableFilters = Record<string, HistoryTableFilter | null | unknown[]>;

const props = defineProps<Props>();
const emit = defineEmits<{
  viewDesign: [record: WorkflowFormDefinitionInfo];
}>();

const currentForm = ref<WorkflowFormDefinitionInfo>();
const records = ref<WorkflowFormDefinitionInfo[]>([]);
const loading = ref(false);
const activeFilters = ref<HistoryTableFilters>({});
const activeSorter = ref<HistoryTableSorter>({});
const tablePagination = reactive<TablePaginationConfig>({
  pageNum: 1,
  pageSize: 1000,
  total: 0,
});

const drawerTitle = computed(() =>
  currentForm.value?.formName
    ? `历史版本 - ${currentForm.value.formName}`
    : '历史版本',
);
const currentCategoryLabel = computed(() =>
  currentForm.value ? resolveCategory(currentForm.value) : '-',
);

const tableConfig = computed(() =>
  defineTableColumns<WorkflowFormDefinitionInfo>(
    [
      {
        dataIndex: 'version',
        title: '版本',
        options: {
          align: 'center',
          customRender: ({ record }) => `v${record.version ?? 1}`,
          width: 90,
        },
      },
      {
        dataIndex: 'status',
        title: '状态',
        options: {
          align: 'center',
          columnType: 'select',
          customRender: ({ record }) =>
            h(WorkflowStatusTag, { status: record.status }),
          selectOptions: formDefinitionStatusOptions,
          width: 110,
        },
      },
      {
        dataIndex: 'publishedTime',
        title: '发布时间',
        options: {
          align: 'center',
          columnType: 'datetime',
          customRender: ({ record }) => record.publishedTime ?? '-',
          width: 180,
        },
      },
      {
        dataIndex: 'remark',
        title: '备注',
        options: {
          sorter: false,
          width: 240,
        },
      },
      {
        dataIndex: 'historyAction',
        title: '操作',
        options: {
          align: 'center',
          fixed: 'right',
          hasFilter: false,
          width: 120,
          customRender: ({ record }) =>
            h(Space, { class: 'form-history__action', size: 'middle' }, () => [
              h('a', { onClick: () => emit('viewDesign', record) }, '查看设计'),
            ]),
        },
      },
    ],
    activeFilters,
    handleColumnEmit,
    tablePagination,
    { minTableWidth: 760 },
  ),
);
const tableScroll = computed(() => tableConfig.value.scroll ?? { x: 760 });
const displayedRecords = computed(() => {
  const filtered = records.value.filter((record) => matchesFilters(record));
  const sorter = activeSorter.value;
  if (!sorter?.field || !sorter?.order) {
    return filtered;
  }

  const direction = sorter.order === 'ascend' ? 1 : -1;
  const field = String(sorter.field);
  return [...filtered].sort(
    (left, right) =>
      compareValues(resolveFieldValue(left, field), resolveFieldValue(right, field)) *
      direction,
  );
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[60%]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'px-5 py-4 sm:px-6',
  footer: false,
  title: drawerTitle.value,
  zIndex: 1000,
});

function resolveCategory(record: WorkflowFormDefinitionInfo) {
  return props.categoryMap[record.categoryId ?? ''] ?? record.categoryId ?? '-';
}

async function loadData(record: WorkflowFormDefinitionInfo) {
  if (!record.id) {
    message.warning('请选择表单版本');
    return;
  }

  loading.value = true;
  try {
    records.value = await listWorkflowFormHistory(record.id);
    tablePagination.total = records.value.length;
  } finally {
    loading.value = false;
  }
}

function handleColumnEmit(
  event: string,
  pagination: unknown,
  filters: HistoryTableFilters,
  sorter: HistoryTableSorter,
) {
  if (event === 'change') {
    handleTableChange(pagination, filters, sorter);
  }
}

function handleTableChange(
  _pagination: unknown,
  filters?: HistoryTableFilters,
  sorter?: HistoryTableSorter | HistoryTableSorter[],
) {
  activeFilters.value = filters || {};
  const nextSorter = Array.isArray(sorter) ? sorter[0] : sorter;
  if (nextSorter?.field && nextSorter?.order) {
    activeSorter.value = {
      field: String(nextSorter.field),
      order: String(nextSorter.order),
    };
  } else if (nextSorter && Object.keys(nextSorter).length > 0) {
    activeSorter.value = {};
  }
}

function matchesFilters(record: WorkflowFormDefinitionInfo) {
  return Object.entries(activeFilters.value).every(([field, filter]) => {
    if (
      !isHistoryTableFilter(filter) ||
      filter.value === undefined ||
      filter.value === null ||
      (Array.isArray(filter.value) && filter.value.length === 0)
    ) {
      return true;
    }
    const value = resolveFieldValue(record, field);
    if (filter.condition === 'in') {
      return Array.isArray(filter.value) && filter.value.includes(value);
    }
    return matchesCondition(
      value,
      filter.condition || filter.apiCondition || 'like',
      filter.value,
    );
  });
}

function isHistoryTableFilter(
  filter: HistoryTableFilters[string],
): filter is HistoryTableFilter {
  return Boolean(
    filter && !Array.isArray(filter) && typeof filter === 'object' && 'value' in filter,
  );
}

function matchesCondition(value: unknown, condition: string, filterValue: unknown) {
  const text = String(value ?? '').toLowerCase();
  const target = String(filterValue ?? '').toLowerCase();
  if (condition === 'like') {
    return text.includes(target);
  }
  if (condition === 'ne') {
    return text !== target;
  }
  if (condition === 'eq') {
    return text === target;
  }
  const left = Number(value);
  const right = Number(filterValue);
  if (Number.isFinite(left) && Number.isFinite(right)) {
    if (condition === 'gt') return left > right;
    if (condition === 'ge') return left >= right;
    if (condition === 'lt') return left < right;
    if (condition === 'le') return left <= right;
  }
  return true;
}

function resolveFieldValue(record: WorkflowFormDefinitionInfo, field: string) {
  return record[field as keyof WorkflowFormDefinitionInfo];
}

function compareValues(left: unknown, right: unknown) {
  if (left == null && right == null) return 0;
  if (left == null) return -1;
  if (right == null) return 1;
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }
  return String(left).localeCompare(String(right), 'zh-CN');
}

async function open(record: WorkflowFormDefinitionInfo) {
  currentForm.value = record;
  records.value = [];
  activeFilters.value = {};
  activeSorter.value = {};
  tablePagination.total = 0;
  drawerApi.setState({ loading: true, title: drawerTitle.value }).open();

  try {
    await loadData(record);
  } finally {
    drawerApi.setState({ loading: false, title: drawerTitle.value });
  }
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="form-history">
      <div class="form-history__summary">
        <Space
          class="form-history__summary-main"
          wrap
        >
          <span>{{ currentForm?.formKey ?? '-' }}</span>
          <span>{{ currentForm?.formName ?? '-' }}</span>
        </Space>
        <Space
          class="form-history__summary-meta"
          wrap
        >
          <span class="form-history__meta-item">
            <span class="form-history__meta-label">流程分类</span>
            <span>{{ currentCategoryLabel }}</span>
          </span>
        </Space>
      </div>

      <BaseTable
        :columns="tableConfig.columns"
        :data-source="displayedRecords"
        :loading="loading"
        :pagination="false"
        :scroll="tableScroll"
        :show-card="false"
        :show-toolbar="false"
        row-key="id"
        size="small"
        @change="handleTableChange"
      />
    </div>
  </Drawer>
</template>

<style scoped>
.form-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-history__summary {
  align-items: center;
  color: #374151;
  display: flex;
  font-size: 14px;
  gap: 12px 24px;
  justify-content: space-between;
}

.form-history__summary-main,
.form-history__summary-meta {
  min-width: 0;
}

.form-history__meta-item {
  align-items: center;
  display: inline-flex;
  gap: 6px;
}

.form-history__meta-label {
  color: #6b7280;
}

.form-history__action {
  justify-content: center;
  width: 100%;
}
</style>
