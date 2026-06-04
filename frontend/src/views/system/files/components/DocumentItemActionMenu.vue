<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type { DocumentBatchAction } from '../types';

import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Menu } from 'ant-design-vue';

interface Props {
  canEdit?: boolean;
  canEditContent?: boolean;
  canPreview?: boolean;
  canViewHistory?: boolean;
  contextCopyableCount?: number;
  contextCuttableCount?: number;
  contextDeletableCount?: number;
  contextDownloadableCount?: number;
  record?: DocumentFileInfo;
  scope: DocumentScope;
  singleContext?: boolean;
}

interface ActionMenuItem {
  action?: string;
  batchAction?: DocumentBatchAction;
  danger?: boolean;
  disabled?: boolean;
  icon: string;
  key: string;
  label: string;
}

const props = withDefaults(defineProps<Props>(), {
  canEdit: false,
  canEditContent: false,
  canPreview: false,
  canViewHistory: false,
  contextCopyableCount: 0,
  contextCuttableCount: 0,
  contextDeletableCount: 0,
  contextDownloadableCount: 0,
  singleContext: true,
});

const emit = defineEmits<{
  action: [event: string, record: DocumentFileInfo];
  batchAction: [event: DocumentBatchAction];
}>();

const batchItems = computed<ActionMenuItem[]>(() => {
  if (props.scope === 'trash') {
    return [];
  }
  const items: ActionMenuItem[] = [
    {
      batchAction: 'copy',
      disabled: props.contextCopyableCount === 0,
      icon: 'lucide:copy',
      key: 'copy',
      label: '复制',
    },
    {
      batchAction: 'cut',
      disabled: props.contextCuttableCount === 0,
      icon: 'lucide:scissors',
      key: 'cut',
      label: '剪切',
    },
  ];
  if (props.singleContext && props.canEdit) {
    items.push({ action: 'rename', icon: 'lucide:pencil', key: 'rename', label: '重命名' });
  }
  if (props.record?.izFolder !== '1') {
    items.push({
      batchAction: 'download',
      disabled: props.contextDownloadableCount === 0,
      icon: 'lucide:download',
      key: 'download',
      label: '下载',
    });
  }
  return items;
});

const singleItems = computed<ActionMenuItem[]>(() => {
  if (!props.singleContext) {
    return [];
  }
  const items: ActionMenuItem[] = [];
  if (props.canPreview) {
    items.push({ action: 'preview', icon: 'lucide:eye', key: 'preview', label: '预览' });
  }
  if (props.canEditContent) {
    items.push({ action: 'edit', icon: 'lucide:file-pen-line', key: 'edit', label: '在线编辑' });
  }
  if (props.canViewHistory) {
    items.push({ action: 'history', icon: 'lucide:history', key: 'history', label: '历史版本' });
  }
  if (props.canEdit) {
    items.push({
      action: 'share',
      icon: 'lucide:share-2',
      key: 'share',
      label: props.record?.sharedFlag ? '查看共享' : '共享',
    });
    if (props.record?.sharedFlag) {
      items.push({
        action: 'cancelShare',
        danger: true,
        icon: 'lucide:share-2',
        key: 'cancel-share',
        label: '取消共享',
      });
    }
  }
  return items;
});

const showDeleteItem = computed(() =>
  props.scope !== 'trash' && props.scope !== 'business',
);

function handleClick(item: ActionMenuItem) {
  if (item.disabled) {
    return;
  }
  if (item.batchAction) {
    emit('batchAction', item.batchAction);
    return;
  }
  if (item.action) {
    if (!props.record) {
      return;
    }
    emit('action', item.action, props.record);
  }
}
</script>

<template>
  <Menu class="document-item-action-menu">
    <Menu.Item
      v-for="item in batchItems"
      :key="item.key"
      :disabled="item.disabled"
      @click="handleClick(item)"
    >
      <span class="document-item-action-menu__item">
        <IconifyIcon class="document-item-action-menu__icon" :icon="item.icon" />
        <span>{{ item.label }}</span>
      </span>
    </Menu.Item>
    <Menu.Item
      v-for="item in singleItems"
      :key="item.key"
      :class="{ 'document-item-action-menu__danger': item.danger }"
      :danger="item.danger"
      @click="handleClick(item)"
    >
      <span class="document-item-action-menu__item">
        <IconifyIcon
          class="document-item-action-menu__icon"
          :class="{ 'document-item-action-menu__icon--active': ['share', 'cancel-share'].includes(item.key) && record?.sharedFlag }"
          :icon="item.icon"
        />
        <span>{{ item.label }}</span>
      </span>
    </Menu.Item>
    <Menu.Item
      v-if="showDeleteItem"
      key="delete"
      class="document-item-action-menu__danger"
      :disabled="contextDeletableCount === 0"
      danger
      @click="emit('batchAction', 'delete')"
    >
      <span class="document-item-action-menu__item">
        <IconifyIcon class="document-item-action-menu__icon" icon="lucide:trash-2" />
        <span>删除</span>
      </span>
    </Menu.Item>
  </Menu>
</template>

<style scoped>
.document-item-action-menu {
  min-width: 128px;
}

.document-item-action-menu__item {
  display: inline-flex;
  align-items: center;
  min-width: 104px;
  white-space: nowrap;
}

.document-item-action-menu__icon {
  display: inline-flex;
  flex: 0 0 16px;
  width: 16px;
  margin-right: 8px;
  vertical-align: -2px;
}

.document-item-action-menu__icon--active {
  color: hsl(var(--primary));
}

.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled)) {
  color: #ff4d4f;
}

.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled) .document-item-action-menu__icon) {
  color: #ff4d4f;
}

.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled):hover),
.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled).ant-menu-item-active),
.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled).ant-menu-item-selected) {
  color: #fff;
  background: #ff4d4f;
}

.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled):hover .document-item-action-menu__icon),
.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled).ant-menu-item-active .document-item-action-menu__icon),
.document-item-action-menu :deep(.document-item-action-menu__danger:not(.ant-menu-item-disabled).ant-menu-item-selected .document-item-action-menu__icon) {
  color: #fff;
}
</style>
