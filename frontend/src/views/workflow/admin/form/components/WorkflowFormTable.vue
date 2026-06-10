<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { WorkflowFormDefinitionInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowFormColumns } from '../hooks/useWorkflowFormColumns';

interface Props {
  activeFilters: Record<string, any>;
  categoryMap: Record<string, string>;
  dataSource: WorkflowFormDefinitionInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  change: [pag: any, filters: any, sorter: any];
  copyAsDraft: [record: WorkflowFormDefinitionInfo];
  delete: [record: WorkflowFormDefinitionInfo];
  design: [record: WorkflowFormDefinitionInfo];
  edit: [record: WorkflowFormDefinitionInfo];
  publish: [record: WorkflowFormDefinitionInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowFormColumns(
    props.categoryMap,
    filterStateRef,
    emit,
    props.pagination,
  ),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新建表单',
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
    :pagination="pagination"
    :scroll="tableConfig.scroll"
    :toolbar-buttons="toolbarButtons"
    row-key="id"
    @change="(pag, tableFilters, sorter) => $emit('change', pag, tableFilters, sorter)"
  />
</template>
