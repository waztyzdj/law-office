<script setup lang="ts">
import type { ArchiveRecordInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { computed, toRef } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Space } from 'ant-design-vue';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getWorkflowArchiveColumns } from '../hooks/useWorkflowArchiveColumns';
import type { WorkflowArchiveTab } from '../hooks/useWorkflowArchiveTable';

interface Props {
  activeFilters: Record<string, any>;
  archivingByQuery?: boolean;
  archivingSelected?: boolean;
  dataSource: ArchiveRecordInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
  scopeTitle: string;
  selectedRowKeys: (number | string)[];
  tab: WorkflowArchiveTab;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  archive: [record: ArchiveRecordInfo];
  archiveByQuery: [];
  archiveSelected: [];
  change: [pag: any, filters: any, sorter: any];
  detail: [record: ArchiveRecordInfo];
  download: [record: ArchiveRecordInfo];
  selectChange: [keys: (number | string)[]];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowArchiveColumns(props.tab, filterStateRef, emit, props.pagination),
);
const rowSelection = computed(() =>
  props.tab === 'unarchived'
    ? {
        selectedRowKeys: props.selectedRowKeys,
        onChange: (keys: (number | string)[]) => emit('selectChange', keys),
      }
    : undefined,
);
</script>

<template>
  <BaseTable
    class="workflow-archive-table"
    :columns="tableConfig.columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :row-selection="rowSelection"
    :scroll="tableConfig.scroll"
    :show-card="false"
    :show-toolbar="false"
    row-key="processInstanceId"
    @change="(pag, tableFilters, sorter) => $emit('change', pag, tableFilters, sorter)"
  >
    <template #beforeTable>
      <div class="workflow-archive-table__header">
        <div class="workflow-archive-table__title">
          {{ scopeTitle }}
        </div>
        <Space v-if="tab === 'unarchived'">
          <Button
            v-access:code="permissionCodes.workflowArchive.manage"
            :disabled="selectedRowKeys.length === 0"
            :loading="archivingSelected"
            type="primary"
            @click="$emit('archiveSelected')"
          >
            <template #icon>
              <IconifyIcon icon="lucide:archive" />
            </template>
            批量归档
          </Button>
          <Button
            v-access:code="permissionCodes.workflowArchive.manage"
            :loading="archivingByQuery"
            @click="$emit('archiveByQuery')"
          >
            <template #icon>
              <IconifyIcon icon="lucide:list-checks" />
            </template>
            按查询归档
          </Button>
        </Space>
      </div>
    </template>
  </BaseTable>
</template>

<style scoped>
.workflow-archive-table {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.workflow-archive-table__title {
  flex: 0 0 auto;
  color: var(--ant-color-text, rgb(0 0 0 / 88%));
  font-size: 15px;
  font-weight: 500;
}

.workflow-archive-table__header {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.workflow-archive-table :deep(.ant-table-wrapper) {
  flex: 0 0 auto;
  min-height: 0;
}

.workflow-archive-table :deep(.ant-spin-nested-loading),
.workflow-archive-table :deep(.ant-spin-container) {
  display: block;
  min-height: 0;
}

.workflow-archive-table :deep(.ant-table) {
  flex: none;
}
</style>
