<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';

import type {
  AvailableProcessInfo,
  InstanceDetailInfo,
  OperationRecordInfo,
  RuntimeTaskInfo,
  StartFormInfo,
  TaskActionReq,
  TaskFormInfo,
} from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  Button,
  Descriptions,
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
import { UserPicker } from '#/components/user-picker';

import { getStatusMeta, getWorkflowActionMeta } from '../../components/status';
import RuntimeFormRenderer from './RuntimeFormRenderer.vue';

type DrawerMode = 'detail' | 'done' | 'start' | 'started' | 'todo';
type WorkflowAction = 'addSign' | 'reject' | 'return' | 'transfer';

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
  comment: string;
  targetNodeId: string;
  targetUserId: string;
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
const activeTab = ref('form');
const instanceTitle = ref('');
const businessKey = ref('');
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const actionModalOpen = ref(false);
const actionSubmitting = ref(false);
const currentAction = ref<WorkflowAction>();
const actionForm = ref<ActionForm>(getEmptyActionForm());

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
const summaryTitle = computed(
  () =>
    detail.value?.processInstance?.instanceTitle ||
    taskForm.value?.instanceTitle ||
    instanceTitle.value ||
    currentProcess.value?.processName ||
    '-',
);
const formTitle = computed(
  () =>
    startForm.value?.formName ||
    taskForm.value?.formName ||
    detail.value?.formInstance?.formName ||
    currentProcess.value?.formName ||
    '',
);
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
const fieldPermissions = computed(() => taskForm.value?.fieldPermissions ?? []);
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
    reject: '不通过',
    return: '退回',
    transfer: '转办',
  };
  return currentAction.value ? titleMap[currentAction.value] : '审批操作';
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[60vw]! sm:max-w-none!',
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
  actionModalOpen.value = false;
  currentAction.value = undefined;
  actionForm.value = getEmptyActionForm();
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
    await startWorkflowProcess({
      businessKey: businessKey.value.trim() || undefined,
      formDataJson: await collectFormDataJson(true),
      instanceTitle: instanceTitle.value.trim(),
      processModelId: currentProcess.value.id,
    });
    message.success('申请已提交');
    emit('success');
    drawerApi.close();
  } finally {
    submitting.value = false;
  }
}

async function handleApprove() {
  if (!taskForm.value?.taskId) {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      formDataJson: await collectFormDataJson(true),
      taskId: taskForm.value.taskId,
    };
    const result = isStartDraftTask.value
      ? await submitWorkflowStartDraft(payload)
      : await approveWorkflowTask(payload);
    message.success(isStartDraftTask.value ? '申请已提交' : '审批已通过');
    if (result.processInstanceId) {
      detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
    }
    emit('success');
    drawerApi.close();
  } finally {
    submitting.value = false;
  }
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
      comment: actionForm.value.comment,
      formDataJson: await collectFormDataJson(true),
      targetNodeId: actionForm.value.targetNodeId,
      targetUserId: actionForm.value.targetUserId,
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

function validateActionForm() {
  if (currentAction.value === 'return' && !actionForm.value.targetNodeId) {
    message.warning('请选择退回节点');
    return false;
  }
  if (
    (currentAction.value === 'transfer' || currentAction.value === 'addSign') &&
    !actionForm.value.targetUserId.trim()
  ) {
    message.warning('请选择目标人员');
    return false;
  }
  return true;
}

