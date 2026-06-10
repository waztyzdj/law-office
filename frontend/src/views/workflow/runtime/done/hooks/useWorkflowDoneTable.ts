import type { RuntimeTaskInfo } from '#/api/workflow';

import { pageDoneTasks } from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowDoneTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageDoneTasks,
    },
    storageConfig: {
      filtersKey: 'workflow_done_list_filters',
    },
  });

  return {
    activeFilters: table.activeFilters,
    handleTableChange: table.handleTableChange,
    loadData: table.loadData,
    loading: table.loading,
    pagination: table.pagination,
    records: table.dataSource as unknown as typeof table.dataSource & {
      value: RuntimeTaskInfo[];
    },
  };
}
