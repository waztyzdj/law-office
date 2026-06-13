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

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  Button,
  Empty,
  message,
  Modal,
  Select,
  Spin,
  Tabs,
  Tag,
  Textarea,
  Timeline,
} from 'ant-design-vue';

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
import UserPickerPanel from '#/components/user-picker/UserPickerPanel.vue';

import { getStatusMeta, getWorkflowActionMeta } from '../../components/status';
import AssigneeSelectPanel from './AssigneeSelectPanel.vue';
import RuntimeFormRenderer from './RuntimeFormRenderer.vue';

type DrawerMode = 'detail' | 'done' | 'start' | 'started' | 'todo';
type WorkflowAction = 'addSign' | 'return' | 'transfer';
type PendingSubmitPayload = StartProcessReq | TaskActionReq;

interface ProcessProgressNode {
  action?: string;
  actor?: string;
  comment?: string;
  id: string;
  name: string;
  resultStatus?: string;
  status: 'current' | 'done' | 'end';
  time?: string;
}

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

function validateSelectedAssignees() {
  for (const node of assigneeSelectNodes.value) {
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
    if (assigneeSelectNodes.value.length) {
      pendingSubmitPayload.value = payload;
      if (runtimeFreeSelectNode.value) {
        if (runtimeFreeSelectNode.value.warningMessage) {
          message.warning(runtimeFreeSelectNode.value.warningMessage);
        }
        draftSelectedAssigneeUsers.value = [];
        assigneePickerOpen.value = true;
      } else {
        selectedAssignees.value = [];
        assigneeSelectModalOpen.value = true;
      }
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
    if (assigneeSelectNodes.value.length) {
      pendingSubmitPayload.value = payload;
      if (runtimeFreeSelectNode.value) {
        if (runtimeFreeSelectNode.value.warningMessage) {
          message.warning(runtimeFreeSelectNode.value.warningMessage);
        }
        draftSelectedAssigneeUsers.value = [];
        assigneePickerOpen.value = true;
      } else {
        selectedAssignees.value = [];
        assigneeSelectModalOpen.value = true;
      }
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
  if (!validateSelectedAssignees()) {
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

function getTimelineColor(action?: string) {
  if (action === 'reject' || action === 'return') {
    return 'red';
  }
  return 'green';
}

function getProgressNodeColor(node: ProcessProgressNode) {
  if (node.status === 'current') {
    return 'blue';
  }
  if (node.status === 'end') {
    return 'green';
  }
  return getTimelineColor(node.action);
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

            <div
              v-if="showApprovalComment"
              class="approval-comment-panel"
            >
              <label class="approval-comment-label">审批意见</label>
              <div class="approval-comment-control">
                <Textarea
                  v-model:value="approvalComment"
                  :maxlength="500"
                  :rows="4"
                  placeholder="请输入审批意见"
                  show-count
                />
              </div>
            </div>
          </section>

          <aside class="runtime-side-section">
            <Tabs
              v-model:active-key="activeTab"
              class="runtime-side-tabs"
            >
              <Tabs.TabPane
                key="records"
                tab="审批意见"
              >
                <div
                  v-if="processProgressNodes.length"
                  class="process-progress-panel"
                >
                  <Timeline class="process-progress-timeline">
                    <Timeline.Item
                      v-for="node in processProgressNodes"
                      :key="node.id"
                      :color="getProgressNodeColor(node)"
                    >
                      <div class="progress-node">
                        <div class="progress-node-head">
                          <span class="progress-node-name">{{ node.name }}</span>
                          <Tag
                            v-if="node.status === 'current'"
                            color="processing"
                          >
                            当前
                          </Tag>
                          <Tag
                            v-else-if="node.action"
                            :color="getWorkflowActionMeta(node.action).color"
                          >
                            {{ getWorkflowActionMeta(node.action).label }}
                          </Tag>
                        </div>
                        <div
                          v-if="node.status !== 'end'"
                          class="progress-node-meta"
                        >
                          <span>处理人：{{ node.actor || '-' }}</span>
                          <span>时间：{{ node.time || '-' }}</span>
                        </div>
                        <div
                          v-if="node.comment && node.status !== 'end'"
                          class="progress-node-comment"
                        >
                          {{ node.comment }}
                        </div>
                      </div>
                    </Timeline.Item>
                  </Timeline>
                </div>
                <div
                  v-else
                  class="runtime-empty-panel"
                >
                  <Empty description="暂无审批意见" />
                </div>
              </Tabs.TabPane>

              <Tabs.TabPane
                key="circulate"
                tab="传阅"
              >
                <div class="runtime-empty-panel circulate-empty-panel">
                  <Empty description="暂无传阅记录">
                    <template #description>
                      <div class="empty-description">
                        <div>暂无传阅记录</div>
                        <span>传阅/抄送属于二期能力，后续会在这里展示传阅人、传阅时间和阅读状态。</span>
                      </div>
                    </template>
                  </Empty>
                </div>
              </Tabs.TabPane>
            </Tabs>
          </aside>
        </div>

        <div
          v-if="showRuntimeActions"
          class="runtime-actions"
        >
          <template v-if="isStartMode">
            <Button
              :disabled="submitting"
              :loading="saving"
              type="primary"
              @click="handleSaveStartDraft"
            >
              保存
            </Button>
            <Button
              :loading="submitting"
              type="primary"
              @click="handleStartSubmit"
            >
              提交
            </Button>
          </template>
          <template v-else-if="isTodoMode">
            <Button
              v-if="isStartDraftTask"
              :disabled="submitting"
              :loading="saving"
              type="primary"
              @click="handleSaveStartDraftTask"
            >
              保存
            </Button>
            <Button
              v-if="actionPermissions?.allowApprove"
              :disabled="saving"
              :loading="submitting"
              type="primary"
              @click="handleApprove"
            >
              {{ isStartDraftTask ? '提交' : '通过' }}
            </Button>
            <Button
              v-if="!isStartDraftTask && actionPermissions?.allowReject"
              :disabled="saving || submitting"
              :loading="submitting"
              danger
              @click="handleReject"
            >
              不通过
            </Button>
            <Button
              v-if="!isStartDraftTask && actionPermissions?.allowReturn"
              @click="openActionModal('return')"
            >
              退回
            </Button>
            <Button
              v-if="!isStartDraftTask && actionPermissions?.allowTransfer"
              @click="openActionUserPicker('transfer')"
            >
              转办
            </Button>
            <Button
              v-if="!isStartDraftTask && actionPermissions?.allowAddSign"
              @click="openActionUserPicker('addSign')"
            >
              加签
            </Button>
          </template>
          <Button
            :disabled="saving || submitting"
            @click="drawerApi.close()"
          >
            取消
          </Button>
        </div>
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

    <Modal
      v-model:open="actionUserPickerOpen"
      :confirm-loading="actionSubmitting"
      :destroy-on-close="false"
      :width="960"
      cancel-text="取消"
      ok-text="确定并发送"
      :title="actionModalTitle"
      wrap-class-name="workflow-user-picker-modal-wrap"
      @cancel="actionSelectedUsers = []"
      @ok="handleActionUserPickerConfirm"
    >
      <UserPickerPanel
        v-model:selected-users="actionSelectedUsers"
        :exclude-user-ids="currentTask?.assigneeUserId ? [currentTask.assigneeUserId] : []"
        mode="single"
      />
    </Modal>

    <Modal
      v-model:open="assigneePickerOpen"
      :confirm-loading="submitting"
      :destroy-on-close="false"
      :width="960"
      cancel-text="取消"
      ok-text="确定并发送"
      title="选择下一审批人"
      wrap-class-name="workflow-user-picker-modal-wrap"
      @cancel="pendingSubmitPayload = undefined"
      @ok="handleAssigneePickerConfirm"
    >
      <UserPickerPanel
        v-model:selected-users="draftSelectedAssigneeUsers"
        mode="single"
        org-only
      />
    </Modal>

    <Modal
      v-model:open="assigneeSelectModalOpen"
      :confirm-loading="submitting"
      :width="620"
      cancel-text="取消"
      ok-text="确定并发送"
      title="选择下一审批人"
      wrap-class-name="workflow-assignee-select-modal-wrap"
      @cancel="pendingSubmitPayload = undefined"
      @ok="handleAssigneeSelectConfirm"
    >
      <AssigneeSelectPanel
        v-model:value="selectedAssignees"
        compact
        :disabled="submitting"
        :nodes="runtimeFixedSelectNodes"
        :show-title="false"
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

.approval-comment-panel {
  align-items: flex-start;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex: 0 0 auto;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
}

.approval-comment-label {
  color: #1f2937;
  flex: 0 0 92px;
  font-size: 14px;
  font-weight: 500;
  line-height: 32px;
  text-align: right;
}

.approval-comment-control {
  flex: 1;
  min-width: 0;
}

.runtime-side-section {
  border-left: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding-left: 20px;
}

.runtime-side-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.runtime-side-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.runtime-side-tabs :deep(.ant-tabs-content),
.runtime-side-tabs :deep(.ant-tabs-tabpane) {
  min-height: 100%;
}

.progress-node-meta {
  color: #6b7280;
  font-size: 13px;
}

.runtime-empty-panel {
  align-items: center;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  justify-content: center;
  min-height: 180px;
}

.empty-description {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}

.empty-description > div {
  color: #4b5563;
  font-size: 14px;
}

.circulate-empty-panel {
  min-height: 180px;
}

.process-progress-panel {
  padding: 8px 4px 0;
}

.process-progress-timeline {
  padding: 4px 4px 0;
}

.progress-node {
  min-width: 0;
}

.progress-node-head {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

.progress-node-name {
  color: #111827;
  font-weight: 500;
}

.progress-node-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.progress-node-comment {
  background: #fafafa;
  border-radius: 6px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 8px;
  padding: 8px 10px;
  white-space: pre-wrap;
  word-break: break-word;
}

.runtime-actions {
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 16px;
  padding-top: 14px;
}

.action-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

:global(.workflow-assignee-select-modal-wrap .ant-modal-content) {
  display: flex;
  flex-direction: column;
  height: 320px;
}

:global(.workflow-assignee-select-modal-wrap .ant-modal-body) {
  flex: 1;
  min-height: 0;
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
