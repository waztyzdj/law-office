import type { ComputedRef, Ref } from 'vue';
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type { DocumentBatchAction, InlineEditorState, ScopeOption } from '../types';

import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  canMove as canMoveDocument,
  isActualSharedItem,
  isActualStarredItem,
  isSharedReadonlyScope,
  isVirtualBusinessItem,
} from '../components/documentExplorerUtils';
import {
  getScopeRootKey,
  isScopeRootKey,
} from '../tree';

interface CachedFolderPath {
  path: DocumentFileInfo[];
  rootKey: string;
}

interface DocumentActionContext {
  personalizeShared: boolean;
  scope: DocumentScope;
}

interface UseDocumentTreeInteractionsOptions {
  activeRootKey: Ref<string>;
  canCreateInsideFolder: (record?: DocumentFileInfo) => boolean;
  canManageFolder: (record?: DocumentFileInfo) => boolean;
  documentActionContext: ComputedRef<DocumentActionContext>;
  ensureFolderTreePathLoaded: (rootKey: string, path: DocumentFileInfo[]) => Promise<void>;
  expandPathKeys: (path: DocumentFileInfo[]) => void;
  findCachedPath: (key: string) => CachedFolderPath | undefined;
  findFolderByKey: (key: string) => DocumentFileInfo | undefined;
  getRootKeyFromFolderNodeKey: (key: string) => string;
  getScopeOptionByRootKey: (rootKey: string) => ScopeOption | undefined;
  getScopeByRootKey: (rootKey: string) => DocumentScope | undefined;
  getActiveSelectedTreeKey: () => string;
  handleBatchAction: (event: DocumentBatchAction, records: DocumentFileInfo[]) => void;
  handleCreateFolder: (parentId?: string) => void;
  handleDeleteFolder: (record?: DocumentFileInfo) => void;
  handlePasteToTreeFolder: (record?: DocumentFileInfo) => void;
  handleRenameFolder: (record?: DocumentFileInfo) => void;
  handleTreeMove: (
    sourceIds: string[],
    sourceParentIds: Array<string | undefined>,
    targetParentId?: string,
  ) => void;
  inlineEditor: Ref<InlineEditorState | undefined>;
  isSharedInboxScope: ComputedRef<boolean>;
  loadData: () => Promise<void>;
  loading: Ref<boolean>;
  moving: Ref<boolean>;
  parentStack: Ref<DocumentFileInfo[]>;
  pushNavigationHistory: () => void;
  rememberDocumentClipboard: (
    mode: Extract<DocumentBatchAction, 'copy' | 'cut'>,
    records: DocumentFileInfo[],
  ) => void;
  scope: ComputedRef<DocumentScope>;
  selectedTreeKeys: Ref<string[]>;
  treeLoading: Ref<boolean>;
}

