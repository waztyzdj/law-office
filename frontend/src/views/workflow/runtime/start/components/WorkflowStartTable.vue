<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { AvailableProcessInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowStartColumns } from '../hooks/useWorkflowStartColumns';

interface Props {
  activeFilters: Record<string, any>;
  categoryOptions: { label: string; value: string }[];
  dataSource: AvailableProcessInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [pag: any, filters: any, sorter: any];
  start: [record: AvailableProcessInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowStartColumns(
    props.categoryOptions,
    filterStateRef,
    emit,
    props.pagination,
  ),
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
