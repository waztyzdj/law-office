<script setup lang="ts">
import type { AdminMonitorInstanceInfo } from '#/api/workflow';
import type { UserPickerValue } from '#/components/user-picker';
import type { Key } from 'ant-design-vue/es/vc-tree/interface';
import type { ProcessProgressNode } from '../../runtime/components/runtimeTypes';

import { onMounted, ref } from 'vue';

import { Input, message, Modal } from 'ant-design-vue';

import {
  reassignAdminMonitorTask,
  resendAdminMonitorNotice,
  terminateAdminMonitorInstance,
} from '#/api/workflow';
import { UserPicker } from '#/components/user-picker';

import WorkflowRuntimeFormDrawer from '../../runtime/components/WorkflowRuntimeFormDrawer.vue';
import WorkflowMonitorTable from './components/WorkflowMonitorTable.vue';
import WorkflowMonitorTree from './components/WorkflowMonitorTree.vue';
import { useWorkflowMonitorTable } from './hooks/useWorkflowMonitorTable';
import { useWorkflowMonitorTree } from './hooks/useWorkflowMonitorTree';

const {
  activeFilters,
  handleScopeChange,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
  scope,
} = useWorkflowMonitorTable();
const {
  expandedKeys,
  loadTree,
  loading: treeLoading,
  selectScope,
  selectedKeys,
  treeData,
} = useWorkflowMonitorTree();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();
const reassignOpen = ref(false);
const reassignSubmitting = ref(false);
const reassignTarget = ref<AdminMonitorInstanceInfo>();
const reassignProcessInstanceId = ref('');
const reassignTaskId = ref('');
const reassignUserId = ref<UserPickerValue>();
const operationReason = ref('');
const reasonOpen = ref(false);
const reasonSubmitting = ref(false);
const reasonAction = ref<'resendNotice' | 'terminate'>('terminate');
const reasonTarget = ref<AdminMonitorInstanceInfo>();
const reasonText = ref('');

const reasonModalTitle = {
  resendNotice: '补发待办通知',
  terminate: '终止流程',
};

function handleDetail(record: AdminMonitorInstanceInfo) {
  drawerRef.value?.open({ instanceId: record.id, mode: 'detail' });
}

function requireInstanceId(record?: AdminMonitorInstanceInfo) {
  if (!record?.id) {
    message.warning('流程实例不存在');
    return '';
  }
  return record.id;
}

function handleReassign(record: AdminMonitorInstanceInfo) {
  reassignTarget.value = record;
  reassignProcessInstanceId.value = '';
  reassignTaskId.value = '';
  drawerRef.value?.open({ instanceId: record.id, mode: 'adminMonitor' });
}

function handleAdminReassign(node: ProcessProgressNode) {
  const processInstanceId = detailProcessInstanceId();
  if (!processInstanceId || !node.taskId) {
    message.warning('当前待办不存在，不能改派');
    return;
  }
  reassignProcessInstanceId.value = processInstanceId;
  reassignTaskId.value = node.taskId;
  reassignUserId.value = undefined;
  operationReason.value = '';
  reassignOpen.value = true;
}

async function handleConfirmReassign() {
  const processInstanceId = reassignProcessInstanceId.value || requireInstanceId(reassignTarget.value);
  const targetUserId =
    typeof reassignUserId.value === 'string' ? reassignUserId.value : undefined;
  if (!processInstanceId) {
    return;
  }
  if (!targetUserId) {
    message.warning('请选择新的处理人');
    return;
  }
  if (!operationReason.value.trim()) {
    message.warning('请填写改派原因');
    return;
  }
  reassignSubmitting.value = true;
  try {
    await reassignAdminMonitorTask({
      operationReason: operationReason.value.trim(),
      processInstanceId,
      taskId: reassignTaskId.value,
      targetUserId,
    });
    message.success('已改派');
    reassignOpen.value = false;
    await loadData();
    await drawerRef.value?.open({ instanceId: processInstanceId, mode: 'adminMonitor' });
  } finally {
    reassignSubmitting.value = false;
  }
}

function detailProcessInstanceId() {
  return reassignTarget.value?.id || '';
}

function handleTerminate(record: AdminMonitorInstanceInfo) {
  openReasonModal('terminate', record);
}

function handleResendNotice(record: AdminMonitorInstanceInfo) {
  openReasonModal('resendNotice', record);
}

function openReasonModal(action: 'resendNotice' | 'terminate', record: AdminMonitorInstanceInfo) {
  reasonAction.value = action;
  reasonTarget.value = record;
  reasonText.value = '';
  reasonOpen.value = true;
}

