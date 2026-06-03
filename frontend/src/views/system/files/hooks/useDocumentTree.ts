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
  shouldRenderFolderTree,
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
  const folderTree = ref<FolderTreeNode[]>([]);
  const folderTreeCache = ref<Record<string, FolderTreeNode[]>>({});
  const treeLoading = ref(false);
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
    if (rootKey === options.activeRootKey.value) {
      folderTree.value = nodes;
    }
  }

  function setFolderTreeNodeChildren(rootKey: string, targetKey: string, children: FolderTreeNode[]) {
    const nextTree = updateFolderTreeNodes(folderTreeCache.value[rootKey] || [], targetKey, children);
    setFolderTreeCache(rootKey, nextTree);
  }

  function hasLoadedFolderTreeRoot(rootKey: string) {
    return Object.prototype.hasOwnProperty.call(folderTreeCache.value, rootKey);
  }

  async function loadFolderTree(rootKey = options.activeRootKey.value, updateSelection = true) {
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
      const nextTree = await loadFolderNodes(undefined, option, rootKey);
      setFolderTreeCache(rootKey, nextTree);
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

  async function handleTreeExpand(keys: unknown[]) {
    expandedTreeKeys.value = keys.map((key) => String(key));
    const loadKeys = expandedTreeKeys.value.filter((key) => (
      isScopeRootKey(key) || getRootKeyFromFolderNodeKey(key)
    ));
    await Promise.all(loadKeys.map((key) => ensureFolderTreeNodeChildrenLoaded(key)));
  }

  function expandPathKeys(path: DocumentFileInfo[]) {
    const keys = path
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
    folderTree,
    folderTreeCache,
    getActiveSelectedTreeKey,
    getRootKeyFromFolderNodeKey,
    getSelectedTreeKey,
    getTreeNodeIcon,
    handleTreeExpand,
    loadFolderTree,
    loadInitialFolderTrees,
    reloadCachedFolderTrees,
    selectedTreeKeys,
    treeData,
    treeLoading,
  };
}
