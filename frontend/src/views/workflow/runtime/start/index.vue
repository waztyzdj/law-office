<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { AvailableProcessInfo } from '#/api/workflow';
import type { WorkflowStartSearchForm } from './hooks/useWorkflowStartTable';

import WorkflowRuntimeFormDrawer from '../components/WorkflowRuntimeFormDrawer.vue';
import WorkflowStartCatalog from './components/WorkflowStartCatalog.vue';
import { useWorkflowStartTable } from './hooks/useWorkflowStartTable';

const {
  handleRefresh,
  handleResetSearch,
  handleSearch,
  loading,
  records,
  searchForm,
} = useWorkflowStartTable();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();

function handleStart(record: AvailableProcessInfo) {
  drawerRef.value?.open({ mode: 'start', process: record });
}

function handleUpdateSearchForm(value: WorkflowStartSearchForm) {
  Object.assign(searchForm, value);
}

onMounted(handleRefresh);
</script>

<template>
  <div class="workflow-start-page">
    <WorkflowStartCatalog
      :data-source="records"
      :loading="loading"
      :search-form="searchForm"
      @reset="handleResetSearch"
      @search="handleSearch"
      @start="handleStart"
      @update-search-form="handleUpdateSearchForm"
    />
    <WorkflowRuntimeFormDrawer
      ref="drawerRef"
      @success="handleRefresh"
    />
  </div>
</template>

<style scoped>
.workflow-start-page {
  display: flex;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 16px;
}
</style>
