import type { LogInfo } from '#/api/system/log';

import { deleteLog, pageLogs } from '#/api/system/log';
import { useTable } from '#/composables/Table';

export function useLogTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageLogs,
      deleteItem: deleteLog,
    },
    defaultSort: {
      sortField: 'createTime',
      sortOrder: 'desc',
    },
    storageConfig: {
      filtersKey: 'log_list_filters',
    },
    deleteConfig: {
      title: '确认删除',
      content: (record: LogInfo) =>
        `确认删除日志“${record.logContent ?? ''}”吗？`,
    },
  });

  const loadData = async (extraFilters?: Record<string, any>) => {
    await table.loadData({}, extraFilters);
  };

  return {
    ...table,
    loadData,
  };
}
