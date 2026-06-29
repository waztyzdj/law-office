<script setup lang="ts">
import type {
  AvailableProcessInfo,
  AdminOperationRecordInfo,
  AssigneeSelectNodeInfo,
  InstanceDiagramInfo,
  InstanceDetailInfo,
  RuntimeTaskInfo,
  StartFormInfo,
  StartProcessResult,
  TaskFormInfo,
  WorkflowAttachmentSource,
} from '#/api/workflow';
import type { UserInfo } from '#/api/system/user';
import type { DrawerMode, ProcessProgressNode } from './runtimeTypes';

import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenDrawer } from '@vben/common-ui';

import { Alert, message, Modal, Select, Space, Spin, Tag } from 'ant-design-vue';

import {
  getAdminMonitorDetail,
  getStartForm,
  getWorkflowInstanceDiagram,
  getWorkflowInstanceDetail,
  getWorkflowTaskForm,
  previewNextAssigneeSelectNodes,
  sendWorkflowCc,
  urgeWorkflowInstance,
  withdrawWorkflowInstance,
} from '#/api/workflow';
import UserPickerPanel from '#/components/user-picker/UserPickerPanel.vue';
import { permissionCodes } from '#/constants/permissions';
import {
  formatApprovalProgress,
  getApprovalModeMeta,
} from '../../components/status';
import { useRuntimeAssigneeSelection } from './hooks/useRuntimeAssigneeSelection';
import { useRuntimeFormData } from './hooks/useRuntimeFormData';
import { useRuntimeProgressNodes } from './hooks/useRuntimeProgressNodes';
import { useRuntimeTaskActions } from './hooks/useRuntimeTaskActions';
import RuntimeActionBar from './RuntimeActionBar.vue';
import RuntimeApprovalComment from './RuntimeApprovalComment.vue';
import RuntimeAssigneeModals from './RuntimeAssigneeModals.vue';
import WorkflowAttachmentPanel from './WorkflowAttachmentPanel.vue';
import RuntimeFormRenderer from './RuntimeFormRenderer.vue';
import RuntimeSideTabs from './RuntimeSideTabs.vue';
import { workflowActionTitleMap } from './runtimeTypes';

interface DrawerPayload {
  instanceId?: string;
  mode: DrawerMode;
  notice?: string;
  process?: AvailableProcessInfo;
  task?: RuntimeTaskInfo;
  taskId?: string;
}

const emit = defineEmits<{
  adminReassign: [node: ProcessProgressNode];
  success: [];
}>();

