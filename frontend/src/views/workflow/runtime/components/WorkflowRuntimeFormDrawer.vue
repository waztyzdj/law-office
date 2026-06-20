<script setup lang="ts">
import type {
  AvailableProcessInfo,
  InstanceDetailInfo,
  RuntimeTaskInfo,
  StartFormInfo,
  TaskFormInfo,
} from '#/api/workflow';
import type { DrawerMode } from './runtimeTypes';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Modal, Select, Space, Spin, Tag } from 'ant-design-vue';

import {
  getStartForm,
  getWorkflowInstanceDetail,
  getWorkflowTaskForm,
} from '#/api/workflow';
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
import RuntimeFormRenderer from './RuntimeFormRenderer.vue';
import RuntimeSideTabs from './RuntimeSideTabs.vue';
import { workflowActionTitleMap } from './runtimeTypes';

interface DrawerPayload {
  instanceId?: string;
  mode: DrawerMode;
  process?: AvailableProcessInfo;
  task?: RuntimeTaskInfo;
  taskId?: string;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('detail');
const currentProcess = ref<AvailableProcessInfo>();
const currentTask = ref<RuntimeTaskInfo>();
const startForm = ref<StartFormInfo>();
const taskForm = ref<TaskFormInfo>();
const detail = ref<InstanceDetailInfo>();
const activeTab = ref('records');
const loading = ref(false);
const drawerOpened = ref(false);
let runtimeRenderFrameId: number | undefined;
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
} = useRuntimeAssigneeSelection({
  mode,
  startAssigneeSelectNodes: computed(() => startForm.value?.assigneeSelectNodes ?? []),
  taskAssigneeSelectNodes: computed(() =>
    shouldSelectNextAssigneeOnApprove.value
      ? (taskForm.value?.assigneeSelectNodes ?? [])
      : [],
  ),
});

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
  activeTab.value = 'records';
  drawerOpened.value = false;
  resetRuntimeFormData(payload.process);
  resetAssigneeSelection();
  resetRuntimeActions();
  drawerApi.setState({ title: drawerTitle.value });
}

async function open(payload: DrawerPayload) {
  resetState(payload);
  loading.value = true;
  drawerApi.open();
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
        detail.value = await getWorkflowInstanceDetail(taskForm.value.processInstanceId);
      }
      drawerApi.setState({ title: drawerTitle.value });
      return;
    }

    const instanceId = payload.instanceId ?? payload.task?.processInstanceId;
    if (instanceId) {
      detail.value = await getWorkflowInstanceDetail(instanceId);
    }
    drawerApi.setState({ title: drawerTitle.value });
  } finally {
    loading.value = false;
  }
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

            <RuntimeApprovalComment
              v-if="showApprovalComment"
              v-model:value="approvalComment"
            />
          </section>

          <aside class="runtime-side-section">
            <RuntimeSideTabs
              v-model:active-key="activeTab"
              :nodes="processProgressNodes"
            />
          </aside>
        </div>

        <RuntimeActionBar
          v-if="showRuntimeActions"
          :action-permissions="actionPermissions"
          :is-start-draft-task="isStartDraftTask"
          :is-start-mode="isStartMode"
          :is-todo-mode="isTodoMode"
          :saving="saving"
          :submitting="submitting"
          @action="handleRuntimeAction"
          @approve="handleApprove"
          @cancel="drawerApi.close()"
          @reject="handleReject"
          @save-start-draft="handleSaveStartDraft"
          @save-start-draft-task="handleSaveStartDraftTask"
          @submit-start="handleStartSubmit"
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

.runtime-form-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-right: 16px;
}

.runtime-form-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
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
