<script setup lang="ts">
import type {
  DocumentContentViewExpose,
  DocumentContentViewEmits,
  DocumentContentViewProps,
} from '../types';

import { IconifyIcon } from '@vben/icons';

import { Button, Input, Tooltip } from 'ant-design-vue';

import { useDocumentInlineEditorFocus } from '../hooks/useDocumentInlineEditorFocus';
import DocumentInlineRenameEditor from './DocumentInlineRenameEditor.vue';
import DocumentItemActionDropdown from './DocumentItemActionDropdown.vue';
import { fileIcon, fileTypeText, formatSize } from './documentExplorerUtils';

withDefaults(defineProps<DocumentContentViewProps>(), {
  creatingHere: false,
  savingName: false,
});

const emit = defineEmits<DocumentContentViewEmits>();
const {
  focusCreateNameInput,
  focusRenameNameInput,
  handleInlineKeydown,
  setCreateNameInputRef,
  setRenameNameInputRef,
} = useDocumentInlineEditorFocus({
  cancel: () => emit('inlineCancel'),
});

defineExpose<DocumentContentViewExpose>({
  focusCreateNameInput,
  focusRenameNameInput,
});
</script>

<template>
  <div class="document-grid">
    <div
      v-if="creatingHere"
      class="document-explorer-item document-tile document-tile--folder document-tile--editing"
    >
      <div class="document-tile__main">
        <IconifyIcon
          icon="lucide:folder"
          class="document-tile__icon document-tile__icon--folder"
        />
        <Input
          :ref="setCreateNameInputRef"
          :value="inlineEditor?.fileName"
          autofocus
          class="document-tile__name-input"
          :disabled="savingName"
          :maxlength="255"
          @blur="$emit('inlineSubmit')"
          @click.stop
          @keydown="handleInlineKeydown"
          @press-enter="$emit('inlineSubmit')"
          @update:value="$emit('inlineChange', $event)"
        />
        <div class="document-tile__meta">
          <span>文件夹</span>
        </div>
      </div>
    </div>
    <DocumentItemActionDropdown
      v-for="item in items"
      :key="item.id"
      mode="context"
      :can-edit-content-item="canEditContentItem"
      :can-edit-item="canEditItem"
      :can-preview-item="canPreviewItem"
      :can-view-history-item="canViewHistoryItem"
      :get-context-copyable-records="getContextCopyableRecords"
      :get-context-cuttable-records="getContextCuttableRecords"
      :get-context-deletable-records="getContextDeletableRecords"
      :get-context-download-records="getContextDownloadRecords"
      :is-single-context="isSingleContext"
      :record="item"
      :scope="scope"
      @action="$emit('action', $event, item)"
      @batch-action="$emit('contextBatchAction', $event, item)"
    >
      <div
        class="document-explorer-item document-tile"
        :data-document-id="itemKey(item)"
        :class="{
          'document-tile--folder': item.izFolder === '1',
          'document-tile--draggable': canMove(item),
          'document-tile--selected': isSelected(item),
          'document-tile--cutting': isCutting(item),
        }"
        :draggable="canMove(item)"
        tabindex="0"
        @click.stop="$emit('itemClick', $event, item)"
        @contextmenu.stop="$emit('contextSelect', item)"
        @dblclick.stop="$emit('itemActivate', item)"
        @dragstart="$emit('itemDragStart', $event, item)"
        @dragover="$emit('folderDragOver', $event, item)"
        @drop="$emit('dropOnFolder', $event, item)"
        @keydown.enter="$emit('itemOpen', item)"
      >
        <div class="document-tile__main">
          <img
            v-if="imageThumbnailUrl(item)"
            :alt="item.fileName || '图片预览'"
            class="document-tile__thumbnail"
            :src="imageThumbnailUrl(item)"
          />
          <IconifyIcon
            v-else
            :icon="fileIcon(item)"
            class="document-tile__icon"
            :class="{ 'document-tile__icon--folder': item.izFolder === '1' }"
          />
          <DocumentInlineRenameEditor
            v-if="isRenaming(item)"
            :ref="setRenameNameInputRef"
            :disabled="savingName"
            :rows="3"
            :value="inlineEditor?.fileName"
            variant="grid"
            @cancel="$emit('inlineCancel')"
            @submit="$emit('inlineSubmit')"
            @update:value="$emit('inlineChange', $event)"
          />
          <Tooltip v-else :title="item.fileName">
            <div class="document-tile__name">{{ item.fileName || '-' }}</div>
          </Tooltip>
          <div class="document-tile__meta">
            <span>{{ fileTypeText(item) }}</span>
            <span v-if="formatSize(item.fileSize)">{{ formatSize(item.fileSize) }}</span>
          </div>
        </div>
        <DocumentItemActionDropdown
          :can-edit-content-item="canEditContentItem"
          :can-edit-item="canEditItem"
          :can-preview-item="canPreviewItem"
          :can-view-history-item="canViewHistoryItem"
          :get-context-copyable-records="getContextCopyableRecords"
          :get-context-cuttable-records="getContextCuttableRecords"
          :get-context-deletable-records="getContextDeletableRecords"
          :get-context-download-records="getContextDownloadRecords"
          :is-single-context="isSingleContext"
          :record="item"
          :scope="scope"
          @action="$emit('action', $event, item)"
          @batch-action="$emit('contextBatchAction', $event, item)"
        >
          <Button class="document-tile__more" size="small" type="text" @click.stop>
            <IconifyIcon icon="lucide:more-vertical" />
          </Button>
        </DocumentItemActionDropdown>
      </div>
    </DocumentItemActionDropdown>
  </div>
