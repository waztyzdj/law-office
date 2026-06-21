<script setup lang="ts">
import type {
  AssigneeSelectNodeInfo,
  RuntimeTaskInfo,
  SelectedAssigneeReq,
} from '#/api/workflow';
import type { UserInfo } from '#/api/system/user';
import type { UserPickerMode } from '#/components/user-picker';
import type { WorkflowAction } from './runtimeTypes';

import { computed } from 'vue';

import { Modal } from 'ant-design-vue';

import UserPickerPanel from '#/components/user-picker/UserPickerPanel.vue';

import AssigneeSelectPanel from './AssigneeSelectPanel.vue';
import { workflowActionTitleMap } from './runtimeTypes';

interface Props {
  actionOpen: boolean;
  actionSelectedUsers: UserInfo[];
  actionSubmitting: boolean;
  assigneePickerOpen: boolean;
  assigneeSelectModalOpen: boolean;
  currentAction?: WorkflowAction;
  currentTask?: RuntimeTaskInfo;
  draftSelectedAssigneeUsers: UserInfo[];
  fixedSelectNodes: AssigneeSelectNodeInfo[];
  freeSelectMode: UserPickerMode;
  selectedAssignees: SelectedAssigneeReq[];
  submitting: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  actionCancel: [];
  actionConfirm: [];
  assigneePickerCancel: [];
  assigneePickerConfirm: [];
  assigneeSelectCancel: [];
  assigneeSelectConfirm: [];
  'update:actionOpen': [value: boolean];
  'update:actionSelectedUsers': [value: UserInfo[]];
  'update:assigneePickerOpen': [value: boolean];
  'update:assigneeSelectModalOpen': [value: boolean];
  'update:draftSelectedAssigneeUsers': [value: UserInfo[]];
  'update:selectedAssignees': [value: SelectedAssigneeReq[]];
}>();

const actionModalTitle = computed(() => {
  return props.currentAction
    ? workflowActionTitleMap[props.currentAction]
    : '审批操作';
});

const excludedActionUserIds = computed(() =>
  props.currentTask?.assigneeUserId ? [props.currentTask.assigneeUserId] : [],
);
</script>

<template>
  <Modal
    :confirm-loading="actionSubmitting"
    :destroy-on-close="false"
    :open="actionOpen"
    :width="960"
    cancel-text="取消"
    ok-text="确定并发送"
    :title="actionModalTitle"
    wrap-class-name="workflow-user-picker-modal-wrap"
    @cancel="emit('actionCancel')"
    @ok="emit('actionConfirm')"
    @update:open="(value) => emit('update:actionOpen', value)"
  >
    <UserPickerPanel
      :exclude-user-ids="excludedActionUserIds"
      mode="single"
      :selected-users="actionSelectedUsers"
      @update:selected-users="(users) => emit('update:actionSelectedUsers', users)"
    />
  </Modal>

  <Modal
    :confirm-loading="submitting"
    :destroy-on-close="false"
    :open="assigneePickerOpen"
    :width="960"
    cancel-text="取消"
    ok-text="确定并发送"
    title="选择下一审批人"
    wrap-class-name="workflow-user-picker-modal-wrap"
    @cancel="emit('assigneePickerCancel')"
    @ok="emit('assigneePickerConfirm')"
    @update:open="(value) => emit('update:assigneePickerOpen', value)"
  >
    <UserPickerPanel
      :mode="freeSelectMode"
      org-only
      :selected-users="draftSelectedAssigneeUsers"
      @update:selected-users="(users) => emit('update:draftSelectedAssigneeUsers', users)"
    />
  </Modal>

  <Modal
    :confirm-loading="submitting"
    :open="assigneeSelectModalOpen"
    :width="620"
    cancel-text="取消"
    ok-text="确定并发送"
    title="选择下一审批人"
    wrap-class-name="workflow-assignee-select-modal-wrap"
    @cancel="emit('assigneeSelectCancel')"
    @ok="emit('assigneeSelectConfirm')"
    @update:open="(value) => emit('update:assigneeSelectModalOpen', value)"
  >
    <AssigneeSelectPanel
      :disabled="submitting"
      :nodes="fixedSelectNodes"
      :value="selectedAssignees"
      compact
      :show-title="false"
      @update:value="(value) => emit('update:selectedAssignees', value)"
    />
  </Modal>
</template>

<style scoped>
:global(.workflow-assignee-select-modal-wrap .ant-modal-content) {
  display: flex;
  flex-direction: column;
  height: 320px;
}

:global(.workflow-assignee-select-modal-wrap .ant-modal-body) {
  flex: 1;
  min-height: 0;
}
</style>
