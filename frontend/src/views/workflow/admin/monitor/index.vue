<script setup lang="ts">
import type { AdminMonitorInstanceInfo } from '#/api/workflow';
import type { UserPickerValue } from '#/components/user-picker';
import type { Key } from 'ant-design-vue/es/vc-tree/interface';
import type { ProcessProgressNode } from '../../runtime/components/runtimeTypes';

import { onMounted, ref } from 'vue';

import { Input, message, Modal } from 'ant-design-vue';

import {
  archiveAdminMonitorInstance,
  batchArchiveAdminMonitorByQuery,
  batchArchiveAdminMonitorInstances,
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
  buildCurrentQueryReq,
  handleScopeChange,
  handleTableChange,
  loadData,
  loading,
  onSelectChange,
  pagination,
  records,
  scope,
  selectedRowKeys,
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
const archiveModalOpen = ref(false);
const archiveSubmitting = ref(false);
const archivingSelected = ref(false);
const archivingByQuery = ref(false);
const archiveMode = ref<'query' | 'selected' | 'single'>('single');
const archiveTarget = ref<AdminMonitorInstanceInfo>();
const archiveReason = ref('');
const archiveFromDetail = ref(false);

const reasonModalTitle = {
  resendNotice: '补发待办通知',
  terminate: '终止流程',
};

function handleDetail(record: AdminMonitorInstanceInfo) {
  reassignTarget.value = record;
  reassignProcessInstanceId.value = '';
  reassignTaskId.value = '';
  drawerRef.value?.open({
    canArchive: Boolean(record.canArchive),
    instanceId: record.id,
    mode: 'adminMonitor',
  });
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
  drawerRef.value?.open({
    canArchive: Boolean(record.canArchive),
    instanceId: record.id,
    mode: 'adminMonitor',
  });
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

function handleArchive(record: AdminMonitorInstanceInfo) {
  archiveMode.value = 'single';
  archiveTarget.value = record;
  archiveReason.value = '';
  archiveFromDetail.value = false;
  archiveModalOpen.value = true;
}

function handleArchiveSelected() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择需要归档的流程');
    return;
  }
  archiveMode.value = 'selected';
  archiveTarget.value = undefined;
  archiveReason.value = '';
  archiveFromDetail.value = false;
  archiveModalOpen.value = true;
}

function handleArchiveByQuery() {
  archiveMode.value = 'query';
  archiveTarget.value = undefined;
  archiveReason.value = '';
  archiveFromDetail.value = false;
  archiveModalOpen.value = true;
}

function handleAdminArchive(processInstanceId: string) {
  if (!processInstanceId) {
    message.warning('流程实例不存在');
    return;
  }
  archiveMode.value = 'single';
  archiveTarget.value = { id: processInstanceId };
  archiveReason.value = '';
  archiveFromDetail.value = true;
  archiveModalOpen.value = true;
}

async function handleConfirmArchive() {
  const reason = archiveReason.value.trim() || undefined;
  archiveSubmitting.value = true;
  if (archiveMode.value === 'selected') {
    archivingSelected.value = true;
  }
  if (archiveMode.value === 'query') {
    archivingByQuery.value = true;
  }
  try {
    if (archiveMode.value === 'single') {
      const processInstanceId = archiveTarget.value?.id;
      if (!processInstanceId) {
        message.warning('流程实例不存在');
        return;
      }
      await archiveAdminMonitorInstance({ archiveReason: reason, processInstanceId });
      message.success('归档成功');
      if (archiveFromDetail.value) {
        await drawerRef.value?.open({
          canArchive: false,
          instanceId: processInstanceId,
          mode: 'adminMonitor',
        });
      }
    } else if (archiveMode.value === 'selected') {
      await batchArchiveAdminMonitorInstances({
        archiveReason: reason,
        processInstanceIds: selectedRowKeys.value.map(String),
      });
      selectedRowKeys.value = [];
      message.success('批量归档成功');
    } else {
      await batchArchiveAdminMonitorByQuery({
        ...buildCurrentQueryReq(),
        archiveReason: reason,
      });
      selectedRowKeys.value = [];
      message.success('按查询条件归档成功');
    }
    archiveModalOpen.value = false;
    await loadData();
  } finally {
    archiveSubmitting.value = false;
    archivingSelected.value = false;
    archivingByQuery.value = false;
    archiveFromDetail.value = false;
  }
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
          :archiving-by-query="archivingByQuery"
          :archiving-selected="archivingSelected"
          :data-source="records"
          :loading="loading"
          :pagination="pagination"
          :scope-title="scope.title"
          :selected-row-keys="selectedRowKeys"
          @archive="handleArchive"
          @archive-by-query="handleArchiveByQuery"
          @archive-selected="handleArchiveSelected"
          @change="handleTableChange"
          @detail="handleDetail"
          @reassign="handleReassign"
          @resend-notice="handleResendNotice"
          @select-change="onSelectChange"
          @terminate="handleTerminate"
        />
      </section>
    </div>

    <WorkflowRuntimeFormDrawer
      ref="drawerRef"
      @admin-archive="handleAdminArchive"
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
            :max-count="1"
            mode="single"
            org-only
            placeholder="请选择新的处理人"
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

    <Modal
      v-model:open="archiveModalOpen"
      :confirm-loading="archiveSubmitting"
      :title="archiveMode === 'query' ? '按查询条件归档' : '确认归档'"
      ok-text="确认归档"
      @ok="handleConfirmArchive"
    >
      <div class="workflow-monitor-archive-confirm">
        <div>
          {{
            archiveMode === 'single'
              ? '确认归档当前流程实例吗？'
              : archiveMode === 'selected'
                ? `确认归档选中的 ${selectedRowKeys.length} 个流程实例吗？`
                : '确认按当前查询条件批量归档未归档流程吗？单次最多处理 1000 条。'
          }}
        </div>
        <Input.TextArea
          v-model:value="archiveReason"
          :maxlength="500"
          :rows="3"
          placeholder="归档说明，可选"
          show-count
        />
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

.workflow-monitor-archive-confirm {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
