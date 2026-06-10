import { ref } from 'vue';

import type {
  AvailableProcessInfo,
  WorkflowCategoryInfo,
} from '#/api/workflow';

import {
  listWorkflowCategories,
  pageAvailableProcesses,
} from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowStartTable() {
  const categoryOptions = ref<{ label: string; value: string }[]>([]);
  const table = useTable({
    apiConfig: {
      fetchData: pageAvailableProcesses,
    },
    storageConfig: {
      filtersKey: 'workflow_start_list_filters',
    },
  });

  async function loadCategories() {
    const categories = await listWorkflowCategories({
      queryParams: { status: 'enabled' },
    });
    categoryOptions.value = mapCategoryOptions(categories ?? []);
  }

  async function handleRefresh() {
    await Promise.all([loadCategories(), table.loadData()]);
  }

  return {
    activeFilters: table.activeFilters,
    categoryOptions,
    handleRefresh,
    handleTableChange: table.handleTableChange,
    loadData: table.loadData,
    loading: table.loading,
    pagination: table.pagination,
    records: table.dataSource as unknown as typeof table.dataSource & {
      value: AvailableProcessInfo[];
    },
  };
}

function mapCategoryOptions(categories: WorkflowCategoryInfo[]) {
  return categories
    .filter((item) => item.id)
    .map((item) => ({
      label: item.categoryName ?? item.categoryCode ?? item.id!,
      value: item.id!,
    }));
}