const mode = ref<DrawerMode>('detail');
const currentProcess = ref<AvailableProcessInfo>();
const currentTask = ref<RuntimeTaskInfo>();
const startForm = ref<StartFormInfo>();
const taskForm = ref<TaskFormInfo>();
const detail = ref<InstanceDetailInfo>();
const diagram = ref<InstanceDiagramInfo>();
const adminOperationRecords = ref<AdminOperationRecordInfo[]>([]);
const activeTab = ref('records');
const loading = ref(false);
const drawerOpened = ref(false);
const manualCcOpen = ref(false);
const manualCcSelectedUsers = ref<UserInfo[]>([]);
const manualCcSubmitting = ref(false);
const runtimeNotice = ref('');
const urgeSubmitting = ref(false);
const withdrawSubmitting = ref(false);
const attachmentPanelKey = ref(0);
const attachmentPanelRef = ref<InstanceType<typeof WorkflowAttachmentPanel>>();
let runtimeRenderFrameId: number | undefined;
const { hasAccessByCodes } = useAccess();
const actionPermissions = computed(() => taskForm.value?.actionPermissions);
const returnNodeOptions = computed(() =>
  (taskForm.value?.returnNodes ?? []).map((item) => ({
    label: item.nodeName,
    value: item.nodeId,
  })),
);
const actionModalTitle = computed(() => {
  return currentAction.value
    ? workflowActionTitleMap[currentAction.value]
    : '审批操作';
});
const runtimeContentReady = computed(() => {
  if (!drawerOpened.value) {
    return false;
  }
  if (loading.value) {
    return false;
  }
  if (mode.value === 'start') {
    return Boolean(startForm.value);
  }
  if (mode.value === 'todo') {
    return Boolean(taskForm.value);
  }
  return Boolean(detail.value);
});
const shouldRenderAssigneeModals = computed(
  () =>
    actionUserPickerOpen.value ||
    assigneePickerOpen.value ||
    assigneeSelectModalOpen.value,
);
const taskApprovalMeta = computed(() =>
  getApprovalModeMeta(taskForm.value?.approvalMode),
);
const taskApprovalProgress = computed(() =>
  taskForm.value ? formatApprovalProgress(taskForm.value) : '',
);
const showTaskContext = computed(
  () => isTodoMode.value && Boolean(taskForm.value) && !isStartDraftTask.value,
);
const shouldSelectNextAssigneeOnApprove = computed(() => {
  if (taskForm.value?.approvalMode !== 'countersign') {
    return true;
  }
  const completed = taskForm.value.groupCompleted ?? 0;
  const total = taskForm.value.groupTotal ?? 1;
  return completed + 1 >= total;
});
const canManualCc = computed(() => Boolean(detail.value?.processInstance?.id));
const canUrge = computed(
  () => mode.value === 'started' && Boolean(detail.value?.processInstance?.canUrge),
);
const canWithdraw = computed(
  () => mode.value === 'started' && Boolean(detail.value?.processInstance?.canWithdraw),
);
const isAdminMonitorMode = computed(() => mode.value === 'adminMonitor');
const canAdminMonitorReassign = computed(
  () =>
    isAdminMonitorMode.value &&
    hasAccessByCodes([permissionCodes.workflowMonitor.manage]),
);
const runtimeProcessInstanceId = computed(
  () => detail.value?.processInstance?.id || taskForm.value?.processInstanceId,
);
const showAttachmentPanel = computed(
  () => isStartMode.value || Boolean(runtimeProcessInstanceId.value),
);
const attachmentEditable = computed(() => isStartMode.value || isTodoMode.value);
const runtimeAttachmentSource = computed<WorkflowAttachmentSource>(() =>
  isStartMode.value || isStartDraftTask.value ? 'start' : 'task',
);

const {
  approvalComment,
  businessKey,
  collectFormDataJson,
  defaultFieldPermission,
  drawerTitle,
  fieldPermissions,
  formDataJson,
  formOptionJson,
  formSchemaJson,
  handleRuntimeFormRef,
  instanceTitle,
  isStartDraftTask,
  isStartMode,
  isTodoMode,
  readonly,
  resetRuntimeFormData,
  resolveApprovalComment,
  showApprovalComment,
  showRuntimeActions,
  validateApprovalComment,
  validateTitle,
} = useRuntimeFormData({
  currentProcess,
  detail,
  mode,
  startForm,
  taskForm,
});

const {
  assigneePickerOpen,
  assigneeSelectModalOpen,
  buildFixedSelectedAssignees,
  buildFreeSelectedAssignees,
  closeAssigneePicker,
  closeAssigneeSelectModal,
  draftSelectedAssigneeUsers,
  freeSelectMode,
  openNextAssigneeSelect,
  pendingSubmitPayload,
  resetAssigneeSelection,
  runtimeFixedSelectNodes,
  selectedAssignees,
} = useRuntimeAssigneeSelection();

const { processProgressNodes } = useRuntimeProgressNodes(detail);

