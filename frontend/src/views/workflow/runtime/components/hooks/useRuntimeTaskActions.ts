import type { Ref } from 'vue';

import type {
  AssigneeSelectNodeInfo,
  AvailableProcessInfo,
  InstanceDetailInfo,
  SelectedAssigneeReq,
  StartProcessReq,
  TaskActionReq,
  TaskActionResult,
  TaskFormInfo,
} from '#/api/workflow';
import type { UserInfo } from '#/api/system/user';
import type { WorkflowAction } from '../runtimeTypes';

import { ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  addSignWorkflowTask,
  approveWorkflowTask,
  getWorkflowInstanceDetail,
  rejectWorkflowTask,
  returnWorkflowTask,
  saveWorkflowStartDraft,
  saveWorkflowStartDraftTask,
  startWorkflowProcess,
  submitWorkflowStartDraft,
  transferWorkflowTask,
} from '#/api/workflow';

type PendingSubmitPayload = StartProcessReq | TaskActionReq;

interface ActionForm {
  targetNodeId: string;
}

interface UseRuntimeTaskActionsOptions {
  actionTitle: Ref<string>;
  approvalComment: Ref<string>;
  businessKey: Ref<string>;
  collectFormDataJson: (validate: boolean) => Promise<string>;
  currentProcess: Ref<AvailableProcessInfo | undefined>;
  detail: Ref<InstanceDetailInfo | undefined>;
  instanceTitle: Ref<string>;
  isStartDraftTask: Ref<boolean>;
  isStartMode: Ref<boolean>;
  onClose: () => void;
  onSuccess: () => void;
  openNextAssigneeSelect: (
    payload: PendingSubmitPayload,
    nodes: AssigneeSelectNodeInfo[],
  ) => boolean;
  previewNextAssigneeSelectNodes: (
    payload: PendingSubmitPayload,
  ) => Promise<AssigneeSelectNodeInfo[]>;
  taskForm: Ref<TaskFormInfo | undefined>;
  validateApprovalComment: () => boolean;
  validateTitle: () => boolean;
  resolveApprovalComment: (defaultComment?: string) => string | undefined;
}

