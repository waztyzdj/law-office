<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Card, Empty, Space, Table } from 'ant-design-vue';

import type { SysDictInfo, SysDictItemInfo } from '#/api/system/dict';
import type { TablePaginationConfig } from '#/composables/Table';

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
</script>

<template>
  <Card class="dict-item-card">
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.dictItem.edit"
          :disabled="!currentDict?.id"
          type="primary"
          @click="$emit('add')"
        >
          新增字典项
        </Button>
      </Space>
    </div>

    <Table
      v-if="currentDict?.id"
      :columns="tableConfig.columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :scroll="tableConfig.scroll"
      bordered
      row-key="id"
      @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
    />
    <Empty
      v-else
      description="请选择一个字典后查看字典项"
    />
  </Card>
</template>

<style scoped>
.dict-item-card {
  margin-top: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>
