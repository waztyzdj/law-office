import type { WorkflowCcRecordInfo } from '#/api/workflow';

import { pageWorkflowCcRecords } from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowCcTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageWorkflowCcRecords,
    },
    storageConfig: {
      filtersKey: 'workflow_cc_list_filters',
    },
  });

  return {
    activeFilters: table.activeFilters,
    handleTableChange: table.handleTableChange,
    loadData: table.loadData,
    loading: table.loading,
    pagination: table.pagination,
    records: table.dataSource as unknown as typeof table.dataSource & {
      value: WorkflowCcRecordInfo[];
    },
  };
}
