<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

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
const route = useRoute();

function handleDetail(record: RuntimeTaskInfo) {
  drawerRef.value?.open({
    instanceId: record.processInstanceId,
    mode: 'done',
    task: record,
  });
}

function getRouteQueryValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}

async function openDetailFromRouteQuery() {
  const instanceId = getRouteQueryValue(route.query.instanceId);
  if (!instanceId) {
    return;
  }
  const matched = records.value.find((record) => record.processInstanceId === instanceId);
  await drawerRef.value?.open({
    instanceId,
    mode: 'done',
    task: matched,
  });
}

onMounted(async () => {
  await loadData();
  await openDetailFromRouteQuery();
});

watch(
  () => route.query.instanceId,
  async () => {
    await loadData();
    await openDetailFromRouteQuery();
  },
);
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
