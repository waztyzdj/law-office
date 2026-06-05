<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';

import { computed, onBeforeUnmount, onMounted, ref, toRef, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Dropdown, Empty, Menu, Spin } from 'ant-design-vue';

import DocumentGridView from './DocumentGridView.vue';
import DocumentListView from './DocumentListView.vue';
import DocumentSelectionBox from './DocumentSelectionBox.vue';
import {
  DOCUMENT_SORT_OPTIONS as sortOptions,
  type DocumentSortField,
  type DocumentSortOrder,
  type DocumentSortState,
} from '../constants';
import type {
  DocumentBatchAction,
  DocumentContentViewExpose,
  DocumentContentViewListeners,
  DocumentContentViewProps,
  DocumentViewMode,
  InlineEditorState,
} from '../types';
import { useDocumentDragDrop } from '../hooks/useDocumentDragDrop';
import { useDocumentSelection } from '../hooks/useDocumentSelection';
import { useDocumentThumbnails } from '../hooks/useDocumentThumbnails';
import {
  canDropOnFolder as canDropOnDocumentFolder,
  canEditContentItem as canEditDocumentContent,
  canEditItem as canEditDocumentItem,
  canMove as canMoveDocument,
  canPreviewItem as canPreviewDocumentItem,
  canViewHistoryItem as canViewDocumentHistory,
  compareDocuments,
  isActualSharedItem,
  isActualStarredItem,
  isReadonlyBrowseScope,
  isReadonlyCollectionScope,
} from './documentExplorerUtils';

interface Props {
  canCreate?: boolean;
  canPaste?: boolean;
  canUpload?: boolean;
  currentFolder?: DocumentFileInfo;
  cuttingIds?: string[];
  dataSource: DocumentFileInfo[];
  inlineEditor?: InlineEditorState;
  loading: boolean;
  moving?: boolean;
  personalizeShared?: boolean;
  savingName?: boolean;
  scope: DocumentScope;
  sortState: DocumentSortState;
  viewMode?: DocumentViewMode;
}

const props = withDefaults(defineProps<Props>(), {
  canCreate: false,
  canPaste: false,
  canUpload: false,
  cuttingIds: () => [],
  moving: false,
  personalizeShared: false,
  savingName: false,
  viewMode: 'grid',
});

const emit = defineEmits<{
  action: [event: string, record: DocumentFileInfo];
  batchAction: [event: DocumentBatchAction, records: DocumentFileInfo[]];
  batchMove: [
    sourceIds: string[],
    targetParentId?: string,
    sourceParentIds?: Array<string | undefined>,
  ];
  createFolder: [];
  createFolderIn: [record: DocumentFileInfo];
  inlineCancel: [];
  inlineChange: [value: string];
  inlineSubmit: [];
  move: [sourceId: string, targetParentId?: string, sourceParentId?: string];
  paste: [];
  sortChange: [state: DocumentSortState];
  upload: [];
}>();

const sortedItems = computed(() =>
  [...props.dataSource].sort((a, b) => {
    const folderWeight = Number(b.izFolder === '1') - Number(a.izFolder === '1');
    if (folderWeight !== 0) {
      return folderWeight;
    }
    return compareDocuments(a, b, props.sortState);
  }),
);
const actionContext = computed(() => ({
  personalizeShared: props.personalizeShared,
  scope: props.scope,
}));

const canCreateInScope = computed(() => props.canCreate);
const canUploadInScope = computed(() => props.canUpload);
const canShowBodyWriteActions = computed(() => !isReadonlyBrowseScope(props.scope));
const hasBodyWriteActions = computed(
  () =>
    canShowBodyWriteActions.value &&
    (props.canPaste || canUploadInScope.value || canCreateInScope.value),
);
const currentFolderId = computed(() => props.currentFolder?.id || '');
const creatingHere = computed(
  () =>
    props.inlineEditor?.mode === 'create' &&
    (props.inlineEditor.parentId || '') === currentFolderId.value,
);
const hasGridContent = computed(() => sortedItems.value.length > 0 || creatingHere.value);
const gridViewRef = ref<DocumentContentViewExpose | null>(null);
const listViewRef = ref<DocumentContentViewExpose | null>(null);
const cuttingIdSet = computed(() => new Set(props.cuttingIds));

