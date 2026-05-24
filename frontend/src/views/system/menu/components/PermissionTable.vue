<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Card, Space, Table } from 'ant-design-vue';

import type { PermissionInfo } from '#/api/system/permission';
import type { TablePaginationConfig } from '#/composables/Table';

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
</script>

<template>
  <Card>
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.permission.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增菜单
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
