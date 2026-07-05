<script setup lang="ts">
import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
  WorkbenchQuickEntryInfo,
} from '#/api/home/workbench';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Spin, message } from 'ant-design-vue';

import { markMessageRead } from '#/api/message/message';
import {
  deleteCurrentWorkbenchQuickEntry,
  saveCurrentWorkbenchQuickEntry,
} from '#/api/home/workbench';
import { markWorkflowCcRead } from '#/api/workflow';
import MessageDetailDrawer from '#/views/message/components/MessageDetailDrawer.vue';
import WorkflowRuntimeFormDrawer from '#/views/workflow/runtime/components/WorkflowRuntimeFormDrawer.vue';
import WorkbenchGrid from './components/WorkbenchGrid.vue';
import WorkbenchPersonalizeModal from './components/WorkbenchPersonalizeModal.vue';
import WorkbenchQuickEntrySettingsModal from './components/WorkbenchQuickEntrySettingsModal.vue';
import { useWorkbench } from './hooks/useWorkbench';
import { getWorkbenchItemConfig } from './utils/workbenchCardFormatters';

const personalizeOpen = ref(false);
const quickEntrySettingsOpen = ref(false);
const quickEntryEditRecord = ref<WorkbenchQuickEntryInfo>();
const editMode = ref(false);
const showBackTop = ref(false);
const draftCards = ref<WorkbenchLayoutCard[]>([]);
const activeWorkflowCard = ref<WorkbenchLayoutCard>();
const messageDetailDrawerRef = ref<InstanceType<typeof MessageDetailDrawer>>();
const workflowDrawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();
const MESSAGE_NOTIFICATION_REFRESH_EVENT =
  'lawoffice:message-notifications-refresh';

const {
  allConfigurableCards,
  cardStates,
  hasAnyCard,
  layoutLoading,
  loadCardData,
  loadLayout,
  refreshAllCards,
  resetLayout,
  saveLayout,
  savingLayout,
  visibleCards,
} = useWorkbench();

const displayedCards = computed(() =>
  editMode.value ? draftCards.value.filter((card) => card.visible) : visibleCards.value,
);

const configurableCards = computed(() => {
  if (!editMode.value) {
    return allConfigurableCards.value;
  }
  const draftCardMap = new Map(draftCards.value.map((card) => [card.cardCode, card]));
  return allConfigurableCards.value.map(
    (card) => draftCardMap.get(card.cardCode) ?? card,
  );
});

async function handleRefreshCard(card: WorkbenchLayoutCard) {
  await loadCardData(card);
}

async function handleOpenWorkflowItem(payload: {
  card: WorkbenchLayoutCard;
  item: WorkbenchCardItem;
}) {
  const { card, item } = payload;
  activeWorkflowCard.value = card;
  if (card.cardCode === 'cc' || item.type === 'unread-cc' || item.type === 'read-cc') {
    await openCcDetail(card, item);
    return;
  }
  if (item.type === 'done' || item.targetPath?.split('?')[0] === '/workflow/done') {
    await openDoneDetail(item);
    return;
  }
  await openTodoTask(item);
}

async function handleOpenMessageItem(payload: {
  card: WorkbenchLayoutCard;
  item: WorkbenchCardItem;
}) {
  const { card, item } = payload;
  const messageId = getStringValue(item.id);
  if (!messageId) {
    message.warning('该消息缺少详情标识');
    return;
  }
  await messageDetailDrawerRef.value?.open({ id: messageId, mode: 'inbox' });
  if (item.type === 'unread-message' || item.status === 'unread') {
    await markMessageRead(messageId);
    window.dispatchEvent(new CustomEvent(MESSAGE_NOTIFICATION_REFRESH_EVENT));
    await loadCardData(card);
  }
}

async function openTodoTask(item: WorkbenchCardItem) {
  const taskId = getStringValue(item.bizId) || getStringValue(item.id);
  const instanceId = getStringValue(item.instanceId);
  if (!taskId && !instanceId) {
    message.warning('该待办缺少办理信息');
    return;
  }
  if (taskId) {
    try {
      await workflowDrawerRef.value?.open({ mode: 'todo', taskId });
      return;
    } catch {
      if (!instanceId) {
        return;
      }
    }
  }
  await workflowDrawerRef.value?.open({
    instanceId,
    mode: 'detail',
    notice: '该待办已被处理，当前为流程详情。',
  });
}

async function openDoneDetail(item: WorkbenchCardItem) {
  const instanceId = getStringValue(item.instanceId) || getStringValue(item.bizId);
  if (!instanceId) {
    message.warning('该已办缺少流程实例信息');
    return;
  }
  await workflowDrawerRef.value?.open({
    instanceId,
    mode: 'done',
  });
}

