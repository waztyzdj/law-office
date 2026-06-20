<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { RoleInfo } from '#/api/system/role';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getRoleColumns } from '../hooks/useRoleColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: RoleInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  assign: [record: RoleInfo];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: RoleInfo];
  edit: [record: RoleInfo];
  members: [record: RoleInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getRoleColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增角色',
    permissionCode: permissionCodes.role.edit,
    type: 'primary' as const,
    onClick: () => emit('add'),
  },
]);
</script>

<template>
  <BaseTable
    class="table-card"
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