const {
  actionForm,
  actionModalOpen,
  actionSelectedUsers,
  actionSubmitting,
  actionUserPickerOpen,
  closeActionUserPicker,
  currentAction,
  handleActionConfirm,
  handleActionUserPickerConfirm,
  handleApprove,
  handleAssigneePickerConfirm: submitWithSelectedAssignee,
  handleReject,
  handleRuntimeAction,
  handleSaveStartDraft,
  handleSaveStartDraftTask,
  handleStartSubmit,
  resetRuntimeActions,
  saving,
  submitting,
  updateActionField,
} = useRuntimeTaskActions({
  actionTitle: actionModalTitle,
  afterStartCreated: handleStartCreated,
  approvalComment,
  businessKey,
  collectFormDataJson,
  currentProcess,
  detail,
  instanceTitle,
  isStartDraftTask,
  isStartMode,
  onClose: () => drawerApi.close(),
  onSuccess: () => emit('success'),
  openNextAssigneeSelect,
  previewNextAssigneeSelectNodes: previewAssigneeSelectNodes,
  resolveApprovalComment,
  taskForm,
  validateApprovalComment,
  validateTitle,
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[90vw]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'workflow-runtime-form-drawer',
  footer: false,
  onClosed: () => {
    cancelRuntimeContentRender();
    drawerOpened.value = false;
  },
  onOpened: () => {
    scheduleRuntimeContentRender();
  },
  title: drawerTitle.value,
});

function cancelRuntimeContentRender() {
  if (runtimeRenderFrameId === undefined) {
    return;
  }
  window.cancelAnimationFrame(runtimeRenderFrameId);
  runtimeRenderFrameId = undefined;
}

function scheduleRuntimeContentRender() {
  cancelRuntimeContentRender();
  runtimeRenderFrameId = window.requestAnimationFrame(() => {
    runtimeRenderFrameId = undefined;
    drawerOpened.value = true;
  });
}

function resetState(payload: DrawerPayload) {
  cancelRuntimeContentRender();
  mode.value = payload.mode;
  currentProcess.value = payload.process;
  currentTask.value = payload.task;
  startForm.value = undefined;
  taskForm.value = undefined;
  detail.value = undefined;
  diagram.value = undefined;
  adminOperationRecords.value = [];
  activeTab.value = 'records';
  drawerOpened.value = false;
  manualCcOpen.value = false;
  manualCcSelectedUsers.value = [];
  manualCcSubmitting.value = false;
  runtimeNotice.value = payload.notice ?? '';
  urgeSubmitting.value = false;
  withdrawSubmitting.value = false;
  attachmentPanelKey.value += 1;
  resetRuntimeFormData(payload.process);
  resetAssigneeSelection();
  resetRuntimeActions();
  drawerApi.setState({ title: drawerTitle.value });
}

async function previewAssigneeSelectNodes(
  payload: { formDataJson?: string; processModelId?: string; taskId?: string },
): Promise<AssigneeSelectNodeInfo[]> {
  if (mode.value === 'todo' && !shouldSelectNextAssigneeOnApprove.value) {
    return [];
  }
  if (mode.value === 'start') {
    const processModelId = payload.processModelId ?? currentProcess.value?.id;
    if (!processModelId) {
      return [];
    }
    return previewNextAssigneeSelectNodes({
      formDataJson: payload.formDataJson,
      processModelId,
    });
  }
  const taskId = payload.taskId ?? taskForm.value?.taskId;
  if (!taskId) {
    return [];
  }
  return previewNextAssigneeSelectNodes({
    formDataJson: payload.formDataJson,
    taskId,
  });
}

async function open(payload: DrawerPayload) {
  resetState(payload);
  loading.value = true;
  drawerApi.open();
  scheduleRuntimeContentRender();
  await loadData(payload);
}

async function loadData(payload: DrawerPayload) {
  loading.value = true;
  try {
    if (payload.mode === 'start' && payload.process?.id) {
      startForm.value = await getStartForm(payload.process.id);
      drawerApi.setState({ title: drawerTitle.value });
      return;
    }

    if (payload.mode === 'todo') {
      const taskId = payload.taskId ?? payload.task?.id;
      if (!taskId) {
        return;
      }
      taskForm.value = await getWorkflowTaskForm(taskId);
      instanceTitle.value = taskForm.value.instanceTitle ?? '';
      if (taskForm.value.processInstanceId) {
        await loadInstanceRuntimeData(taskForm.value.processInstanceId);
      }
      drawerApi.setState({ title: drawerTitle.value });
      return;
    }

    const instanceId = payload.instanceId ?? payload.task?.processInstanceId;
    if (instanceId && payload.mode === 'adminMonitor') {
      await loadAdminMonitorRuntimeData(instanceId);
      return;
    }

    if (instanceId) {
      await loadInstanceRuntimeData(instanceId);
    }
    drawerApi.setState({ title: drawerTitle.value });
  } finally {
    loading.value = false;
  }
}

