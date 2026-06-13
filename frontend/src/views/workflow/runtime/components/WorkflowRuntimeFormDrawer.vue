<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';

import type {
  AvailableProcessInfo,
  AssigneeSelectNodeInfo,
  InstanceDetailInfo,
  OperationRecordInfo,
  RuntimeTaskInfo,
  SelectedAssigneeReq,
  StartFormInfo,
  StartProcessReq,
  TaskActionReq,
  TaskFormInfo,
} from '#/api/workflow';
import type { UserInfo } from '#/api/system/user';
import type {
  DrawerMode,
  ProcessProgressNode,
  WorkflowAction,
} from './runtimeTypes';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message, Modal, Select, Spin } from 'ant-design-vue';

import {
  addSignWorkflowTask,
  approveWorkflowTask,
  getStartForm,
  getWorkflowInstanceDetail,
  getWorkflowTaskForm,
  rejectWorkflowTask,
  returnWorkflowTask,
  saveWorkflowStartDraft,
  saveWorkflowStartDraftTask,
  startWorkflowProcess,
  submitWorkflowStartDraft,
  transferWorkflowTask,
} from '#/api/workflow';
import { getStatusMeta } from '../../components/status';
import RuntimeActionBar from './RuntimeActionBar.vue';
import RuntimeApprovalComment from './RuntimeApprovalComment.vue';
import RuntimeAssigneeModals from './RuntimeAssigneeModals.vue';
import RuntimeFormRenderer from './RuntimeFormRenderer.vue';
import RuntimeSideTabs from './RuntimeSideTabs.vue';

type PendingSubmitPayload = StartProcessReq | TaskActionReq;

interface DrawerPayload {
  instanceId?: string;
  mode: DrawerMode;
  process?: AvailableProcessInfo;
  task?: RuntimeTaskInfo;
  taskId?: string;
}

interface ActionForm {
  targetNodeId: string;
}

const emit = defineEmits<{
  success: [];
}>();

const userStore = useUserStore();

const mode = ref<DrawerMode>('detail');
const currentProcess = ref<AvailableProcessInfo>();
const currentTask = ref<RuntimeTaskInfo>();
const startForm = ref<StartFormInfo>();
const taskForm = ref<TaskFormInfo>();
const detail = ref<InstanceDetailInfo>();
const runtimeFormRef = ref<InstanceType<typeof RuntimeFormRenderer>>();
const activeTab = ref('records');
const instanceTitle = ref('');
const businessKey = ref('');
const approvalComment = ref('');
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const actionModalOpen = ref(false);
const actionSubmitting = ref(false);
const actionUserPickerOpen = ref(false);
const assigneePickerOpen = ref(false);
const assigneeSelectModalOpen = ref(false);
const pendingSubmitPayload = ref<PendingSubmitPayload>();
const currentAction = ref<WorkflowAction>();
const actionForm = ref<ActionForm>(getEmptyActionForm());
const actionSelectedUsers = ref<UserInfo[]>([]);
const selectedAssignees = ref<SelectedAssigneeReq[]>([]);
const draftSelectedAssigneeUsers = ref<UserInfo[]>([]);

