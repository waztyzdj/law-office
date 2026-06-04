import type { ComputedRef, Ref } from 'vue';
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type { DocumentBatchAction, InlineEditorState } from '../types';

import { computed, ref } from 'vue';

import { isVirtualBusinessItem } from '../components/documentExplorerUtils';

interface SelectionBoxState {
  currentX: number;
  currentY: number;
  startX: number;
  startY: number;
}

interface UseDocumentSelectionOptions {
  canEditItem: (record: DocumentFileInfo) => boolean;
  canMove: (record: DocumentFileInfo) => boolean;
  canPaste: Readonly<Ref<boolean>>;
  confirmInlineEdit: () => void;
  emitBatchAction: (event: DocumentBatchAction, records: DocumentFileInfo[]) => void;
  emitPaste: () => void;
  inlineEditor: Readonly<Ref<InlineEditorState | undefined>>;
  isRenaming: (record: DocumentFileInfo) => boolean;
  loading: Readonly<Ref<boolean>>;
  moving: Readonly<Ref<boolean>>;
  openRename: (record: DocumentFileInfo) => void;
  scope: Readonly<Ref<DocumentScope>>;
  sortedItems: ComputedRef<DocumentFileInfo[]>;
}

export function useDocumentSelection(options: UseDocumentSelectionOptions) {
  const explorerBodyRef = ref<HTMLElement>();
  const selectedIds = ref<Set<string>>(new Set());
  const selectionAnchorKey = ref<string>();
  const selecting = ref(false);
  const selectionMoved = ref(false);
  const suppressNextBodyClick = ref(false);
  const selectionBox = ref<SelectionBoxState>({
    currentX: 0,
    currentY: 0,
    startX: 0,
    startY: 0,
  });
  let renameTimer: number | undefined;

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
    options.sortedItems.value.filter((item) => selectedIds.value.has(itemKey(item))),
  );

  const selectedMovableIds = computed(() =>
    selectedRecords.value
      .filter((item) => item.id && options.canMove(item))
      .map((item) => item.id || ''),
  );

  function clearRenameTimer() {
    if (renameTimer) {
      window.clearTimeout(renameTimer);
      renameTimer = undefined;
    }
  }

  function itemKey(record: DocumentFileInfo) {
    return record.id || record.fileName || '';
  }

  function selectOnly(record: DocumentFileInfo) {
    const key = itemKey(record);
    selectedIds.value = key ? new Set([key]) : new Set();
    selectionAnchorKey.value = key || undefined;
  }

  function selectRangeTo(record: DocumentFileInfo, append: boolean) {
    const key = itemKey(record);
    const keys = options.sortedItems.value.map((item) => itemKey(item));
    const targetIndex = keys.indexOf(key);
    const anchorKey = selectionAnchorKey.value || Array.from(selectedIds.value).at(-1) || key;
    const anchorIndex = keys.indexOf(anchorKey);
    if (targetIndex < 0 || anchorIndex < 0) {
      selectOnly(record);
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

  function handleItemClick(event: MouseEvent, record: DocumentFileInfo) {
    if (options.inlineEditor.value && !options.isRenaming(record)) {
      options.confirmInlineEdit();
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
    selectOnly(record);
    if (!alreadySelected || !options.canEditItem(record) || options.isRenaming(record)) {
      return;
    }
    renameTimer = window.setTimeout(() => {
      options.openRename(record);
      renameTimer = undefined;
    }, 220);
  }

  function handleContextSelect(record: DocumentFileInfo) {
    clearRenameTimer();
    if (selectedIds.value.size > 1 && selectedIds.value.has(itemKey(record))) {
      return;
    }
    selectOnly(record);
  }

  function clearSelection() {
    clearRenameTimer();
    selectedIds.value = new Set();
    selectionAnchorKey.value = undefined;
  }

  function handleBodyClick() {
    options.confirmInlineEdit();
    if (suppressNextBodyClick.value) {
      suppressNextBodyClick.value = false;
      return;
    }
    clearSelection();
  }

  function isSelected(record: DocumentFileInfo) {
    return selectedIds.value.has(itemKey(record));
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
      (item) => options.canEditItem(item) && options.scope.value !== 'trash' && options.scope.value !== 'business',
    );
  }

  function getContextCuttableRecords(record: DocumentFileInfo) {
    return getContextRecords(record).filter((item) => options.canMove(item));
  }

  function getContextCopyableRecords(record: DocumentFileInfo) {
    return getContextRecords(record).filter(
      (item) => item.id && options.scope.value !== 'trash' && !isVirtualBusinessItem(item),
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
    options.emitBatchAction(event, records);
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
    if (options.loading.value || options.moving.value || isEditableShortcutTarget(event.target)) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === 'delete' || (event.metaKey && key === 'backspace')) {
      const records = selectedRecords.value.filter(
        (item) => options.canEditItem(item) && options.scope.value !== 'trash' && options.scope.value !== 'business',
      );
      if (records.length === 0) {
        return;
      }
      event.preventDefault();
      options.emitBatchAction('delete', records);
      return;
    }
    const shortcutPressed = event.ctrlKey || event.metaKey;
    if (!shortcutPressed || event.altKey) {
      return;
    }
    if (key === 'x') {
      const records = selectedRecords.value.filter((item) => options.canMove(item));
      if (records.length === 0) {
        return;
      }
      event.preventDefault();
      options.emitBatchAction('cut', records);
      return;
    }
    if (key === 'c') {
      const records = selectedRecords.value.filter(
        (item) => item.id && options.scope.value !== 'trash' && !isVirtualBusinessItem(item),
      );
      if (records.length === 0) {
        return;
      }
      event.preventDefault();
      options.emitBatchAction('copy', records);
      return;
    }
    if (key === 'a') {
      const keys = options.sortedItems.value.map((item) => itemKey(item)).filter(Boolean);
      if (keys.length === 0) {
        return;
      }
      event.preventDefault();
      selectedIds.value = new Set(keys);
      selectionAnchorKey.value = keys.at(-1);
      return;
    }
    if (key === 'v' && options.canPaste.value) {
      event.preventDefault();
      options.emitPaste();
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
    if (event.button !== 0 || options.loading.value || options.moving.value || isSelectionIgnoredTarget(event.target)) {
      return;
    }
    if (options.inlineEditor.value) {
      event.preventDefault();
      options.confirmInlineEdit();
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

  function cleanupSelectionListeners() {
    clearRenameTimer();
    window.removeEventListener('mousemove', handleSelectionMouseMove);
    window.removeEventListener('mouseup', handleSelectionMouseUp);
  }

  return {
    clearRenameTimer,
    clearSelection,
    emitContextBatchAction,
    explorerBodyRef,
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
    selectedIds,
    selectedMovableIds,
    selectedRecords,
    selecting,
    selectionBoxStyle,
  };
}
