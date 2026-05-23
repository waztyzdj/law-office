import { h } from 'vue';
import { Space } from 'ant-design-vue';
import { useAccess } from '@vben/access';
import type { TableColumnsResult, TablePaginationConfig } from '#/composables/Table';
import type { RoleInfo } from '#/api/system/role';
import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

export function getRoleColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditRole = hasAccessByCodes([permissionCodes.role.edit]);

  const columns: any[] = [
    {
      dataIndex: 'roleCode',
      title: '角色编码',
      options: { width: 160 },
    },
    {
      dataIndex: 'roleName',
      title: '角色名称',
      options: { width: 160 },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
    canEditRole
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 180,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: RoleInfo }) =>
              h(Space, { size: 'middle' }, () => [
                h('a', { onClick: () => emit('assign', record) }, '授权'),
                h('a', { onClick: () => emit('edit', record) }, '编辑'),
                h(
                  'a',
                  { style: { color: 'red' }, onClick: () => emit('delete', record) },
                  '删除',
                ),
              ]),
          },
        }
      : null,
  ];

  return defineTableColumns(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
  );
}
