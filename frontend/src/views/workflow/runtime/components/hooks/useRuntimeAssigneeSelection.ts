import type { Ref } from 'vue';

import type {
  AssigneeSelectNodeInfo,
  SelectedAssigneeReq,
  StartProcessReq,
  TaskActionReq,
} from '#/api/workflow';
import type { UserInfo } from '#/api/system/user';
import type { DrawerMode } from '../runtimeTypes';

import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';

type PendingSubmitPayload = StartProcessReq | TaskActionReq;

interface UseRuntimeAssigneeSelectionOptions {
  mode: Ref<DrawerMode>;
  startAssigneeSelectNodes: Ref<AssigneeSelectNodeInfo[]>;
  taskAssigneeSelectNodes: Ref<AssigneeSelectNodeInfo[]>;
}

export function useRuntimeAssigneeSelection(
  options: UseRuntimeAssigneeSelectionOptions,
) {
  const assigneePickerOpen = ref(false);
  const assigneeSelectModalOpen = ref(false);
  const pendingSubmitPayload = ref<PendingSubmitPayload>();
  const selectedAssignees = ref<SelectedAssigneeReq[]>([]);
  const draftSelectedAssigneeUsers = ref<UserInfo[]>([]);

  const assigneeSelectNodes = computed<AssigneeSelectNodeInfo[]>(() => {
    if (options.mode.value === 'start') {
      return options.startAssigneeSelectNodes.value;
    }
    if (options.mode.value === 'todo') {
      return options.taskAssigneeSelectNodes.value;
    }
    return [];
  });
  const runtimeFreeSelectNode = computed(() =>
    assigneeSelectNodes.value.find((node) => node.assigneeType === 'starter_select'),
  );
  const runtimeFixedSelectNodes = computed(() =>
    assigneeSelectNodes.value.filter((node) => node.assigneeType !== 'starter_select'),
  );

  function resetAssigneeSelection() {
    assigneePickerOpen.value = false;
    assigneeSelectModalOpen.value = false;
    pendingSubmitPayload.value = undefined;
    selectedAssignees.value = [];
    draftSelectedAssigneeUsers.value = [];
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

  function buildFreeSelectedAssignees() {
    const node = runtimeFreeSelectNode.value;
    const user = draftSelectedAssigneeUsers.value[0];
    if (!node?.nodeId || !user?.id) {
      message.warning('请选择下一审批人');
      return undefined;
    }
    return [{ nodeId: node.nodeId, userIds: [user.id] }];
  }

  function buildFixedSelectedAssignees() {
    if (!validateSelectedAssignees(runtimeFixedSelectNodes.value)) {
      return undefined;
    }
    return collectSelectedAssignees();
  }

  function clearPendingAssigneePayload() {
    pendingSubmitPayload.value = undefined;
  }

  function closeAssigneePicker() {
    assigneePickerOpen.value = false;
    pendingSubmitPayload.value = undefined;
    draftSelectedAssigneeUsers.value = [];
  }

  function closeAssigneeSelectModal() {
    assigneeSelectModalOpen.value = false;
    pendingSubmitPayload.value = undefined;
    selectedAssignees.value = [];
  }

  return {
    assigneePickerOpen,
    assigneeSelectModalOpen,
    buildFixedSelectedAssignees,
    buildFreeSelectedAssignees,
    clearPendingAssigneePayload,
    closeAssigneePicker,
    closeAssigneeSelectModal,
    draftSelectedAssigneeUsers,
    openNextAssigneeSelect,
    pendingSubmitPayload,
    resetAssigneeSelection,
    runtimeFixedSelectNodes,
    selectedAssignees,
  };
}
