<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Card, Table } from 'ant-design-vue';

import type { LogInfo } from '#/api/system/log';
import type { TablePaginationConfig } from '#/composables/Table';

import { getLogColumns } from '../hooks/useLogColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: LogInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  change: [pag: any, filters: any, sorter: any];
  delete: [record: LogInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getLogColumns(filterStateRef, emit, props.pagination),
);
</script>

<template>
  <Card>
    <Table
      :columns="tableConfig.columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :scroll="tableConfig.scroll"
      bordered
      row-key="id"
      @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
    />
  </Card>
</template>
