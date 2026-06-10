<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { WorkflowCategoryInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowCategoryColumns } from '../hooks/useWorkflowCategoryColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: WorkflowCategoryInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  addChild: [record: WorkflowCategoryInfo];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: WorkflowCategoryInfo];
  edit: [record: WorkflowCategoryInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowCategoryColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增分类',
    type: 'primary' as const,
    onClick: () => emit('add'),
  },
]);
</script>

<template>
  <BaseTable
    :columns="tableConfig.columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="false"
    :scroll="tableConfig.scroll"
    :toolbar-buttons="toolbarButtons"
    default-expand-all-rows
    row-key="id"
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  />
</template>
