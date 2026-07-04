<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { WorkbenchCardInfo } from '#/api/home/workbench';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getWorkbenchCardColumns } from '../hooks/useWorkbenchCardColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: WorkbenchCardInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  change: [pag: any, filters: any, sorter: any];
  edit: [record: WorkbenchCardInfo];
  saveSort: [];
  status: [record: WorkbenchCardInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkbenchCardColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增卡片',
    permissionCode: permissionCodes.homeCard.manage,
    type: 'primary' as const,
    onClick: () => emit('add'),
  },
  {
    key: 'saveSort',
    label: '保存排序',
    permissionCode: permissionCodes.homeCard.manage,
    onClick: () => emit('saveSort'),
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
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  />
</template>