async function handleConfirmReason() {
  const processInstanceId = requireInstanceId(reasonTarget.value);
  if (!processInstanceId) {
    return;
  }
  if (!reasonText.value.trim()) {
    message.warning('请填写维护原因');
    return;
  }
  reasonSubmitting.value = true;
  try {
    if (reasonAction.value === 'terminate') {
      await terminateAdminMonitorInstance({
        operationReason: reasonText.value.trim(),
        processInstanceId,
      });
      message.success('已终止');
    } else {
      await resendAdminMonitorNotice({
        operationReason: reasonText.value.trim(),
        processInstanceId,
      });
      message.success('已补发');
    }
    reasonOpen.value = false;
    await loadData();
  } finally {
    reasonSubmitting.value = false;
  }
}

async function handleTreeSelect(nextScope: typeof scope.value, key: Key) {
  selectScope(nextScope, key);
  await handleScopeChange(nextScope);
}

function handleTreeExpand(keys: Key[]) {
  expandedKeys.value = keys;
}

onMounted(async () => {
  await Promise.all([loadTree(), loadData()]);
});
</script>

<template>
  <div class="workflow-monitor-page">
    <div class="workflow-monitor-layout">
      <aside class="workflow-monitor-layout__tree">
        <WorkflowMonitorTree
          :expanded-keys="expandedKeys"
          :loading="treeLoading"
          :selected-keys="selectedKeys"
          :tree-data="treeData"
          @expand="handleTreeExpand"
          @select="handleTreeSelect"
        />
      </aside>
      <section class="workflow-monitor-layout__content">
        <WorkflowMonitorTable
          :active-filters="activeFilters"
          :data-source="records"
          :loading="loading"
          :pagination="pagination"
          :scope-title="scope.title"
          @change="handleTableChange"
          @detail="handleDetail"
          @reassign="handleReassign"
          @resend-notice="handleResendNotice"
          @terminate="handleTerminate"
        />
      </section>
    </div>

    <WorkflowRuntimeFormDrawer
      ref="drawerRef"
      @admin-reassign="handleAdminReassign"
    />

    <Modal
      v-model:open="reassignOpen"
      :confirm-loading="reassignSubmitting"
      title="管理员改派"
      width="520px"
      @ok="handleConfirmReassign"
    >
      <div class="workflow-monitor-reassign">
        <div>
          <div class="workflow-monitor-reassign__label">新处理人</div>
          <UserPicker
            v-model:value="reassignUserId"
            placeholder="请选择新的处理人"
            :max-count="1"
          mode="single"
          org-only
          />
        </div>
        <div class="workflow-monitor-reassign__reason">
          <div class="workflow-monitor-reassign__label">改派原因</div>
          <Input.TextArea
            v-model:value="operationReason"
            :maxlength="500"
            :rows="3"
            placeholder="请输入改派原因"
            show-count
          />
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="reasonOpen"
      :confirm-loading="reasonSubmitting"
      :ok-button-props="{ danger: reasonAction === 'terminate' }"
      :ok-text="reasonAction === 'terminate' ? '确认终止' : '确认补发'"
      :title="reasonModalTitle[reasonAction]"
      @ok="handleConfirmReason"
    >
      <Input.TextArea
        v-model:value="reasonText"
        :maxlength="500"
        :rows="3"
        :placeholder="reasonAction === 'terminate' ? '请输入终止原因' : '请输入补发通知原因'"
        show-count
      />
      <div
        v-if="reasonAction === 'terminate'"
        class="workflow-monitor-reason-tip"
      >
        终止后流程将结束，当前待办会被取消，不能继续办理。
      </div>
      <div
        v-else
        class="workflow-monitor-reason-tip"
      >
        只会补发当前有效待办通知，不会新增待办或改变流程状态。
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.workflow-monitor-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 88px);
  padding: 16px;
}

.workflow-monitor-layout {
  display: flex;
  flex: 1;
  gap: 12px;
  min-height: 0;
}

.workflow-monitor-layout__tree {
  flex: 0 0 280px;
  min-width: 240px;
  min-height: 0;
}

.workflow-monitor-layout__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.workflow-monitor-layout__content :deep(.workflow-monitor-table) {
  flex: 1;
  min-height: 0;
}

.workflow-monitor-reassign {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-monitor-reassign__label {
  margin-bottom: 6px;
  color: rgb(0 0 0 / 65%);
  font-size: 13px;
}

.workflow-monitor-reason-tip {
  margin-top: 8px;
  color: rgb(0 0 0 / 45%);
  font-size: 12px;
}
</style>
