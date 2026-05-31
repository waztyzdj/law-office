<script setup lang="ts">
import type { MessageInboxInfo } from '#/api/message/message';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import {
  batchDeleteInboxMessages,
  markAllTenantMessagesRead,
  markMessageBatchRead,
  markMessageRead,
  toggleMessageStar,
} from '#/api/message/message';

import MessageDetailDrawer from '../components/MessageDetailDrawer.vue';
import MessageInboxTable from './components/MessageInboxTable.vue';
import { useMessageInboxTable } from './hooks/useMessageInboxTable';

interface Props {
  embedded?: boolean;
}

const { embedded = false } = defineProps<Props>();

const {
  activeFilters,
  dataSource,
  handleDelete,
  handleTableChange,
  loadData,
  loading,
  onSelectChange,
  pagination,
  selectedRowKeys,
} = useMessageInboxTable();

const detailDrawerRef = ref<InstanceType<typeof MessageDetailDrawer>>();
const MESSAGE_NOTIFICATION_UPDATED_EVENT =
  'lawoffice:message-notifications-updated';
const MESSAGE_NOTIFICATION_REFRESH_EVENT =
  'lawoffice:message-notifications-refresh';
const readingSelected = ref(false);
const readingAll = ref(false);
const deletingSelected = ref(false);

function handleView(record: MessageInboxInfo) {
  if (!record.id) {
    return;
  }
  detailDrawerRef.value?.open({ id: record.id, mode: 'inbox' });
  if (record.readStatus !== 1) {
    void handleRead(record);
  }
}

async function handleRead(record: MessageInboxInfo) {
  if (!record.id) {
    return;
  }
  await markMessageRead(record.id);
  await loadData();
  dispatchNotificationRefresh();
}

async function handleStar(record: MessageInboxInfo) {
  if (!record.id) {
    return;
  }
  await toggleMessageStar(record.id);
  message.success(record.starFlag === 1 ? '已取消收藏' : '已收藏');
  await loadData();
}

function dispatchNotificationRefresh() {
  window.dispatchEvent(new CustomEvent(MESSAGE_NOTIFICATION_REFRESH_EVENT));
}

async function handleMarkSelectedRead() {
  if (selectedRowKeys.value.length === 0 || readingSelected.value) {
    return;
  }

  readingSelected.value = true;
  try {
    await markMessageBatchRead(selectedRowKeys.value.map(String));
    message.success('选中消息已标记为已读');
    selectedRowKeys.value = [];
    await loadData();
    dispatchNotificationRefresh();
  } finally {
    readingSelected.value = false;
  }
}

async function handleMarkAllRead() {
  if (readingAll.value) {
    return;
  }

  readingAll.value = true;
  try {
    await markAllTenantMessagesRead();
    message.success('当前租户消息已全部标记为已读');
    selectedRowKeys.value = [];
    await loadData();
    dispatchNotificationRefresh();
  } finally {
    readingAll.value = false;
  }
}

function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0 || deletingSelected.value) {
    return;
  }

  const count = selectedRowKeys.value.length;
  Modal.confirm({
    cancelText: '取消',
    content: `确认删除选中的 ${count} 条收件消息吗？`,
    okText: '确定',
    title: '确认批量删除',
    async onOk() {
      deletingSelected.value = true;
      try {
        await batchDeleteInboxMessages(selectedRowKeys.value.map(String));
        message.success('批量删除成功');
        selectedRowKeys.value = [];
        await loadData();
        dispatchNotificationRefresh();
      } finally {
        deletingSelected.value = false;
      }
    },
  });
}

function openDetail(id: string) {
  if (!id) {
    return;
  }
  detailDrawerRef.value?.open({ id, mode: 'inbox' });
}

function handleMessageNotificationUpdated() {
  void loadData();
}

onMounted(() => {
  void loadData();
  window.addEventListener(
    MESSAGE_NOTIFICATION_UPDATED_EVENT,
    handleMessageNotificationUpdated,
  );
});

onBeforeUnmount(() => {
  window.removeEventListener(
    MESSAGE_NOTIFICATION_UPDATED_EVENT,
    handleMessageNotificationUpdated,
  );
});

defineExpose({
  openDetail,
});
</script>

<template>
  <div :class="['message-page', { 'message-page-embedded': embedded }]">
    <MessageInboxTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :deleting-selected="deletingSelected"
      :loading="loading"
      :pagination="pagination"
      :reading-all="readingAll"
      :reading-selected="readingSelected"
      :selected-row-keys="selectedRowKeys"
      :show-card="!embedded"
      :show-toolbar="true"
      @batch-delete="handleBatchDelete"
      @change="handleTableChange"
      @delete="handleDelete"
      @mark-all-read="handleMarkAllRead"
      @mark-selected-read="handleMarkSelectedRead"
      @select-change="onSelectChange"
      @star="handleStar"
      @view="handleView"
    />
    <MessageDetailDrawer ref="detailDrawerRef" />
  </div>
</template>

<style scoped>
.message-page {
  padding: 16px;
}

.message-page-embedded {
  padding: 0;
}
</style>
