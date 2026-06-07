import type { ComputedRef, Ref } from 'vue';
import type { DocumentFileInfo } from '#/api/document';

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
  TREE_NODE_KEY_SEPARATOR,
  updateFolderTreeRecord,
  updateFolderTreeNodes,
} from '../tree';
import type { FolderTreeNode, ScopeOption } from '../types';

interface UseDocumentTreeOptions {
  activeRootKey: Ref<string>;
  batchLoadFolderTree?: (
    items: { key: string; option?: ScopeOption; parentId?: string }[],
  ) => Promise<Record<string, DocumentFileInfo[]>>;
  currentParentId: ComputedRef<string | undefined>;
  fetchDocuments: (
    parentId?: string,
    searchKeyword?: string,
    option?: ScopeOption,
    fetchOptions?: { folderOnly?: boolean },
  ) => Promise<DocumentFileInfo[]>;
  prefetchFolderTree?: (
    parentIds: string[],
    option?: ScopeOption,
  ) => Promise<Record<string, DocumentFileInfo[]>>;
  scopeOptions: ComputedRef<ScopeOption[]>;
}

interface TreeExpandInfo {
  expanded?: boolean;
  node?: {
    key?: number | string;
  };
}

export function useDocumentTree(options: UseDocumentTreeOptions) {
  const folderTreeCache = ref<Record<string, FolderTreeNode[]>>({});
  const treeLoading = ref(false);
  const treeRenderKey = ref(0);
  const expandedTreeKeys = ref<string[]>([]);
  const selectedTreeKeys = ref<string[]>([getScopeRootKey('my')]);
  const folderNodeLoadPromises = new Map<string, Promise<FolderTreeNode[]>>();

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

  function collectFolderNodeKeys(nodes: FolderTreeNode[] = []) {
    const keys: string[] = [];
    for (const node of nodes) {
      keys.push(node.key);
      if (node.children?.length) {
        keys.push(...collectFolderNodeKeys(node.children));
      }
    }
    return keys;
  }

  function collectScopeOptionTreeKeys(option?: ScopeOption) {
    if (!option?.children?.length) {
      return [];
    }
    const keys: string[] = [];
    for (const child of option.children) {
      keys.push(
        getScopeRootKey(child.key),
        ...collectFolderNodeKeys(folderTreeCache.value[child.key] || []),
      );
      keys.push(...collectScopeOptionTreeKeys(child));
    }
    return keys;
  }

  function collectDescendantExpandedKeys(key: string) {
    const rootKey = getRootKeyFromFolderNodeKey(key);
    const nodes = folderTreeCache.value[rootKey] || [];
    if (isScopeRootKey(key)) {
      return [
        ...collectScopeOptionTreeKeys(findScopeOption(rootKey)),
        ...collectFolderNodeKeys(nodes),
      ];
    }
    const node = findFolderTreeNode(nodes, key);
    return collectFolderNodeKeys(node?.children || []);
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

  function buildFolderTreeNodes(records: DocumentFileInfo[], rootKey: string) {
    return records
      .filter((item) => item.izFolder === '1' && item.id)
      .map((record) => buildFolderTreeNode(record, rootKey));
  }

  async function loadFolderNodes(
    parentId?: string,
    option: ScopeOption | undefined = findScopeOption(options.activeRootKey.value),
    rootKey = options.activeRootKey.value,
  ): Promise<FolderTreeNode[]> {
    const children = await options.fetchDocuments(parentId, undefined, option, { folderOnly: true });
    return buildFolderTreeNodes(children, rootKey);
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
    if (!options.batchLoadFolderTree) {
      await Promise.all(rootKeys.map((rootKey) => loadFolderTree(rootKey, false)));
      selectedTreeKeys.value = [getActiveSelectedTreeKey()];
      return;
    }
    const loadItems = rootKeys
      .map((rootKey) => ({
        key: rootKey,
        option: findScopeOption(rootKey),
      }))
      .filter((item) => (
        item.option?.scope &&
        shouldRenderFolderTree(item.option) &&
        item.option.scope !== 'trash'
      ));
    treeLoading.value = true;
    try {
      const result = await options.batchLoadFolderTree(loadItems);
      const nextCache: Record<string, FolderTreeNode[]> = {};
      for (const rootKey of rootKeys) {
        const option = findScopeOption(rootKey);
        nextCache[rootKey] = option?.scope && shouldRenderFolderTree(option) && option.scope !== 'trash'
          ? buildFolderTreeNodes(result[rootKey] || [], rootKey)
          : [];
      }
      folderTreeCache.value = nextCache;
      treeRenderKey.value += 1;
      pruneExpandedTreeKeys();
      normalizeSelectedTreeKeys();
    } finally {
      treeLoading.value = false;
    }
    selectedTreeKeys.value = [getActiveSelectedTreeKey()];
  }

  async function loadFolderTreeNodeChildren(rootKey: string, parentId: string) {
    const option = findScopeOption(rootKey);
    if (!option?.scope || !shouldRenderFolderTree(option) || option.scope === 'trash') {
      return [];
    }
    const targetKey = getFolderNodeKey(rootKey, parentId);
    const loadingKey = `${rootKey}:${parentId}`;
    const loadingPromise = folderNodeLoadPromises.get(loadingKey);
    if (loadingPromise) {
      return loadingPromise;
    }

    const promise = (async () => {
      const children = await loadFolderNodes(parentId, option, rootKey);
      setFolderTreeNodeChildren(rootKey, targetKey, children);
      return children;
    })();
    folderNodeLoadPromises.set(loadingKey, promise);
    try {
      return await promise;
    } finally {
      if (folderNodeLoadPromises.get(loadingKey) === promise) {
        folderNodeLoadPromises.delete(loadingKey);
      }
    }
  }

  async function prefetchFolderTreeNextLevel(rootKey: string, nodes: FolderTreeNode[]) {
    const option = findScopeOption(rootKey);
    if (!option?.scope || !shouldRenderFolderTree(option) || option.scope === 'trash') {
      return;
    }
    const loadableParentIds = nodes
      .filter((node) => node.file?.id && !node.isLeaf && !Array.isArray(node.children))
      .map((node) => node.file?.id)
      .filter((id): id is string => Boolean(id));
    if (loadableParentIds.length === 0) {
      return;
    }
    const pendingParentIds = loadableParentIds.filter(
      (parentId) => !folderNodeLoadPromises.has(`${rootKey}:${parentId}`),
    );
    if (pendingParentIds.length === 0) {
      return;
    }
    if (!options.prefetchFolderTree) {
      await Promise.allSettled(
        pendingParentIds.map((parentId) => loadFolderTreeNodeChildren(rootKey, parentId)),
      );
      return;
    }

    const promiseMap = new Map<string, Promise<FolderTreeNode[]>>();
    const resolveMap = new Map<string, (children: FolderTreeNode[]) => void>();
    for (const parentId of pendingParentIds) {
      const loadingKey = `${rootKey}:${parentId}`;
      const promise = new Promise<FolderTreeNode[]>((resolve) => {
        resolveMap.set(parentId, resolve);
      });
      promiseMap.set(parentId, promise);
      folderNodeLoadPromises.set(loadingKey, promise);
    }

    try {
      const result = await options.prefetchFolderTree(pendingParentIds, option);
      for (const parentId of pendingParentIds) {
        const children = buildFolderTreeNodes(result[parentId] || [], rootKey);
        setFolderTreeNodeChildren(rootKey, getFolderNodeKey(rootKey, parentId), children);
        resolveMap.get(parentId)?.(children);
      }
    } catch {
      await Promise.allSettled(
        pendingParentIds.map(async (parentId) => {
          const loadingKey = `${rootKey}:${parentId}`;
          const prefetchPromise = promiseMap.get(parentId);
          if (folderNodeLoadPromises.get(loadingKey) === prefetchPromise) {
            folderNodeLoadPromises.delete(loadingKey);
          }
          try {
            const children = await loadFolderTreeNodeChildren(rootKey, parentId);
            resolveMap.get(parentId)?.(children);
          } catch {
            resolveMap.get(parentId)?.([]);
          }
        }),
      );
    } finally {
      for (const parentId of pendingParentIds) {
        const loadingKey = `${rootKey}:${parentId}`;
        const prefetchPromise = promiseMap.get(parentId);
        if (folderNodeLoadPromises.get(loadingKey) === prefetchPromise) {
          folderNodeLoadPromises.delete(loadingKey);
        }
      }
    }
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
      void prefetchFolderTreeNextLevel(rootKey, folderTreeCache.value[rootKey] || []);
      return;
    }
    if (!hasLoadedFolderTreeRoot(rootKey)) {
      await loadFolderTree(rootKey, false);
    }
    const node = findFolderTreeNode(folderTreeCache.value[rootKey] || [], key);
    if (!node?.file?.id || node.isLeaf) {
      return;
    }
    const children = Array.isArray(node.children)
      ? node.children
      : await loadFolderTreeNodeChildren(rootKey, node.file.id);
    void prefetchFolderTreeNextLevel(rootKey, children);
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

  function isLoadableTreeKey(key: string) {
    return isScopeRootKey(key) || key.includes(TREE_NODE_KEY_SEPARATOR);
  }

  async function handleTreeExpand(keys: unknown[], info?: TreeExpandInfo) {
    const targetKey = info?.node?.key === undefined ? undefined : String(info.node.key);
    let nextExpandedKeys = keys.map((key) => String(key));
    if (targetKey && info?.expanded === false) {
      const descendantKeys = new Set(collectDescendantExpandedKeys(targetKey));
      nextExpandedKeys = nextExpandedKeys.filter((key) => !descendantKeys.has(key));
    }
    expandedTreeKeys.value = nextExpandedKeys;
    if (targetKey && info?.expanded === true) {
      await ensureFolderTreeNodeChildrenLoaded(targetKey);
      return;
    }
    const loadKeys = expandedTreeKeys.value.filter((key) => isLoadableTreeKey(key));
    await Promise.all(loadKeys.map((key) => ensureFolderTreeNodeChildrenLoaded(key)));
  }

  function expandPathKeys(path: DocumentFileInfo[], rootKey = options.activeRootKey.value) {
    if (!shouldRenderFolderTree(findScopeOption(rootKey))) {
      expandedTreeKeys.value = Array.from(
        new Set([getScopeRootKey(rootKey), ...expandedTreeKeys.value]),
      );
      return;
    }
    const keys = path
      .slice(0, -1)
      .map((item) => getFolderNodeKey(rootKey, item.id))
      .filter(Boolean) as string[];
    expandedTreeKeys.value = Array.from(
      new Set([getScopeRootKey(rootKey), ...expandedTreeKeys.value, ...keys]),
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
