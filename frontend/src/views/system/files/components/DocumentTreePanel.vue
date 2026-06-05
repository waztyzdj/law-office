<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';
import type { DocumentBatchAction } from '../types';

import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Dropdown,
  Input,
  InputSearch,
  Spin,
  Tree,
} from 'ant-design-vue';

import DocumentItemActionMenu from './DocumentItemActionMenu.vue';

interface Props {
  canManageTreeFolder: (key: string) => boolean;
  canShowTreeContextMenu: (key: string) => boolean;
  expandedKeys: string[];
  findFolderByKey: (key: string) => DocumentFileInfo | undefined;
  getTreeNodeScope: (key: string) => DocumentScope;
  getTreeCopyableRecords: (record?: DocumentFileInfo) => DocumentFileInfo[];
  getTreeCuttableRecords: (record?: DocumentFileInfo) => DocumentFileInfo[];
  getTreeDeletableRecords: (record?: DocumentFileInfo) => DocumentFileInfo[];
  getTreeDownloadableRecords: (record?: DocumentFileInfo) => DocumentFileInfo[];
  getTreeNodeIcon: (key: string) => string;
  inlineFileName: string;
  isEditingTreeNode: (key: string) => boolean;
  keyword: string;
  loading: boolean;
  scope: DocumentScope;
  selectedKeys: string[];
  treeData: DataNode[];
  treeRenderKey: number;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  action: [event: string, record: DocumentFileInfo];
  activateShortcut: [];
  batchAction: [event: DocumentBatchAction, record?: DocumentFileInfo];
  dropToTree: [event: DragEvent, key: string];
  inlineCancel: [];
  inlineChange: [value: string];
  inlineSubmit: [];
  search: [value: string];
  treeDragOver: [event: DragEvent, key: string];
  treeDragStart: [event: DragEvent, key: string];
  treeExpand: [keys: Key[]];
  treeSelect: [keys: Key[], info: { node?: { key?: Key } }];
  updateExpandedKeys: [keys: string[]];
  updateKeyword: [value: string];
}>();

const keywordModel = computed({
  get: () => props.keyword,
  set: (value: string) => emit('updateKeyword', value),
});

const expandedKeysModel = computed({
  get: () => props.expandedKeys,
  set: (keys: Key[]) => emit('updateExpandedKeys', keys.map(String)),
});

function handleExpand(keys: Key[]) {
  emit('treeExpand', keys);
}

function handleSelect(keys: Key[], info: { node?: { key?: Key } }) {
  emit('treeSelect', keys, info);
}

function handleAction(event: string, record?: DocumentFileInfo) {
  if (!record) {
    return;
  }
  emit('action', event, record);
}

function isReadonlyTreeScope(key: string) {
  return ['business', 'shared', 'sharedByMe', 'starred'].includes(props.getTreeNodeScope(key));
}

function getTreeCopyableCount(key: string) {
  return isReadonlyTreeScope(key) ? 0 : props.getTreeCopyableRecords(props.findFolderByKey(key)).length;
}

function getTreeCuttableCount(key: string) {
  return isReadonlyTreeScope(key) ? 0 : props.getTreeCuttableRecords(props.findFolderByKey(key)).length;
}

function getTreeDeletableCount(key: string) {
  return isReadonlyTreeScope(key) ? 0 : props.getTreeDeletableRecords(props.findFolderByKey(key)).length;
}
</script>

<template>
  <div class="document-tree-panel" @mousedown.capture="emit('activateShortcut')">
    <InputSearch
      v-model:value="keywordModel"
      class="document-tree-search"
      allow-clear
      placeholder="搜索文件名"
      @search="emit('search', $event)"
    />
    <Spin :spinning="loading">
      <Tree
        :key="treeRenderKey"
        v-model:expanded-keys="expandedKeysModel"
        block-node
        :selected-keys="selectedKeys"
        :tree-data="treeData"
        @expand="handleExpand"
        @select="handleSelect"
      >
        <template #title="{ key, title }">
          <Dropdown
            v-if="canShowTreeContextMenu(String(key))"
            :trigger="['contextmenu']"
          >
            <span
              class="document-tree-node"
              :class="{ 'document-tree-node--draggable': canManageTreeFolder(String(key)) }"
              :draggable="canManageTreeFolder(String(key))"
              @dragstart="emit('treeDragStart', $event, String(key))"
              @dragover="emit('treeDragOver', $event, String(key))"
              @drop="emit('dropToTree', $event, String(key))"
            >
              <IconifyIcon
                :icon="getTreeNodeIcon(String(key))"
                class="document-tree-node__icon"
              />
              <Input
                v-if="isEditingTreeNode(String(key))"
                :value="inlineFileName"
                class="document-tree-node__input"
                size="small"
                @blur="emit('inlineSubmit')"
                @click.stop
                @keydown.esc.stop.prevent="emit('inlineCancel')"
                @press-enter="emit('inlineSubmit')"
                @update:value="emit('inlineChange', $event)"
              />
              <span v-else>{{ title }}</span>
            </span>
            <template #overlay>
              <DocumentItemActionMenu
                :can-edit="canManageTreeFolder(String(key))"
                :context-copyable-count="getTreeCopyableCount(String(key))"
                :context-cuttable-count="getTreeCuttableCount(String(key))"
                :context-deletable-count="getTreeDeletableCount(String(key))"
                :context-downloadable-count="getTreeDownloadableRecords(findFolderByKey(String(key))).length"
                :record="findFolderByKey(String(key))"
                :scope="getTreeNodeScope(String(key))"
                @action="handleAction($event, findFolderByKey(String(key)))"
                @batch-action="emit('batchAction', $event, findFolderByKey(String(key)))"
              />
            </template>
          </Dropdown>
          <span
            v-else
            class="document-tree-node"
            @dragover="emit('treeDragOver', $event, String(key))"
            @drop="emit('dropToTree', $event, String(key))"
          >
            <IconifyIcon
              :icon="getTreeNodeIcon(String(key))"
              class="document-tree-node__icon"
            />
            <Input
              v-if="isEditingTreeNode(String(key))"
              :value="inlineFileName"
              class="document-tree-node__input"
              size="small"
              @blur="emit('inlineSubmit')"
              @click.stop
              @keydown.esc.stop.prevent="emit('inlineCancel')"
              @press-enter="emit('inlineSubmit')"
              @update:value="emit('inlineChange', $event)"
            />
            <span v-else>{{ title }}</span>
          </span>
        </template>
      </Tree>
    </Spin>
  </div>
</template>

<style scoped>
.document-tree-panel {
  min-height: 0;
}

.document-tree-search {
  width: 100%;
  margin-bottom: 12px;
}

.document-tree-panel :deep(.ant-tree-treenode) {
  align-items: center;
  min-height: 28px;
}

.document-tree-panel :deep(.ant-tree-switcher) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 28px;
  line-height: 28px;
}

.document-tree-panel :deep(.ant-tree-indent-unit) {
  width: 22px;
}

.document-tree-panel :deep(.ant-tree-node-content-wrapper) {
  display: inline-flex;
  align-items: center;
  cursor: default;
  min-height: 28px;
  line-height: 28px;
}

.document-tree-panel :deep(.ant-tree-title) {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  width: 100%;
}

.document-tree-node {
  display: inline-flex;
  align-items: center;
  cursor: default;
  gap: 6px;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-tree-node--draggable {
  cursor: default;
}

.document-tree-node--draggable:active {
  cursor: default;
}

.document-tree-node__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
  line-height: 1;
}

.document-tree-node__input {
  width: 150px;
}
</style>