const currentUserName = computed(
  () =>
    userStore.userInfo?.realName ||
    userStore.userInfo?.realname ||
    userStore.userInfo?.username ||
    '',
);
const isStartMode = computed(() => mode.value === 'start');
const isTodoMode = computed(() => mode.value === 'todo');
const isStartDraftTask = computed(
  () => taskForm.value?.taskType === 'start_draft',
);
const readonly = computed(() => !isStartMode.value && !isTodoMode.value);
const showApprovalComment = computed(
  () => isTodoMode.value && !isStartDraftTask.value,
);
const showRuntimeActions = computed(() => isStartMode.value || isTodoMode.value);
const drawerTitle = computed(() => {
  if (isStartMode.value) {
    return currentProcess.value?.processName
      ? `发起申请：${currentProcess.value.processName}`
      : '发起申请';
  }
  if (isTodoMode.value) {
    return isStartDraftTask.value ? '提交申请' : '办理审批';
  }
  return '审批详情';
});
const formSchemaJson = computed(
  () =>
    startForm.value?.schemaJson ||
    taskForm.value?.schemaJson ||
    detail.value?.formInstance?.formSchemaSnapshotJson ||
    '[]',
);
const formOptionJson = computed(
  () =>
    startForm.value?.optionJson ||
    taskForm.value?.optionJson ||
    detail.value?.formInstance?.formOptionSnapshotJson ||
    '{}',
);
const formDataJson = computed(
  () => taskForm.value?.formDataJson || detail.value?.formInstance?.formDataJson || '{}',
);
const fieldPermissions = computed(() => {
  if (isStartMode.value) {
    return startForm.value?.fieldPermissions ?? [];
  }
  return taskForm.value?.fieldPermissions ?? [];
});
const assigneeSelectNodes = computed<AssigneeSelectNodeInfo[]>(() => {
  if (isStartMode.value) {
    return startForm.value?.assigneeSelectNodes ?? [];
  }
  if (isTodoMode.value) {
    return taskForm.value?.assigneeSelectNodes ?? [];
  }
  return [];
});
const runtimeFreeSelectNode = computed(() =>
  assigneeSelectNodes.value.find((node) => node.assigneeType === 'starter_select'),
);
const runtimeFixedSelectNodes = computed(() =>
  assigneeSelectNodes.value.filter((node) => node.assigneeType !== 'starter_select'),
);
const processInstance = computed(() => detail.value?.processInstance);
const chronologicalRecords = computed(() =>
  [...(detail.value?.records ?? [])].sort((a, b) =>
    String(a.operateTime ?? '').localeCompare(String(b.operateTime ?? '')),
  ),
);
const currentTasks = computed(() => detail.value?.currentTasks ?? []);
const processProgressNodes = computed<ProcessProgressNode[]>(() => {
  const nodes: ProcessProgressNode[] = chronologicalRecords.value.map((record, index) => ({
    action: record.action,
    actor: formatActor(record),
    comment: formatRecordComment(record),
    id: record.id ?? `record-${index}`,
    name: formatRecordNode(record),
    status: 'done',
    time: record.operateTime,
  }));

  currentTasks.value.forEach((task, index) => {
    nodes.push({
      actor: formatTaskActor(task),
      id: task.id ?? `current-${index}`,
      name: task.taskName ?? task.nodeId ?? '当前任务',
      status: 'current',
      time: task.startTime,
    });
  });

  if (isProcessFinished(processInstance.value?.status)) {
    const lastRecord = chronologicalRecords.value.at(-1);
    nodes.push({
      actor: '系统',
      comment: getProcessEndComment(processInstance.value?.status),
      id: `${processInstance.value?.id ?? 'process'}-end`,
      name: '流程结束',
      resultStatus: processInstance.value?.status,
      status: 'end',
      time: processInstance.value?.endTime || lastRecord?.operateTime,
    });
  }

  return nodes;
});
const actionPermissions = computed(() => taskForm.value?.actionPermissions);
const returnNodeOptions = computed(() =>
  (taskForm.value?.returnNodes ?? []).map((item) => ({
    label: item.nodeName,
    value: item.nodeId,
  })),
);
const actionModalTitle = computed(() => {
  const titleMap: Record<WorkflowAction, string> = {
    addSign: '加签',
    return: '退回',
    transfer: '转办',
  };
  return currentAction.value ? titleMap[currentAction.value] : '审批操作';
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[90vw]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'workflow-runtime-form-drawer',
  footer: false,
  title: drawerTitle.value,
});

function handleRuntimeFormRef(instance: Element | ComponentPublicInstance | null) {
  runtimeFormRef.value =
    instance as InstanceType<typeof RuntimeFormRenderer> | undefined;
}

function resetState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentProcess.value = payload.process;
  currentTask.value = payload.task;
  startForm.value = undefined;
  taskForm.value = undefined;
  detail.value = undefined;
  runtimeFormRef.value = undefined;
  activeTab.value = 'records';
  instanceTitle.value = payload.process
    ? buildDefaultInstanceTitle(payload.process)
    : '';
  businessKey.value = '';
  approvalComment.value = '';
  actionModalOpen.value = false;
  actionUserPickerOpen.value = false;
  assigneePickerOpen.value = false;
  assigneeSelectModalOpen.value = false;
  pendingSubmitPayload.value = undefined;
  currentAction.value = undefined;
  actionForm.value = getEmptyActionForm();
  actionSelectedUsers.value = [];
  selectedAssignees.value = [];
  draftSelectedAssigneeUsers.value = [];
  drawerApi.setState({ title: drawerTitle.value });
}

