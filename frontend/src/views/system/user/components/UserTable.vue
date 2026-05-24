<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { UserInfo } from '#/api/system/user';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getUserColumns } from '../hooks/useUserColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: UserInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

interface Emits {
  (e: 'add'): void;
  (e: 'assignRole', record: UserInfo): void;
  (e: 'change', pag: any, filters: any, sorter: any): void;
  (e: 'delete', record: UserInfo): void;
  (e: 'edit', record: UserInfo): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getUserColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增用户',
    permissionCode: permissionCodes.user.edit,
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
