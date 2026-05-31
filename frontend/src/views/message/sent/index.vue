<script setup lang="ts">
import type { MessageSentInfo } from '#/api/message/message';

import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import { batchDeleteSentMessages, recallMessage } from '#/api/message/message';

import MessageComposeDrawer from '../components/MessageComposeDrawer.vue';
import MessageDetailDrawer from '../components/MessageDetailDrawer.vue';
import MessageSentTable from './components/MessageSentTable.vue';
import { useMessageSentTable } from './hooks/useMessageSentTable';

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
} = useMessageSentTable();

const composeDrawerRef = ref<InstanceType<typeof MessageComposeDrawer>>();
const detailDrawerRef = ref<InstanceType<typeof MessageDetailDrawer>>();
const MESSAGE_NOTIFICATION_REFRESH_EVENT =
  'lawoffice:message-notifications-refresh';
const deletingSelected = ref(false);

function handleAdd() {
  composeDrawerRef.value?.open();
}

function handleView(record: MessageSentInfo) {
  if (!record.id) {
    return;
  }
  detailDrawerRef.value?.open({ id: record.id, mode: 'sent' });
}

function openDetail(id: string) {
  if (!id) {
    return;
  }
  detailDrawerRef.value?.open({ id, mode: 'sent' });
}

function handleRecall(record: MessageSentInfo) {
  if (!record.id) {
    return;
  }
  if ((record.readCount || 0) > 0) {
    message.warning('消息已有接收人阅读，不能撤回');
    return;
  }

  Modal.confirm({
    content: `确认撤回消息“${record.title || ''}”吗？`,
    title: '确认撤回',
    async onOk() {
      await recallMessage(record.id || '');
      message.success('消息已撤回');
      await loadData();
    },
  });
}

async function handleSendSuccess() {
  await loadData();
  window.dispatchEvent(new CustomEvent(MESSAGE_NOTIFICATION_REFRESH_EVENT));
}

function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0 || deletingSelected.value) {
    return;
  }

  const count = selectedRowKeys.value.length;
  Modal.confirm({
    cancelText: '取消',
    content: `确认删除选中的 ${count} 条发件消息吗？`,
    okText: '确定',
    title: '确认批量删除',
    async onOk() {
      deletingSelected.value = true;
      try {
        await batchDeleteSentMessages(selectedRowKeys.value.map(String));
        message.success('批量删除成功');
        selectedRowKeys.value = [];
        await loadData();
      } finally {
        deletingSelected.value = false;
      }
    },
  });
}

onMounted(loadData);

defineExpose({
  openDetail,
});
</script>

<template>
  <div :class="['message-page', { 'message-page-embedded': embedded }]">
    <MessageSentTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :deleting-selected="deletingSelected"
      :loading="loading"
      :pagination="pagination"
      :selected-row-keys="selectedRowKeys"
      :show-card="!embedded"
      :show-toolbar="true"
      @add="handleAdd"
      @batch-delete="handleBatchDelete"
      @change="handleTableChange"
      @delete="handleDelete"
      @recall="handleRecall"
      @select-change="onSelectChange"
      @view="handleView"
    />
    <MessageComposeDrawer ref="composeDrawerRef" @success="handleSendSuccess" />
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