async function focusCreateNameInput() {
  if (props.viewMode === 'grid') {
    await gridViewRef.value?.focusCreateNameInput();
    return;
  }
  await listViewRef.value?.focusCreateNameInput();
}

async function focusRenameNameInput() {
  if (props.viewMode === 'grid') {
    await gridViewRef.value?.focusRenameNameInput();
    return;
  }
  await listViewRef.value?.focusRenameNameInput();
}

watch(
  () => [creatingHere.value, props.viewMode] as const,
  ([active]) => {
    if (active) {
      void focusCreateNameInput();
    }
  },
  { flush: 'post', immediate: true },
);

watch(
  () => [props.inlineEditor?.mode, props.inlineEditor?.record?.id, props.viewMode] as const,
  ([mode]) => {
    if (mode === 'rename') {
      void focusRenameNameInput();
    }
  },
  { flush: 'post' },
);

function setSort(field: DocumentSortField, order?: DocumentSortOrder) {
  const nextOrder =
    order ||
    (props.sortState.field === field && props.sortState.order === 'asc' ? 'desc' : 'asc');
  emit('sortChange', { field, order: nextOrder });
}

function setSortField(field: DocumentSortField) {
  emit('sortChange', {
    field,
    order: props.sortState.order,
  });
}

function setSortOrder(order: DocumentSortOrder) {
  emit('sortChange', {
    field: props.sortState.field,
    order,
  });
}

function isActiveSort(field: DocumentSortField, order?: DocumentSortOrder) {
  return props.sortState.field === field && (!order || props.sortState.order === order);
}

function canMove(record: DocumentFileInfo) {
  return Boolean(canMoveDocument(record, actionContext.value));
}

function canEditItem(record: DocumentFileInfo) {
  return canEditDocumentItem(record, actionContext.value);
}

function canPreviewItem(record: DocumentFileInfo) {
  return canPreviewDocumentItem(record, actionContext.value);
}

function canEditContentItem(record: DocumentFileInfo) {
  return canEditDocumentContent(record, actionContext.value);
}

function canViewHistoryItem(record: DocumentFileInfo) {
  return canViewDocumentHistory(record, actionContext.value);
}

function canDropOnFolder(target: DocumentFileInfo) {
  return canDropOnDocumentFolder(target, actionContext.value);
}

function canShowItemActionMenu(record: DocumentFileInfo) {
  if (props.scope === 'shared') {
    return record.izFolder !== '1';
  }
  if (props.scope === 'business') {
    return record.izFolder !== '1';
  }
  if (props.scope === 'starred') {
    return record.izFolder !== '1' || isActualStarredItem(record);
  }
  if (props.scope === 'sharedByMe') {
    return record.izFolder !== '1' || isActualSharedItem(record);
  }
  return !isReadonlyCollectionScope(props.scope);
}

function handleOpen(record: DocumentFileInfo) {
  if (record.izFolder === '1') {
    emit('action', 'open', record);
    return;
  }
  if (canPreviewItem(record)) {
    emit('action', 'preview', record);
    return;
  }
  if (record.canDownload) {
    emit('action', 'download', record);
  }
}

function emitAction(event: string, record: DocumentFileInfo) {
  emit('action', event, record);
}

function itemKey(record: DocumentFileInfo) {
  return record.id || record.fileName || '';
}

const {
  cleanupImageThumbnails,
  imageThumbnailUrl,
} = useDocumentThumbnails({
  itemKey,
  sortedItems,
});

function confirmInlineEdit() {
  if (props.inlineEditor) {
    emit('inlineSubmit');
  }
}

