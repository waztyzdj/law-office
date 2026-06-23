<script setup lang="ts">
import type {
  MessageActionInfo,
  MessageAttachmentInfo,
  MessageDetailInfo,
} from '#/api/message/message';
import type { LocationQueryRaw } from 'vue-router';

import { computed, nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenDrawer } from '@vben/common-ui';
import { DownloadOutlined, PaperClipOutlined } from '@ant-design/icons-vue';

import {
  Button,
  Descriptions,
  DescriptionsItem,
  Divider,
  Space,
  Spin,
  message,
} from 'ant-design-vue';

import {
  downloadMessageAttachment,
  getInboxMessageDetail,
  getSentMessageDetail,
} from '#/api/message/message';
import WorkflowRuntimeFormDrawer from '#/views/workflow/runtime/components/WorkflowRuntimeFormDrawer.vue';

import {
  getOptionLabel,
  messageTypeOptions,
  priorityOptions,
  readStatusOptions,
  sendStatusOptions,
} from '../constants';

type DetailMode = 'inbox' | 'sent';

interface DetailPayload {
  id: string;
  mode: DetailMode;
}

const router = useRouter();
const loading = ref(false);
const detail = ref<MessageDetailInfo>({});
const downloadingFileIds = ref<string[]>([]);
const mode = ref<DetailMode>('inbox');
const workflowDrawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();
const expiredTodoNotice = '该待办已被处理或已失效，当前为流程详情。';

const drawerTitle = computed(() => detail.value.title || '消息详情');
const receiverText = computed(() =>
  detail.value.receiverNames && detail.value.receiverNames.length > 0
    ? detail.value.receiverNames.join('、')
    : '-',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '关闭',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: closeDrawer,
  showCancelButton: false,
  title: drawerTitle.value,
});

function parseRouteQuery(routeQuery?: string): LocationQueryRaw | undefined {
  if (!routeQuery) {
    return undefined;
  }

  try {
    const query = JSON.parse(routeQuery) as unknown;
    if (!query || typeof query !== 'object' || Array.isArray(query)) {
      return undefined;
    }
    return Object.entries(query as Record<string, unknown>).reduce<LocationQueryRaw>(
      (result, [key, value]) => {
        if (value === undefined || value === null) {
          return result;
        }
        if (Array.isArray(value)) {
          result[key] = value
            .filter(
              (item) =>
                typeof item === 'string' ||
                typeof item === 'number' ||
                typeof item === 'boolean',
            )
            .map(String);
          return result;
        }
        if (
          typeof value === 'string' ||
          typeof value === 'number' ||
          typeof value === 'boolean'
        ) {
          result[key] = String(value);
        }
        return result;
      },
      {},
    );
  } catch {
    message.warning('路由参数格式不正确，已按普通页面打开');
    return undefined;
  }
}

function getRouteQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.find((item) => typeof item === 'string') ?? '';
  }
  return typeof value === 'string' ? value : '';
}

function isWorkflowTodoAction(action: MessageActionInfo, path: string) {
  if (
    action.bizType === 'workflow_urge' ||
    action.bizType === 'workflow_timeout'
  ) {
    return true;
  }
  return path === '/workflow/todo/detail' || path === '/workflow/todo';
}

async function openWorkflowTodoDetail(query?: LocationQueryRaw) {
  const taskId = getRouteQueryValue(query?.taskId);
  const instanceId = getRouteQueryValue(query?.instanceId);
  if (!taskId && !instanceId) {
    message.warning('缺少审批任务参数');
    return;
  }

  drawerApi.close();
  await nextTick();

  if (taskId) {
    try {
      await workflowDrawerRef.value?.open({ mode: 'todo', taskId });
      return;
    } catch {
      if (!instanceId) {
        message.warning('该待办已失效或无权办理');
        return;
      }
    }
  }

  await workflowDrawerRef.value?.open({
    instanceId,
    mode: 'detail',
    notice: expiredTodoNotice,
  });
}

function openExternal(url?: string, newWindow = true) {
  if (!url) {
    message.warning('链接为空');
    return;
  }
  window.open(url, newWindow ? '_blank' : '_self', 'noopener,noreferrer');
}

async function openInternal(action: MessageActionInfo) {
  if (!action.routePath) {
    message.warning('内部路径为空');
    return;
  }
  const query = parseRouteQuery(action.routeQuery);
  if (isWorkflowTodoAction(action, action.routePath)) {
    await openWorkflowTodoDetail(query);
    return;
  }
  const route = {
    path: action.routePath,
    query,
  };
  if (action.openType === 2) {
    const resolved = router.resolve(route);
    window.open(resolved.href, '_blank', 'noopener,noreferrer');
    return;
  }
  drawerApi.close();
  await router.push(route);
}

async function handleAction(action: MessageActionInfo) {
  if (action.actionType === 2) {
    openExternal(action.externalUrl, action.openType === 2);
    return;
  }
  if (action.actionType === 4 && !action.routePath && action.externalUrl) {
    openExternal(action.externalUrl, action.openType === 2);
    return;
  }
  await openInternal(action);
}

function buildDownloadName(fileName?: string) {
  return fileName?.trim() || 'download';
}