async function open(payload: DrawerPayload) {
  resetState(payload);
  drawerApi.open();
  await loadData(payload);
}

async function loadData(payload: DrawerPayload) {
  loading.value = true;
  try {
    if (payload.mode === 'start' && payload.process?.id) {
      startForm.value = await getStartForm(payload.process.id);
      drawerApi.setState({ title: drawerTitle.value });
      await nextTick();
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
      await nextTick();
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

function buildDefaultInstanceTitle(record: AvailableProcessInfo) {
  const processName = record.processName ?? '';
  if (!currentUserName.value) {
    return processName;
  }
  return processName ? `${currentUserName.value}的${processName}` : currentUserName.value;
}

async function collectFormDataJson(validate: boolean) {
  const formData = validate
    ? await runtimeFormRef.value?.getValidatedFormData()
    : runtimeFormRef.value?.getFormData?.();
  return JSON.stringify(formData ?? {});
}

function validateTitle() {
  if (!instanceTitle.value.trim()) {
    message.warning('请输入申请标题');
    return false;
  }
  return true;
}

function validateSelectedAssignees(nodes: AssigneeSelectNodeInfo[]) {
  for (const node of nodes) {
    if (!node.nodeId) {
      continue;
    }
    const selected = selectedAssignees.value.find((item) => item.nodeId === node.nodeId);
    if (!selected?.userIds?.[0]) {
      message.warning(`请选择${node.nodeName || node.nodeId}的审批人`);
      return false;
    }
  }
  return true;
}

function collectSelectedAssignees() {
  const result: SelectedAssigneeReq[] = [];
  for (const item of selectedAssignees.value) {
    const nodeId = item.nodeId;
    const userId = item.userIds?.[0];
    if (nodeId && userId) {
      result.push({ nodeId, userIds: [userId] });
    }
  }
  return result;
}

function openNextAssigneeSelect(payload: PendingSubmitPayload) {
  if (!assigneeSelectNodes.value.length) {
    return false;
  }
  pendingSubmitPayload.value = payload;
  if (runtimeFixedSelectNodes.value.length) {
    selectedAssignees.value = [];
    assigneeSelectModalOpen.value = true;
    return true;
  }
  if (runtimeFreeSelectNode.value) {
    if (runtimeFreeSelectNode.value.warningMessage) {
      message.warning(runtimeFreeSelectNode.value.warningMessage);
    }
    draftSelectedAssigneeUsers.value = [];
    assigneePickerOpen.value = true;
    return true;
  }
  return false;
}

async function handleSaveStartDraft() {
  if (!currentProcess.value?.id || !validateTitle()) {
    return;
  }
  saving.value = true;
  try {
    await saveWorkflowStartDraft({
      businessKey: businessKey.value.trim() || undefined,
      formDataJson: await collectFormDataJson(false),
      instanceTitle: instanceTitle.value.trim(),
      processModelId: currentProcess.value.id,
    });
    message.success('已保存到我的待办');
    emit('success');
    drawerApi.close();
  } finally {
    saving.value = false;
  }
}

async function handleStartSubmit() {
  if (!currentProcess.value?.id || !validateTitle()) {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      businessKey: businessKey.value.trim() || undefined,
      formDataJson: await collectFormDataJson(true),
      instanceTitle: instanceTitle.value.trim(),
      processModelId: currentProcess.value.id,
    };
    if (openNextAssigneeSelect(payload)) {
      return;
    }
    await submitStartPayload(payload);
  } finally {
    submitting.value = false;
  }
}

async function submitStartPayload(payload: {
  businessKey?: string;
  formDataJson?: string;
  instanceTitle?: string;
  processModelId?: string;
  selectedAssignees?: SelectedAssigneeReq[];
}) {
  await startWorkflowProcess(payload);
  message.success('申请已提交');
  emit('success');
  drawerApi.close();
}

async function handleApprove() {
  if (!taskForm.value?.taskId) {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      comment: showApprovalComment.value
        ? resolveApprovalComment('同意')
        : undefined,
      formDataJson: await collectFormDataJson(true),
      taskId: taskForm.value.taskId,
    };
    if (openNextAssigneeSelect(payload)) {
      return;
    }
    await submitApprovePayload(payload);
  } finally {
    submitting.value = false;
  }
}

async function handleReject() {
  if (!taskForm.value?.taskId) {
    return;
  }
  submitting.value = true;
  try {
    const result = await rejectWorkflowTask({
      comment: resolveApprovalComment('不同意'),
      formDataJson: await collectFormDataJson(true),
      taskId: taskForm.value.taskId,
    });
    message.success('不通过已提交');
    if (result.processInstanceId) {
      detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
    }
    emit('success');
    drawerApi.close();
  } finally {
    submitting.value = false;
  }
}

async function handleAssigneePickerConfirm() {
  if (!pendingSubmitPayload.value) {
    return;
  }
  const node = runtimeFreeSelectNode.value;
  const user = draftSelectedAssigneeUsers.value[0];
  if (!node?.nodeId || !user?.id) {
    message.warning('请选择下一审批人');
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      ...pendingSubmitPayload.value,
      selectedAssignees: [{ nodeId: node.nodeId, userIds: [user.id] }],
    };
    if (isStartMode.value) {
      await submitStartPayload(payload);
    } else {
      await submitApprovePayload(payload);
    }
    assigneePickerOpen.value = false;
    pendingSubmitPayload.value = undefined;
    draftSelectedAssigneeUsers.value = [];
  } finally {
    submitting.value = false;
  }
}

