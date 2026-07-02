<script setup lang="ts">
import type { RuntimeActionPermissions, WorkflowAction } from './runtimeTypes';

import {
  BellOutlined,
  CheckOutlined,
  CloseOutlined,
  DownloadOutlined,
  FolderOpenOutlined,
  PrinterOutlined,
  RollbackOutlined,
  SaveOutlined,
  SendOutlined,
  SwapOutlined,
  UndoOutlined,
  UserAddOutlined,
} from '@ant-design/icons-vue';
import { Button } from 'ant-design-vue';

interface Props {
  actionPermissions?: RuntimeActionPermissions;
  adminArchiveSubmitting: boolean;
  canCc: boolean;
  canAdminArchive: boolean;
  canDownload: boolean;
  canPrint: boolean;
  canUrge: boolean;
  canWithdraw: boolean;
  ccSubmitting: boolean;
  downloading: boolean;
  isStartDraftTask: boolean;
  isStartMode: boolean;
  isTodoMode: boolean;
  saving: boolean;
  submitting: boolean;
  urgeSubmitting: boolean;
  withdrawSubmitting: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  action: [action: WorkflowAction];
  adminArchive: [];
  approve: [];
  cancel: [];
  cc: [];
  download: [];
  print: [];
  reject: [];
  saveStartDraft: [];
  saveStartDraftTask: [];
  submitStart: [];
  urge: [];
  withdraw: [];
}>();
</script>

<template>
  <div class="runtime-actions">
    <template v-if="isStartMode">
      <Button
        :disabled="submitting"
        :loading="saving"
        type="primary"
        @click="emit('saveStartDraft')"
      >
        <SaveOutlined />
        保存
      </Button>
      <Button
        :loading="submitting"
        type="primary"
        @click="emit('submitStart')"
      >
        <SendOutlined />
        提交
      </Button>
    </template>
    <template v-else-if="isTodoMode">
      <Button
        v-if="isStartDraftTask"
        :disabled="submitting"
        :loading="saving"
        type="primary"
        @click="emit('saveStartDraftTask')"
      >
        <SaveOutlined />
        保存
      </Button>
      <Button
        v-if="actionPermissions?.allowApprove"
        :disabled="saving"
        :loading="submitting"
        type="primary"
        @click="emit('approve')"
      >
        <SendOutlined v-if="isStartDraftTask" />
        <CheckOutlined v-else />
        {{ isStartDraftTask ? '提交' : '通过' }}
      </Button>
      <Button
        v-if="!isStartDraftTask && actionPermissions?.allowReject"
        :disabled="saving || submitting"
        :loading="submitting"
        danger
        @click="emit('reject')"
      >
        <CloseOutlined />
        不通过
      </Button>
      <Button
        v-if="!isStartDraftTask && actionPermissions?.allowReturn"
        @click="emit('action', 'return')"
      >
        <RollbackOutlined />
        退回
      </Button>
      <Button
        v-if="!isStartDraftTask && actionPermissions?.allowTransfer"
        @click="emit('action', 'transfer')"
      >
        <SwapOutlined />
        转办
      </Button>
      <Button
        v-if="!isStartDraftTask && actionPermissions?.allowAddSign"
        @click="emit('action', 'addSign')"
      >
        <UserAddOutlined />
        加签
      </Button>
    </template>
    <Button
      v-if="canCc"
      :disabled="saving || submitting || urgeSubmitting"
      :loading="ccSubmitting"
      :type="isTodoMode ? 'default' : 'primary'"
      @click="emit('cc')"
    >
      <SendOutlined />
      抄送
    </Button>
    <Button
      v-if="canUrge"
      :disabled="saving || submitting || ccSubmitting || urgeSubmitting || withdrawSubmitting"
      :loading="urgeSubmitting"
      @click="emit('urge')"
    >
      <BellOutlined />
      催办
    </Button>
    <Button
      v-if="canWithdraw"
      :disabled="saving || submitting || ccSubmitting || urgeSubmitting || withdrawSubmitting"
      :loading="withdrawSubmitting"
      danger
      @click="emit('withdraw')"
    >
      <UndoOutlined />
      撤回
    </Button>
    <Button
      v-if="canAdminArchive"
      :disabled="saving || submitting || ccSubmitting || urgeSubmitting || withdrawSubmitting || downloading || adminArchiveSubmitting"
      :loading="adminArchiveSubmitting"
      type="primary"
      @click="emit('adminArchive')"
    >
      <FolderOpenOutlined />
      归档
    </Button>
    <Button
      v-if="canPrint"
      :disabled="saving || submitting || ccSubmitting || urgeSubmitting || withdrawSubmitting || downloading"
      @click="emit('print')"
    >
      <PrinterOutlined />
      打印
    </Button>
    <Button
      v-if="canDownload"
      :disabled="saving || submitting || ccSubmitting || urgeSubmitting || withdrawSubmitting"
      :loading="downloading"
      @click="emit('download')"
    >
      <DownloadOutlined />
      下载
    </Button>
    <Button
      :disabled="saving || submitting || ccSubmitting || urgeSubmitting || downloading || adminArchiveSubmitting"
      @click="emit('cancel')"
    >
      <CloseOutlined />
      取消
    </Button>
  </div>
</template>

<style scoped>
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
</style>