function submitTaskAction(action: WorkflowAction, req: TaskActionReq) {
  switch (action) {
    case 'addSign': {
      return addSignWorkflowTask(req);
    }
    case 'reject': {
      return rejectWorkflowTask(req);
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
    comment: '',
    targetNodeId,
    targetUserId: '',
  };
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
        <section class="runtime-form-section">
          <RuntimeFormRenderer
            :ref="handleRuntimeFormRef"
            :field-permissions="fieldPermissions"
            :form-data-json="formDataJson"
            :loading="loading"
            :option-json="formOptionJson"
            :readonly="readonly"
            :schema-json="formSchemaJson"
          />
        </section>

        <Tabs v-model:active-key="activeTab">
          <Tabs.TabPane
            key="records"
            tab="审批意见"
          >
            <div
              v-if="processProgressNodes.length"
              class="process-progress-panel"
            >
              <div class="process-progress-head">
                <div>
                  <div class="process-progress-title">审批意见</div>
                  <div class="process-progress-subtitle">
                    按处理时间展示审批记录和当前待办
                  </div>
                </div>
              </div>

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

          <Tabs.TabPane
            key="diagram"
            tab="流程图"
          >
            <div class="process-info-panel">
              <div class="process-info-head">
                <div>
                  <div class="runtime-title">{{ summaryTitle }}</div>
                  <div class="runtime-subtitle">
                    {{ formTitle || '-' }}
                    <span v-if="taskForm?.formVersion || startForm?.formVersion || detail?.formInstance?.formVersion">
                      · v{{ taskForm?.formVersion ?? startForm?.formVersion ?? detail?.formInstance?.formVersion }}
                    </span>
                  </div>
                </div>
                <Tag :color="getStatusMeta(processInstance?.status || taskForm?.taskType).color">
                  {{ getStatusMeta(processInstance?.status || taskForm?.taskType).label }}
                </Tag>
              </div>

              <Descriptions
                class="process-description"
                :column="3"
                bordered
                size="small"
              >
                <Descriptions.Item label="审批编号">
                  {{ processInstance?.instanceNo ?? taskForm?.instanceNo ?? '-' }}
                </Descriptions.Item>
                <Descriptions.Item label="发起人">
                  {{ processInstance?.starterRealname ?? processInstance?.starterUsername ?? currentTask?.starterRealname ?? '-' }}
                </Descriptions.Item>
                <Descriptions.Item label="发起时间">
                  {{ processInstance?.startTime ?? '-' }}
                </Descriptions.Item>
                <Descriptions.Item label="当前节点">
                  {{ processInstance?.currentTaskNames ?? taskForm?.taskName ?? '-' }}
                </Descriptions.Item>
                <Descriptions.Item label="当前处理人">
                  {{ processInstance?.currentAssigneeNames ?? currentTask?.assigneeRealname ?? '-' }}
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  {{ getStatusMeta(processInstance?.status || taskForm?.taskType).label }}
                </Descriptions.Item>
              </Descriptions>

              <div class="runtime-empty-panel diagram-empty-panel">
                <Empty description="BPMN 流程图展示待接入" />
              </div>
            </div>
          </Tabs.TabPane>
        </Tabs>

        <div class="runtime-actions">
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
              danger
              @click="openActionModal('reject')"
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
              @click="openActionModal('transfer')"
            >
              转办
            </Button>
            <Button
              v-if="!isStartDraftTask && actionPermissions?.allowAddSign"
              @click="openActionModal('addSign')"
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
        <UserPicker
          v-if="currentAction === 'transfer' || currentAction === 'addSign'"
          :exclude-user-ids="currentTask?.assigneeUserId ? [currentTask.assigneeUserId] : []"
          :value="actionForm.targetUserId"
          placeholder="请选择目标人员"
          @update:value="(value) => updateActionField('targetUserId', Array.isArray(value) ? (value[0] ?? '') : (value ?? ''))"
        />
        <Textarea
          :maxlength="500"
          :rows="4"
          :value="actionForm.comment"
          placeholder="请输入审批意见"
          @update:value="(value) => updateActionField('comment', String(value ?? ''))"
        />
      </div>
    </Modal>
  </Drawer>
</template>

<style scoped>
:global(.workflow-runtime-form-drawer) {
  height: calc(100vh - 110px);
  overflow: auto;
  padding: 24px 32px;
}

.runtime-drawer-body {
  margin: 0 auto;
  max-width: 1080px;
}

.process-info-head {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 18px;
}

.runtime-title {
  color: #111827;
  font-size: 20px;
  font-weight: 600;
}

.runtime-subtitle {
  color: #6b7280;
  font-size: 13px;
  margin-top: 4px;
}

.process-info-panel {
  padding-top: 4px;
}

.process-description {
  margin-bottom: 18px;
}

.runtime-form-section {
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
  padding-bottom: 18px;
}

.process-progress-subtitle,
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

.diagram-empty-panel {
  min-height: 220px;
}

.process-progress-panel {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 14px 16px 6px;
}

.process-progress-head {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 16px;
}

.process-progress-title {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
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
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
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

  .process-info-head {
    flex-direction: column;
  }

  .process-progress-head {
    flex-direction: column;
  }
}
</style>
