import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { SysDictInfo, SysDictItemInfo } from '#/api/system/dict';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

export function getDictColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditDict = hasAccessByCodes([permissionCodes.dict.edit]);

  const columns: any[] = [
    {
      dataIndex: 'dictCode',
      title: '字典编码',
      options: { width: 160 },
    },
    {
      dataIndex: 'dictName',
      title: '字典名称',
      options: { width: 180 },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
    canEditDict
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 260,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: SysDictInfo }) =>
              h(Space, { size: 'middle' }, () => [
                h('a', { onClick: () => emit('select', record) }, '管理字典项'),
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

  return defineTableColumns<SysDictInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1120, tableKey: 'system_dict' },
  );
}

export function getDictItemColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditDictItem = hasAccessByCodes([permissionCodes.dictItem.edit]);

  const columns: any[] = [
    {
      dataIndex: 'itemText',
      title: '字典项文本',
      options: { width: 180 },
    },
    {
      dataIndex: 'itemValue',
      title: '字典项值',
      options: { width: 160 },
    },
    {
      dataIndex: 'sortOrder',
      title: '排序',
      options: { width: 100, columnType: 'number' as const },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 100,
        columnType: 'select' as const,
        selectOptions: [
          { label: '正常', value: 1, color: 'green' },
          { label: '冻结', value: 0, color: 'red' },
        ],
      },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
    canEditDictItem
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 180,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: SysDictItemInfo }) =>
              h(Space, { size: 'middle' }, () => [
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

  return defineTableColumns<SysDictItemInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1120, tableKey: 'system_dict_item' },
  );
}
