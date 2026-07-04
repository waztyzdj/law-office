<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { WorkbenchQuickEntryInfo } from '#/api/home/workbench';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getWorkbenchQuickEntryColumns } from '../hooks/useWorkbenchQuickEntryColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: WorkbenchQuickEntryInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  change: [pag: any, filters: any, sorter: any];
  edit: [record: WorkbenchQuickEntryInfo];
  status: [record: WorkbenchQuickEntryInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkbenchQuickEntryColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增快捷菜单',
    permissionCode: permissionCodes.homeCard.manage,
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
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  />
</template>
