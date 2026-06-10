<script setup lang="ts">
import { Button, Card, Space } from 'ant-design-vue';

import type { TaskActionPermissionInfo } from '#/api/workflow';

import type { WorkflowTaskAction } from '../hooks/useWorkflowTaskPage';

defineProps<{
  actionPermissions?: TaskActionPermissionInfo;
  isStartDraftMode: boolean;
  isStartMode: boolean;
  submitting: boolean;
}>();

const emit = defineEmits<{
  approve: [];
  back: [];
  openAction: [action: WorkflowTaskAction];
  start: [];
}>();
</script>

<template>
  <Card>
    <Space>
      <Button @click="emit('back')">取消</Button>
      <Button
        v-if="isStartMode || isStartDraftMode"
        :loading="submitting"
        type="primary"
        @click="isStartDraftMode ? emit('approve') : emit('start')"
      >
        提交
      </Button>
      <template v-else>
        <Button
          v-if="actionPermissions?.allowApprove"
          :loading="submitting"
          type="primary"
          @click="emit('approve')"
        >
          通过
        </Button>
        <Button
          v-if="actionPermissions?.allowReject"
          danger
          @click="emit('openAction', 'reject')"
        >
          拒绝
        </Button>
        <Button
          v-if="actionPermissions?.allowReturn"
          @click="emit('openAction', 'return')"
        >
          退回
        </Button>
        <Button
          v-if="actionPermissions?.allowTransfer"
          @click="emit('openAction', 'transfer')"
        >
          转办
        </Button>
        <Button
          v-if="actionPermissions?.allowAddSign"
          @click="emit('openAction', 'addSign')"
        >
          加签
        </Button>
      </template>
    </Space>
  </Card>
</template>
