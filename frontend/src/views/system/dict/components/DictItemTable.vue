<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Empty } from 'ant-design-vue';

import type { SysDictInfo, SysDictItemInfo } from '#/api/system/dict';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';
import { permissionCodes } from '#/constants/permissions';

import { getDictItemColumns } from '../hooks/useDictColumns';

interface Props {
  activeFilters: Record<string, any>;
  currentDict?: SysDictInfo;
  dataSource: SysDictItemInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: SysDictItemInfo];
  edit: [record: SysDictItemInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getDictItemColumns(filterStateRef, emit, props.pagination),
);
const toolbarButtons = computed(() => [
  {
    disabled: !props.currentDict?.id,
    key: 'add',
    label: '新增字典项',
    permissionCode: permissionCodes.dictItem.edit,
    type: 'primary' as const,
    onClick: () => emit('add'),
  },
]);
</script>

<template>
  <BaseTable
    class="dict-item-card"
    :columns="currentDict?.id ? tableConfig.columns : []"
    :data-source="currentDict?.id ? dataSource : []"
    :loading="loading"
    :pagination="currentDict?.id ? pagination : false"
    :scroll="currentDict?.id ? tableConfig.scroll : undefined"
    :toolbar-buttons="toolbarButtons"
    row-key="id"
    @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
  >
    <template #emptyText>
      <Empty description="请选择一个字典后查看字典项" />
    </template>
  </BaseTable>
</template>

<style scoped>
.dict-item-card {
  margin-top: 16px;
}
</style>
