<script setup lang="ts">
import { computed, toRef } from 'vue';

import { Button, Space } from 'ant-design-vue';

import type { WorkflowProcessModelInfo } from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { BaseTable } from '#/components/BaseTable';

import { getWorkflowProcessColumns } from '../hooks/useWorkflowProcessColumns';

interface Props {
  activeFilters: Record<string, any>;
  categoryMap: Record<string, string>;
  dataSource: WorkflowProcessModelInfo[];
  formMap: Record<string, string>;
  loading: boolean;
  pagination: TablePaginationConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  addSimple: [];
  change: [pag: any, filters: any, sorter: any];
  copyAsDraft: [record: WorkflowProcessModelInfo];
  delete: [record: WorkflowProcessModelInfo];
  design: [record: WorkflowProcessModelInfo];
  edit: [record: WorkflowProcessModelInfo];
  fieldPermission: [record: WorkflowProcessModelInfo];
  history: [record: WorkflowProcessModelInfo];
  importBpmn: [];
  publish: [record: WorkflowProcessModelInfo];
  viewDesign: [record: WorkflowProcessModelInfo];
}>();

const filterStateRef = toRef(props, 'activeFilters');
const tableConfig = computed(() =>
  getWorkflowProcessColumns(
    {
      categoryMap: props.categoryMap,
      formMap: props.formMap,
    },
    filterStateRef,
    emit,
    props.pagination,
  ),
);
</script>

<template>
  <BaseTable
    :columns="tableConfig.columns"
    :data-source="dataSource"
    :loading="loading"
    :pagination="pagination"
    :scroll="tableConfig.scroll"
    row-key="id"
    @change="(pag, tableFilters, sorter) => $emit('change', pag, tableFilters, sorter)"
  >
    <template #toolbar>
      <Space wrap>
        <Button
          type="primary"
          @click="$emit('addSimple')"
        >
          新建流程
        </Button>
        <Button @click="$emit('importBpmn')">导入 BPMN</Button>
      </Space>
    </template>
  </BaseTable>
</template>
