import type { RuntimeTaskInfo } from '#/api/workflow';

import { pageTodoTasks } from '#/api/workflow';
import { useTable } from '#/composables/Table';

export function useWorkflowTodoTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageTodoTasks,
    },
    storageConfig: {
      filtersKey: 'workflow_todo_list_filters',
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
