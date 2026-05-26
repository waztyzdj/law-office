<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { DepartInfo } from '#/api/system/depart';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getDepartColumns } from '../hooks/useDepartColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: DepartInfo[];
  loading: boolean;
  orgTypeSelectOptions: any[];
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  addChild: [record: DepartInfo];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: DepartInfo];
  edit: [record: DepartInfo];
  members: [record: DepartInfo];
  roles: [record: DepartInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getDepartColumns(
    filterStateRef,
    emit,
    props.pagination,
    props.orgTypeSelectOptions,
  ),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增机构',
    permissionCode: permissionCodes.depart.edit,
    type: 'primary' as const,
    onClick: () => emit('add'),
  },
]);
</script>

<template>
  <BaseTable
    class="depart-table"
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

<style scoped>
:deep(.depart-table .depart-action-cell) {
  width: 260px !important;
  min-width: 260px !important;
  max-width: 260px !important;
}

:deep(.depart-table .depart-action-links) {
  justify-content: center;
  width: 100%;
  white-space: nowrap;
}
</style>