async function openCcDetail(card: WorkbenchLayoutCard, item: WorkbenchCardItem) {
  const ccRecordId = getStringValue(item.id);
  const instanceId = getStringValue(item.instanceId) || getStringValue(item.bizId);
  if (!instanceId) {
    message.warning('该抄送缺少流程实例信息');
    return;
  }
  if ((item.type === 'unread-cc' || item.status === 'unread') && ccRecordId) {
    await markWorkflowCcRead(ccRecordId);
    await loadCardData(card);
  }
  await workflowDrawerRef.value?.open({
    instanceId,
    mode: 'detail',
  });
}

async function handleWorkflowDrawerSuccess() {
  if (activeWorkflowCard.value) {
    await loadCardData(activeWorkflowCard.value);
  }
}

function getStringValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}

async function handleSaveLayout(cards: WorkbenchLayoutCard[]) {
  if (editMode.value) {
    draftCards.value = cards.map((card) => ({ ...card }));
    personalizeOpen.value = false;
    return;
  }
  await saveLayout(cards);
  personalizeOpen.value = false;
}

function handleGridLayoutChange(cards: WorkbenchLayoutCard[]) {
  if (!editMode.value) {
    return;
  }
  const changedCardMap = new Map(cards.map((card) => [card.cardCode, card]));
  draftCards.value = draftCards.value.map(
    (card) => changedCardMap.get(card.cardCode) ?? card,
  );
}

async function handleResetLayout() {
  await resetLayout();
  editMode.value = false;
  draftCards.value = [];
  personalizeOpen.value = false;
}

function handleEnterEditMode() {
  draftCards.value = allConfigurableCards.value.map((card) => ({ ...card }));
  editMode.value = true;
}

async function handleConfirmEditMode() {
  await saveLayout(draftCards.value);
  editMode.value = false;
  draftCards.value = [];
}

function handleHideCard(card: WorkbenchLayoutCard) {
  draftCards.value = draftCards.value.map((item) =>
    item.cardCode === card.cardCode ? { ...item, visible: false } : item,
  );
}

async function handleQuickEntrySettingsSuccess() {
  await refreshQuickEntryCard();
}

async function refreshQuickEntryCard() {
  const quickEntryCard = visibleCards.value.find(
    (card) => card.cardCode === 'quick-entry',
  );
  if (quickEntryCard) {
    await loadCardData(quickEntryCard);
  }
}

function openQuickEntryCreate() {
  quickEntryEditRecord.value = undefined;
  quickEntrySettingsOpen.value = true;
}

function openQuickEntryEdit(item: WorkbenchCardItem) {
  quickEntryEditRecord.value = toQuickEntryInfo(item);
  quickEntrySettingsOpen.value = true;
}

async function handleQuickEntrySortSave(items: WorkbenchCardItem[]) {
  const currentQuickEntryItems = cardStates['quick-entry']?.data?.items ?? [];
  const existingUserItems = currentQuickEntryItems.filter(
    (item: WorkbenchCardItem) => item.ownerType === 'user' && getStringValue(item.id),
  );
  const userItems = items.filter(
    (item) => item.ownerType === 'user' && getStringValue(item.id),
  );
  const nextUserIds = new Set(userItems.map((item) => getStringValue(item.id)));
  const deletedIds = existingUserItems
    .map((item: WorkbenchCardItem) => getStringValue(item.id))
    .filter((id: string) => id && !nextUserIds.has(id));

  if (userItems.length === 0 && deletedIds.length === 0) {
    return;
  }

  await Promise.all([
    ...deletedIds.map((id: string) => deleteCurrentWorkbenchQuickEntry(id)),
    ...userItems.map((item, index) =>
      saveCurrentWorkbenchQuickEntry({
        ...toQuickEntryInfo(item),
        sortNo:
          (items.findIndex((candidate) => candidate.id === item.id) + 1 || index + 1) * 10,
      }),
    ),
  ]);
  message.success(
    deletedIds.length > 0 ? '快捷菜单修改已保存' : '快捷菜单排序已保存',
  );
  await refreshQuickEntryCard();
}

function getNumberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function toQuickEntryInfo(item: WorkbenchCardItem): WorkbenchQuickEntryInfo {
  const entryType = getStringValue(item.entryType || item.targetType);
  return {
    config: getWorkbenchItemConfig(item),
    entryCode: getStringValue(item.entryCode),
    entryName: getStringValue(item.entryName || item.title),
    entryType: entryType === 'link' ? 'link' : 'menu',
    icon: getStringValue(item.icon),
    id: getStringValue(item.id),
    menuId: getStringValue(item.menuId || item.bizId),
    ownerType: getStringValue(item.ownerType),
    path: getStringValue(item.path || item.targetPath),
    permissionCode: getStringValue(item.permissionCode),
    sortNo: getNumberValue(item.sortNo),
    status: getStringValue(item.status) === 'disabled' ? 'disabled' : 'enabled',
  };
}

