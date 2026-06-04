<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';

import { downloadDocumentThumbnail } from '#/api/system/document';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Dropdown, Empty, Input, Menu, Spin, Tooltip } from 'ant-design-vue';

import DocumentItemActionMenu from './DocumentItemActionMenu.vue';
import {
  DOCUMENT_SORT_OPTIONS as sortOptions,
  type DocumentSortField,
  type DocumentSortOrder,
  type DocumentSortState,
} from '../constants';
import type {
  DocumentBatchAction,
  DocumentViewMode,
  InlineEditorState,
} from '../types';
import {
  canDropOnFolder as canDropOnDocumentFolder,
  canEditContentItem as canEditDocumentContent,
  canEditItem as canEditDocumentItem,
  canMove as canMoveDocument,
  canPreviewItem as canPreviewDocumentItem,
  canViewHistoryItem as canViewDocumentHistory,
  compareDocuments,
  documentListColumns,
  fileIcon,
  fileTypeText,
  formatDateTime,
  formatSize,
  isImageFile,
  isVirtualBusinessItem,
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

interface FocusableInput {
  focus: () => void;
  input?: HTMLInputElement;
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
  batchMove: [sourceIds: string[], targetParentId?: string];
  createFolder: [];
  createFolderIn: [record: DocumentFileInfo];
  inlineCancel: [];
  inlineChange: [value: string];
  inlineSubmit: [];
  move: [sourceId: string, targetParentId?: string];
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
const currentFolderId = computed(() => props.currentFolder?.id || '');
const creatingHere = computed(
  () =>
    props.inlineEditor?.mode === 'create' &&
    (props.inlineEditor.parentId || '') === currentFolderId.value,
);
const hasGridContent = computed(() => sortedItems.value.length > 0 || creatingHere.value);
const createNameInputRef = ref<FocusableInput | null>(null);
const renameNameInputRef = ref<FocusableInput | null>(null);
const explorerBodyRef = ref<HTMLElement>();
const selectedIds = ref<Set<string>>(new Set());
// Shift 连续选择需要一个稳定锚点，保持与桌面文件管理器一致。
const selectionAnchorKey = ref<string>();
const selecting = ref(false);
const selectionMoved = ref(false);
const suppressNextBodyClick = ref(false);
const selectionBox = ref({
  currentX: 0,
  currentY: 0,
  startX: 0,
  startY: 0,
});
const imageThumbnailUrls = ref<Record<string, string>>({});
let imageThumbnailLoadVersion = 0;
const selectionBoxStyle = computed(() => {
  const left = Math.min(selectionBox.value.startX, selectionBox.value.currentX);
  const top = Math.min(selectionBox.value.startY, selectionBox.value.currentY);
  const width = Math.abs(selectionBox.value.currentX - selectionBox.value.startX);
  const height = Math.abs(selectionBox.value.currentY - selectionBox.value.startY);
  return {
    height: `${height}px`,
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
  };
});
const selectedRecords = computed(() =>
  sortedItems.value.filter((item) => selectedIds.value.has(itemKey(item))),
);
const cuttingIdSet = computed(() => new Set(props.cuttingIds));
const selectedMovableIds = computed(() =>
  selectedRecords.value
    .filter((item) => item.id && canMove(item))
    .map((item) => item.id || ''),
);
let renameTimer: number | undefined;

async function focusCreateNameInput() {
  await nextTick();
  createNameInputRef.value?.focus();
  createNameInputRef.value?.input?.select();
}

async function focusRenameNameInput() {
  await nextTick();
  renameNameInputRef.value?.focus();
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

function sortDirectionIcon(field: DocumentSortField) {
  if (!isActiveSort(field)) {
    return 'lucide:chevrons-up-down';
  }
  return props.sortState.order === 'asc' ? 'lucide:arrow-up' : 'lucide:arrow-down';
}

function imageThumbnailUrl(record: DocumentFileInfo) {
  const key = itemKey(record);
  return key ? imageThumbnailUrls.value[key] : undefined;
}

function revokeImageThumbnailUrl(key: string) {
  const url = imageThumbnailUrls.value[key];
  if (url) {
    URL.revokeObjectURL(url);
  }
}

function revokeAllImageThumbnailUrls() {
  for (const key of Object.keys(imageThumbnailUrls.value)) {
    revokeImageThumbnailUrl(key);
  }
  imageThumbnailUrls.value = {};
}

async function loadImageThumbnails() {
  const version = ++imageThumbnailLoadVersion;
  const imageItems = sortedItems.value.filter((item) => item.id && isImageFile(item));
  const activeKeys = new Set(imageItems.map((item) => itemKey(item)).filter(Boolean));

  for (const key of Object.keys(imageThumbnailUrls.value)) {
    if (!activeKeys.has(key)) {
      revokeImageThumbnailUrl(key);
      delete imageThumbnailUrls.value[key];
    }
  }

  for (const item of imageItems) {
    const key = itemKey(item);
    if (!item.id || !key || imageThumbnailUrls.value[key]) {
      continue;
    }
    try {
      const blob = await downloadDocumentThumbnail(item.id);
      if (version !== imageThumbnailLoadVersion) {
        continue;
      }
      imageThumbnailUrls.value = {
        ...imageThumbnailUrls.value,
        [key]: URL.createObjectURL(blob),
      };
    } catch {
      // 缩略图加载失败时保留文件类型图标，避免影响文件列表使用。
    }
  }
}

function canMove(record: DocumentFileInfo) {
  return canMoveDocument(record, actionContext.value);
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

function handleDragStart(event: DragEvent, record: DocumentFileInfo) {
  if (!record.id || !canMove(record) || props.moving) {
    event.preventDefault();
    return;
  }
  if (!selectedIds.value.has(itemKey(record))) {
    selectedIds.value = new Set([itemKey(record)]);
  }
  const sourceIds = selectedIds.value.has(itemKey(record)) && selectedMovableIds.value.length > 0
    ? selectedMovableIds.value
    : [record.id];
  event.dataTransfer?.setData('application/x-document-id', sourceIds[0] || record.id);
  event.dataTransfer?.setData('application/x-document-ids', JSON.stringify(sourceIds));
  event.dataTransfer?.setData('text/plain', record.fileName || '');
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
  }
}

function isDocumentDrag(event: DragEvent) {
  const types = Array.from(event.dataTransfer?.types || []);
  return types.includes('application/x-document-id') || types.includes('application/x-document-ids');
}

function getDragSourceIds(event: DragEvent) {
  const rawIds = event.dataTransfer?.getData('application/x-document-ids');
  if (rawIds) {
    try {
      const parsed = JSON.parse(rawIds);
      if (Array.isArray(parsed)) {
        return parsed.filter((id): id is string => typeof id === 'string' && id.length > 0);
      }
    } catch {
      // Fallback to the single-item payload below.
    }
  }
  const sourceId = event.dataTransfer?.getData('application/x-document-id');
  return sourceId ? [sourceId] : [];
}

function handleFolderDragOver(event: DragEvent, target: DocumentFileInfo) {
  if (!canDropOnFolder(target)) {
    return;
  }
  if (!isDocumentDrag(event)) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
}

function handleDropOnFolder(event: DragEvent, target: DocumentFileInfo) {
  event.preventDefault();
  const sourceIds = getDragSourceIds(event).filter((sourceId) => sourceId !== target.id);
  if (sourceIds.length === 0 || !canDropOnFolder(target) || props.moving) {
    return;
  }
  if (sourceIds.length === 1) {
    const sourceId = sourceIds[0];
    if (!sourceId) {
      return;
    }
    emit('move', sourceId, target.id);
    return;
  }
  emit('batchMove', sourceIds, target.id);
}

function emitAction(event: string, record: DocumentFileInfo) {
  emit('action', event, record);
}

function clearRenameTimer() {
  if (renameTimer) {
    window.clearTimeout(renameTimer);
    renameTimer = undefined;
  }
}

function itemKey(record: DocumentFileInfo) {
  return record.id || record.fileName || '';
}

function selectRangeTo(record: DocumentFileInfo, append: boolean) {
  const key = itemKey(record);
  const keys = sortedItems.value.map((item) => itemKey(item));
  const targetIndex = keys.indexOf(key);
  const anchorKey = selectionAnchorKey.value || Array.from(selectedIds.value).at(-1) || key;
  const anchorIndex = keys.indexOf(anchorKey);
  if (targetIndex < 0 || anchorIndex < 0) {
    selectedIds.value = new Set([key]);
    selectionAnchorKey.value = key;
    return;
  }
  const startIndex = Math.min(anchorIndex, targetIndex);
  const endIndex = Math.max(anchorIndex, targetIndex);
  const nextSelected = append ? new Set(selectedIds.value) : new Set<string>();
  for (const rangeKey of keys.slice(startIndex, endIndex + 1)) {
    if (rangeKey) {
      nextSelected.add(rangeKey);
    }
  }
  selectedIds.value = nextSelected;
}

function toggleSelected(record: DocumentFileInfo) {
  const key = itemKey(record);
  const nextSelected = new Set(selectedIds.value);
  if (nextSelected.has(key)) {
    nextSelected.delete(key);
  } else if (key) {
    nextSelected.add(key);
  }
  selectedIds.value = nextSelected;
  selectionAnchorKey.value = key;
}

function confirmInlineEdit() {
  if (props.inlineEditor) {
    emit('inlineSubmit');
  }
}

function handleTileClick(event: MouseEvent, record: DocumentFileInfo) {
  if (props.inlineEditor && !isRenaming(record)) {
    confirmInlineEdit();
  }
  const key = itemKey(record);
  if (event.shiftKey) {
    clearRenameTimer();
    selectRangeTo(record, event.ctrlKey || event.metaKey);
    return;
  }
  if (event.ctrlKey || event.metaKey) {
    clearRenameTimer();
    toggleSelected(record);
    return;
  }
  const alreadySelected = selectedIds.value.size === 1 && selectedIds.value.has(key);
  clearRenameTimer();
  selectedIds.value = new Set([key]);
  selectionAnchorKey.value = key;
  if (!alreadySelected || !canEditItem(record) || isRenaming(record)) {
    return;
  }
  renameTimer = window.setTimeout(() => {
    emitAction('rename', record);
    renameTimer = undefined;
  }, 220);
}

function handleContextSelect(record: DocumentFileInfo) {
  clearRenameTimer();
  if (selectedIds.value.size > 1 && selectedIds.value.has(itemKey(record))) {
    return;
  }
  selectedIds.value = new Set([itemKey(record)]);
  selectionAnchorKey.value = itemKey(record);
}

function handleTileOpen(record: DocumentFileInfo) {
  clearRenameTimer();
  handleOpen(record);
}

function clearSelection() {
  clearRenameTimer();
  selectedIds.value = new Set();
  selectionAnchorKey.value = undefined;
}

function handleBodyClick() {
  confirmInlineEdit();
  if (suppressNextBodyClick.value) {
    suppressNextBodyClick.value = false;
    return;
  }
  clearSelection();
}

function isSelected(record: DocumentFileInfo) {
  return selectedIds.value.has(itemKey(record));
}

function isCutting(record: DocumentFileInfo) {
  return Boolean(record.id && cuttingIdSet.value.has(record.id));
}

function getContextRecords(record: DocumentFileInfo) {
  return selectedIds.value.has(itemKey(record)) && selectedRecords.value.length > 0
    ? selectedRecords.value
    : [record];
}

function getContextDownloadRecords(record: DocumentFileInfo) {
  return getContextRecords(record).filter((item) => item.canDownload && item.izFolder !== '1');
}

function getContextDeletableRecords(record: DocumentFileInfo) {
  return getContextRecords(record).filter(
    (item) => canEditItem(item) && props.scope !== 'trash' && props.scope !== 'business',
  );
}

function getContextCuttableRecords(record: DocumentFileInfo) {
  return getContextRecords(record).filter((item) => canMove(item));
}

function getContextCopyableRecords(record: DocumentFileInfo) {
  return getContextRecords(record).filter(
    (item) => item.id && props.scope !== 'trash' && !isVirtualBusinessItem(item),
  );
}

function isSingleContext(record: DocumentFileInfo) {
  return getContextRecords(record).length === 1;
}

function emitContextBatchAction(event: DocumentBatchAction, record: DocumentFileInfo) {
  const records =
    event === 'download'
      ? getContextDownloadRecords(record)
      : event === 'delete'
        ? getContextDeletableRecords(record)
        : event === 'cut'
          ? getContextCuttableRecords(record)
          : getContextCopyableRecords(record);
  emit('batchAction', event, records);
}

function isEditableShortcutTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tagName = target.tagName.toLowerCase();
  return (
    target.isContentEditable ||
    tagName === 'input' ||
    tagName === 'textarea' ||
    tagName === 'select' ||
    Boolean(target.closest('[contenteditable="true"], .ant-input, .ant-select'))
  );
}

function handleShortcutKeydown(event: KeyboardEvent) {
  if (props.loading || props.moving || isEditableShortcutTarget(event.target)) {
    return;
  }
  const key = event.key.toLowerCase();
  if (key === 'delete' || (event.metaKey && key === 'backspace')) {
    const records = selectedRecords.value.filter(
      (item) => canEditItem(item) && props.scope !== 'trash' && props.scope !== 'business',
    );
    if (records.length === 0) {
      return;
    }
    event.preventDefault();
    emit('batchAction', 'delete', records);
    return;
  }
  const shortcutPressed = event.ctrlKey || event.metaKey;
  if (!shortcutPressed || event.altKey) {
    return;
  }
  if (key === 'x') {
    const records = selectedRecords.value.filter((item) => canMove(item));
    if (records.length === 0) {
      return;
    }
    event.preventDefault();
    emit('batchAction', 'cut', records);
    return;
  }
  if (key === 'c') {
    const records = selectedRecords.value.filter(
      (item) => item.id && props.scope !== 'trash' && !isVirtualBusinessItem(item),
    );
    if (records.length === 0) {
      return;
    }
    event.preventDefault();
    emit('batchAction', 'copy', records);
    return;
  }
  if (key === 'a') {
    const keys = sortedItems.value.map((item) => itemKey(item)).filter(Boolean);
    if (keys.length === 0) {
      return;
    }
    event.preventDefault();
    selectedIds.value = new Set(keys);
    selectionAnchorKey.value = keys.at(-1);
    return;
  }
  if (key === 'v' && props.canPaste) {
    event.preventDefault();
    emit('paste');
  }
}

function toBodyPoint(event: MouseEvent) {
  const body = explorerBodyRef.value;
  if (!body) {
    return { x: 0, y: 0 };
  }
  const rect = body.getBoundingClientRect();
  return {
    x: event.clientX - rect.left + body.scrollLeft,
    y: event.clientY - rect.top + body.scrollTop,
  };
}

function isSelectionIgnoredTarget(target: EventTarget | null) {
  if (!(target instanceof Element)) {
    return true;
  }
  return Boolean(
    target.closest(
      '.document-explorer-item, button, input, textarea, .ant-dropdown, .ant-dropdown-menu',
    ),
  );
}

function handleSelectionMouseDown(event: MouseEvent) {
  if (event.button !== 0 || props.loading || props.moving || isSelectionIgnoredTarget(event.target)) {
    return;
  }
  if (props.inlineEditor) {
    event.preventDefault();
    confirmInlineEdit();
    clearSelection();
    return;
  }
  event.preventDefault();
  clearRenameTimer();
  const point = toBodyPoint(event);
  selectionBox.value = {
    currentX: point.x,
    currentY: point.y,
    startX: point.x,
    startY: point.y,
  };
  selectedIds.value = new Set();
  selectionAnchorKey.value = undefined;
  selectionMoved.value = false;
  selecting.value = true;
  window.addEventListener('mousemove', handleSelectionMouseMove);
  window.addEventListener('mouseup', handleSelectionMouseUp);
}

function handleSelectionMouseMove(event: MouseEvent) {
  if (!selecting.value) {
    return;
  }
  const point = toBodyPoint(event);
  selectionBox.value = {
    ...selectionBox.value,
    currentX: point.x,
    currentY: point.y,
  };
  if (
    Math.abs(selectionBox.value.currentX - selectionBox.value.startX) > 3 ||
    Math.abs(selectionBox.value.currentY - selectionBox.value.startY) > 3
  ) {
    selectionMoved.value = true;
  }
  updateSelectionByBox();
}

function handleSelectionMouseUp() {
  if (!selecting.value) {
    return;
  }
  selecting.value = false;
  suppressNextBodyClick.value = selectionMoved.value;
  window.removeEventListener('mousemove', handleSelectionMouseMove);
  window.removeEventListener('mouseup', handleSelectionMouseUp);
}

function updateSelectionByBox() {
  const body = explorerBodyRef.value;
  if (!body) {
    return;
  }
  const bodyRect = body.getBoundingClientRect();
  const left = Math.min(selectionBox.value.startX, selectionBox.value.currentX);
  const top = Math.min(selectionBox.value.startY, selectionBox.value.currentY);
  const right = Math.max(selectionBox.value.startX, selectionBox.value.currentX);
  const bottom = Math.max(selectionBox.value.startY, selectionBox.value.currentY);
  const nextSelected = new Set<string>();
  for (const tile of body.querySelectorAll<HTMLElement>('.document-explorer-item[data-document-id]')) {
    const tileRect = tile.getBoundingClientRect();
    const tileLeft = tileRect.left - bodyRect.left + body.scrollLeft;
    const tileTop = tileRect.top - bodyRect.top + body.scrollTop;
    const tileRight = tileLeft + tileRect.width;
    const tileBottom = tileTop + tileRect.height;
    const intersects = tileLeft <= right && tileRight >= left && tileTop <= bottom && tileBottom >= top;
    const id = tile.dataset.documentId;
    if (intersects && id) {
      nextSelected.add(id);
    }
  }
  selectedIds.value = nextSelected;
  selectionAnchorKey.value = Array.from(nextSelected).at(-1);
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

function handleInlineKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('inlineCancel');
  }
}

watch(
  () =>
    sortedItems.value
      .filter((item) => item.id && isImageFile(item))
      .map((item) => itemKey(item))
      .join(','),
  () => {
    void loadImageThumbnails();
  },
  { immediate: true },
);

onMounted(() => {
  window.addEventListener('keydown', handleShortcutKeydown);
});

onBeforeUnmount(() => {
  imageThumbnailLoadVersion += 1;
  revokeAllImageThumbnailUrls();
  clearRenameTimer();
  window.removeEventListener('keydown', handleShortcutKeydown);
  window.removeEventListener('mousemove', handleSelectionMouseMove);
  window.removeEventListener('mouseup', handleSelectionMouseUp);
});
</script>

<template>
  <div class="document-explorer">
    <Dropdown :trigger="['contextmenu']">
      <div
        ref="explorerBodyRef"
        class="document-explorer__body"
        :class="{ 'document-explorer__body--selecting': selecting }"
        @click="handleBodyClick"
        @mousedown="handleSelectionMouseDown"
      >
        <Spin :spinning="loading || moving">
          <div v-if="hasGridContent && viewMode === 'grid'" class="document-grid">
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
                  ref="createNameInputRef"
                  :value="inlineEditor?.fileName"
                  autofocus
                  class="document-tile__name-input"
                  :disabled="savingName"
                  :maxlength="255"
                  @blur="$emit('inlineSubmit')"
                  @click.stop
                  @keydown="handleInlineKeydown"
                  @press-enter="$emit('inlineSubmit')"
                  @update:value="handleNameInput"
                />
                <div class="document-tile__meta">
                  <span>文件夹</span>
                </div>
              </div>
            </div>
            <Dropdown
              v-for="item in sortedItems"
              :key="item.id"
              :trigger="['contextmenu']"
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
                @click.stop="handleTileClick($event, item)"
                @contextmenu.stop="handleContextSelect(item)"
                @dblclick.stop="handleTileOpen(item)"
                @dragstart="handleDragStart($event, item)"
                @dragover="handleFolderDragOver($event, item)"
                @drop="handleDropOnFolder($event, item)"
                @keydown.enter="handleOpen(item)"
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
                  <div
                    v-if="isRenaming(item)"
                    class="document-tile__rename-editor"
                    @click.stop
                  >
                    <Input.TextArea
                      ref="renameNameInputRef"
                      :value="inlineEditor?.fileName"
                      autofocus
                      class="document-tile__rename-textarea"
                      :disabled="savingName"
                      :maxlength="255"
                      :rows="3"
                      @blur="$emit('inlineSubmit')"
                      @keydown="handleInlineKeydown"
                      @keydown.enter.stop.prevent="$emit('inlineSubmit')"
                      @update:value="handleNameInput"
                    />
                    <span
                      v-if="inlineEditor?.extension"
                      class="document-tile__rename-extension"
                    >
                      {{ inlineEditor.extension }}
                    </span>
                  </div>
                  <Tooltip v-else :title="item.fileName">
                    <div class="document-tile__name">{{ item.fileName || '-' }}</div>
                  </Tooltip>
                  <div class="document-tile__meta">
                    <span>{{ fileTypeText(item) }}</span>
                    <span v-if="formatSize(item.fileSize)">{{ formatSize(item.fileSize) }}</span>
                  </div>
                </div>
                <Dropdown trigger="click">
                  <Button class="document-tile__more" size="small" type="text" @click.stop>
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
                      @action="emitAction"
                      @batch-action="emitContextBatchAction($event, item)"
                    />
                  </template>
                </Dropdown>
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
                  @action="emitAction"
                  @batch-action="emitContextBatchAction($event, item)"
                />
              </template>
            </Dropdown>
          </div>
          <div v-else-if="hasGridContent" class="document-list">
            <div class="document-list__header">
              <button
                v-for="column in documentListColumns"
                :key="column.field"
                class="document-list__cell document-list__sort"
                :class="column.className"
                type="button"
                @click="setSort(column.field)"
              >
                <span>{{ column.label }}</span>
                <IconifyIcon
                  class="document-list__sort-icon"
                  :class="{ 'document-list__sort-icon--active': isActiveSort(column.field) }"
                  :icon="sortDirectionIcon(column.field)"
                />
              </button>
              <div class="document-list__cell document-list__cell--actions"></div>
            </div>
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
                  @update:value="handleNameInput"
                />
              </div>
              <div class="document-list__cell document-list__cell--type">文件夹</div>
              <div class="document-list__cell document-list__cell--size">-</div>
              <div class="document-list__cell document-list__cell--time">-</div>
              <div class="document-list__cell document-list__cell--actions"></div>
            </div>
            <Dropdown
              v-for="item in sortedItems"
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
                @click.stop="handleTileClick($event, item)"
                @contextmenu.stop="handleContextSelect(item)"
                @dblclick.stop="handleTileOpen(item)"
                @dragstart="handleDragStart($event, item)"
                @dragover="handleFolderDragOver($event, item)"
                @drop="handleDropOnFolder($event, item)"
                @keydown.enter="handleOpen(item)"
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
                  <div
                    v-if="isRenaming(item)"
                    class="document-list-row__rename-editor"
                    @click.stop
                  >
                    <Input.TextArea
                      ref="renameNameInputRef"
                      :value="inlineEditor?.fileName"
                      autofocus
                      class="document-list-row__rename-textarea"
                      :disabled="savingName"
                      :maxlength="255"
                      :rows="1"
                      @blur="$emit('inlineSubmit')"
                      @keydown="handleInlineKeydown"
                      @keydown.enter.stop.prevent="$emit('inlineSubmit')"
                      @update:value="handleNameInput"
                    />
                    <span
                      v-if="inlineEditor?.extension"
                      class="document-list-row__rename-extension"
                    >
                      {{ inlineEditor.extension }}
                    </span>
                  </div>
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
                        @action="emitAction"
                        @batch-action="emitContextBatchAction($event, item)"
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
                  @action="emitAction"
                  @batch-action="emitContextBatchAction($event, item)"
                />
              </template>
            </Dropdown>
          </div>
          <Empty v-else class="document-empty" description="当前文件夹暂无内容" />
        </Spin>
        <div
          v-if="selecting"
          class="document-selection-box"
          :style="selectionBoxStyle"
        />
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
          <Menu.Divider />
          <Menu.Item v-if="canPaste" @click="$emit('paste')">
            <IconifyIcon class="document-menu-icon" icon="lucide:clipboard-paste" />
            粘贴
          </Menu.Item>
          <Menu.Item v-if="canUploadInScope" @click="$emit('upload')">
            <IconifyIcon class="document-menu-icon" icon="lucide:upload" />
            上传文件
          </Menu.Item>
          <Menu.Item v-if="canCreateInScope" @click="$emit('createFolder')">
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

.document-selection-box {
  position: absolute;
  z-index: 5;
  pointer-events: none;
  border: 1px solid hsl(var(--primary) / 70%);
  background: hsl(var(--primary) / 12%);
}

.document-grid {
  display: grid;
  overflow: auto;
  height: 100%;
  align-content: start;
  grid-template-columns: repeat(auto-fill, minmax(124px, 1fr));
  gap: 12px;
  padding: 2px;
}

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

.document-list__header,
.document-list-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 120px 100px 150px 116px;
  align-items: center;
  min-width: 720px;
}

