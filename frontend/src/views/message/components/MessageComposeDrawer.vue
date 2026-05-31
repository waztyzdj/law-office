<script setup lang="ts">
import type {
  MessageAttachmentInfo,
  SendMessageReq,
} from '#/api/message/message';
import type { UserInfo } from '#/api/system/user';

import { computed, nextTick, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons-vue';

import {
  Button,
  Divider,
  Form,
  FormItem,
  Input,
  Select,
  SelectOption,
  Spin,
  Upload,
  message,
} from 'ant-design-vue';

import { sendMessage } from '#/api/message/message';
import { uploadFile } from '#/api/system/file';
import { getCurrentTenantUsers } from '#/api/system/user';

import {
  messageTypeOptions,
  priorityOptions,
} from '../constants';

interface UserOption {
  label: string;
  value: string;
}

interface AttachmentDraft {
  fileId: string;
  fileName: string;
  fileSize?: number;
  fileType?: string;
}

interface MessageComposeFormState {
  content: string;
  messageType: number;
  priority: number;
  receiverIds: string[];
  title: string;
}

const emit = defineEmits<{
  success: [];
}>();

const MAX_ATTACHMENT_SIZE = 50 * 1024 * 1024;

const loading = ref(false);
const attachmentUploadingCount = ref(0);
const userOptions = ref<UserOption[]>([]);
const attachments = ref<AttachmentDraft[]>([]);

const form = reactive<MessageComposeFormState>({
  content: '',
  messageType: 1,
  priority: 1,
  receiverIds: [],
  title: '',
});

const hasAttachments = computed(() => attachments.value.length > 0);
const isAttachmentUploading = computed(() => attachmentUploadingCount.value > 0);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '发送',
  contentClass: 'message-compose-content px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: '发送消息',
});

function resetForm() {
  Object.assign(form, {
    content: '',
    messageType: 1,
    priority: 1,
    receiverIds: [],
    title: '',
  });
  attachments.value = [];
  attachmentUploadingCount.value = 0;
}

function toUserOptions(users: UserInfo[]): UserOption[] {
  return users
    .filter((user) => user.id)
    .map((user) => {
      const displayName = user.realname || user.username || user.id || '';
      return {
        label: user.username ? `${displayName} (${user.username})` : displayName,
        value: user.id || '',
      };
    });
}

async function loadUsers() {
  loading.value = true;
  try {
    const users = await getCurrentTenantUsers();
    userOptions.value = toUserOptions(users);
  } catch {
    message.error('加载租户用户失败');
  } finally {
    loading.value = false;
  }
}

function removeAttachment(index: number) {
  attachments.value.splice(index, 1);
}

function clearAttachments() {
  attachments.value = [];
}

