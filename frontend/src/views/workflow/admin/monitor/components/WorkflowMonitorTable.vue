<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { AdminMonitorInstanceInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowMonitorColumns } from '../hooks/useWorkflowMonitorColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: AdminMonitorInstanceInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
  scopeTitle: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [pag: any, filters: any, sorter: any];
  detail: [record: AdminMonitorInstanceInfo];
  reassign: [record: AdminMonitorInstanceInfo];
  resendNotice: [record: AdminMonitorInstanceInfo];
  terminate: [record: AdminMonitorInstanceInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowMonitorColumns(filterStateRef, emit, props.pagination),
);
</script>

<template>
  <BaseTable
    class="workflow-monitor-table"
    :columns="tableConfig.columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :scroll="tableConfig.scroll"
    row-key="id"
    @change="(pag, tableFilters, sorter) => $emit('change', pag, tableFilters, sorter)"
  >
    <template #beforeTable>
      <div class="workflow-monitor-table__title">
        {{ scopeTitle }}
      </div>
    </template>
  </BaseTable>
</template>

<style scoped>
.workflow-monitor-table {
  height: 100%;
  overflow: hidden;
}

.workflow-monitor-table :deep(.ant-card-body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.workflow-monitor-table__title {
  flex: 0 0 auto;
  margin-bottom: 12px;
  color: var(--ant-color-text, rgb(0 0 0 / 88%));
  font-size: 15px;
  font-weight: 500;
}

.workflow-monitor-table :deep(.ant-table-wrapper) {
  flex: 0 0 auto;
  min-height: 0;
}

.workflow-monitor-table :deep(.ant-spin-nested-loading),
.workflow-monitor-table :deep(.ant-spin-container) {
  display: block;
  min-height: 0;
}

.workflow-monitor-table :deep(.ant-table) {
  flex: none;
}
</style>
