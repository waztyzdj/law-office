<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Dropdown, Empty, Input, Menu, Spin, Tooltip } from 'ant-design-vue';

const BUSINESS_VIEW_STORE_TYPE = 'business_view';
const BUSINESS_MODULE_VIEW_STORE_TYPE = 'business_module_view';
const BUSINESS_RECORD_VIEW_STORE_TYPE = 'business_record_view';

interface InlineEditorState {
  extension?: string;
  fileName: string;
  mode: 'create' | 'rename';
  parentId?: string;
  record?: DocumentFileInfo;
}

type BatchMenuAction = 'copy' | 'cut' | 'delete' | 'download';
type DocumentViewMode = 'grid' | 'list';

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
  batchAction: [event: BatchMenuAction, records: DocumentFileInfo[]];
  batchMove: [sourceIds: string[], targetParentId?: string];
  createFolder: [];
  createFolderIn: [record: DocumentFileInfo];
  inlineCancel: [];
  inlineChange: [value: string];
  inlineSubmit: [];
  move: [sourceId: string, targetParentId?: string];
  paste: [];
  upload: [];
}>();

const sortedItems = computed(() =>
  [...props.dataSource].sort((a, b) => {
    const folderWeight = Number(b.izFolder === '1') - Number(a.izFolder === '1');
    if (folderWeight !== 0) {
      return folderWeight;
    }
    return String(a.fileName || '').localeCompare(String(b.fileName || ''), 'zh-CN');
  }),
);

const canCreateInScope = computed(() => props.canCreate);
const canUploadInScope = computed(() => props.canUpload);
const canRenameCurrentFolder = computed(
  () => props.scope !== 'trash' && Boolean(props.currentFolder?.ownerFlag),
);
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

