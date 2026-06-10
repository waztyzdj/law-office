<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { RuntimeTaskInfo } from '#/api/workflow';

import WorkflowRuntimeFormDrawer from '../components/WorkflowRuntimeFormDrawer.vue';
import WorkflowTodoTable from './components/WorkflowTodoTable.vue';
import { useWorkflowTodoTable } from './hooks/useWorkflowTodoTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
} = useWorkflowTodoTable();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();

function handleTask(record: RuntimeTaskInfo) {
  drawerRef.value?.open({ mode: 'todo', task: record, taskId: record.id });
}

onMounted(loadData);
</script>

<template>
  <div class="workflow-todo-page">
    <WorkflowTodoTable
      :active-filters="activeFilters"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
      @handle-task="handleTask"
    />
    <WorkflowRuntimeFormDrawer
      ref="drawerRef"
      @success="loadData"
    />
  </div>
</template>

<style scoped>
.workflow-todo-page {
  padding: 16px;
}
</style>
