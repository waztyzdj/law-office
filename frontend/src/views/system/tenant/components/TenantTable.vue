<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { TenantInfo } from '#/api/system/tenant';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getTenantColumns } from '../hooks/useTenantColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: TenantInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: TenantInfo];
  edit: [record: TenantInfo];
  adminPermissions: [record: TenantInfo];
  admins: [record: TenantInfo];
  users: [record: TenantInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getTenantColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增租户',
    permissionCode: permissionCodes.tenant.edit,
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