function formatFileSize(size?: number) {
  if (!size || size <= 0) {
    return '-';
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
}

async function handleAttachmentBeforeUpload(file: File) {
  if (file.size > MAX_ATTACHMENT_SIZE) {
    message.error('单个附件不能超过50MB');
    return false;
  }

  attachmentUploadingCount.value += 1;
  try {
    const uploadResult = await uploadFile(file);
    if (!uploadResult.fileId) {
      throw new Error('文件上传失败');
    }

    attachments.value.push({
      fileId: uploadResult.fileId,
      fileName: uploadResult.fileName || file.name,
      fileSize: uploadResult.fileSize,
      fileType: uploadResult.fileType,
    });
    message.success(`附件「${uploadResult.fileName || file.name}」已上传`);
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '文件上传失败';
    message.error(errorMessage);
  } finally {
    attachmentUploadingCount.value = Math.max(attachmentUploadingCount.value - 1, 0);
  }
  return false;
}

function buildAttachments(): MessageAttachmentInfo[] {
  return attachments.value
    .map((item, index) => ({
      fileId: item.fileId?.trim() || undefined,
      fileName: item.fileName?.trim() || undefined,
      fileSize: item.fileSize,
      fileType: item.fileType?.trim() || undefined,
      sortOrder: index,
    }))
    .filter((item) => item.fileId);
}

function validateForm() {
  if (!form.title.trim()) {
    message.warning('请输入消息标题');
    return false;
  }
  if (form.receiverIds.length === 0) {
    message.warning('请选择接收人');
    return false;
  }
  if (isAttachmentUploading.value) {
    message.warning('附件正在上传，请稍后再发送');
    return false;
  }
  return true;
}

function buildPayload(): SendMessageReq {
  return {
    attachments: buildAttachments(),
    content: form.content?.trim() || undefined,
    contentType: 1,
    messageType: form.messageType,
    priority: form.priority,
    receiverIds: form.receiverIds,
    sendScene: 1,
    sendScope: 1,
    title: form.title.trim(),
  };
}

async function handleSubmit() {
  if (!validateForm()) {
    return;
  }

  try {
    drawerApi.lock();
    const result = await sendMessage(buildPayload());
    message.success(`消息已发送给 ${result.receiverCount || form.receiverIds.length} 人`);
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open() {
  resetForm();
  drawerApi.setState({ loading: false, title: '发送消息' }).open();
  await nextTick();
  void loadUsers();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading">
      <Form
        :label-col="{ style: { width: '92px' } }"
        :model="form"
        :wrapper-col="{ flex: '1' }"
      >
        <FormItem label="接收人" required>
          <Select
            v-model:value="form.receiverIds"
            mode="multiple"
            option-filter-prop="label"
            placeholder="请选择同租户用户"
            show-search
          >
            <SelectOption
              v-for="user in userOptions"
              :key="user.value"
              :label="user.label"
              :value="user.value"
            >
              {{ user.label }}
            </SelectOption>
          </Select>
        </FormItem>
        <FormItem label="消息标题" required>
          <Input v-model:value="form.title" :maxlength="200" placeholder="请输入消息标题" />
        </FormItem>
        <FormItem label="消息内容">
          <Input.TextArea
            v-model:value="form.content"
            :maxlength="4000"
            :rows="5"
            placeholder="请输入消息内容"
          />
        </FormItem>
        <div class="compose-grid">
          <FormItem label="消息类型">
            <Select v-model:value="form.messageType">
              <SelectOption
                v-for="option in messageTypeOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </SelectOption>
            </Select>
          </FormItem>
          <FormItem label="优先级">
            <Select v-model:value="form.priority">
              <SelectOption
                v-for="option in priorityOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </SelectOption>
            </Select>
          </FormItem>
        </div>
        <Divider orientation="left">附件</Divider>
        <div class="attachment-toolbar">
          <Upload
            :before-upload="handleAttachmentBeforeUpload"
            :disabled="isAttachmentUploading"
            :multiple="true"
            :show-upload-list="false"
          >
            <Button :loading="isAttachmentUploading" type="primary">
              <UploadOutlined />
              上传附件
            </Button>
          </Upload>
          <Button :disabled="!hasAttachments" @click="clearAttachments">清空附件</Button>
        </div>
        <div v-if="hasAttachments" class="attachment-list">
          <div
            v-for="(attachment, index) in attachments"
            :key="attachment.fileId || index"
            class="attachment-row"
          >
            <div class="attachment-main">
              <div class="attachment-name">{{ attachment.fileName }}</div>
              <div class="attachment-meta">
                <span>{{ attachment.fileType || '-' }}</span>
                <span>{{ formatFileSize(attachment.fileSize) }}</span>
              </div>
            </div>
            <Button danger type="text" @click="removeAttachment(index)">
              <DeleteOutlined />
              删除
            </Button>
          </div>
        </div>
      </Form>
    </Spin>
  </Drawer>
</template>

<style scoped>
:global(.message-compose-content) {
  max-height: calc(100vh - 65px);
  overflow: auto;
}

.compose-grid {
  display: grid;
  grid-template-columns: 1fr;
}

.attachment-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  background: hsl(var(--background));
}

.attachment-main {
  min-width: 0;
}

.attachment-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 4px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}

@media (max-width: 960px) {
  .compose-grid {
    grid-template-columns: 1fr;
  }

  .attachment-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .attachment-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
