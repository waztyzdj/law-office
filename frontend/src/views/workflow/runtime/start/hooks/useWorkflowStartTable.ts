import { reactive } from 'vue';

import type { AvailableProcessInfo } from '#/api/workflow';

import { pageAvailableProcesses } from '#/api/workflow';
import { useTable } from '#/composables/Table';

const START_PROCESS_PAGE_SIZE = 10_000;

export interface WorkflowStartSearchForm {
  processName?: string;
}

export function useWorkflowStartTable() {
  const searchForm = reactive<WorkflowStartSearchForm>({
    processName: undefined,
  });
  const table = useTable({
    apiConfig: {
      fetchData: pageAvailableProcesses,
    },
    storageConfig: {
      filtersKey: 'workflow_start_catalog_filters',
    },
  });
  table.pagination.pageSize = START_PROCESS_PAGE_SIZE;
  table.pagination.showSizeChanger = false;
  table.pagination.showQuickJumper = false;
  const records = table.dataSource as unknown as typeof table.dataSource & {
    value: AvailableProcessInfo[];
  };

  function buildSearchParams() {
    const params: WorkflowStartSearchForm = {};
    const processName = searchForm.processName?.trim();
    if (processName) {
      params.processName = processName;
    }
    return params;
  }

  async function loadProcesses() {
    await table.loadData(buildSearchParams());
  }

  async function handleRefresh() {
    await loadProcesses();
  }

  async function handleSearch() {
    table.resetPagination();
    await loadProcesses();
  }

  async function handleResetSearch() {
    searchForm.processName = undefined;
    table.resetPagination();
    await loadProcesses();
  }

  return {
    activeFilters: table.activeFilters,
    handleRefresh,
    handleResetSearch,
    handleSearch,
    handleTableChange: table.handleTableChange,
    loadData: loadProcesses,
    loading: table.loading,
    records,
    searchForm,
  };
}
