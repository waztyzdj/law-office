<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { PermissionInfo } from '#/api/system/permission';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getPermissionColumns } from '../hooks/usePermissionColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: PermissionInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  addChild: [record: PermissionInfo];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: PermissionInfo];
  edit: [record: PermissionInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getPermissionColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增菜单',
    permissionCode: permissionCodes.permission.edit,
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
    row-key="id"
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  />
</template>
