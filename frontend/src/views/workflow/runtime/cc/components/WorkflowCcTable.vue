<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { WorkflowCcRecordInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowCcColumns } from '../hooks/useWorkflowCcColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: WorkflowCcRecordInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [pag: any, filters: any, sorter: any];
  detail: [record: WorkflowCcRecordInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowCcColumns(filterStateRef, emit, props.pagination),
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
