import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { PermissionInfo } from '#/api/system/permission';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { menuTypeOptions } from '#/constants/menu-types';
import { permissionCodes } from '#/constants/permissions';

export function getPermissionColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditPermission = hasAccessByCodes([permissionCodes.permission.edit]);

  const columns: any[] = [
    {
      dataIndex: 'name',
      title: '名称',
      options: { width: 220 },
    },
    {
      dataIndex: 'menuType',
      title: '类型',
      options: {
        width: 90,
        columnType: 'select' as const,
        selectOptions: menuTypeOptions,
      },
    },
    {
      dataIndex: 'url',
      title: '路径',
      options: { width: 180 },
    },
    {
      dataIndex: 'component',
      title: '组件',
      options: { width: 240 },
    },
    {
      dataIndex: 'perms',
      title: '权限码',
      options: { width: 150 },
    },
    {
      dataIndex: 'sortNo',
      title: '排序',
      options: { width: 80, columnType: 'number' as const },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 90,
        columnType: 'select' as const,
        selectOptions: [
          { label: '正常', value: '1', color: 'green' },
          { label: '停用', value: '0', color: 'red' },
        ],
      },
    },
    canEditPermission
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 220,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: PermissionInfo }) =>
              h(Space, { size: 'middle' }, () => [
                h('a', { onClick: () => emit('addChild', record) }, '新增下级'),
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

  return defineTableColumns<PermissionInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1180 },
  );
}
