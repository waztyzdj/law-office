import type { Ref } from 'vue';
import type { DocumentFileInfo } from '#/api/system/document';
import type { DocumentNavigationLocation, FolderTreeNode, ScopeOption } from '../types';

import { computed, ref } from 'vue';

import {
  getScopeFromRootKey,
  getScopeRootKey,
  isScopeRootKey,
} from '../tree';

interface CachedFolderPath {
  path: DocumentFileInfo[];
  rootKey: string;
}

interface UseDocumentNavigationOptions {
  activateTreeShortcut: () => void;
  cancelInlineEditor: () => void;
  canManageTreeFolder: (key: string) => boolean;
  isEditingTreeNode: (key: string) => boolean;
  isGlobalSearch: () => boolean;
  loadData: () => Promise<void>;
  renameFolder: (record: DocumentFileInfo) => void;
  resetAndLoad: () => void;
}

interface DocumentNavigationTreeOptions {
  ensureFolderTreePathLoaded: (rootKey: string, path: DocumentFileInfo[]) => Promise<void>;
  expandedTreeKeys: Ref<string[]>;
  expandPathKeys: (path: DocumentFileInfo[]) => void;
  findCachedPath: (key: string) => CachedFolderPath | undefined;
  findFolderByKey: (key: string) => DocumentFileInfo | undefined;
  findScopeOption: (key: string) => ScopeOption | undefined;
  folderTree: Ref<FolderTreeNode[]>;
  folderTreeCache: Ref<Record<string, FolderTreeNode[]>>;
  getActiveSelectedTreeKey: () => string;
  getSelectedTreeKey: (rootKey: string, fileId?: string) => string;
  loadFolderTree: (rootKey?: string, updateSelection?: boolean) => Promise<void>;
  selectedTreeKeys: Ref<string[]>;
}

