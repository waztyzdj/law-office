import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type {
  CurrentUserOrganization,
  CurrentUserTenant,
} from '#/api/system/user';
import type { ScopeOption } from '../types';

import { ref } from 'vue';

import { pageDocuments } from '#/api/system/document';
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

export function useDocumentDataLoader(options: UseDocumentDataLoaderOptions) {
  const currentDeparts = ref<CurrentUserOrganization['departs']>([]);
  const currentTenant = ref<CurrentUserTenant>();
  const dataSource = ref<DocumentFileInfo[]>([]);
  const keyword = ref('');
  const loading = ref(false);

  async function fetchDocuments(
    parentId?: string,
    searchKeyword?: string,
    option: ScopeOption | undefined = options.getActiveScopeOption(),
  ) {
    const records: DocumentFileInfo[] = [];
    let pageNum = 1;
    let total = 0;
    const normalizedKeyword = searchKeyword?.trim();
    const globalSearch = Boolean(normalizedKeyword);

    do {
      const result = await pageDocuments({
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

  async function loadData() {
    loading.value = true;
    try {
      dataSource.value = await fetchDocuments(options.getCurrentParentId(), keyword.value);
    } finally {
      loading.value = false;
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
    resetAndLoad();
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
    dataSource,
    fetchDocuments,
    handleSearch,
    keyword,
    loadData,
    loading,
    loadShareRootContext,
    reloadAll,
    resetAndLoad,
  };
}
