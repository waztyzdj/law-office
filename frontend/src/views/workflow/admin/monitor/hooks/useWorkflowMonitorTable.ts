import { reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import type {
  AdminMonitorInstanceInfo,
  AdminMonitorPageReq,
} from '#/api/workflow';
import type { TablePaginationConfig } from '#/composables/Table';

import { pageAdminMonitorInstances } from '#/api/workflow';
import { convertTableFiltersToQueryParams } from '#/composables/Table/useTable';

export interface WorkflowMonitorScope {
  categoryId?: string;
  processKey?: string;
  title: string;
  type: 'all' | 'category' | 'process';
}

const FILTERS_KEY = 'workflow_monitor_list_filters';

export function useWorkflowMonitorTable() {
  const activeFilters = ref<Record<string, any>>(loadFiltersFromStorage());
  const currentSort = reactive<{
    sortField?: string;
    sortOrder?: string;
  }>({});
  const loading = ref(false);
  const records = ref<AdminMonitorInstanceInfo[]>([]);
  const scope = ref<WorkflowMonitorScope>({ title: '全部流程', type: 'all' });
  const pagination = reactive<TablePaginationConfig>({
    pageNum: 1,
    pageSize: 10,
    showQuickJumper: true,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
    total: 0,
  });

  async function loadData() {
    loading.value = true;
    try {
      const queryParams = convertTableFiltersToQueryParams(activeFilters.value);
      const params: AdminMonitorPageReq = {
        ...buildScopeParams(scope.value),
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        queryParams: Object.keys(queryParams).length > 0 ? queryParams : undefined,
      };
      if (currentSort.sortField) {
        params.sortField = currentSort.sortField;
        params.sortOrder = currentSort.sortOrder || 'desc';
      }
      const page = await pageAdminMonitorInstances(params);
      records.value = page.records ?? [];
      pagination.total = page.total ?? 0;
    } catch (error) {
      message.error('加载数据失败');
      console.error('加载流程监控列表失败:', error);
    } finally {
      loading.value = false;
    }
  }

  async function handleScopeChange(nextScope: WorkflowMonitorScope) {
    scope.value = nextScope;
    pagination.pageNum = 1;
    await loadData();
  }

  function handleTableChange(pag: any, filters: Record<string, any>, sorter: any) {
    pagination.pageNum = pag?.current ?? pag?.pageNum ?? pagination.pageNum;
    pagination.pageSize = pag?.pageSize ?? pagination.pageSize;
    updateFilters(filters);
    updateSort(sorter);
    void loadData();
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
    activeFilters.value = updatedFilters;
    saveFiltersToStorage(updatedFilters);
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
    handleScopeChange,
    handleTableChange,
    loadData,
    loading,
    pagination,
    records,
    scope,
  };
}

function buildScopeParams(scope: WorkflowMonitorScope) {
  if (scope.type === 'category') {
    return { categoryId: scope.categoryId };
  }
  if (scope.type === 'process') {
    return { processKey: scope.processKey };
  }
  return {};
}

function loadFiltersFromStorage(): Record<string, any> {
  try {
    const stored = localStorage.getItem(FILTERS_KEY);
    return stored ? JSON.parse(stored) : {};
  } catch {
    return {};
  }
}

function saveFiltersToStorage(filters: Record<string, any>) {
  try {
    localStorage.setItem(FILTERS_KEY, JSON.stringify(filters));
  } catch (error) {
    console.error('保存流程监控筛选条件失败:', error);
  }
}
