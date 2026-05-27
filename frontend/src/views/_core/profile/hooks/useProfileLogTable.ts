import { pageCurrentUserLogs } from '#/api/system/user';
import { useTable } from '#/composables/Table';

export function useProfileLogTable() {
  return useTable({
    apiConfig: {
      fetchData: pageCurrentUserLogs,
    },
    defaultSort: {
      sortField: 'createTime',
      sortOrder: 'desc',
    },
    storageConfig: {
      filtersKey: 'profile_log_list_filters',
    },
  });
}