.document-list__header {
  position: sticky;
  top: 0;
  z-index: 1;
  min-height: 36px;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--muted) / 45%);
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  font-weight: 500;
}

.document-list__sort {
  display: inline-flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: center;
}

.document-list__sort:hover,
.document-list__sort:focus-visible {
  background: hsl(var(--muted) / 70%);
  color: hsl(var(--foreground));
  outline: none;
}

.document-list__sort-icon {
  width: 14px;
  height: 14px;
  color: hsl(var(--muted-foreground));
}

.document-list__sort-icon--active {
  color: hsl(var(--primary));
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

.document-list__header .document-list__cell {
  justify-content: center;
  color: hsl(var(--muted-foreground));
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

.document-list-row__rename-editor {
  display: flex;
  width: min(420px, 100%);
  min-width: 0;
  align-items: stretch;
  border: 1px solid hsl(var(--primary) / 70%);
  border-radius: 6px;
  background: hsl(var(--background));
  box-shadow: 0 0 0 2px hsl(var(--primary) / 12%);
}

.document-list-row__rename-textarea {
  flex: 1 1 auto;
  min-width: 0;
}

.document-list-row__rename-textarea :deep(textarea.ant-input) {
  height: 28px;
  min-height: 28px;
  max-height: 28px;
  resize: none;
  border: 0;
  box-shadow: none;
  color: hsl(var(--foreground));
  font-size: 13px;
  line-height: 20px;
  overflow: hidden;
  overflow-wrap: normal;
  white-space: nowrap;
}

.document-list-row__rename-textarea :deep(textarea.ant-input:focus) {
  box-shadow: none;
}

.document-list-row__rename-extension {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  border-left: 1px solid hsl(var(--border));
  padding: 0 6px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}

.document-list-row__more {
  width: 26px;
  height: 26px;
  padding: 0;
  color: hsl(var(--muted-foreground));
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

.document-tile__rename-editor {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: stretch;
  border: 1px solid hsl(var(--primary) / 70%);
  border-radius: 6px;
  background: hsl(var(--background));
  box-shadow: 0 0 0 2px hsl(var(--primary) / 12%);
  text-align: left;
}

.document-tile__rename-textarea {
  flex: 1 1 auto;
  min-width: 0;
}

.document-tile__rename-textarea :deep(textarea.ant-input) {
  height: 60px;
  min-height: 60px;
  max-height: 60px;
  resize: none;
  border: 0;
  box-shadow: none;
  color: hsl(var(--foreground));
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.document-tile__rename-textarea :deep(textarea.ant-input:focus) {
  box-shadow: none;
}

.document-tile__rename-extension {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  border-left: 1px solid hsl(var(--border));
  padding: 0 6px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
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

.document-empty {
  padding: 56px 0;
}

@media (max-width: 768px) {
  .document-grid {
    grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
  }
}
</style>
