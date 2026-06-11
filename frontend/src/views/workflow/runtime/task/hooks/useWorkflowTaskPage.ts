import type { ComputedRef } from 'vue';

import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { message } from 'ant-design-vue';

import type {
  StartFormInfo,
  TaskActionReq,
  TaskFormInfo,
} from '#/api/workflow';

import {
  addSignWorkflowTask,
  approveWorkflowTask,
  getStartForm,
  getWorkflowTaskForm,
  rejectWorkflowTask,
  returnWorkflowTask,
  startWorkflowProcess,
  submitWorkflowStartDraft,
  transferWorkflowTask,
} from '#/api/workflow';

import type RuntimeFormRenderer from '../../components/RuntimeFormRenderer.vue';

export type WorkflowTaskAction = 'addSign' | 'reject' | 'return' | 'transfer';

export interface WorkflowTaskActionForm {
  comment: string;
  targetNodeId: string;
  targetUserId: string;
}

export interface WorkflowTaskFormExpose {
  getValidatedFormData: InstanceType<
    typeof RuntimeFormRenderer
  >['getValidatedFormData'];
}

const actionTitleMap: Record<WorkflowTaskAction, string> = {
  addSign: '加签',
  reject: '不通过',
  return: '退回',
  transfer: '转办',
};

export function useWorkflowTaskPage() {
  const route = useRoute();
  const router = useRouter();

  const loading = ref(false);
  const submitting = ref(false);
  const actionModalOpen = ref(false);
  const actionSubmitting = ref(false);
  const taskForm = ref<TaskFormInfo>();
  const startForm = ref<StartFormInfo>();
  const runtimeFormRef = ref<WorkflowTaskFormExpose>();
  const instanceTitle = ref('');
  const businessKey = ref('');
  const currentAction = ref<WorkflowTaskAction>();
  const actionForm = ref<WorkflowTaskActionForm>(getEmptyActionForm());

  const taskId = computed(() => String(route.query.taskId ?? ''));
  const processModelId = computed(() => String(route.query.processModelId ?? ''));
  const isStartMode = computed(() => route.query.mode === 'start');
  const isStartDraftMode = computed(
    () => taskForm.value?.taskType === 'start_draft',
  );
  const hasRequiredParams = computed(
    () => Boolean(taskId.value) || Boolean(processModelId.value),
  );
  const formSchemaJson = computed(
    () => taskForm.value?.schemaJson ?? startForm.value?.schemaJson ?? '[]',
  );
  const formOptionJson = computed(
    () => taskForm.value?.optionJson ?? startForm.value?.optionJson ?? '{}',
  );
  const formDataJson = computed(() => taskForm.value?.formDataJson ?? '{}');
  const pageTitle = computed(() =>
    isStartMode.value
      ? `发起申请：${startForm.value?.processName ?? ''}`
      : isStartDraftMode.value
        ? '提交申请'
      : '任务办理',
  );
  const actionModalTitle: ComputedRef<string> = computed(() =>
    currentAction.value ? actionTitleMap[currentAction.value] : '审批操作',
  );

  async function loadData() {
    loading.value = true;
    try {
      taskForm.value = undefined;
      startForm.value = undefined;
      if (isStartMode.value && processModelId.value) {
        startForm.value = await getStartForm(processModelId.value);
        instanceTitle.value = startForm.value.processName ?? '';
        return;
      }
      if (taskId.value) {
        taskForm.value = await getWorkflowTaskForm(taskId.value);
        instanceTitle.value = taskForm.value.instanceTitle ?? '';
      }
    } finally {
      loading.value = false;
    }
  }

  function handleBack() {
    void router.push({ name: isStartMode.value ? 'WorkflowStart' : 'WorkflowTodo' });
  }

  async function handleStart() {
    if (!processModelId.value) {
      return;
    }
    if (!instanceTitle.value.trim()) {
      message.warning('请输入申请标题');
      return;
    }

    submitting.value = true;
    try {
      const result = await startWorkflowProcess({
        businessKey: businessKey.value.trim() || undefined,
        formDataJson: await collectFormDataJson(),
        instanceTitle: instanceTitle.value,
        processModelId: processModelId.value,
      });
      message.success('申请已提交');
      await router.replace({
        name: 'WorkflowInstanceDetail',
        query: { id: result.processInstanceId },
      });
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
        formDataJson: await collectFormDataJson(),
        taskId: taskForm.value.taskId,
      };
      const result = isStartDraftMode.value
        ? await submitWorkflowStartDraft(payload)
        : await approveWorkflowTask(payload);
      message.success(isStartDraftMode.value ? '申请已提交' : '审批已通过');
      await router.replace({
        name: 'WorkflowInstanceDetail',
        query: { id: result.processInstanceId },
      });
    } finally {
      submitting.value = false;
    }
  }

  function openActionModal(action: WorkflowTaskAction) {
    currentAction.value = action;
    actionForm.value = getEmptyActionForm(taskForm.value?.returnNodes?.[0]?.nodeId);
    actionModalOpen.value = true;
  }

  function setRuntimeFormRef(instance: WorkflowTaskFormExpose | null) {
    runtimeFormRef.value = instance ?? undefined;
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
        formDataJson: await collectFormDataJson(),
        targetNodeId: actionForm.value.targetNodeId,
        targetUserId: actionForm.value.targetUserId,
        taskId: taskForm.value.taskId,
      };
      const result = await submitTaskAction(currentAction.value, req);

      message.success(`${actionModalTitle.value}已提交`);
      actionModalOpen.value = false;
      await router.replace({
        name: 'WorkflowInstanceDetail',
        query: { id: result.processInstanceId },
      });
    } finally {
      actionSubmitting.value = false;
    }
  }

  async function collectFormDataJson() {
    const formData = await runtimeFormRef.value?.getValidatedFormData();
    return JSON.stringify(formData ?? {});
  }

  function validateActionForm() {
    if (currentAction.value === 'return' && !actionForm.value.targetNodeId) {
      message.warning('请选择退回节点');
      return false;
    }
    if (
      (currentAction.value === 'transfer' ||
        currentAction.value === 'addSign') &&
      !actionForm.value.targetUserId.trim()
    ) {
      message.warning('请选择目标人员');
      return false;
    }
    return true;
  }

  function submitTaskAction(action: WorkflowTaskAction, req: TaskActionReq) {
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

  return {
    actionForm,
    actionModalOpen,
    actionModalTitle,
    actionSubmitting,
    businessKey,
    currentAction,
    formDataJson,
    formOptionJson,
    formSchemaJson,
    handleActionConfirm,
    handleApprove,
    handleBack,
    handleStart,
    hasRequiredParams,
    instanceTitle,
    isStartDraftMode,
    isStartMode,
    loadData,
    loading,
    openActionModal,
    pageTitle,
    setRuntimeFormRef,
    startForm,
    submitting,
    taskForm,
  };
}

function getEmptyActionForm(targetNodeId = ''): WorkflowTaskActionForm {
  return {
    comment: '',
    targetNodeId,
    targetUserId: '',
  };
}
