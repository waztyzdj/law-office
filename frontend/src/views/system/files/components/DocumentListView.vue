<script setup lang="ts">
import type {
  DocumentListViewEmits,
  DocumentListViewProps,
} from '../types';

import { nextTick, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Dropdown, Input, Tooltip } from 'ant-design-vue';

import DocumentInlineRenameEditor from './DocumentInlineRenameEditor.vue';
import DocumentItemActionMenu from './DocumentItemActionMenu.vue';
import DocumentListSortHeader from './DocumentListSortHeader.vue';
import {
  fileIcon,
  fileTypeText,
  formatDateTime,
  formatSize,
} from './documentExplorerUtils';

interface FocusableInput {
  focus: () => void;
  input?: HTMLInputElement;
}

interface RenameEditorExpose {
  focus: () => void;
}

withDefaults(defineProps<DocumentListViewProps>(), {
  creatingHere: false,
  savingName: false,
});

const emit = defineEmits<DocumentListViewEmits>();

const createNameInputRef = ref<FocusableInput | null>(null);
const renameNameInputRef = ref<RenameEditorExpose | null>(null);

async function focusCreateNameInput() {
  await nextTick();
  createNameInputRef.value?.focus();
  createNameInputRef.value?.input?.select();
}

async function focusRenameNameInput() {
  await nextTick();
  renameNameInputRef.value?.focus();
}

function handleInlineKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('inlineCancel');
  }
}

defineExpose({
  focusCreateNameInput,
  focusRenameNameInput,
});
</script>

<template>
  <div class="document-list">
    <DocumentListSortHeader :sort-state="sortState" @sort="$emit('sort', $event)" />
    <div
      v-if="creatingHere"
      class="document-list-row document-list-row--editing"
    >
      <div class="document-list__cell document-list__cell--name">
        <IconifyIcon
          icon="lucide:folder"
          class="document-list-row__icon document-list-row__icon--folder"
        />
        <Input
          ref="createNameInputRef"
          :value="inlineEditor?.fileName"
          autofocus
          class="document-list-row__name-input"
          :disabled="savingName"
          :maxlength="255"
          @blur="$emit('inlineSubmit')"
          @click.stop
          @keydown="handleInlineKeydown"
          @press-enter="$emit('inlineSubmit')"
          @update:value="$emit('inlineChange', $event)"
        />
      </div>
      <div class="document-list__cell document-list__cell--type">文件夹</div>
      <div class="document-list__cell document-list__cell--size">-</div>
      <div class="document-list__cell document-list__cell--time">-</div>
      <div class="document-list__cell document-list__cell--actions"></div>
    </div>
    <Dropdown
      v-for="item in items"
      :key="item.id"
      :trigger="['contextmenu']"
    >
      <div
        class="document-explorer-item document-list-row"
        :data-document-id="itemKey(item)"
        :class="{
          'document-list-row--folder': item.izFolder === '1',
          'document-list-row--draggable': canMove(item),
          'document-list-row--selected': isSelected(item),
          'document-list-row--cutting': isCutting(item),
        }"
        :draggable="canMove(item)"
        tabindex="0"
        @click.stop="$emit('itemClick', $event, item)"
        @contextmenu.stop="$emit('contextSelect', item)"
        @dblclick.stop="$emit('itemTileOpen', item)"
        @dragstart="$emit('itemDragStart', $event, item)"
        @dragover="$emit('folderDragOver', $event, item)"
        @drop="$emit('dropOnFolder', $event, item)"
        @keydown.enter="$emit('itemOpen', item)"
      >
        <div class="document-list__cell document-list__cell--name">
          <img
            v-if="imageThumbnailUrl(item)"
            :alt="item.fileName || '图片预览'"
            class="document-list-row__thumbnail"
            :src="imageThumbnailUrl(item)"
          />
          <IconifyIcon
            v-else
            :icon="fileIcon(item)"
            class="document-list-row__icon"
            :class="{ 'document-list-row__icon--folder': item.izFolder === '1' }"
          />
          <DocumentInlineRenameEditor
            v-if="isRenaming(item)"
            ref="renameNameInputRef"
            :disabled="savingName"
            :rows="1"
            :value="inlineEditor?.fileName"
            variant="list"
            @cancel="$emit('inlineCancel')"
            @submit="$emit('inlineSubmit')"
            @update:value="$emit('inlineChange', $event)"
          />
          <Tooltip v-else :title="item.fileName">
            <span class="document-list-row__name">{{ item.fileName || '-' }}</span>
          </Tooltip>
        </div>
        <div class="document-list__cell document-list__cell--type">
          {{ fileTypeText(item) }}
        </div>
        <div class="document-list__cell document-list__cell--size">
          {{ formatSize(item.fileSize) || '-' }}
        </div>
        <div class="document-list__cell document-list__cell--time">
          {{ formatDateTime(item.updateTime || item.createTime) }}
        </div>
        <div class="document-list__cell document-list__cell--actions">
          <Dropdown trigger="click">
            <Button class="document-list-row__more" size="small" type="text" @click.stop>
              <IconifyIcon icon="lucide:more-vertical" />
            </Button>
            <template #overlay>
              <DocumentItemActionMenu
                :can-edit="canEditItem(item)"
                :can-edit-content="canEditContentItem(item)"
                :can-preview="canPreviewItem(item)"
                :can-view-history="canViewHistoryItem(item)"
                :context-copyable-count="getContextCopyableRecords(item).length"
                :context-cuttable-count="getContextCuttableRecords(item).length"
                :context-deletable-count="getContextDeletableRecords(item).length"
                :context-downloadable-count="getContextDownloadRecords(item).length"
                :record="item"
                :scope="scope"
                @action="$emit('action', $event, item)"
                @batch-action="$emit('contextBatchAction', $event, item)"
              />
            </template>
          </Dropdown>
        </div>
      </div>

      <template #overlay>
        <DocumentItemActionMenu
          :can-edit="isSingleContext(item) && canEditItem(item)"
          :can-edit-content="isSingleContext(item) && canEditContentItem(item)"
          :can-preview="isSingleContext(item) && canPreviewItem(item)"
          :can-view-history="isSingleContext(item) && canViewHistoryItem(item)"
          :context-copyable-count="getContextCopyableRecords(item).length"
          :context-cuttable-count="getContextCuttableRecords(item).length"
          :context-deletable-count="getContextDeletableRecords(item).length"
          :context-downloadable-count="getContextDownloadRecords(item).length"
          :record="item"
          :scope="scope"
          :single-context="isSingleContext(item)"
          @action="$emit('action', $event, item)"
          @batch-action="$emit('contextBatchAction', $event, item)"
        />
      </template>
    </Dropdown>
  </div>
