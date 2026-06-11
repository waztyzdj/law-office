<script setup lang="ts">
import { computed } from 'vue';

import { Form, Modal, Select, Textarea } from 'ant-design-vue';

import type { TaskReturnNodeInfo } from '#/api/workflow';

import { UserPicker } from '#/components/user-picker';

import type {
  WorkflowTaskAction,
  WorkflowTaskActionForm,
} from '../hooks/useWorkflowTaskPage';

const props = defineProps<{
  action?: WorkflowTaskAction;
  confirmLoading: boolean;
  form: WorkflowTaskActionForm;
  open: boolean;
  returnNodes?: TaskReturnNodeInfo[];
  title: string;
}>();

const emit = defineEmits<{
  confirm: [];
  'update:form': [form: WorkflowTaskActionForm];
  'update:open': [open: boolean];
}>();

const actionForm = computed({
  get: () => props.form,
  set: (value) => emit('update:form', value),
});

const openModel = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
});

const returnNodeOptions = computed(() =>
  (props.returnNodes ?? []).map((item) => ({
    label: item.nodeName,
    value: item.nodeId,
  })),
);

function updateField(field: keyof WorkflowTaskActionForm, value: string) {
  actionForm.value = {
    ...actionForm.value,
    [field]: value,
  };
}
</script>

<template>
  <Modal
    v-model:open="openModel"
    :confirm-loading="confirmLoading"
    :title="title"
    @ok="emit('confirm')"
  >
    <Form layout="vertical">
      <Form.Item
        v-if="action === 'return'"
        label="退回节点"
        required
      >
        <Select
          :options="returnNodeOptions"
          :value="actionForm.targetNodeId"
          placeholder="请选择退回节点"
          @update:value="(value) => updateField('targetNodeId', String(value ?? ''))"
        />
      </Form.Item>
      <Form.Item
        v-if="action === 'transfer' || action === 'addSign'"
        label="目标人员"
        required
      >
        <UserPicker
          :value="actionForm.targetUserId"
          placeholder="请选择目标人员"
          @update:value="(value) => updateField('targetUserId', Array.isArray(value) ? (value[0] ?? '') : (value ?? ''))"
        />
      </Form.Item>
      <Form.Item label="审批意见">
        <Textarea
          :maxlength="500"
          :rows="4"
          :value="actionForm.comment"
          placeholder="请输入审批意见"
          @update:value="(value) => updateField('comment', String(value ?? ''))"
        />
      </Form.Item>
    </Form>
  </Modal>
</template>
