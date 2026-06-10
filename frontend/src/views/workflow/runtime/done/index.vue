<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { RuntimeTaskInfo } from '#/api/workflow';

import WorkflowRuntimeFormDrawer from '../components/WorkflowRuntimeFormDrawer.vue';
import WorkflowDoneTable from './components/WorkflowDoneTable.vue';
import { useWorkflowDoneTable } from './hooks/useWorkflowDoneTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
} = useWorkflowDoneTable();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();

function handleDetail(record: RuntimeTaskInfo) {
  drawerRef.value?.open({
    instanceId: record.processInstanceId,
    mode: 'done',
    task: record,
  });
}

onMounted(loadData);
</script>

<template>
  <div class="workflow-done-page">
    <WorkflowDoneTable
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
.workflow-done-page {
  padding: 16px;
}
</style>
