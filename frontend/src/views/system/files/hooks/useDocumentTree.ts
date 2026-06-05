import type { ComputedRef, Ref } from 'vue';
import type { DocumentFileInfo } from '#/api/system/document';

import { computed, ref } from 'vue';

import {
  buildFolderTreeNode,
  collectScopeRootKeys,
  findFolderTreeNode,
  findPath,
  findScopeOption as findScopeOptionInTree,
  getFolderIcon,
  getFolderNodeKey,
  getRootKeyFromFolderNodeKey as resolveRootKeyFromFolderNodeKey,
  getScopeFromRootKey,
  getScopeRootKey,
  isScopeRootKey,
  mergeFolderTreeNodes,
  shouldRenderFolderTree,
  updateFolderTreeRecord,
  updateFolderTreeNodes,
} from '../tree';
import type { FolderTreeNode, ScopeOption } from '../types';

interface UseDocumentTreeOptions {
  activeRootKey: Ref<string>;
  currentParentId: ComputedRef<string | undefined>;
  fetchDocuments: (
    parentId?: string,
    searchKeyword?: string,
    option?: ScopeOption,
  ) => Promise<DocumentFileInfo[]>;
  scopeOptions: ComputedRef<ScopeOption[]>;
}

export function useDocumentTree(options: UseDocumentTreeOptions) {
  const folderTreeCache = ref<Record<string, FolderTreeNode[]>>({});
  const treeLoading = ref(false);
  const treeRenderKey = ref(0);
  const expandedTreeKeys = ref<string[]>([getScopeRootKey('my')]);
  const selectedTreeKeys = ref<string[]>([getScopeRootKey('my')]);

  const treeData = computed<FolderTreeNode[]>(() =>
    options.scopeOptions.value.map((item) => buildScopeTreeNode(item)),
  );

  function buildScopeTreeNode(option: ScopeOption): FolderTreeNode {
    const optionChildren = option.children?.map((item) => buildScopeTreeNode(item)) || [];
    const activeChildren = shouldRenderFolderTree(option) ? folderTreeCache.value[option.key] || [] : [];
    const children = [...optionChildren, ...activeChildren];
    const canLoadFolderTree = shouldRenderFolderTree(option) && Boolean(option.scope) && option.scope !== 'trash';
    const folderTreeLoaded = hasLoadedFolderTreeRoot(option.key);
    return {
      children: children.length > 0 ? children : undefined,
      isLeaf: (!canLoadFolderTree || folderTreeLoaded) && children.length === 0,
      key: getScopeRootKey(option.key),
      selectable: option.selectable,
      title: option.title,
    };
  }

  function findScopeOption(key: string, scopeOptions = options.scopeOptions.value) {
    return findScopeOptionInTree(key, scopeOptions);
  }

  function getSelectedTreeKey(rootKey: string, fileId?: string) {
    return shouldRenderFolderTree(findScopeOption(rootKey))
      ? getFolderNodeKey(rootKey, fileId)
      : getScopeRootKey(rootKey);
  }

  function getActiveSelectedTreeKey() {
    return getSelectedTreeKey(options.activeRootKey.value, options.currentParentId.value);
  }

  function selectFolderTreeParent(parentId?: string) {
    selectedTreeKeys.value = [getSelectedTreeKey(options.activeRootKey.value, parentId)];
  }

  function getRootKeyFromFolderNodeKey(key: string) {
    return resolveRootKeyFromFolderNodeKey(key, options.activeRootKey.value);
  }

  function getTreeNodeIcon(key: string) {
    if (!isScopeRootKey(key)) {
      return getFolderIcon(findFolderByKey(key));
    }
    const scopeKey = getScopeFromRootKey(key);
    return findScopeOption(scopeKey)?.icon || 'lucide:folder';
  }

  function findFolderByKey(key: string) {
    return findCachedPath(key)?.path.at(-1);
  }

  function findCachedPath(key: string) {
    for (const [rootKey, nodes] of Object.entries(folderTreeCache.value)) {
      const path = findPath(nodes, key);
      if (path) {
        return { path, rootKey };
      }
    }
    return undefined;
  }

  function resolveCachedFolderPath(rootKey: string, path: DocumentFileInfo[]) {
    const resolvedPath: DocumentFileInfo[] = [];
    for (const folder of path) {
      if (!folder.id) {
        break;
      }
      const node = findFolderTreeNode(folderTreeCache.value[rootKey] || [], getFolderNodeKey(rootKey, folder.id));
      if (!node?.file) {
        break;
      }
      resolvedPath.push(node.file);
    }
    return resolvedPath;
  }

  async function loadFolderNodes(
    parentId?: string,
    option: ScopeOption | undefined = findScopeOption(options.activeRootKey.value),
    rootKey = options.activeRootKey.value,
  ): Promise<FolderTreeNode[]> {
    const children = await options.fetchDocuments(parentId, undefined, option);
    const records = children.filter((item) => item.izFolder === '1' && item.id);
    return records.map((record) => buildFolderTreeNode(record, rootKey));
  }

  function setFolderTreeCache(rootKey: string, nodes: FolderTreeNode[]) {
    folderTreeCache.value = {
      ...folderTreeCache.value,
      [rootKey]: nodes,
    };
    treeRenderKey.value += 1;
  }

  function setFolderTreeNodeChildren(rootKey: string, targetKey: string, children: FolderTreeNode[]) {
    const nextTree = updateFolderTreeNodes(folderTreeCache.value[rootKey] || [], targetKey, children);
    setFolderTreeCache(rootKey, nextTree);
    pruneExpandedTreeKeys();
    normalizeSelectedTreeKeys();
  }

  function isExpandableTreeKey(key: string) {
    if (isScopeRootKey(key)) {
      return true;
    }
    const rootKey = getRootKeyFromFolderNodeKey(key);
    const node = findFolderTreeNode(folderTreeCache.value[rootKey] || [], key);
    return Boolean(node && !node.isLeaf && node.children?.length);
  }

  function pruneExpandedTreeKeys() {
    expandedTreeKeys.value = expandedTreeKeys.value.filter((key) => isExpandableTreeKey(key));
  }

  function normalizeSelectedTreeKeys() {
    const selectedKey = selectedTreeKeys.value[0];
    if (!selectedKey || isScopeRootKey(selectedKey)) {
      return;
    }
    const rootKey = getRootKeyFromFolderNodeKey(selectedKey);
    const node = findFolderTreeNode(folderTreeCache.value[rootKey] || [], selectedKey);
    if (!node) {
      selectedTreeKeys.value = [getActiveSelectedTreeKey()];
    }
  }

  function updateCachedFolderTreeRecord(record: DocumentFileInfo) {
    if (!record.id || record.izFolder !== '1') {
      return;
    }
    const nextCache = Object.fromEntries(
      Object.entries(folderTreeCache.value).map(([rootKey, nodes]) => [
        rootKey,
        updateFolderTreeRecord(nodes, getFolderNodeKey(rootKey, record.id), record),
      ]),
    );
    folderTreeCache.value = nextCache;
  }

  function hasLoadedFolderTreeRoot(rootKey: string) {
    return Object.prototype.hasOwnProperty.call(folderTreeCache.value, rootKey);
  }

  async function loadFolderTree(
    rootKey = options.activeRootKey.value,
    updateSelection = true,
    preserveLoadedChildren = false,
  ) {
    const option = findScopeOption(rootKey);
    if (!option?.scope) {
      return;
    }
    if (!shouldRenderFolderTree(option) || option.scope === 'trash') {
      setFolderTreeCache(rootKey, []);
      if (updateSelection) {
        selectedTreeKeys.value = [getScopeRootKey(rootKey)];
      }
      return;
    }
    treeLoading.value = true;
    try {
      const loadedTree = await loadFolderNodes(undefined, option, rootKey);
      const nextTree = preserveLoadedChildren
        ? mergeFolderTreeNodes(loadedTree, folderTreeCache.value[rootKey] || [])
        : loadedTree;
      setFolderTreeCache(rootKey, nextTree);
      pruneExpandedTreeKeys();
      normalizeSelectedTreeKeys();
      if (updateSelection) {
        selectedTreeKeys.value = [getSelectedTreeKey(rootKey, options.currentParentId.value)];
      }
    } finally {
      treeLoading.value = false;
    }
  }

  async function reloadCachedFolderTrees(path: DocumentFileInfo[] = []) {
    const rootKeys = Array.from(
      new Set([options.activeRootKey.value, ...Object.keys(folderTreeCache.value)]),
    );
    await Promise.all(rootKeys.map((rootKey) => loadFolderTree(rootKey, false)));
    await ensureFolderTreePathLoaded(options.activeRootKey.value, path);
    if (options.currentParentId.value) {
      await loadFolderTreeNodeChildren(options.activeRootKey.value, options.currentParentId.value);
    }
    selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  }

  async function refreshFolderTreeChildren(parentId = options.currentParentId.value) {
    const rootKey = options.activeRootKey.value;
    const option = findScopeOption(rootKey);
    if (!option?.scope || !shouldRenderFolderTree(option) || option.scope === 'trash') {
      selectedTreeKeys.value = [getActiveSelectedTreeKey()];
      return;
    }
    treeLoading.value = true;
    try {
      if (parentId) {
        await loadFolderTreeNodeChildren(rootKey, parentId);
      } else {
        await loadFolderTree(rootKey, false, true);
      }
      selectedTreeKeys.value = [getActiveSelectedTreeKey()];
    } finally {
      treeLoading.value = false;
    }
  }

  async function loadInitialFolderTrees() {
    const rootKeys = Array.from(new Set(collectScopeRootKeys(options.scopeOptions.value)));
    await Promise.all(rootKeys.map((rootKey) => loadFolderTree(rootKey, false)));
    selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  }

  async function loadFolderTreeNodeChildren(rootKey: string, parentId: string) {
    const option = findScopeOption(rootKey);
    if (!option?.scope || !shouldRenderFolderTree(option) || option.scope === 'trash') {
      return;
    }
    const targetKey = getFolderNodeKey(rootKey, parentId);
    const children = await loadFolderNodes(parentId, option, rootKey);
    setFolderTreeNodeChildren(rootKey, targetKey, children);
  }

  async function ensureFolderTreeNodeChildrenLoaded(key: string) {
    const rootKey = getRootKeyFromFolderNodeKey(key);
    const option = findScopeOption(rootKey);
    if (!option?.scope || !shouldRenderFolderTree(option) || option.scope === 'trash') {
      return;
    }
    if (isScopeRootKey(key)) {
      if (!hasLoadedFolderTreeRoot(rootKey)) {
        await loadFolderTree(rootKey, false);
      }
      return;
    }
    if (!hasLoadedFolderTreeRoot(rootKey)) {
      await loadFolderTree(rootKey, false);
    }
    const node = findFolderTreeNode(folderTreeCache.value[rootKey] || [], key);
    if (!node?.file?.id || node.isLeaf || Array.isArray(node.children)) {
      return;
    }
    await loadFolderTreeNodeChildren(rootKey, node.file.id);
  }

  async function ensureFolderTreePathLoaded(rootKey: string, path: DocumentFileInfo[]) {
    await ensureFolderTreeNodeChildrenLoaded(getScopeRootKey(rootKey));
    for (const folder of path.slice(0, -1)) {
      if (folder.id) {
        await ensureFolderTreeNodeChildrenLoaded(getFolderNodeKey(rootKey, folder.id));
      }
    }
  }

  async function resolveExistingFolderTreePath(rootKey: string, path: DocumentFileInfo[]) {
    await ensureFolderTreePathLoaded(rootKey, path);
    return resolveCachedFolderPath(rootKey, path);
  }

  async function handleTreeExpand(keys: unknown[]) {
    expandedTreeKeys.value = keys.map((key) => String(key));
    const loadKeys = expandedTreeKeys.value.filter((key) => (
      isScopeRootKey(key) || getRootKeyFromFolderNodeKey(key)
    ));
    await Promise.all(loadKeys.map((key) => ensureFolderTreeNodeChildrenLoaded(key)));
  }

  function expandPathKeys(path: DocumentFileInfo[]) {
    const keys = path
      .slice(0, -1)
      .map((item) => getFolderNodeKey(options.activeRootKey.value, item.id))
      .filter(Boolean) as string[];
    expandedTreeKeys.value = Array.from(
      new Set([getScopeRootKey(options.activeRootKey.value), ...expandedTreeKeys.value, ...keys]),
    );
  }

  return {
    ensureFolderTreePathLoaded,
    expandedTreeKeys,
    expandPathKeys,
    findCachedPath,
    findFolderByKey,
    findScopeOption,
    getActiveSelectedTreeKey,
    getRootKeyFromFolderNodeKey,
    getSelectedTreeKey,
    getTreeNodeIcon,
    handleTreeExpand,
    loadFolderTree,
    loadInitialFolderTrees,
    reloadCachedFolderTrees,
    refreshFolderTreeChildren,
    resolveExistingFolderTreePath,
    selectFolderTreeParent,
    selectedTreeKeys,
    treeData,
    treeLoading,
    treeRenderKey,
    updateCachedFolderTreeRecord,
  };
}
