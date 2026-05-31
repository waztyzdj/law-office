<script setup lang="ts">
import type { MessageInboxInfo } from '#/api/message/message';
import type {
  FilterCondition,
  TablePaginationConfig,
} from '#/composables/Table';

import { computed, toRef } from 'vue';

import { BaseTable } from '#/components/BaseTable';

import { getInboxColumns } from '../hooks/useMessageInboxColumns';

interface Props {
  activeFilters: Record<string, FilterCondition | unknown>;
  dataSource: MessageInboxInfo[];
  deletingSelected?: boolean;
  loading: boolean;
  pagination: TablePaginationConfig;
  readingAll?: boolean;
  readingSelected?: boolean;
  selectedRowKeys: (number | string)[];
  showCard?: boolean;
  showToolbar?: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [pag: unknown, filters: unknown, sorter: unknown];
  batchDelete: [];
  delete: [record: MessageInboxInfo];
  markAllRead: [];
  markSelectedRead: [];
  selectChange: [keys: (number | string)[]];
  star: [record: MessageInboxInfo];
  view: [record: MessageInboxInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const handleColumnAction = (
  event: 'delete' | 'star' | 'view',
  record: MessageInboxInfo,
) => {
  if (event === 'delete') {
    emit('delete', record);
    return;
  }
  if (event === 'star') {
    emit('star', record);
    return;
  }
  emit('view', record);
};
const tableConfig = computed(() =>
  getInboxColumns(filterStateRef, handleColumnAction, props.pagination),
);
const rowSelection = computed(() => ({
  selectedRowKeys: props.selectedRowKeys,
  onChange: (keys: (number | string)[]) => emit('selectChange', keys),
}));
const toolbarButtons = computed(() => [
  {
    disabled: props.selectedRowKeys.length === 0,
    key: 'markSelectedRead',
    label: '标记已读',
    loading: props.readingSelected,
    onClick: () => emit('markSelectedRead'),
    type: 'primary' as const,
  },
  {
    key: 'markAllRead',
    label: '全部标记已读',
    loading: props.readingAll,
    onClick: () => emit('markAllRead'),
    type: 'primary' as const,
  },
  {
    danger: true,
    disabled: props.selectedRowKeys.length === 0,
    key: 'batchDelete',
    label: '批量删除',
    loading: props.deletingSelected,
    onClick: () => emit('batchDelete'),
    type: 'primary' as const,
  },
]);
</script>

<template>
  <BaseTable
    :columns="tableConfig.columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :row-selection="rowSelection"
    :scroll="tableConfig.scroll"
    :show-card="showCard"
    :show-toolbar="showToolbar"
    :toolbar-buttons="toolbarButtons"
    row-key="id"
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  />
</template>
