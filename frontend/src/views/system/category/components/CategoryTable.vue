<script setup lang="ts">
import { computed, toRef } from 'vue';

import type { CategoryInfo } from '#/api/system/category';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getCategoryColumns } from '../hooks/useCategoryColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: CategoryInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  addChild: [record: CategoryInfo];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: CategoryInfo];
  edit: [record: CategoryInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getCategoryColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    key: 'add',
    label: '新增类型',
    permissionCode: permissionCodes.category.edit,
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
    default-expand-all-rows
    row-key="id"
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  />
</template>