</template>

<style scoped>
.document-grid {
  display: grid;
  overflow: auto;
  height: 100%;
  align-content: start;
  grid-template-columns: repeat(auto-fill, minmax(124px, 1fr));
  gap: 12px;
  padding: 2px;
}

.document-tile {
  position: relative;
  display: flex;
  min-height: 138px;
  cursor: default;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  background: hsl(var(--background));
  padding: 12px 10px 10px;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
}

.document-tile:hover,
.document-tile:focus-visible {
  border-color: hsl(var(--primary) / 45%);
  box-shadow: 0 8px 20px hsl(var(--foreground) / 8%);
  outline: none;
}

.document-tile--selected {
  border-color: hsl(var(--primary) / 65%);
  background: hsl(var(--primary) / 6%);
  box-shadow: 0 8px 20px hsl(var(--foreground) / 8%);
}

.document-tile--cutting {
  opacity: 0.48;
  filter: grayscale(35%);
}

.document-tile--draggable {
  cursor: default;
}

.document-tile--draggable:active {
  cursor: default;
}

.document-tile--editing {
  border-color: hsl(var(--primary) / 45%);
  box-shadow: 0 8px 20px hsl(var(--foreground) / 8%);
}

.document-tile__main {
  min-width: 0;
  text-align: center;
}

.document-tile__icon {
  margin: 2px auto 10px;
  width: 42px;
  height: 42px;
  color: hsl(var(--muted-foreground));
}

.document-tile__thumbnail {
  display: block;
  width: 56px;
  height: 56px;
  margin: 0 auto 8px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  background: hsl(var(--muted) / 40%);
  object-fit: cover;
}

.document-tile__icon--folder {
  color: #f5b93f;
}

.document-tile__name {
  display: -webkit-box;
  min-height: 40px;
  overflow: hidden;
  color: hsl(var(--foreground));
  font-weight: 500;
  font-size: 13px;
  line-height: 20px;
  text-overflow: ellipsis;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.document-tile__name-input {
  width: min(220px, 100%);
  text-align: center;
}

.document-tile__name-input :deep(.ant-input) {
  text-align: center;
}

.document-tile__meta {
  display: flex;
  min-height: 18px;
  justify-content: center;
  gap: 6px;
  overflow: hidden;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  white-space: nowrap;
}

.document-tile__more {
  position: absolute;
  top: 6px;
  right: 6px;
}

@media (max-width: 768px) {
  .document-grid {
    grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
  }
}
</style>
