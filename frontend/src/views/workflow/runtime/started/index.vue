<script setup lang="ts">
import { onMounted, ref } from 'vue';

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

function handleDetail(record: StartedInstanceInfo) {
  drawerRef.value?.open({ instanceId: record.id, mode: 'started' });
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
