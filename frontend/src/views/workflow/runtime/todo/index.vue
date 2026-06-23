<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

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
const route = useRoute();
const expiredUrgeNotice = '该待办已被其他审批人处理，催办已失效，当前为流程详情。';

function handleTask(record: RuntimeTaskInfo) {
  drawerRef.value?.open({ mode: 'todo', task: record, taskId: record.id });
}

function getRouteQueryValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}

async function openReadonlyDetail(instanceId: string) {
  await drawerRef.value?.open({
    instanceId,
    mode: 'detail',
    notice: expiredUrgeNotice,
  });
}

async function openTaskFromRouteQuery() {
  const taskId = getRouteQueryValue(route.query.taskId);
  const instanceId = getRouteQueryValue(route.query.instanceId);
  if (!taskId && !instanceId) {
    return;
  }
  const matched = records.value.find((record) => record.id === taskId)
    ?? records.value.find((record) => record.processInstanceId === instanceId);
  if (taskId) {
    try {
      await drawerRef.value?.open({ mode: 'todo', task: matched, taskId });
      return;
    } catch {
      if (!instanceId) {
        return;
      }
      await openReadonlyDetail(instanceId);
      return;
    }
  }
  if (matched) {
    handleTask(matched);
    return;
  }
  if (instanceId) {
    await openReadonlyDetail(instanceId);
  }
}

onMounted(async () => {
  await loadData();
  await openTaskFromRouteQuery();
});

watch(
  () => [route.query.taskId, route.query.instanceId],
  async () => {
    await loadData();
    await openTaskFromRouteQuery();
  },
);
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
