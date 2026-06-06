import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type {
  CurrentUserOrganization,
  CurrentUserTenant,
} from '#/api/system/user';
import type { ScopeOption } from '../types';

import { ref } from 'vue';

import {
  batchLoadDocumentTree,
  pageDocuments,
  prefetchDocumentTree,
} from '#/api/system/document';
import {
  getCurrentUserOrganization,
  getCurrentUserTenantOptions,
} from '#/api/system/user';

const PAGE_SIZE = 500;

interface UseDocumentDataLoaderOptions {
  getActiveScopeOption: () => ScopeOption | undefined;
  getCurrentParentId: () => string | undefined;
  getScope: () => DocumentScope;
  reloadCachedFolderTrees: () => Promise<void>;
}

interface FetchDocumentsOptions {
  folderOnly?: boolean;
}

interface BatchLoadFolderTreeItem {
  key: string;
  option?: ScopeOption;
  parentId?: string;
}

export function useDocumentDataLoader(options: UseDocumentDataLoaderOptions) {
  const currentDeparts = ref<CurrentUserOrganization['departs']>([]);
  const currentTenant = ref<CurrentUserTenant>();
  const dataSource = ref<DocumentFileInfo[]>([]);
  const keyword = ref('');
  const activeKeyword = ref('');
  const loading = ref(false);
  let loadSeq = 0;

  async function fetchDocuments(
    parentId?: string,
    searchKeyword?: string,
    option: ScopeOption | undefined = options.getActiveScopeOption(),
    fetchOptions: FetchDocumentsOptions = {},
  ) {
    const records: DocumentFileInfo[] = [];
    let pageNum = 1;
    let total = 0;
    const normalizedKeyword = searchKeyword?.trim();
    const globalSearch = Boolean(normalizedKeyword);

    do {
      const result = await pageDocuments({
        folderOnly: fetchOptions.folderOnly || undefined,
        keyword: normalizedKeyword || undefined,
        pageNum,
        pageSize: PAGE_SIZE,
        parentId: globalSearch ? undefined : parentId,
        scope: globalSearch ? 'all' : option?.scope || options.getScope(),
        shareTargetId: globalSearch ? undefined : option?.shareTargetId,
        shareTargetType: globalSearch ? undefined : option?.shareTargetType,
      });
      const pageRecords = result.records || [];
      records.push(...pageRecords);
      total = result.total || records.length;
      pageNum += 1;
    } while (records.length < total && pageNum < 20);

    return records;
  }

  async function prefetchFolderTree(
    parentIds: string[],
    option: ScopeOption | undefined = options.getActiveScopeOption(),
  ) {
    const normalizedParentIds = Array.from(
      new Set(parentIds.map((id) => id.trim()).filter(Boolean)),
    );
    if (normalizedParentIds.length === 0) {
      return {};
    }
    return prefetchDocumentTree({
      parentIds: normalizedParentIds,
      scope: option?.scope || options.getScope(),
      shareTargetId: option?.shareTargetId,
      shareTargetType: option?.shareTargetType,
    });
  }

  async function batchLoadFolderTree(items: BatchLoadFolderTreeItem[]) {
    const requestItems = items
      .filter((item) => item.key && item.option?.scope)
      .map((item) => ({
        key: item.key,
        parentId: item.parentId,
        scope: item.option?.scope,
        shareTargetId: item.option?.shareTargetId,
        shareTargetType: item.option?.shareTargetType,
      }));
    if (requestItems.length === 0) {
      return {};
    }
    return batchLoadDocumentTree({ items: requestItems });
  }

  async function loadData() {
    const seq = ++loadSeq;
    const parentId = options.getCurrentParentId();
    const searchKeyword = activeKeyword.value;
    const scopeOption = options.getActiveScopeOption();
    loading.value = true;
    try {
      const records = await fetchDocuments(parentId, searchKeyword, scopeOption);
      if (seq === loadSeq) {
        dataSource.value = records;
      }
    } finally {
      if (seq === loadSeq) {
        loading.value = false;
      }
    }
  }

  async function reloadAll() {
    await Promise.all([loadData(), options.reloadCachedFolderTrees()]);
  }

  function resetAndLoad() {
    void loadData();
  }

  function handleSearch(value: string) {
    keyword.value = value;
    activeKeyword.value = value.trim();
    resetAndLoad();
  }

  function handleKeywordChange(value: string) {
    keyword.value = value;
    if (!value.trim() && activeKeyword.value) {
      activeKeyword.value = '';
      resetAndLoad();
    }
  }

  function clearSearchKeyword() {
    if (keyword.value || activeKeyword.value) {
      loadSeq += 1;
    }
    keyword.value = '';
    activeKeyword.value = '';
  }

  async function loadShareRootContext() {
    const [organization, tenants] = await Promise.all([
      getCurrentUserOrganization(),
      getCurrentUserTenantOptions(),
    ]);
    const departMap = new Map<string, CurrentUserOrganization['departs'][number]>();
    for (const depart of organization.departs || []) {
      if (depart.id) {
        departMap.set(depart.id, depart);
      }
    }
    currentDeparts.value = [...departMap.values()];
    currentTenant.value = tenants.find((item) => item.current) || tenants[0];
  }

  return {
    currentDeparts,
    currentTenant,
    activeKeyword,
    batchLoadFolderTree,
    clearSearchKeyword,
    dataSource,
    fetchDocuments,
    handleKeywordChange,
    handleSearch,
    keyword,
    loadData,
    loading,
    loadShareRootContext,
    prefetchFolderTree,
    reloadAll,
    resetAndLoad,
  };
}
