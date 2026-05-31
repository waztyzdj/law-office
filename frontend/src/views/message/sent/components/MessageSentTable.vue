<script setup lang="ts">
import type { MessageSentInfo } from '#/api/message/message';
import type {
  FilterCondition,
  TablePaginationConfig,
} from '#/composables/Table';

import { computed, toRef } from 'vue';

import { BaseTable } from '#/components/BaseTable';

import { getSentColumns } from '../hooks/useMessageSentColumns';

interface Props {
  activeFilters: Record<string, FilterCondition | unknown>;
  dataSource: MessageSentInfo[];
  deletingSelected?: boolean;
  loading: boolean;
  pagination: TablePaginationConfig;
  selectedRowKeys: (number | string)[];
  showCard?: boolean;
  showToolbar?: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  batchDelete: [];
  change: [pag: unknown, filters: unknown, sorter: unknown];
  delete: [record: MessageSentInfo];
  recall: [record: MessageSentInfo];
  selectChange: [keys: (number | string)[]];
  view: [record: MessageSentInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const handleColumnAction = (
  event: 'delete' | 'recall' | 'view',
  record: MessageSentInfo,
) => {
  if (event === 'delete') {
    emit('delete', record);
    return;
  }
  if (event === 'recall') {
    emit('recall', record);
    return;
  }
  emit('view', record);
};
const tableConfig = computed(() =>
  getSentColumns(filterStateRef, handleColumnAction, props.pagination),
);
const rowSelection = computed(() => ({
  selectedRowKeys: props.selectedRowKeys,
  onChange: (keys: (number | string)[]) => emit('selectChange', keys),
}));
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '发送消息',
    type: 'primary' as const,
    onClick: () => emit('add'),
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
