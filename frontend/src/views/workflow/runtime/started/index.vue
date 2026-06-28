<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { message, Modal } from 'ant-design-vue';

import type { StartedInstanceInfo } from '#/api/workflow';

import { urgeWorkflowInstance, withdrawWorkflowInstance } from '#/api/workflow';
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
const route = useRoute();

function handleDetail(record: StartedInstanceInfo) {
  drawerRef.value?.open({ instanceId: record.id, mode: 'started' });
}

function getRouteQueryValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}

async function openDetailFromRouteQuery() {
  const instanceId = getRouteQueryValue(route.query.instanceId);
  if (!instanceId) {
    return;
  }
  await drawerRef.value?.open({ instanceId, mode: 'started' });
}

function handleWithdraw(record: StartedInstanceInfo) {
  if (!record.id) {
    return;
  }
  Modal.confirm({
    title: '确认撤回该审批？',
    content: '撤回后流程将结束，当前审批人不能继续办理。',
    okButtonProps: { danger: true },
    okText: '确认撤回',
    async onOk() {
      await withdrawWorkflowInstance(record.id!);
      message.success('已撤回');
      await loadData();
    },
  });
}

async function handleUrge(record: StartedInstanceInfo) {
  if (!record.id) {
    return;
  }
  await urgeWorkflowInstance(record.id);
  message.success('已催办');
  await loadData();
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
  <div class="workflow-started-page">
    <WorkflowStartedTable
      :active-filters="activeFilters"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
      @detail="handleDetail"
      @urge="handleUrge"
      @withdraw="handleWithdraw"
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
