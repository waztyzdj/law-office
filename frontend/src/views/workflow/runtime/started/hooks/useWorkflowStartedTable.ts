import type { StartedInstanceInfo } from '#/api/workflow';

import { pageStartedInstances } from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowStartedTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageStartedInstances,
    },
    storageConfig: {
      filtersKey: 'workflow_started_list_filters',
    },
  });

  return {
    activeFilters: table.activeFilters,
    handleTableChange: table.handleTableChange,
    loadData: table.loadData,
    loading: table.loading,
    pagination: table.pagination,
    records: table.dataSource as unknown as typeof table.dataSource & {
      value: StartedInstanceInfo[];
    },
  };
}
