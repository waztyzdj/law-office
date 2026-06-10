<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { RuntimeTaskInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowTodoColumns } from '../hooks/useWorkflowTodoColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: RuntimeTaskInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [pag: any, filters: any, sorter: any];
  handleTask: [record: RuntimeTaskInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowTodoColumns(filterStateRef, emit, props.pagination),
);
</script>

<template>
  <BaseTable
    :columns="tableConfig.columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :scroll="tableConfig.scroll"
    row-key="id"
    @change="(pag, tableFilters, sorter) => $emit('change', pag, tableFilters, sorter)"
  />
</template>
