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
      options: { width: 160 },
    },
    {
      dataIndex: 'departName',
      title: '机构名称',
      options: { width: 220 },
    },
    {
      dataIndex: 'departNameEn',
      title: '英文名',
      options: { width: 180 },
    },
    {
      dataIndex: 'departNameAbbr',
      title: '缩写',
      options: { width: 160 },
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
      dataIndex: 'departOrder',
      title: '排序',
      options: { width: 100, columnType: 'number' as const },
    },
    {
      dataIndex: 'mobile',
      title: '手机号',
      options: { width: 140 },
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
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 220,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: DepartInfo }) =>
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

  return defineTableColumns<DepartInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1720 },
  );
}
