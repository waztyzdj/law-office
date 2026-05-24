<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Card, Space, Table } from 'ant-design-vue';

import type { TenantInfo } from '#/api/system/tenant';
import type { TablePaginationConfig } from '#/composables/Table';

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
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getTenantColumns(filterStateRef, emit, props.pagination),
);
</script>

<template>
  <Card>
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.tenant.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增租户
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
