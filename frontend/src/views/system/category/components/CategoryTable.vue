<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Card, Space, Table } from 'ant-design-vue';

import type { CategoryInfo } from '#/api/system/category';
import type { TablePaginationConfig } from '#/composables/Table';

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
</script>

<template>
  <Card>
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.category.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增类型
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