async function handleAssigneeSelectConfirm() {
  if (!pendingSubmitPayload.value) {
    return;
  }
  if (!validateSelectedAssignees(runtimeFixedSelectNodes.value)) {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      ...pendingSubmitPayload.value,
      selectedAssignees: collectSelectedAssignees(),
    };
    if (isStartMode.value) {
      await submitStartPayload(payload);
    } else {
      await submitApprovePayload(payload);
    }
    assigneeSelectModalOpen.value = false;
    pendingSubmitPayload.value = undefined;
    selectedAssignees.value = [];
  } finally {
    submitting.value = false;
  }
}

async function submitApprovePayload(payload: TaskActionReq) {
  const result = isStartDraftTask.value
    ? await submitWorkflowStartDraft(payload)
    : await approveWorkflowTask(payload);
  message.success(isStartDraftTask.value ? '申请已提交' : '审批已通过');
  if (result.processInstanceId) {
    detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
  }
  emit('success');
  drawerApi.close();
}

async function handleSaveStartDraftTask() {
  if (!taskForm.value?.taskId) {
    return;
  }
  saving.value = true;
  try {
    const result = await saveWorkflowStartDraftTask({
      formDataJson: await collectFormDataJson(false),
      taskId: taskForm.value.taskId,
    });
    message.success('草稿已保存');
    if (result.processInstanceId) {
      detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
    }
    emit('success');
  } finally {
    saving.value = false;
  }
}

function openActionModal(action: WorkflowAction) {
  currentAction.value = action;
  actionForm.value = getEmptyActionForm(taskForm.value?.returnNodes?.[0]?.nodeId);
  actionModalOpen.value = true;
}

function openActionUserPicker(action: Extract<WorkflowAction, 'addSign' | 'transfer'>) {
  if (!validateApprovalComment()) {
    return;
  }
  currentAction.value = action;
  actionSelectedUsers.value = [];
  actionUserPickerOpen.value = true;
}

function handleRuntimeAction(action: WorkflowAction) {
  if (action === 'return') {
    openActionModal(action);
    return;
  }
  openActionUserPicker(action);
}

async function handleActionConfirm() {
  if (!taskForm.value?.taskId || !currentAction.value) {
    return;
  }
  if (!validateActionForm()) {
    return;
  }
  actionSubmitting.value = true;
  try {
    const req: TaskActionReq = {
      comment: resolveApprovalComment(),
      formDataJson: await collectFormDataJson(true),
      targetNodeId: actionForm.value.targetNodeId,
      taskId: taskForm.value.taskId,
    };
    const result = await submitTaskAction(currentAction.value, req);
    message.success(`${actionModalTitle.value}已提交`);
    actionModalOpen.value = false;
    if (result.processInstanceId) {
      detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
    }
    emit('success');
    drawerApi.close();
  } finally {
    actionSubmitting.value = false;
  }
}