export function useDocumentNavigation(options: UseDocumentNavigationOptions) {
  const activeRootKey = ref('my');
  const navigationHistory = ref<DocumentNavigationLocation[]>([]);
  const parentStack = ref<DocumentFileInfo[]>([]);
  let treeOptions: DocumentNavigationTreeOptions | undefined;
  let treeRenameTimer: number | undefined;

  const currentParentId = computed(() => parentStack.value.at(-1)?.id);
  const currentFolder = computed(() => parentStack.value.at(-1));
  const canGoParent = computed(() => !options.isGlobalSearch() && parentStack.value.length > 0);
  const canGoBack = computed(() => !options.isGlobalSearch() && navigationHistory.value.length > 0);

  function setTreeNavigationOptions(nextOptions: DocumentNavigationTreeOptions) {
    treeOptions = nextOptions;
  }

  function getTreeOptions() {
    if (!treeOptions) {
      throw new Error('Document tree navigation options are not initialized.');
    }
    return treeOptions;
  }

  function getCurrentNavigationLocation(): DocumentNavigationLocation {
    return {
      parentStack: [...parentStack.value],
      rootKey: activeRootKey.value,
    };
  }

  function isSameNavigationLocation(
    first: DocumentNavigationLocation,
    second: DocumentNavigationLocation,
  ) {
    return (
      first.rootKey === second.rootKey &&
      (first.parentStack.at(-1)?.id || '') === (second.parentStack.at(-1)?.id || '')
    );
  }

  function pushNavigationHistory() {
    const current = getCurrentNavigationLocation();
    const last = navigationHistory.value.at(-1);
    if (!last || !isSameNavigationLocation(last, current)) {
      navigationHistory.value = [...navigationHistory.value.slice(-49), current];
    }
  }

  async function applyNavigationLocation(location: DocumentNavigationLocation) {
    const tree = getTreeOptions();
    options.cancelInlineEditor();
    activeRootKey.value = location.rootKey;
    parentStack.value = [...location.parentStack];
    tree.selectedTreeKeys.value = [tree.getSelectedTreeKey(location.rootKey, currentParentId.value)];
    tree.expandedTreeKeys.value = Array.from(
      new Set([...tree.expandedTreeKeys.value, getScopeRootKey(location.rootKey)]),
    );
    tree.expandPathKeys(parentStack.value);
    await Promise.all([options.loadData(), tree.loadFolderTree(location.rootKey, false)]);
    await tree.ensureFolderTreePathLoaded(location.rootKey, parentStack.value);
    tree.selectedTreeKeys.value = [tree.getSelectedTreeKey(location.rootKey, currentParentId.value)];
  }

  async function handleGoBack() {
    const location = navigationHistory.value.at(-1);
    if (!location) {
      return;
    }
    navigationHistory.value = navigationHistory.value.slice(0, -1);
    await applyNavigationLocation(location);
  }

  function handleGoParent() {
    const tree = getTreeOptions();
    if (!canGoParent.value) {
      return;
    }
    pushNavigationHistory();
    parentStack.value = parentStack.value.slice(0, -1);
    tree.selectedTreeKeys.value = [tree.getActiveSelectedTreeKey()];
    options.cancelInlineEditor();
    options.resetAndLoad();
  }

  function clearTreeRenameTimer() {
    if (treeRenameTimer) {
      window.clearTimeout(treeRenameTimer);
      treeRenameTimer = undefined;
    }
  }

  async function handleSelectTree(keys: unknown[], info?: { node?: { key?: string | number } }) {
    const tree = getTreeOptions();
    options.activateTreeShortcut();
    const key = String(
      info?.node?.key || keys[0] || tree.selectedTreeKeys.value[0] || getScopeRootKey(activeRootKey.value),
    );
    const alreadySelected = tree.selectedTreeKeys.value[0] === key;
    clearTreeRenameTimer();
    tree.selectedTreeKeys.value = [key];
    if (alreadySelected && options.canManageTreeFolder(key) && !options.isEditingTreeNode(key)) {
      const folder = tree.findFolderByKey(key);
      if (folder) {
        treeRenameTimer = window.setTimeout(() => {
          options.renameFolder(folder);
          treeRenameTimer = undefined;
        }, 220);
        return;
      }
    }
    options.cancelInlineEditor();
    if (isScopeRootKey(key)) {
      const nextRootKey = getScopeFromRootKey(key);
      const nextRoot = tree.findScopeOption(nextRootKey);
      if (!nextRoot?.scope) {
        return;
      }
      const nextLocation = { parentStack: [], rootKey: nextRootKey };
      const currentLocation = getCurrentNavigationLocation();
      if (!isSameNavigationLocation(currentLocation, nextLocation)) {
        pushNavigationHistory();
      }
      parentStack.value = [];
      tree.expandedTreeKeys.value = Array.from(new Set([...tree.expandedTreeKeys.value, key]));
      if (nextRootKey !== activeRootKey.value) {
        activeRootKey.value = nextRootKey;
        await Promise.all([options.loadData(), tree.loadFolderTree()]);
        return;
      }
      options.resetAndLoad();
      return;
    }

    const cachedPath = tree.findCachedPath(key);
    const selectedRecord = cachedPath?.path.at(-1);
    const parentPath = selectedRecord?.izFolder === '1'
      ? cachedPath?.path || []
      : cachedPath?.path.slice(0, -1) || [];
    const nextLocation = {
      parentStack: parentPath,
      rootKey: cachedPath?.rootKey || activeRootKey.value,
    };
    const currentLocation = getCurrentNavigationLocation();
    if (!isSameNavigationLocation(currentLocation, nextLocation)) {
      pushNavigationHistory();
    }
    if (cachedPath) {
      activeRootKey.value = cachedPath.rootKey;
      tree.folderTree.value = tree.folderTreeCache.value[cachedPath.rootKey] || [];
    }
    parentStack.value = nextLocation.parentStack;
    tree.expandPathKeys(parentStack.value);
    options.resetAndLoad();
  }

  async function handleOpenFolder(record: DocumentFileInfo) {
    const tree = getTreeOptions();
    if (!record.id || record.izFolder !== '1') {
      return;
    }
    options.cancelInlineEditor();
    pushNavigationHistory();
    parentStack.value = [...parentStack.value, record];
    tree.selectedTreeKeys.value = [tree.getActiveSelectedTreeKey()];
    tree.expandPathKeys(parentStack.value);
    await Promise.all([
      options.loadData(),
      tree.ensureFolderTreePathLoaded(activeRootKey.value, parentStack.value),
    ]);
  }

  function handleGoRoot() {
    const tree = getTreeOptions();
    if (parentStack.value.length === 0) {
      return;
    }
    pushNavigationHistory();
    parentStack.value = [];
    tree.selectedTreeKeys.value = [getScopeRootKey(activeRootKey.value)];
    options.cancelInlineEditor();
    options.resetAndLoad();
  }

  function handleGoBreadcrumb(index: number) {
    const tree = getTreeOptions();
    if (index === parentStack.value.length - 1) {
      return;
    }
    pushNavigationHistory();
    parentStack.value = parentStack.value.slice(0, index + 1);
    tree.selectedTreeKeys.value = [tree.getActiveSelectedTreeKey()];
    options.cancelInlineEditor();
    options.resetAndLoad();
  }

  return {
    activeRootKey,
    canGoBack,
    canGoParent,
    clearTreeRenameTimer,
    currentFolder,
    currentParentId,
    handleGoBack,
    handleGoBreadcrumb,
    handleGoParent,
    handleGoRoot,
    handleOpenFolder,
    handleSelectTree,
    parentStack,
    pushNavigationHistory,
    setTreeNavigationOptions,
  };
}