export function useDocumentTreeInteractions(options: UseDocumentTreeInteractionsOptions) {
  const treeShortcutActive = ref(false);

  const selectedTreeFolder = computed(() => {
    const key = options.selectedTreeKeys.value[0];
    return key && !isScopeRootKey(key) ? options.findFolderByKey(key) : undefined;
  });

  function activateTreeShortcut() {
    treeShortcutActive.value = true;
  }

  function deactivateTreeShortcut() {
    treeShortcutActive.value = false;
  }

  function getTreeNodeScope(key: string) {
    return options.getScopeByRootKey(options.getRootKeyFromFolderNodeKey(key)) || options.scope.value;
  }

  function isReadonlyBrowseTree(key?: string) {
    const nodeScope = key ? getTreeNodeScope(key) : options.scope.value;
    const option = key
      ? options.getScopeOptionByRootKey(options.getRootKeyFromFolderNodeKey(key))
      : undefined;
    return (
      nodeScope === 'business' ||
      (nodeScope === 'shared' && !option?.shareTargetType) ||
      nodeScope === 'sharedByMe' ||
      nodeScope === 'starred'
    );
  }

  function canShowSharedByMeShareMenu(key: string) {
    return isActualSharedItem(options.findFolderByKey(key));
  }

  function canShowStarredMenu(key: string) {
    return isActualStarredItem(options.findFolderByKey(key));
  }

  function canManageTreeFolder(key: string) {
    if (isReadonlyBrowseTree(key)) {
      return false;
    }
    const record = options.findFolderByKey(key);
    return !isScopeRootKey(key) && record?.izFolder === '1' && options.canManageFolder(record);
  }

  function canCreateInTreeFolder(key: string) {
    if (isReadonlyBrowseTree(key)) {
      return false;
    }
    return !isScopeRootKey(key) && options.canCreateInsideFolder(options.findFolderByKey(key));
  }

  function canShowTreeContextMenu(key: string) {
    const nodeScope = getTreeNodeScope(key);
    const option = options.getScopeOptionByRootKey(options.getRootKeyFromFolderNodeKey(key));
    if (nodeScope === 'business' || (isSharedReadonlyScope(nodeScope) && !option?.shareTargetType)) {
      return false;
    }
    if (nodeScope === 'sharedByMe') {
      return canShowSharedByMeShareMenu(key);
    }
    if (nodeScope === 'starred') {
      return canShowStarredMenu(key);
    }
    return canManageTreeFolder(key) || canCreateInTreeFolder(key);
  }

  function getTreeContextRecords(record?: DocumentFileInfo) {
    return record?.id ? [record] : [];
  }

  function getTreeCopyableRecords(record?: DocumentFileInfo) {
    if (isReadonlyBrowseTree()) {
      return [];
    }
    return getTreeContextRecords(record).filter(
      (item) => item.id && options.scope.value !== 'trash' && !isVirtualBusinessItem(item),
    );
  }

  function getTreeCuttableRecords(record?: DocumentFileInfo) {
    if (isReadonlyBrowseTree()) {
      return [];
    }
    return getTreeContextRecords(record).filter((item) =>
      canMoveDocument(item, options.documentActionContext.value),
    );
  }

  function getTreeDownloadableRecords(record?: DocumentFileInfo) {
    return getTreeContextRecords(record).filter((item) => item.canDownload && item.izFolder !== '1');
  }

  function getTreeDeletableRecords(record?: DocumentFileInfo) {
    if (isReadonlyBrowseTree()) {
      return [];
    }
    return getTreeContextRecords(record).filter(
      (item) => options.canManageFolder(item) && options.scope.value !== 'trash' && options.scope.value !== 'business',
    );
  }

  function canDropToTreeTarget(key: string) {
    if (
      options.scope.value === 'trash' ||
      isReadonlyBrowseTree() ||
      isReadonlyBrowseTree(key)
    ) {
      return false;
    }
    if (isScopeRootKey(key)) {
      return key === getScopeRootKey(options.activeRootKey.value);
    }
    const target = options.findFolderByKey(key);
    if (!target?.id) {
      return false;
    }
    if (options.isSharedInboxScope.value) {
      return Boolean(target.ownerFlag) && target.storeType === 'shared_view';
    }
    return Boolean(target.canManage);
  }

  async function handleCreateFolderIn(record?: DocumentFileInfo) {
    if (!record?.id || !options.canCreateInsideFolder(record)) {
      return;
    }
    options.pushNavigationHistory();
    options.parentStack.value = [...options.parentStack.value, record];
    options.selectedTreeKeys.value = [options.getActiveSelectedTreeKey()];
    options.expandPathKeys(options.parentStack.value);
    await Promise.all([
      options.loadData(),
      options.ensureFolderTreePathLoaded(options.activeRootKey.value, options.parentStack.value),
    ]);
    options.handleCreateFolder(record.id);
  }

  function isEditingTreeNode(key: string) {
    const record = options.findFolderByKey(key);
    return (
      options.inlineEditor.value?.mode === 'rename' &&
      options.inlineEditor.value.record?.id === record?.id
    );
  }

  function handleTreeMenuBatchAction(event: DocumentBatchAction, record?: DocumentFileInfo) {
    const records =
      event === 'download'
        ? getTreeDownloadableRecords(record)
        : event === 'delete'
          ? getTreeDeletableRecords(record)
          : event === 'cut'
            ? getTreeCuttableRecords(record)
            : getTreeCopyableRecords(record);
    options.handleBatchAction(event, records);
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

  function handleTreeShortcutKeydown(event: KeyboardEvent) {
    if (
      !treeShortcutActive.value ||
      options.loading.value ||
      options.moving.value ||
      options.treeLoading.value ||
      isReadonlyBrowseTree() ||
      isEditableShortcutTarget(event.target)
    ) {
      return;
    }
    const folder = selectedTreeFolder.value;
    if (!folder?.id) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === 'f2') {
      event.preventDefault();
      event.stopImmediatePropagation();
      options.handleRenameFolder(folder);
      return;
    }
    if (key === 'delete' || (event.metaKey && key === 'backspace')) {
      event.preventDefault();
      event.stopImmediatePropagation();
      options.handleDeleteFolder(folder);
      return;
    }
    const shortcutPressed = event.ctrlKey || event.metaKey;
    if (!shortcutPressed || event.altKey) {
      return;
    }
    if (event.shiftKey && key === 'n') {
      event.preventDefault();
      event.stopImmediatePropagation();
      void handleCreateFolderIn(folder);
      return;
    }
    if (key === 'x') {
      if (!options.canManageFolder(folder)) {
        return;
      }
      event.preventDefault();
      event.stopImmediatePropagation();
      options.rememberDocumentClipboard('cut', [folder]);
      return;
    }
    if (key === 'c') {
      if (!options.canManageFolder(folder)) {
        return;
      }
      event.preventDefault();
      event.stopImmediatePropagation();
      options.rememberDocumentClipboard('copy', [folder]);
      return;
    }
    if (key === 'v') {
      event.preventDefault();
      event.stopImmediatePropagation();
      options.handlePasteToTreeFolder(folder);
    }
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

  function getDragSourceParentIds(event: DragEvent) {
    const rawParentIds = event.dataTransfer?.getData('application/x-document-source-parent-ids');
    if (!rawParentIds) {
      return [];
    }
    try {
      const parsed = JSON.parse(rawParentIds);
      if (Array.isArray(parsed)) {
        return parsed
          .filter((parentId): parentId is string | null => typeof parentId === 'string' || parentId === null)
          .map((parentId) => parentId ?? undefined);
      }
    } catch {
      return [];
    }
    return [];
  }

  function handleDropToTree(event: DragEvent, targetKey: string) {
    event.preventDefault();
    const targetFolder = isScopeRootKey(targetKey) ? undefined : options.findFolderByKey(targetKey);
    const targetParentId = isScopeRootKey(targetKey) ? undefined : targetFolder?.id;
    const sourceIds = getDragSourceIds(event).filter((sourceId) => sourceId !== targetParentId);
    if (
      sourceIds.length === 0 ||
      options.scope.value === 'trash' ||
      isReadonlyBrowseTree() ||
      isReadonlyBrowseTree(targetKey)
    ) {
      return;
    }
    if (!canDropToTreeTarget(targetKey)) {
      return;
    }
    const targetPath = isScopeRootKey(targetKey)
      ? []
      : options.findCachedPath(targetKey)?.path || [];
    if (targetPath.some((item) => item.id && sourceIds.includes(item.id))) {
      message.warning('不能移动到自身或子文件夹');
      return;
    }
    const sourceParentIds = getDragSourceParentIds(event);
    options.handleTreeMove(
      sourceIds,
      sourceParentIds.length > 0 ? sourceParentIds : [options.parentStack.value.at(-1)?.id],
      targetParentId,
    );
  }

  function handleTreeDragStart(event: DragEvent, key: string) {
    const folder = options.findFolderByKey(key);
    if (!folder?.id || !canManageTreeFolder(key) || options.moving.value) {
      event.preventDefault();
      return;
    }
    event.dataTransfer?.setData('application/x-document-id', folder.id);
    event.dataTransfer?.setData('application/x-document-ids', JSON.stringify([folder.id]));
    event.dataTransfer?.setData(
      'application/x-document-source-parent-ids',
      JSON.stringify([folder.parentId || null]),
    );
    event.dataTransfer?.setData('text/plain', folder.fileName || '');
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  function isDocumentDrag(event: DragEvent) {
    const types = Array.from(event.dataTransfer?.types || []);
    return types.includes('application/x-document-id') || types.includes('application/x-document-ids');
  }

  function handleTreeDragOver(event: DragEvent, targetKey: string) {
    if (
      !isDocumentDrag(event) ||
      options.scope.value === 'trash' ||
      isReadonlyBrowseTree() ||
      isReadonlyBrowseTree(targetKey)
    ) {
      return;
    }
    if (!canDropToTreeTarget(targetKey)) {
      return;
    }
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
  }

  return {
    activateTreeShortcut,
    canDropToTreeTarget,
    canManageTreeFolder,
    canShowTreeContextMenu,
    deactivateTreeShortcut,
    getTreeCopyableRecords,
    getTreeCuttableRecords,
    getTreeDeletableRecords,
    getTreeDownloadableRecords,
    handleCreateFolderIn,
    handleDropToTree,
    handleTreeDragOver,
    handleTreeDragStart,
    handleTreeMenuBatchAction,
    handleTreeShortcutKeydown,
    isEditingTreeNode,
  };
}
