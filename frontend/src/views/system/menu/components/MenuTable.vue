<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { PermissionInfo as MenuInfo } from '#/api/system/permission';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getMenuColumns } from '../hooks/useMenuColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: MenuInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  addChild: [record: MenuInfo];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: MenuInfo];
  edit: [record: MenuInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getMenuColumns(filterStateRef, emit, props.pagination),
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
