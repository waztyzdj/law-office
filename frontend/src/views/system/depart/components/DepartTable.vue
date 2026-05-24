<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Card, Space, Table } from 'ant-design-vue';

import type { DepartInfo } from '#/api/system/depart';
import type { TablePaginationConfig } from '#/composables/Table';

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
</script>

<template>
  <Card>
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.depart.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增机构
        </Button>
      </Space>
    </div>

    <Table
      :columns="tableConfig.columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="false"
      :scroll="tableConfig.scroll"
      bordered
      default-expand-all-rows
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
