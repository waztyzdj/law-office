<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { StartedInstanceInfo } from '#/api/workflow';

import WorkflowRuntimeFormDrawer from '../components/WorkflowRuntimeFormDrawer.vue';
import WorkflowStartedTable from './components/WorkflowStartedTable.vue';
import { useWorkflowStartedTable } from './hooks/useWorkflowStartedTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
} = useWorkflowStartedTable();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();

function handleDetail(record: StartedInstanceInfo) {
  drawerRef.value?.open({ instanceId: record.id, mode: 'started' });
}

onMounted(loadData);
</script>

<template>
  <div class="workflow-started-page">
    <WorkflowStartedTable
      :active-filters="activeFilters"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
      @detail="handleDetail"
    />
    <WorkflowRuntimeFormDrawer
      ref="drawerRef"
      @success="loadData"
    />
  </div>
</template>

<style scoped>
.workflow-started-page {
  padding: 16px;
}
</style>