function formatSize(size?: number) {
  if (!size || size <= 0) {
    return '';
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${Math.round(size)} B`;
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function fileIcon(record: DocumentFileInfo) {
  if (record.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE) {
    return 'lucide:briefcase-business';
  }
  if (record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE) {
    return 'lucide:database';
  }
  if (record.izFolder === '1') {
    return 'lucide:folder';
  }
  const type = String(record.fileType || '').toLowerCase();
  if (type === 'image') {
    return 'lucide:file-image';
  }
  if (type === 'pdf') {
    return 'lucide:file-text';
  }
  if (type === 'excel') {
    return 'lucide:file-spreadsheet';
  }
  if (type === 'ppt') {
    return 'lucide:file-type-2';
  }
  if (type === 'archive') {
    return 'lucide:file-archive';
  }
  if (type === 'video') {
    return 'lucide:file-video';
  }
  return 'lucide:file';
}

function fileTypeText(record: DocumentFileInfo) {
  if (record.izFolder === '1') {
    return '文件夹';
  }
  const typeText: Record<string, string> = {
    archive: '压缩包',
    doc: 'Word',
    excel: 'Excel',
    image: '图片',
    pdf: 'PDF',
    ppt: 'PPT',
    video: '视频',
  };
  return typeText[String(record.fileType)] || record.fileType || '文件';
}

function canMove(record: DocumentFileInfo) {
  if (props.scope === 'business') {
    return record.izFolder === '1'
      ? canEditItem(record)
      : Boolean(record.id);
  }
  return (
    props.scope !== 'trash' &&
    Boolean(record.id) &&
    (Boolean(record.ownerFlag) || props.personalizeShared)
  );
}

function canEditItem(record: DocumentFileInfo) {
  if (props.scope === 'business') {
    return (
      Boolean(record.ownerFlag) &&
      record.izFolder === '1' &&
      record.storeType === BUSINESS_VIEW_STORE_TYPE
    );
  }
  return props.scope !== 'trash' && Boolean(record.ownerFlag);
}

function canCreateFolderInItem(record: DocumentFileInfo) {
  if (record.izFolder !== '1' || !record.id) {
    return false;
  }
  if (props.scope === 'business') {
    return (
      record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE ||
      (Boolean(record.ownerFlag) && record.storeType === BUSINESS_VIEW_STORE_TYPE)
    );
  }
  return canEditItem(record);
}

function canDropOnFolder(target: DocumentFileInfo) {
  if (props.scope === 'trash' || target.izFolder !== '1' || !target.id) {
    return false;
  }
  if (props.personalizeShared) {
    return Boolean(target.ownerFlag) && target.storeType === 'shared_view';
  }
  if (props.scope === 'business') {
    return (
      target.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE ||
      (Boolean(target.ownerFlag) && target.storeType === BUSINESS_VIEW_STORE_TYPE)
    );
  }
  return Boolean(target.ownerFlag);
}

function handleOpen(record: DocumentFileInfo) {
  if (record.izFolder === '1') {
    emit('action', 'open', record);
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

function isVirtualBusinessItem(record: DocumentFileInfo) {
  return (
    record.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE ||
    record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE
  );
}

function getContextCopyableRecords(record: DocumentFileInfo) {
  return getContextRecords(record).filter(
    (item) => item.id && props.scope !== 'trash' && !isVirtualBusinessItem(item),
  );
}

function isSingleContext(record: DocumentFileInfo) {
  return getContextRecords(record).length === 1;
}

function emitContextBatchAction(event: BatchMenuAction, record: DocumentFileInfo) {
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

onMounted(() => {
  window.addEventListener('keydown', handleShortcutKeydown);
});

onBeforeUnmount(() => {
  clearRenameTimer();
  window.removeEventListener('keydown', handleShortcutKeydown);
  window.removeEventListener('mousemove', handleSelectionMouseMove);
  window.removeEventListener('mouseup', handleSelectionMouseUp);
});
</script>

<template>
  <div class="document-explorer">
    <Dropdown
      :disabled="!canCreateInScope && !canUploadInScope && !canPaste && !canRenameCurrentFolder"
      :trigger="['contextmenu']"
    >
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
                  <IconifyIcon
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
                    <Menu>
                      <Menu.Item v-if="item.izFolder === '1'" @click="emitAction('open', item)">
                        <IconifyIcon class="document-menu-icon" icon="lucide:folder-open" />
                        打开
                      </Menu.Item>
                      <Menu.Item
                        v-if="canCreateFolderInItem(item)"
                        @click="$emit('createFolderIn', item)"
                      >
                        <IconifyIcon class="document-menu-icon" icon="lucide:folder-plus" />
                        新建文件夹
                      </Menu.Item>
                      <Menu.Item
                        v-if="item.canDownload && item.izFolder !== '1'"
                        @click="emitAction('download', item)"
                      >
                        <IconifyIcon class="document-menu-icon" icon="lucide:download" />
                        下载
                      </Menu.Item>
                      <Menu.Item v-if="canEditItem(item)" @click="emitAction('star', item)">
                        <IconifyIcon
                          class="document-menu-icon"
                          :class="{ 'document-menu-icon--starred': item.izStar === '1' }"
                          icon="lucide:star"
                        />
                        {{ item.izStar === '1' ? '取消收藏' : '收藏' }}
                      </Menu.Item>
                      <Menu.Item
                        v-if="canEditItem(item)"
                        @click="emitAction(item.sharedFlag ? 'cancelShare' : 'share', item)"
                      >
                        <IconifyIcon
                          class="document-menu-icon"
                          :class="{ 'document-menu-icon--active': item.sharedFlag }"
                          icon="lucide:share-2"
                        />
                        {{ item.sharedFlag ? '取消共享' : '共享' }}
                      </Menu.Item>
                      <Menu.Item v-if="canEditItem(item)" @click="emitAction('rename', item)">
                        <IconifyIcon class="document-menu-icon" icon="lucide:pencil" />
                        重命名
                      </Menu.Item>
                      <Menu.Item v-if="scope === 'trash'" @click="emitAction('restore', item)">
                        <IconifyIcon class="document-menu-icon" icon="lucide:rotate-ccw" />
                        恢复
                      </Menu.Item>
                      <Menu.Item v-if="scope === 'trash' && item.ownerFlag" danger @click="emitAction('purge', item)">
                        <IconifyIcon class="document-menu-icon" icon="lucide:trash" />
                        彻底删除
                      </Menu.Item>
                      <Menu.Item
                        v-if="canEditItem(item) && scope !== 'trash' && scope !== 'business'"
                        danger
                        @click="emitAction('delete', item)"
                      >
                        <IconifyIcon class="document-menu-icon" icon="lucide:trash-2" />
                        删除
                      </Menu.Item>
                    </Menu>
                  </template>
                </Dropdown>
              </div>

              <template #overlay>
                <Menu>
                  <Menu.Item
                    :disabled="getContextCopyableRecords(item).length === 0"
                    @click="emitContextBatchAction('copy', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:copy" />
                    复制
                  </Menu.Item>
                  <Menu.Item
                    :disabled="getContextCuttableRecords(item).length === 0"
                    @click="emitContextBatchAction('cut', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:scissors" />
                    剪切
                  </Menu.Item>
                  <Menu.Item
                    :disabled="getContextDownloadRecords(item).length === 0"
                    @click="emitContextBatchAction('download', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:download" />
                    下载
                  </Menu.Item>
                  <Menu.Item
                    v-if="isSingleContext(item) && canEditItem(item)"
                    @click="emitAction('rename', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:pencil" />
                    重命名
                  </Menu.Item>
                  <Menu.Item
                    :disabled="getContextDeletableRecords(item).length === 0"
                    danger
                    @click="emitContextBatchAction('delete', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:trash-2" />
                    删除
                  </Menu.Item>
                </Menu>
              </template>
            </Dropdown>
          </div>
          <div v-else-if="hasGridContent" class="document-list">
            <div class="document-list__header">
              <div class="document-list__cell document-list__cell--name">名称</div>
              <div class="document-list__cell document-list__cell--type">类型</div>
              <div class="document-list__cell document-list__cell--size">大小</div>
              <div class="document-list__cell document-list__cell--time">修改时间</div>
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
                  <IconifyIcon
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
                      <Menu>
                        <Menu.Item v-if="item.izFolder === '1'" @click="emitAction('open', item)">
                          <IconifyIcon class="document-menu-icon" icon="lucide:folder-open" />
                          打开
                        </Menu.Item>
                        <Menu.Item
                          v-if="canCreateFolderInItem(item)"
                          @click="$emit('createFolderIn', item)"
                        >
                          <IconifyIcon class="document-menu-icon" icon="lucide:folder-plus" />
                          新建文件夹
                        </Menu.Item>
                        <Menu.Item
                          v-if="item.canDownload && item.izFolder !== '1'"
                          @click="emitAction('download', item)"
                        >
                          <IconifyIcon class="document-menu-icon" icon="lucide:download" />
                          下载
                        </Menu.Item>
                        <Menu.Item v-if="canEditItem(item)" @click="emitAction('star', item)">
                          <IconifyIcon
                            class="document-menu-icon"
                            :class="{ 'document-menu-icon--starred': item.izStar === '1' }"
                            icon="lucide:star"
                          />
                          {{ item.izStar === '1' ? '取消收藏' : '收藏' }}
                        </Menu.Item>
                        <Menu.Item
                          v-if="canEditItem(item)"
                          @click="emitAction(item.sharedFlag ? 'cancelShare' : 'share', item)"
                        >
                          <IconifyIcon
                            class="document-menu-icon"
                            :class="{ 'document-menu-icon--active': item.sharedFlag }"
                            icon="lucide:share-2"
                          />
                          {{ item.sharedFlag ? '取消共享' : '共享' }}
                        </Menu.Item>
                        <Menu.Item v-if="canEditItem(item)" @click="emitAction('rename', item)">
                          <IconifyIcon class="document-menu-icon" icon="lucide:pencil" />
                          重命名
                        </Menu.Item>
                        <Menu.Item v-if="scope === 'trash'" @click="emitAction('restore', item)">
                          <IconifyIcon class="document-menu-icon" icon="lucide:rotate-ccw" />
                          恢复
                        </Menu.Item>
                        <Menu.Item v-if="scope === 'trash' && item.ownerFlag" danger @click="emitAction('purge', item)">
                          <IconifyIcon class="document-menu-icon" icon="lucide:trash" />
                          彻底删除
                        </Menu.Item>
                        <Menu.Item
                          v-if="canEditItem(item) && scope !== 'trash' && scope !== 'business'"
                          danger
                          @click="emitAction('delete', item)"
                        >
                          <IconifyIcon class="document-menu-icon" icon="lucide:trash-2" />
                          删除
                        </Menu.Item>
                      </Menu>
                    </template>
                  </Dropdown>
                </div>
              </div>

              <template #overlay>
                <Menu>
                  <Menu.Item
                    :disabled="getContextCopyableRecords(item).length === 0"
                    @click="emitContextBatchAction('copy', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:copy" />
                    复制
                  </Menu.Item>
                  <Menu.Item
                    :disabled="getContextCuttableRecords(item).length === 0"
                    @click="emitContextBatchAction('cut', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:scissors" />
                    剪切
                  </Menu.Item>
                  <Menu.Item
                    :disabled="getContextDownloadRecords(item).length === 0"
                    @click="emitContextBatchAction('download', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:download" />
                    下载
                  </Menu.Item>
                  <Menu.Item
                    v-if="isSingleContext(item) && canEditItem(item)"
                    @click="emitAction('rename', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:pencil" />
                    重命名
                  </Menu.Item>
                  <Menu.Item
                    :disabled="getContextDeletableRecords(item).length === 0"
                    danger
                    @click="emitContextBatchAction('delete', item)"
                  >
                    <IconifyIcon class="document-menu-icon" icon="lucide:trash-2" />
                    删除
                  </Menu.Item>
                </Menu>
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
          <Menu.Item
            v-if="canRenameCurrentFolder && currentFolder"
            @click="emitAction('rename', currentFolder)"
          >
            <IconifyIcon class="document-menu-icon" icon="lucide:pencil" />
            修改文件夹名称
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

.document-explorer {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.document-explorer__body {
  position: relative;
  flex: 1;
  min-height: 220px;
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
  grid-template-columns: repeat(auto-fill, minmax(124px, 1fr));
  gap: 12px;
  padding: 2px;
}

.document-list {
  display: flex;
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
  min-height: 36px;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--muted) / 45%);
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  font-weight: 500;
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