export function useRuntimeTaskActions(options: UseRuntimeTaskActionsOptions) {
  const saving = ref(false);
  const submitting = ref(false);
  const actionModalOpen = ref(false);
  const actionSubmitting = ref(false);
  const actionUserPickerOpen = ref(false);
  const currentAction = ref<WorkflowAction>();
  const actionForm = ref<ActionForm>(getEmptyActionForm());
  const actionSelectedUsers = ref<UserInfo[]>([]);

  function resetRuntimeActions() {
    saving.value = false;
    submitting.value = false;
    actionModalOpen.value = false;
    actionSubmitting.value = false;
    actionUserPickerOpen.value = false;
    currentAction.value = undefined;
    actionForm.value = getEmptyActionForm();
    actionSelectedUsers.value = [];
  }

  async function handleSaveStartDraft() {
    if (!options.currentProcess.value?.id || !options.validateTitle()) {
      return;
    }
    saving.value = true;
    try {
      await saveWorkflowStartDraft({
        businessKey: options.businessKey.value.trim() || undefined,
        formDataJson: await options.collectFormDataJson(false),
        instanceTitle: options.instanceTitle.value.trim(),
        processModelId: options.currentProcess.value.id,
      });
      message.success('已保存到我的待办');
      options.onSuccess();
      options.onClose();
    } finally {
      saving.value = false;
    }
  }

  async function handleStartSubmit() {
    if (!options.currentProcess.value?.id || !options.validateTitle()) {
      return;
    }
    submitting.value = true;
    try {
      const payload = {
        businessKey: options.businessKey.value.trim() || undefined,
        formDataJson: await options.collectFormDataJson(true),
        instanceTitle: options.instanceTitle.value.trim(),
        processModelId: options.currentProcess.value.id,
      };
      const nextAssigneeNodes = await options.previewNextAssigneeSelectNodes(payload);
      if (options.openNextAssigneeSelect(payload, nextAssigneeNodes)) {
        return;
      }
      await submitStartPayload(payload);
    } finally {
      submitting.value = false;
    }
  }

  async function submitStartPayload(payload: StartProcessReq) {
    await startWorkflowProcess(payload);
    message.success('申请已提交');
    options.onSuccess();
    options.onClose();
  }

  async function handleApprove() {
    if (!options.taskForm.value?.taskId) {
      return;
    }
    submitting.value = true;
    try {
      const payload = {
        comment: !options.isStartDraftTask.value
          ? options.resolveApprovalComment('同意')
          : undefined,
        formDataJson: await options.collectFormDataJson(true),
        taskId: options.taskForm.value.taskId,
      };
      const nextAssigneeNodes = await options.previewNextAssigneeSelectNodes(payload);
      if (options.openNextAssigneeSelect(payload, nextAssigneeNodes)) {
        return;
      }
      await submitApprovePayload(payload);
    } finally {
      submitting.value = false;
    }
  }

  async function handleReject() {
    if (!options.taskForm.value?.taskId) {
      return;
    }
    submitting.value = true;
    try {
      const result = await rejectWorkflowTask({
        comment: options.resolveApprovalComment('不同意'),
        formDataJson: await options.collectFormDataJson(true),
        taskId: options.taskForm.value.taskId,
      });
      message.success('不通过已提交');
      await refreshDetail(result);
      options.onSuccess();
      options.onClose();
    } finally {
      submitting.value = false;
    }
  }

  async function handleAssigneePickerConfirm(
    pendingPayload: PendingSubmitPayload | undefined,
    selectedAssignees: SelectedAssigneeReq[] | undefined,
    onSubmitted: () => void,
  ) {
    if (!pendingPayload || !selectedAssignees) {
      return;
    }
    submitting.value = true;
    try {
      const payload = {
        ...pendingPayload,
        selectedAssignees,
      };
      if (options.isStartMode.value) {
        await submitStartPayload(payload);
      } else {
        await submitApprovePayload(payload);
      }
      onSubmitted();
    } finally {
      submitting.value = false;
    }
  }

  async function submitApprovePayload(payload: TaskActionReq) {
    const result = options.isStartDraftTask.value
      ? await submitWorkflowStartDraft(payload)
      : await approveWorkflowTask(payload);
    message.success(options.isStartDraftTask.value ? '申请已提交' : '审批已通过');
    await refreshDetail(result);
    options.onSuccess();
    options.onClose();
  }

  async function handleSaveStartDraftTask() {
    if (!options.taskForm.value?.taskId) {
      return;
    }
    saving.value = true;
    try {
      const result = await saveWorkflowStartDraftTask({
        formDataJson: await options.collectFormDataJson(false),
        taskId: options.taskForm.value.taskId,
      });
      message.success('草稿已保存');
      await refreshDetail(result);
      options.onSuccess();
    } finally {
      saving.value = false;
    }
  }

  function openActionModal(action: WorkflowAction) {
    if (!options.validateApprovalComment()) {
      return;
    }
    currentAction.value = action;
    actionForm.value = getEmptyActionForm(options.taskForm.value?.returnNodes?.[0]?.nodeId);
    actionModalOpen.value = true;
  }

  function openActionUserPicker(action: Extract<WorkflowAction, 'addSign' | 'transfer'>) {
    if (!options.validateApprovalComment()) {
      return;
    }
    currentAction.value = action;
    actionSelectedUsers.value = [];
    actionUserPickerOpen.value = true;
  }

  function closeActionUserPicker() {
    actionUserPickerOpen.value = false;
    actionSelectedUsers.value = [];
  }

  function handleRuntimeAction(action: WorkflowAction) {
    if (action === 'return') {
      openActionModal(action);
      return;
    }
    openActionUserPicker(action);
  }

  async function handleActionConfirm() {
    if (!options.taskForm.value?.taskId || !currentAction.value) {
      return;
    }
    if (!validateActionForm()) {
      return;
    }
    actionSubmitting.value = true;
    try {
      const req: TaskActionReq = {
        comment: options.resolveApprovalComment(),
        formDataJson: await options.collectFormDataJson(true),
        targetNodeId: actionForm.value.targetNodeId,
        taskId: options.taskForm.value.taskId,
      };
      const result = await submitTaskAction(currentAction.value, req);
      message.success(`${options.actionTitle.value}已提交`);
      actionModalOpen.value = false;
      await refreshDetail(result);
      options.onSuccess();
      options.onClose();
    } finally {
      actionSubmitting.value = false;
    }
  }

  async function handleActionUserPickerConfirm() {
    if (!options.taskForm.value?.taskId || !currentAction.value) {
      return;
    }
    if (!options.validateApprovalComment()) {
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
        comment: options.resolveApprovalComment(),
        formDataJson: await options.collectFormDataJson(true),
        targetUserId: targetUser.id,
        taskId: options.taskForm.value.taskId,
      };
      const result = await submitTaskAction(currentAction.value, req);
      message.success(`${options.actionTitle.value}已提交`);
      closeActionUserPicker();
      await refreshDetail(result);
      options.onSuccess();
      options.onClose();
    } finally {
      actionSubmitting.value = false;
    }
  }

  function validateActionForm() {
    if (!options.validateApprovalComment()) {
      return false;
    }
    if (currentAction.value === 'return' && !actionForm.value.targetNodeId) {
      message.warning('请选择退回节点');
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

  async function refreshDetail(result: TaskActionResult) {
    if (result.processInstanceId) {
      options.detail.value = await getWorkflowInstanceDetail(result.processInstanceId);
    }
  }

  function getEmptyActionForm(targetNodeId = ''): ActionForm {
    return {
      targetNodeId,
    };
  }

  function updateActionField(field: keyof ActionForm, value: string) {
    actionForm.value = {
      ...actionForm.value,
      [field]: value,
    };
  }

  return {
    actionForm,
    actionModalOpen,
    actionSelectedUsers,
    actionSubmitting,
    actionUserPickerOpen,
    currentAction,
    handleActionConfirm,
    handleActionUserPickerConfirm,
    handleApprove,
    handleAssigneePickerConfirm,
    handleReject,
    handleRuntimeAction,
    handleSaveStartDraft,
    handleSaveStartDraftTask,
    handleStartSubmit,
    closeActionUserPicker,
    resetRuntimeActions,
    saving,
    submitting,
    updateActionField,
  };
}
