<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import type { WorkflowCcRecordInfo } from '#/api/workflow';

import { markWorkflowCcRead } from '#/api/workflow';

import WorkflowRuntimeFormDrawer from '../components/WorkflowRuntimeFormDrawer.vue';
import WorkflowCcTable from './components/WorkflowCcTable.vue';
import { useWorkflowCcTable } from './hooks/useWorkflowCcTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
} = useWorkflowCcTable();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();
const route = useRoute();

async function handleDetail(record: WorkflowCcRecordInfo) {
  if (record.id && record.status !== 'read') {
    await markWorkflowCcRead(record.id);
    await loadData();
  }
  drawerRef.value?.open({
    instanceId: record.processInstanceId,
    mode: 'detail',
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
  if (matched) {
    await handleDetail(matched);
    return;
  }
  await drawerRef.value?.open({ instanceId, mode: 'detail' });
}

onMounted(async () => {
  await loadData();
  await openDetailFromRouteQuery();
});

watch(
  () => route.query.instanceId,
  async () => {
    await openDetailFromRouteQuery();
  },
);
</script>

<template>
  <div class="workflow-cc-page">
    <WorkflowCcTable
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
.workflow-cc-page {
  padding: 16px;
}
</style>
