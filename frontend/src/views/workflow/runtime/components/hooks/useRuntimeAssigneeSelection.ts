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
  const freeSelectMode = computed(() =>
    runtimeFreeSelectNode.value?.selectType === 'multiple' ? 'multiple' : 'single',
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
      if (!selected?.userIds?.length) {
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
      const userIds = item.userIds?.filter(Boolean) ?? [];
      if (nodeId && userIds.length) {
        result.push({ nodeId, userIds });
      }
    }
    return result;
  }

  function buildFreeSelectedAssignees() {
    const node = runtimeFreeSelectNode.value;
    const userIds = draftSelectedAssigneeUsers.value
      .map((user) => user.id)
      .filter((id): id is string => !!id);
    if (!node?.nodeId || !userIds.length) {
      message.warning('请选择下一审批人');
      return undefined;
    }
    return [{ nodeId: node.nodeId, userIds }];
  }

  function buildFixedSelectedAssignees() {
    if (!validateSelectedAssignees(runtimeFixedSelectNodes.value)) {
      return undefined;
    }
    return collectSelectedAssignees();
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
    closeAssigneePicker,
    closeAssigneeSelectModal,
    draftSelectedAssigneeUsers,
    freeSelectMode,
    openNextAssigneeSelect,
    pendingSubmitPayload,
    resetAssigneeSelection,
    runtimeFixedSelectNodes,
    selectedAssignees,
  };
}