const documentSelection = useDocumentSelection({
  canEditItem,
  canMove,
  canPaste: toRef(props, 'canPaste'),
  confirmInlineEdit,
  emitBatchAction: (event, records) => emit('batchAction', event, records),
  emitPaste: () => emit('paste'),
  inlineEditor: toRef(props, 'inlineEditor'),
  isRenaming,
  loading: toRef(props, 'loading'),
  moving: toRef(props, 'moving'),
  openRename: (record) => emitAction('rename', record),
  scope: toRef(props, 'scope'),
  sortedItems,
});
const {
  clearRenameTimer,
  emitContextBatchAction,
  getContextCopyableRecords,
  getContextCuttableRecords,
  getContextDeletableRecords,
  getContextDownloadRecords,
  handleBodyClick,
  handleContextSelect,
  handleItemClick,
  handleSelectionMouseDown,
  handleShortcutKeydown,
  isSelected,
  isSingleContext,
  cleanupSelectionListeners,
  selectOnly,
  selectedMovableIds,
  selectedMovableRecords,
  selecting,
  selectionBoxStyle,
} = documentSelection;

const {
  handleDragStart,
  handleDropOnFolder,
  handleFolderDragOver,
} = useDocumentDragDrop({
  canDropOnFolder,
  canMove,
  emitBatchMove: (sourceIds, targetParentId, sourceParentIds) =>
    emit('batchMove', sourceIds, targetParentId, sourceParentIds),
  emitMove: (sourceId, targetParentId, sourceParentId) =>
    emit('move', sourceId, targetParentId, sourceParentId),
  isSelected,
  moving: toRef(props, 'moving'),
  selectOnly,
  selectedMovableIds,
  selectedMovableRecords,
});

function setExplorerBodyRef(element: unknown) {
  documentSelection.explorerBodyRef.value = element instanceof HTMLElement ? element : undefined;
}

function handleItemActivate(record: DocumentFileInfo) {
  clearRenameTimer();
  handleOpen(record);
}

function isCutting(record: DocumentFileInfo) {
  return Boolean(record.id && cuttingIdSet.value.has(record.id));
}

function isRenaming(record: DocumentFileInfo) {
  return (
    props.inlineEditor?.mode === 'rename' &&
    Boolean(record.id) &&
    props.inlineEditor.record?.id === record.id
  );
}

function handleNameInput(value: string) {
  emit('inlineChange', value);
}

const contentViewProps = computed<DocumentContentViewProps>(() => ({
  canEditContentItem,
  canEditItem,
  canMove,
  canShowItemActionMenu,
  canPreviewItem,
  canViewHistoryItem,
  creatingHere: creatingHere.value,
  getContextCopyableRecords,
  getContextCuttableRecords,
  getContextDeletableRecords,
  getContextDownloadRecords,
  imageThumbnailUrl,
  inlineEditor: props.inlineEditor,
  isCutting,
  isRenaming,
  isSelected,
  isSingleContext,
  itemKey,
  items: sortedItems.value,
  savingName: props.savingName,
  scope: props.scope,
}));

const contentViewListeners: DocumentContentViewListeners = {
  action: emitAction,
  contextBatchAction: emitContextBatchAction,
  contextSelect: handleContextSelect,
  dropOnFolder: handleDropOnFolder,
  folderDragOver: handleFolderDragOver,
  inlineCancel: () => emit('inlineCancel'),
  inlineChange: handleNameInput,
  inlineSubmit: () => emit('inlineSubmit'),
  itemActivate: handleItemActivate,
  itemClick: handleItemClick,
  itemDragStart: handleDragStart,
  itemOpen: handleOpen,
};

onMounted(() => {
  window.addEventListener('keydown', handleShortcutKeydown);
});

onBeforeUnmount(() => {
  cleanupImageThumbnails();
  cleanupSelectionListeners();
  window.removeEventListener('keydown', handleShortcutKeydown);
});
</script>