async function handleActionUserPickerConfirm() {
  if (!taskForm.value?.taskId || !currentAction.value) {
    return;
  }
  if (!validateApprovalComment()) {
    return;
  }
  const targetUser = actionSelectedUsers.value[0];
  if (!targetUser?.id) {
    message.warning('请选择目标人员');
    return;
  }

  actionSubmitting.value = true;
  try {
    const req: TaskActionReq = {
      comment: resolveApprovalComment(),
      formDataJson: await collectFormDataJson(true),
      targetUserId: targetUser.id,
      taskId: taskForm.value.taskId,
    };
    const result = await submitTaskAction(currentAction.value, req);
    message.success(`${actionModalTitle.value}已提交`);
    actionUserPickerOpen.value = false;
    if (result.processInstanceId) {
      detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
    }
    emit('success');
    drawerApi.close();
  } finally {
    actionSubmitting.value = false;
  }
}

function validateActionForm() {
  if (!validateApprovalComment()) {
    return false;
  }
  if (currentAction.value === 'return' && !actionForm.value.targetNodeId) {
    message.warning('请选择退回节点');
    return false;
  }
  return true;
}

function validateApprovalComment() {
  if (!approvalComment.value.trim()) {
    message.warning('请输入审批意见');
    return false;
  }
  return true;
}

function submitTaskAction(action: WorkflowAction, req: TaskActionReq) {
  switch (action) {
    case 'addSign': {
      return addSignWorkflowTask(req);
    }
    case 'return': {
      return returnWorkflowTask(req);
    }
    case 'transfer': {
      return transferWorkflowTask(req);
    }
  }
}

function getEmptyActionForm(targetNodeId = ''): ActionForm {
  return {
    targetNodeId,
  };
}

function resolveApprovalComment(defaultComment?: string) {
  const comment = approvalComment.value.trim();
  if (comment) {
    return comment;
  }
  if (defaultComment) {
    approvalComment.value = defaultComment;
    return defaultComment;
  }
  return undefined;
}

function updateActionField(field: keyof ActionForm, value: string) {
  actionForm.value = {
    ...actionForm.value,
    [field]: value,
  };
}

function formatActor(record: OperationRecordInfo) {
  return record.operatorRealname ?? record.operatorUsername ?? '-';
}

function formatTaskActor(task: RuntimeTaskInfo) {
  return (
    task.assigneeRealname ||
    task.assigneeUsername ||
    task.candidateAssigneeNames ||
    '-'
  );
}

function formatRecordNode(record: OperationRecordInfo) {
  return record.nodeName ?? record.nodeId ?? '流程记录';
}

function formatRecordComment(record: OperationRecordInfo) {
  return record.comment?.trim() || '无';
}

function isProcessFinished(status?: string) {
  return ['approved', 'rejected', 'terminated'].includes(status || '');
}

function getProcessEndComment(status?: string) {
  const statusLabel = getStatusMeta(status).label;
  return statusLabel === '-' ? '流程已结束' : `流程${statusLabel}`;
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading">
      <div class="runtime-drawer-body">
        <div class="runtime-drawer-main">
          <section class="runtime-form-section">
            <div class="runtime-form-content">
              <RuntimeFormRenderer
                :ref="handleRuntimeFormRef"
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
      :submitting="submitting"
      @action-cancel="actionSelectedUsers = []"
      @action-confirm="handleActionUserPickerConfirm"
      @assignee-picker-cancel="pendingSubmitPayload = undefined"
      @assignee-picker-confirm="handleAssigneePickerConfirm"
      @assignee-select-cancel="pendingSubmitPayload = undefined"
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

.runtime-drawer-main {
  display: grid;
  flex: 1;
  gap: 20px;
  grid-template-columns: minmax(0, 3fr) minmax(360px, 2fr);
  min-height: 0;
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