</template>

<style scoped>
.document-list {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  overflow: auto;
  background: hsl(var(--background));
}

.document-list-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 120px 100px 150px 116px;
  align-items: center;
  min-width: 720px;
}

.document-list-row {
  min-height: 44px;
  cursor: default;
  border-bottom: 1px solid hsl(var(--border) / 70%);
  color: hsl(var(--foreground));
  transition:
    background 0.16s ease,
    box-shadow 0.16s ease;
}

.document-list-row:last-child {
  border-bottom: 0;
}

.document-list-row:hover,
.document-list-row:focus-visible {
  background: hsl(var(--muted) / 38%);
  outline: none;
}

.document-list-row--selected {
  background: hsl(var(--primary) / 8%);
  box-shadow: inset 3px 0 0 hsl(var(--primary));
}

.document-list-row--cutting {
  opacity: 0.48;
  filter: grayscale(35%);
}

.document-list-row--draggable {
  cursor: default;
}

.document-list-row--draggable:active {
  cursor: default;
}

.document-list-row--editing {
  background: hsl(var(--primary) / 5%);
}

.document-list__cell {
  min-width: 0;
  padding: 0 10px;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.document-list__cell--name {
  display: flex;
  align-items: center;
  gap: 8px;
  color: hsl(var(--foreground));
}

.document-list__cell--actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.document-list-row__icon {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  color: hsl(var(--muted-foreground));
}

.document-list-row__thumbnail {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border: 1px solid hsl(var(--border));
  border-radius: 4px;
  background: hsl(var(--muted) / 40%);
  object-fit: cover;
}

.document-list-row__icon--folder {
  color: #f5b93f;
}

.document-list-row__name {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-list-row__name-input {
  max-width: 280px;
}

.document-list-row__more {
  width: 26px;
  height: 26px;
  padding: 0;
  color: hsl(var(--muted-foreground));
}
</style>
