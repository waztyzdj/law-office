<script setup lang="ts">
import type {
  DocumentFileInfo,
  OnlyOfficeHistoryVersion,
} from '#/api/system/document';
import type { TablePaginationConfig } from '#/composables/Table';

import { computed, h, reactive, ref } from 'vue';

import { Button, Modal, Popconfirm, Space, Tag, message } from 'ant-design-vue';

import {
  listOnlyOfficeHistory,
  restoreOnlyOfficeHistoryVersion,
} from '#/api/system/document';
import { BaseTable } from '#/components/BaseTable';
import { defineTableColumns } from '#/composables/Table';

const openState = ref(false);
const loading = ref(false);
const restoringId = ref('');
const currentFile = ref<DocumentFileInfo>();
const versions = ref<OnlyOfficeHistoryVersion[]>([]);

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

const activeFilters = ref<HistoryTableFilters>({});
const activeSorter = ref<HistoryTableSorter>({});
const tablePagination = reactive<TablePaginationConfig>({
  pageNum: 1,
  pageSize: 1000,
  total: 0,
});

const emit = defineEmits<{
  preview: [version: OnlyOfficeHistoryVersion];
  restored: [];
}>();

const canRestore = computed(() => Boolean(currentFile.value?.canUpdate));
const modalTitle = computed(() =>
  currentFile.value?.fileName ? `历史版本 - ${currentFile.value.fileName}` : '历史版本',
);

const versionTypeOptions = [
  { color: 'cyan', label: '上传', value: 'upload' },
  { color: 'green', label: '保存', value: 'final' },
  { color: 'blue', label: '恢复', value: 'restore' },
];

const tableConfig = computed(() =>
  defineTableColumns<OnlyOfficeHistoryVersion>(
    [
      {
        dataIndex: 'version',
        title: '版本',
        options: { align: 'center', width: 100 },
      },
      {
        dataIndex: 'editorName',
        title: '编辑人',
        options: { width: 150 },
      },
      {
        dataIndex: 'editTime',
        title: '保存时间',
        options: { columnType: 'datetime', width: 190 },
      },
      {
        dataIndex: 'fileSize',
        title: '大小',
        options: {
          columnType: 'number',
          customRender: ({ record }) => formatSize(record.fileSize),
          width: 120,
        },
      },
      {
        dataIndex: 'versionType',
        title: '类型',
        options: {
          columnType: 'select',
          customRender: ({ record }) =>
            h(
              Tag,
              { color: resolveVersionTypeColor(record.versionType) },
              () => formatVersionType(record.versionType),
            ),
          selectOptions: versionTypeOptions,
          width: 120,
        },
      },
      {
        dataIndex: 'action',
        title: '操作',
        options: {
          fixed: 'right',
          hasFilter: false,
          width: 180,
          customRender: ({ record }) =>
            h(Space, { size: 'middle' }, () => [
              h(
                'a',
                { onClick: () => emit('preview', record) },
                '预览',
              ),
              h(
                Popconfirm,
                {
                  disabled: !canRestore.value,
                  okText: '恢复',
                  title: '确认恢复到该历史版本？当前文件内容会被覆盖。',
                  onConfirm: () => handleRestore(record),
                },
                {
                  default: () =>
                    h(
                      Button,
                      {
                        disabled: !canRestore.value,
                        loading: restoringId.value === record.id,
                        size: 'small',
                        type: 'link',
                      },
                      () => '恢复',
                    ),
                },
              ),
            ]),
        },
      },
    ],
    activeFilters,
    handleColumnEmit,
    tablePagination,
    { minTableWidth: 860 },
  ),
);

const tableScroll = computed(() => tableConfig.value.scroll || { x: 860 });

const displayedVersions = computed(() => {
  const filtered = versions.value.filter((record) => matchesFilters(record));
  const sorter = activeSorter.value;
  if (!sorter?.field || !sorter?.order) {
    return filtered;
  }
  const direction = sorter.order === 'ascend' ? 1 : -1;
  const field = String(sorter.field || '');
  return [...filtered].sort((a, b) => compareValues(resolveFieldValue(a, field), resolveFieldValue(b, field)) * direction);
});

async function open(file: DocumentFileInfo) {
  if (!file.id) {
    return;
  }
  currentFile.value = file;
  openState.value = true;
  activeFilters.value = {};
  activeSorter.value = {};
  await loadHistory(file.id);
}

async function loadHistory(fileId: string) {
  loading.value = true;
  try {
    versions.value = await listOnlyOfficeHistory(fileId);
    tablePagination.total = versions.value.length;
  } finally {
    loading.value = false;
  }
}

function formatSize(size?: number) {
  if (!size || size <= 0) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function formatVersionType(type?: string) {
  if (type === 'upload') {
    return '上传';
  }
  if (type === 'restore') {
    return '恢复';
  }
  return '保存';
}

function resolveVersionTypeColor(type?: string) {
  if (type === 'upload') {
    return 'cyan';
  }
  if (type === 'restore') {
    return 'blue';
  }
  return 'green';
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

function matchesFilters(record: OnlyOfficeHistoryVersion) {
  return Object.entries(activeFilters.value).every(([field, filter]) => {
    if (!isHistoryTableFilter(filter) || !filter.value || (Array.isArray(filter.value) && filter.value.length === 0)) {
      return true;
    }
    const value = resolveFieldValue(record, field);
    if (filter.condition === 'in') {
      return Array.isArray(filter.value) && filter.value.includes(value);
    }
    return matchesCondition(value, filter.condition || filter.apiCondition || 'like', filter.value);
  });
}

function isHistoryTableFilter(filter: HistoryTableFilters[string]): filter is HistoryTableFilter {
  return Boolean(filter && !Array.isArray(filter) && typeof filter === 'object' && 'value' in filter);
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

function resolveFieldValue(record: OnlyOfficeHistoryVersion, field: string) {
  return record[field as keyof OnlyOfficeHistoryVersion];
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

async function handleRestore(version: OnlyOfficeHistoryVersion) {
  if (!version.id) {
    return;
  }
  restoringId.value = version.id;
  try {
    await restoreOnlyOfficeHistoryVersion(version.id);
    message.success('历史版本已恢复');
    if (currentFile.value?.id) {
      await loadHistory(currentFile.value.id);
    }
    emit('restored');
  } finally {
    restoringId.value = '';
  }
}

function handleAfterClose() {
  currentFile.value = undefined;
  versions.value = [];
  activeFilters.value = {};
  activeSorter.value = {};
  restoringId.value = '';
}

defineExpose({
  open,
});
</script>

<template>
  <Modal
    v-model:open="openState"
    class="document-history-modal"
    destroy-on-close
    :body-style="{ height: '540px', overflow: 'auto', padding: '16px 24px' }"
    :footer="null"
    :title="modalTitle"
    width="1040px"
    @after-close="handleAfterClose"
  >
    <BaseTable
      class="document-history-table"
      :columns="tableConfig.columns"
      :data-source="displayedVersions"
      :loading="loading"
      :pagination="false"
      :scroll="tableScroll"
      :show-card="false"
      row-key="id"
      size="small"
      @change="handleTableChange"
    />
  </Modal>
</template>
