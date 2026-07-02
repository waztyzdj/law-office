import { computed, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import type {
  ArchivePageReq,
  ArchiveRecordInfo,
} from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import {
  pageWorkflowArchivedRecords,
  pageWorkflowUnarchivedRecords,
} from '#/api/workflow';
import { convertTableFiltersToQueryParams } from '#/composables/Table/useTable';

export type WorkflowArchiveTab = 'archived' | 'unarchived';

export interface WorkflowArchiveScope {
  categoryId?: string;
  processKey?: string;
  title: string;
  type: 'all' | 'category' | 'process';
}

const FILTERS_KEY = 'workflow_archive_list_filters';

export function useWorkflowArchiveTable() {
  const activeTab = ref<WorkflowArchiveTab>('archived');
  const filterMap = ref<Record<WorkflowArchiveTab, Record<string, any>>>(
    loadFiltersFromStorage(),
  );
  const currentSort = reactive<{
    sortField?: string;
    sortOrder?: string;
  }>({});
  const loading = ref(false);
  const records = ref<ArchiveRecordInfo[]>([]);
  const selectedRowKeys = ref<(number | string)[]>([]);
  const scope = ref<WorkflowArchiveScope>({ title: '全部流程', type: 'all' });
  const pagination = reactive<TablePaginationConfig>({
    pageNum: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
    total: 0,
  });

  const activeFilters = computed(() => filterMap.value[activeTab.value] ?? {});

  async function loadData() {
    loading.value = true;
    try {
      const params = buildCurrentPageReq();
      const page =
        activeTab.value === 'archived'
          ? await pageWorkflowArchivedRecords(params)
          : await pageWorkflowUnarchivedRecords(params);
      records.value = page.records ?? [];
      pagination.total = page.total ?? 0;
    } catch (error) {
      message.error('加载归档数据失败');
      console.error('加载流程归档列表失败:', error);
    } finally {
      loading.value = false;
    }
  }

  async function handleScopeChange(nextScope: WorkflowArchiveScope) {
    scope.value = nextScope;
    pagination.pageNum = 1;
    selectedRowKeys.value = [];
    await loadData();
  }

  async function handleTabChange(nextTab: WorkflowArchiveTab) {
    activeTab.value = nextTab;
    pagination.pageNum = 1;
    selectedRowKeys.value = [];
    currentSort.sortField = undefined;
    currentSort.sortOrder = undefined;
    await loadData();
  }

  function handleTableChange(pag: any, filters: Record<string, any>, sorter: any) {
    pagination.pageNum = pag?.current ?? pag?.pageNum ?? pagination.pageNum;
    pagination.pageSize = pag?.pageSize ?? pagination.pageSize;
    updateFilters(filters);
    updateSort(sorter);
    void loadData();
  }

  function onSelectChange(keys: (number | string)[]) {
    selectedRowKeys.value = keys;
  }

  function buildCurrentQueryReq(extra?: Partial<ArchivePageReq>): ArchivePageReq {
    const queryParams = convertTableFiltersToQueryParams(activeFilters.value);
    const params: ArchivePageReq = {
      ...buildScopeParams(scope.value),
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...(Object.keys(queryParams).length > 0 ? { queryParams } : {}),
      ...extra,
    };
    if (currentSort.sortField) {
      params.sortField = currentSort.sortField;
      params.sortOrder = currentSort.sortOrder || 'desc';
    }
    return params;
  }

  function buildCurrentPageReq(): ArchivePageReq {
    return buildCurrentQueryReq({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    });
  }

  function updateFilters(filters?: Record<string, any>) {
    if (!filters) {
      return;
    }
    const updatedFilters: Record<string, any> = { ...activeFilters.value };
    Object.entries(filters).forEach(([key, value]) => {
      if (value === undefined) {
        delete updatedFilters[key];
      } else if (value === 'filtered' || (Array.isArray(value) && value.includes('filtered'))) {
        // 表格排序会带回筛选状态标记，不能覆盖真实筛选值。
      } else {
        updatedFilters[key] = value;
      }
    });
    filterMap.value = {
      ...filterMap.value,
      [activeTab.value]: updatedFilters,
    };
    saveFiltersToStorage(filterMap.value);
  }

  function updateSort(sorter?: { field?: string; order?: string }) {
    if (!sorter?.field) {
      return;
    }
    if (!sorter.order) {
      currentSort.sortField = undefined;
      currentSort.sortOrder = undefined;
      return;
    }
    currentSort.sortField = sorter.field;
    currentSort.sortOrder = sorter.order === 'ascend' ? 'asc' : 'desc';
  }

  return {
    activeFilters,
    activeTab,
    buildCurrentQueryReq,
    handleScopeChange,
    handleTableChange,
    handleTabChange,
    loadData,
    loading,
    onSelectChange,
    pagination,
    records,
    scope,
    selectedRowKeys,
  };
}

function buildScopeParams(scope: WorkflowArchiveScope) {
  if (scope.type === 'category') {
    return { categoryId: scope.categoryId };
  }
  if (scope.type === 'process') {
    return { processKey: scope.processKey };
  }
  return {};
}

function loadFiltersFromStorage(): Record<WorkflowArchiveTab, Record<string, any>> {
  try {
    const stored = localStorage.getItem(FILTERS_KEY);
    if (stored) {
      return JSON.parse(stored);
    }
  } catch {
    // 读取失败时回到空筛选，避免脏缓存影响页面打开。
  }
  return { archived: {}, unarchived: {} };
}

function saveFiltersToStorage(filters: Record<WorkflowArchiveTab, Record<string, any>>) {
  try {
    localStorage.setItem(FILTERS_KEY, JSON.stringify(filters));
  } catch (error) {
    console.error('保存流程归档筛选条件失败:', error);
  }
}
