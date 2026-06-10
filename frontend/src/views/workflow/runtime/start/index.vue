<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { AvailableProcessInfo } from '#/api/workflow';

import WorkflowRuntimeFormDrawer from '../components/WorkflowRuntimeFormDrawer.vue';
import WorkflowStartTable from './components/WorkflowStartTable.vue';
import { useWorkflowStartTable } from './hooks/useWorkflowStartTable';

const {
  activeFilters,
  categoryOptions,
  handleRefresh,
  handleTableChange,
  loading,
  pagination,
  records,
} = useWorkflowStartTable();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();

function handleStart(record: AvailableProcessInfo) {
  drawerRef.value?.open({ mode: 'start', process: record });
}

onMounted(handleRefresh);
</script>

<template>
  <div class="workflow-start-page">
    <WorkflowStartTable
      :active-filters="activeFilters"
      :category-options="categoryOptions"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
      @start="handleStart"
    />
    <WorkflowRuntimeFormDrawer
      ref="drawerRef"
      @success="handleRefresh"
    />
  </div>
</template>

<style scoped>
.workflow-start-page {
  padding: 16px;
}
</style>