async function loadInstanceRuntimeData(instanceId: string) {
  const [nextDetail, nextDiagram] = await Promise.all([
    getWorkflowInstanceDetail(instanceId),
    getWorkflowInstanceDiagram(instanceId),
  ]);
  detail.value = nextDetail;
  diagram.value = nextDiagram;
}

async function loadAdminMonitorRuntimeData(instanceId: string) {
  const [nextMonitorDetail, nextDiagram] = await Promise.all([
    getAdminMonitorDetail(instanceId),
    getWorkflowInstanceDiagram(instanceId),
  ]);
  detail.value = nextMonitorDetail.detail;
  adminOperationRecords.value = nextMonitorDetail.adminOperationRecords ?? [];
  diagram.value = nextDiagram;
}

async function handleAssigneePickerConfirm() {
  await submitWithSelectedAssignee(
    pendingSubmitPayload.value,
    buildFreeSelectedAssignees(),
    closeAssigneePicker,
  );
}

async function handleAssigneeSelectConfirm() {
  await submitWithSelectedAssignee(
    pendingSubmitPayload.value,
    buildFixedSelectedAssignees(),
    closeAssigneeSelectModal,
  );
}

function openManualCcPicker() {
  manualCcSelectedUsers.value = [];
  manualCcOpen.value = true;
}

function closeManualCcPicker() {
  manualCcOpen.value = false;
  manualCcSelectedUsers.value = [];
}

async function handleManualCcConfirm() {
  const processInstanceId = detail.value?.processInstance?.id;
  if (!processInstanceId) {
    return;
  }
  const receiverUserIds = manualCcSelectedUsers.value
    .map((user) => user.id)
    .filter(Boolean) as string[];
  if (receiverUserIds.length === 0) {
    message.warning('请选择抄送人员');
    return;
  }
  manualCcSubmitting.value = true;
  try {
    await sendWorkflowCc({
      processInstanceId,
      receiverUserIds,
    });
    message.success('已发送抄送');
    closeManualCcPicker();
    if (mode.value === 'adminMonitor') {
      await loadAdminMonitorRuntimeData(processInstanceId);
    } else {
      await loadInstanceRuntimeData(processInstanceId);
    }
    emit('success');
  } finally {
    manualCcSubmitting.value = false;
  }
}

async function handleStartCreated(result: StartProcessResult) {
  const processInstanceId = result.processInstanceId;
  if (!processInstanceId) {
    return;
  }
  try {
    await attachmentPanelRef.value?.bindPendingAttachments(processInstanceId);
  } catch {
    message.error('申请已创建，但附件绑定失败，请稍后在办理页重新上传');
  }
}

function handleWithdraw() {
  const processInstanceId = detail.value?.processInstance?.id;
  if (!processInstanceId) {
    return;
  }
  Modal.confirm({
    title: '确认撤回该审批？',
    content: '撤回后流程将结束，当前审批人不能继续办理。',
    okButtonProps: { danger: true },
    okText: '确认撤回',
    async onOk() {
      withdrawSubmitting.value = true;
      try {
        await withdrawWorkflowInstance(processInstanceId);
        message.success('已撤回');
        await loadInstanceRuntimeData(processInstanceId);
        emit('success');
      } finally {
        withdrawSubmitting.value = false;
      }
    },
  });
}

async function handleUrge() {
  const processInstanceId = detail.value?.processInstance?.id;
  if (!processInstanceId) {
    return;
  }
  urgeSubmitting.value = true;
  try {
    await urgeWorkflowInstance(processInstanceId);
    message.success('已催办');
    await loadInstanceRuntimeData(processInstanceId);
    emit('success');
  } finally {
    urgeSubmitting.value = false;
  }
}

