import type { TenantInfo } from '#/api/system/tenant';

import { deleteTenant, pageTenants } from '#/api/system/tenant';
import { useTable } from '#/composables/Table';

export function useTenantTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageTenants,
      deleteItem: deleteTenant,
    },
    storageConfig: {
      filtersKey: 'tenant_list_filters',
    },
    deleteConfig: {
      title: '确认删除',
      content: (record: TenantInfo) =>
        `确定要删除租户“${record.name ?? ''}”吗？`,
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
