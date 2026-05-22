<script setup lang="ts">
import { computed, toRef } from 'vue';
import { Button, Card, Space, Table } from 'ant-design-vue';
import type { RoleInfo } from '#/api/system/role';
import type { TablePaginationConfig } from '#/composables/Table';
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
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getRoleColumns(filterStateRef, emit, props.pagination),
);
</script>

<template>
  <Card class="table-card">
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.role.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增角色
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
