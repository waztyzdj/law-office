<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Card, Space, Table } from 'ant-design-vue';

import type { SysDictInfo } from '#/api/system/dict';
import type { TablePaginationConfig } from '#/composables/Table';

import { permissionCodes } from '#/constants/permissions';

import { getDictColumns } from '../hooks/useDictColumns';

interface Props {
  activeFilters: Record<string, any>;
  dataSource: SysDictInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  add: [];
  change: [pag: any, filters: any, sorter: any];
  delete: [record: SysDictInfo];
  edit: [record: SysDictInfo];
  select: [record: SysDictInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getDictColumns(filterStateRef, emit, props.pagination),
);
</script>

<template>
  <Card>
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.dict.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增字典
        </Button>
      </Space>
    </div>

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

<style scoped>
.table-toolbar {
  margin-bottom: 16px;
}
</style>
