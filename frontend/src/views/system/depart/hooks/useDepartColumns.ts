import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { DepartInfo } from '#/api/system/depart';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

export function getDepartColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
  orgTypeSelectOptions: any[],
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditDepart = hasAccessByCodes([permissionCodes.depart.edit]);

  const columns: any[] = [
    {
      dataIndex: 'orgCode',
      title: '机构编码',
      options: { width: 220 },
    },
    {
      dataIndex: 'departName',
      title: '机构名称',
      options: { width: 320 },
    },
    {
      dataIndex: 'orgType',
      title: '机构类型',
      options: {
        width: 140,
        columnType: 'select' as const,
        selectOptions: orgTypeSelectOptions,
      },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 100,
        columnType: 'select' as const,
        selectOptions: [
          { label: '正常', value: '1', color: 'green' },
          { label: '停用', value: '0', color: 'red' },
        ],
      },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
    canEditDepart
      ? {
          dataIndex: 'departAction',
          title: '操作',
          options: {
            align: 'center',
            className: 'depart-action-cell',
            fixed: 'right' as const,
            hasFilter: false,
            width: 260,
            customRender: ({ record }: { record: DepartInfo }) =>
              h(Space, { class: 'depart-action-links', size: 8 }, () =>
                [
                  h('a', { onClick: () => emit('addChild', record) }, '新增下级'),
                  h('a', { onClick: () => emit('edit', record) }, '编辑'),
                  h(
                    'a',
                    { style: { color: 'red' }, onClick: () => emit('delete', record) },
                    '删除',
                  ),
                  h('a', { onClick: () => emit('members', record) }, '人员'),
                  h('a', { onClick: () => emit('roles', record) }, '部门角色'),
                ].filter(Boolean),
              ),
          },
        }
      : null,
  ];

  return defineTableColumns<DepartInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { actionColumnKey: 'departAction', minTableWidth: 1200 },
  );
}