<template>
  <div class="document-explorer">
    <Dropdown :trigger="['contextmenu']">
      <div
        :ref="setExplorerBodyRef"
        class="document-explorer__body"
        :class="{ 'document-explorer__body--selecting': selecting }"
        @click="handleBodyClick"
        @mousedown="handleSelectionMouseDown"
      >
        <Spin :spinning="loading || moving">
          <DocumentGridView
            v-if="hasGridContent && viewMode === 'grid'"
            ref="gridViewRef"
            v-bind="contentViewProps"
            v-on="contentViewListeners"
          />
          <DocumentListView
            v-else-if="hasGridContent"
            ref="listViewRef"
            :sort-state="sortState"
            v-bind="contentViewProps"
            v-on="contentViewListeners"
            @sort="setSort"
          />
          <Empty v-else class="document-empty" description="当前文件夹暂无内容" />
        </Spin>
        <DocumentSelectionBox :style="selectionBoxStyle" :visible="selecting" />
      </div>
      <template #overlay>
        <Menu>
          <Menu.SubMenu key="sort-context">
            <template #title>
              <IconifyIcon class="document-menu-icon" icon="lucide:arrow-up-down" />
              排序方式
            </template>
            <Menu.Item
              v-for="option in sortOptions"
              :key="`context-${option.field}`"
              @click="setSortField(option.field)"
            >
              <IconifyIcon
                v-if="isActiveSort(option.field)"
                class="document-menu-icon"
                icon="lucide:check"
              />
              <span v-else class="document-menu-icon document-menu-icon--placeholder" />
              {{ option.label }}
            </Menu.Item>
            <Menu.Divider />
            <Menu.Item key="context-sort-asc" @click="setSortOrder('asc')">
              <IconifyIcon
                v-if="props.sortState.order === 'asc'"
                class="document-menu-icon"
                icon="lucide:check"
              />
              <span v-else class="document-menu-icon document-menu-icon--placeholder" />
              升序
            </Menu.Item>
            <Menu.Item key="context-sort-desc" @click="setSortOrder('desc')">
              <IconifyIcon
                v-if="props.sortState.order === 'desc'"
                class="document-menu-icon"
                icon="lucide:check"
              />
              <span v-else class="document-menu-icon document-menu-icon--placeholder" />
              降序
            </Menu.Item>
          </Menu.SubMenu>
          <Menu.Divider v-if="hasBodyWriteActions" />
          <Menu.Item v-if="canShowBodyWriteActions && canPaste" @click="$emit('paste')">
            <IconifyIcon class="document-menu-icon" icon="lucide:clipboard-paste" />
            粘贴
          </Menu.Item>
          <Menu.Item v-if="canShowBodyWriteActions && canUploadInScope" @click="$emit('upload')">
            <IconifyIcon class="document-menu-icon" icon="lucide:upload" />
            上传文件
          </Menu.Item>
          <Menu.Item v-if="canShowBodyWriteActions && canCreateInScope" @click="$emit('createFolder')">
            <IconifyIcon class="document-menu-icon" icon="lucide:folder-plus" />
            新建文件夹
          </Menu.Item>
        </Menu>
      </template>
    </Dropdown>
  </div>
</template>

<style scoped>
.document-menu-icon {
  display: inline-flex;
  width: 16px;
  margin-right: 8px;
  vertical-align: -2px;
}

.document-menu-icon--active {
  color: hsl(var(--primary));
}

.document-menu-icon--starred {
  color: #f5b93f;
}

.document-menu-icon--starred :deep(svg) {
  fill: currentColor;
}

.document-menu-icon--placeholder {
  flex: 0 0 auto;
}

.document-explorer {
  display: flex;
  overflow: hidden;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.document-explorer__body {
  position: relative;
  overflow: hidden;
  flex: 1;
  min-height: 0;
}

.document-explorer__body :deep(.ant-spin-nested-loading),
.document-explorer__body :deep(.ant-spin-container) {
  height: 100%;
  min-height: 0;
}

.document-explorer__body--selecting {
  user-select: none;
}

.document-empty {
  padding: 56px 0;
}
</style>
