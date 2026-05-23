import type { RoleInfo } from '#/api/system/role';
import { deleteRole, pageRoles } from '#/api/system/role';
import { useTable } from '#/composables/Table';

export function useRoleTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageRoles,
      deleteItem: deleteRole,
    },
    deleteConfig: {
      title: '确认删除',
      content: (record: RoleInfo) => `确定要删除角色"${record.roleName}"吗？`,
    },
    storageConfig: {
      filtersKey: 'role_list_filters',
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