function formatAttachmentSize(size?: number) {
  if (!size || size <= 0) {
    return '-';
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} MB`;
  }
  return `${Math.round(size)} KB`;
}

function isAttachmentDownloading(fileId?: string) {
  return !!fileId && downloadingFileIds.value.includes(fileId);
}

function setAttachmentDownloading(fileId: string, downloading: boolean) {
  downloadingFileIds.value = downloading
    ? [...downloadingFileIds.value, fileId]
    : downloadingFileIds.value.filter((item) => item !== fileId);
}

async function downloadAttachment(item: MessageAttachmentInfo) {
  if (!item.fileId) {
    message.warning('附件文件ID为空');
    return;
  }

  setAttachmentDownloading(item.fileId, true);
  try {
    const blob = await downloadMessageAttachment(item.fileId);
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = buildDownloadName(item.fileName);
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
  } catch {
    message.error('附件下载失败');
  } finally {
    setAttachmentDownloading(item.fileId, false);
  }
}

async function loadDetail(payload: DetailPayload) {
  loading.value = true;
  try {
    detail.value =
      payload.mode === 'inbox'
        ? await getInboxMessageDetail(payload.id)
        : await getSentMessageDetail(payload.id);
    drawerApi.setState({ title: drawerTitle.value });
  } finally {
    loading.value = false;
  }
}

async function open(payload: DetailPayload) {
  mode.value = payload.mode;
  detail.value = {};
  drawerApi.setState({ loading: false, title: '消息详情' }).open();
  await nextTick();
  void loadDetail(payload);
}

function closeDrawer() {
  drawerApi.close();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading">
      <div class="message-detail">
        <h2>{{ detail.title || '-' }}</h2>
        <Descriptions :column="2" bordered size="small">
          <DescriptionsItem label="消息类型">
            {{ getOptionLabel(messageTypeOptions, detail.messageType) }}
          </DescriptionsItem>
          <DescriptionsItem label="优先级">
            {{ getOptionLabel(priorityOptions, detail.priority) }}
          </DescriptionsItem>
          <DescriptionsItem label="发送人">
            {{ detail.senderName || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="发送时间">
            {{ detail.sendTime || '-' }}
          </DescriptionsItem>
          <DescriptionsItem v-if="mode === 'inbox'" label="阅读状态">
            {{ getOptionLabel(readStatusOptions, detail.readStatus) }}
          </DescriptionsItem>
          <DescriptionsItem v-else label="发送状态">
            {{ getOptionLabel(sendStatusOptions, detail.sendStatus) }}
          </DescriptionsItem>
          <DescriptionsItem v-if="mode === 'sent'" :span="2" label="接收人">
            {{ receiverText }}
          </DescriptionsItem>
        </Descriptions>

        <Divider orientation="left">内容</Divider>
        <div class="message-content">{{ detail.content || '暂无内容' }}</div>

        <template v-if="detail.actions && detail.actions.length > 0">
          <Divider orientation="left">动作</Divider>
          <Space wrap>
            <Button
              v-for="action in detail.actions"
              :key="action.id"
              type="primary"
              @click="handleAction(action)"
            >
              {{ action.actionName || '打开' }}
            </Button>
          </Space>
        </template>

        <template v-if="detail.attachments && detail.attachments.length > 0">
          <Divider orientation="left">附件</Divider>
          <div class="attachment-list">
            <div
              v-for="item in detail.attachments"
              :key="item.id || item.fileId || item.fileName"
              class="attachment-item"
            >
              <div class="attachment-icon">
                <PaperClipOutlined />
              </div>
              <div class="attachment-main">
                <div class="attachment-name">{{ item.fileName || '-' }}</div>
                <div class="attachment-meta">
                  <span v-if="item.fileType">{{ item.fileType }}</span>
                  <span v-if="item.fileSize">{{ formatAttachmentSize(item.fileSize) }}</span>
                </div>
              </div>
              <Button
                class="attachment-download"
                type="primary"
                ghost
                :disabled="!item.fileId"
                :loading="isAttachmentDownloading(item.fileId)"
                @click="downloadAttachment(item)"
              >
                <DownloadOutlined />
                下载
              </Button>
            </div>
          </div>
        </template>

      </div>
    </Spin>
  </Drawer>
  <WorkflowRuntimeFormDrawer ref="workflowDrawerRef" />
</template>

<style scoped>
.message-detail {
  min-height: 100%;
}

.message-detail h2 {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
}

.message-content {
  min-height: 120px;
  padding: 12px 14px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  background: hsl(var(--muted) / 30%);
  white-space: pre-wrap;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  background: hsl(var(--muted) / 18%);
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.attachment-item:hover {
  border-color: hsl(var(--primary) / 45%);
  background: hsl(var(--primary) / 5%);
}

.attachment-icon {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 10%);
  font-size: 16px;
}

.attachment-main {
  min-width: 0;
}

.attachment-name {
  overflow: hidden;
  color: hsl(var(--foreground));
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 18px;
}

.attachment-download {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

@media (max-width: 640px) {
  .attachment-item {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .attachment-download {
    grid-column: 2;
    width: fit-content;
  }
}
</style>