function handleAdminReassign(node: ProcessProgressNode) {
  if (mode.value !== 'adminMonitor') {
    return;
  }
  emit('adminReassign', node);
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading">
      <div
        v-if="runtimeContentReady"
        class="runtime-drawer-body"
      >
        <Alert
          v-if="runtimeNotice"
          class="runtime-notice"
          show-icon
          type="warning"
          :message="runtimeNotice"
        />
        <div
          v-if="showTaskContext"
          class="task-context-bar"
        >
          <Space wrap>
            <span class="task-context-title">{{ taskForm?.taskName || taskForm?.nodeId }}</span>
            <Tag :color="taskApprovalMeta.color">
              {{ taskApprovalMeta.label }}
            </Tag>
            <Tag
              v-if="taskApprovalProgress"
              color="processing"
            >
              {{ taskApprovalProgress }}
            </Tag>
          </Space>
        </div>

        <div class="runtime-drawer-main">
          <section class="runtime-form-section">
            <div class="runtime-form-content">
              <RuntimeFormRenderer
                :ref="handleRuntimeFormRef"
                :default-permission="defaultFieldPermission"
                :field-permissions="fieldPermissions"
                :form-data-json="formDataJson"
                :loading="loading"
                :option-json="formOptionJson"
                :readonly="readonly"
                :schema-json="formSchemaJson"
              />
            </div>
            <div
              v-if="showAttachmentPanel || showApprovalComment"
              class="runtime-form-fixed-panels"
            >
              <WorkflowAttachmentPanel
                v-if="showAttachmentPanel"
                :key="attachmentPanelKey"
                ref="attachmentPanelRef"
                :attachment-source="runtimeAttachmentSource"
                :editable="attachmentEditable"
                :node-id="taskForm?.nodeId"
                :node-name="taskForm?.taskName"
                :process-instance-id="runtimeProcessInstanceId"
                :task-id="taskForm?.taskId"
              />

              <RuntimeApprovalComment
                v-if="showApprovalComment"
                v-model:value="approvalComment"
              />
            </div>
          </section>

          <aside class="runtime-side-section">
            <RuntimeSideTabs
              v-model:active-key="activeTab"
              :admin-monitor-mode="isAdminMonitorMode"
              :admin-reassignable="canAdminMonitorReassign"
              :admin-operation-records="adminOperationRecords"
              :cc-records="detail?.ccRecords ?? []"
              :detail="detail"
              :diagram="diagram"
              :nodes="processProgressNodes"
              @admin-reassign="handleAdminReassign"
            />
          </aside>
        </div>

        <RuntimeActionBar
          v-if="showRuntimeActions || canManualCc || canUrge || canWithdraw"
          :action-permissions="actionPermissions"
          :can-cc="canManualCc"
          :can-urge="canUrge"
          :can-withdraw="canWithdraw"
          :cc-submitting="manualCcSubmitting"
          :is-start-draft-task="isStartDraftTask"
          :is-start-mode="isStartMode"
          :is-todo-mode="isTodoMode"
          :saving="saving"
          :submitting="submitting"
          :urge-submitting="urgeSubmitting"
          :withdraw-submitting="withdrawSubmitting"
          @action="handleRuntimeAction"
          @approve="handleApprove"
          @cancel="drawerApi.close()"
          @cc="openManualCcPicker"
          @reject="handleReject"
          @save-start-draft="handleSaveStartDraft"
          @save-start-draft-task="handleSaveStartDraftTask"
          @submit-start="handleStartSubmit"
          @urge="handleUrge"
          @withdraw="handleWithdraw"
        />
      </div>
      <div
        v-else
        class="runtime-drawer-placeholder"
      />
    </Spin>

    <Modal
      v-model:open="actionModalOpen"
      :confirm-loading="actionSubmitting"
      :title="actionModalTitle"
      @ok="handleActionConfirm"
    >
      <div class="action-form">
        <Select
          v-if="currentAction === 'return'"
          :options="returnNodeOptions"
          :value="actionForm.targetNodeId"
          placeholder="请选择退回节点"
          @update:value="(value) => updateActionField('targetNodeId', String(value ?? ''))"
        />
      </div>
    </Modal>

    <RuntimeAssigneeModals
      v-if="shouldRenderAssigneeModals"
      v-model:action-open="actionUserPickerOpen"
      v-model:action-selected-users="actionSelectedUsers"
      v-model:assignee-picker-open="assigneePickerOpen"
      v-model:assignee-select-modal-open="assigneeSelectModalOpen"
      v-model:draft-selected-assignee-users="draftSelectedAssigneeUsers"
      v-model:selected-assignees="selectedAssignees"
      :action-submitting="actionSubmitting"
      :current-action="currentAction"
      :current-task="currentTask"
      :fixed-select-nodes="runtimeFixedSelectNodes"
      :free-select-mode="freeSelectMode"
      :submitting="submitting"
      @action-cancel="closeActionUserPicker"
      @action-confirm="handleActionUserPickerConfirm"
      @assignee-picker-cancel="closeAssigneePicker"
      @assignee-picker-confirm="handleAssigneePickerConfirm"
      @assignee-select-cancel="closeAssigneeSelectModal"
      @assignee-select-confirm="handleAssigneeSelectConfirm"
    />

    <Modal
      v-model:open="manualCcOpen"
      :confirm-loading="manualCcSubmitting"
      :destroy-on-close="false"
      :width="960"
      cancel-text="取消"
      ok-text="确定并发送"
      title="选择抄送人员"
      wrap-class-name="workflow-user-picker-modal-wrap"
      @cancel="closeManualCcPicker"
      @ok="handleManualCcConfirm"
    >
      <UserPickerPanel
        mode="multiple"
        org-only
        :selected-users="manualCcSelectedUsers"
        @update:selected-users="(users) => (manualCcSelectedUsers = users)"
      />
    </Modal>
  </Drawer>
</template>

<style scoped>
:global(.workflow-runtime-form-drawer) {
  height: calc(100vh - 110px);
  overflow: hidden;
  padding: 20px 24px;
}

:global(.workflow-runtime-form-drawer .ant-spin-container),
:global(.workflow-runtime-form-drawer .ant-spin-nested-loading) {
  height: 100%;
}

.runtime-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.runtime-notice {
  margin-bottom: 12px;
}

.runtime-drawer-placeholder {
  height: 100%;
  min-height: 360px;
}

.runtime-drawer-main {
  display: grid;
  flex: 1;
  gap: 20px;
  grid-template-columns: minmax(0, 3fr) minmax(360px, 2fr);
  min-height: 0;
}

.task-context-bar {
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 14px;
  padding-bottom: 12px;
}

.task-context-title {
  color: #111827;
  font-weight: 500;
}

:global(.workflow-user-picker-modal-wrap) {
  align-items: center;
  display: flex;
  justify-content: center;
}

:global(.workflow-user-picker-modal-wrap .ant-modal) {
  max-width: 960px;
  top: 0;
}

:global(.workflow-user-picker-modal-wrap .ant-modal-content) {
  display: flex;
  flex-direction: column;
  height: 640px;
}

:global(.workflow-user-picker-modal-wrap .ant-modal-body) {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.runtime-form-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding-right: 16px;
}

.runtime-form-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.runtime-form-fixed-panels {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  padding-top: 8px;
}

.runtime-side-section {
  border-left: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding-left: 20px;
}

.action-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

@media (max-width: 768px) {
  :global(.workflow-runtime-form-drawer) {
    padding: 16px;
  }

  .runtime-drawer-main {
    grid-template-columns: 1fr;
  }

  .runtime-form-section {
    padding-right: 0;
  }

  .runtime-side-section {
    border-left: 0;
    border-top: 1px solid #f0f0f0;
    padding-left: 0;
    padding-top: 16px;
  }
}
</style>