function updateBackTopVisible() {
  showBackTop.value = document.documentElement.scrollTop >= 200;
}

function handleBackTop() {
  document.documentElement.scrollTo({ top: 0, behavior: 'smooth' });
}

onMounted(() => {
  updateBackTopVisible();
  window.addEventListener('scroll', updateBackTopVisible, { passive: true });
  loadLayout();
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateBackTopVisible);
});
</script>

<template>
  <div class="workbench-page">
    <div
      v-show="!personalizeOpen && !quickEntrySettingsOpen"
      class="workbench-floating-actions"
    >
      <Button
        v-if="showBackTop"
        aria-label="回到顶部"
        shape="circle"
        title="回到顶部"
        @click="handleBackTop"
      >
        <template #icon>
          <IconifyIcon icon="lucide:arrow-up-to-line" />
        </template>
      </Button>
      <Button
        v-if="!editMode"
        aria-label="刷新卡片"
        shape="circle"
        title="刷新卡片"
        :loading="layoutLoading"
        @click="refreshAllCards"
      >
        <template #icon>
          <IconifyIcon icon="lucide:refresh-cw" />
        </template>
      </Button>
      <Button
        v-if="editMode"
        aria-label="添加卡片"
        shape="circle"
        title="添加卡片"
        @click="personalizeOpen = true"
      >
        <template #icon>
          <IconifyIcon icon="lucide:plus" />
        </template>
      </Button>
      <Button
        :aria-label="editMode ? '保存布局' : '调整布局'"
        shape="circle"
        :title="editMode ? '保存布局' : '调整布局'"
        :loading="savingLayout"
        type="primary"
        @click="editMode ? handleConfirmEditMode() : handleEnterEditMode()"
      >
        <template #icon>
          <IconifyIcon :icon="editMode ? 'lucide:check' : 'lucide:settings'" />
        </template>
      </Button>
    </div>

    <Spin :spinning="layoutLoading && !hasAnyCard">
      <WorkbenchGrid
        v-if="displayedCards.length > 0"
        :card-states="cardStates"
        :cards="displayedCards"
        :editable="editMode"
        @hide="handleHideCard"
        @layout-change="handleGridLayoutChange"
        @open-message-item="handleOpenMessageItem"
        @open-workflow-item="handleOpenWorkflowItem"
        @quick-entry-add="openQuickEntryCreate"
        @quick-entry-edit="openQuickEntryEdit"
        @quick-entry-sort-save="handleQuickEntrySortSave"
        @refresh="handleRefreshCard"
      />
      <Empty
        v-else
        class="workbench-empty"
        description="暂无可展示卡片，请确认工作台权限或打开个性化设置"
      >
        <Button type="primary" @click="personalizeOpen = true">
          打开个性化设置
        </Button>
      </Empty>
    </Spin>

    <WorkbenchPersonalizeModal
      v-model:open="personalizeOpen"
      :cards="configurableCards"
      :loading="savingLayout"
      @reset="handleResetLayout"
      @save="handleSaveLayout"
    />
    <WorkbenchQuickEntrySettingsModal
      v-model:open="quickEntrySettingsOpen"
      :record="quickEntryEditRecord"
      @success="handleQuickEntrySettingsSuccess"
    />
    <MessageDetailDrawer ref="messageDetailDrawerRef" />
    <WorkflowRuntimeFormDrawer
      ref="workflowDrawerRef"
      @success="handleWorkflowDrawerSuccess"
    />
  </div>
</template>

<style scoped>
.workbench-page {
  position: relative;
  min-height: 100%;
  padding: 16px;
  background:
    radial-gradient(circle at top left, rgb(14 165 233 / 12%), transparent 28rem),
    radial-gradient(circle at 80% 0, rgb(34 197 94 / 10%), transparent 26rem);
}

.workbench-floating-actions {
  position: fixed;
  z-index: 50;
  right: 28px;
  bottom: 32px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.workbench-floating-actions :deep(.ant-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  box-shadow: 0 14px 30px rgb(15 23 42 / 16%);
}

.workbench-floating-actions :deep(.ant-btn-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.workbench-floating-actions :deep(svg) {
  display: block;
  width: 18px;
  height: 18px;
}

.workbench-empty {
  margin-top: 80px;
}

@media (max-width: 768px) {
  .workbench-page {
    padding: 12px;
  }

  .workbench-floating-actions {
    right: 16px;
    bottom: 20px;
  }
}
</style>
