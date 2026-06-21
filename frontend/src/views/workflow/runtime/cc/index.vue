<script setup lang="ts">
import { onMounted, ref } from 'vue';
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

onMounted(async () => {
  await loadData();
  const instanceId = typeof route.query.instanceId === 'string' ? route.query.instanceId : '';
  if (!instanceId) {
    return;
  }
  const matched = records.value.find((record) => record.processInstanceId === instanceId);
  if (matched) {
    await handleDetail(matched);
  }
});
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
