import { h } from 'vue';

import { useAccess } from '@vben/access';

import { InputNumber, Space } from 'ant-design-vue';

import type { WorkbenchCardInfo } from '#/api/home/workbench';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

import {
  workbenchCardSizeOptions,
  workbenchStatusOptions,
} from '../../constants';

export function getWorkbenchCardColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canManage = hasAccessByCodes([permissionCodes.homeCard.manage]);

  const columns: any[] = [
    {
      dataIndex: 'cardCode',
      title: '卡片编码',
      options: { width: 150 },
    },
    {
      dataIndex: 'cardName',
      title: '卡片名称',
      options: { width: 150 },
    },
    {
      dataIndex: 'componentKey',
      title: '组件 Key',
      options: { width: 220 },
    },
    {
      dataIndex: 'permissionCode',
      title: '权限码',
      options: { width: 190 },
    },
    {
      dataIndex: 'defaultSize',
      title: '默认尺寸',
      options: {
        width: 110,
        columnType: 'select' as const,
        selectOptions: workbenchCardSizeOptions,
      },
    },
    {
      dataIndex: 'defaultVisible',
      title: '默认显示',
      options: {
        width: 110,
        columnType: 'select' as const,
        selectOptions: [
          { label: '显示', value: 1, color: 'green' },
          { label: '隐藏', value: 0, color: 'default' },
        ],
      },
    },
    {
      dataIndex: 'defaultSort',
      title: '默认排序',
      options: {
        width: 120,
        hasFilter: false,
        customRender: ({ record }: { record: WorkbenchCardInfo }) =>
          h(InputNumber as any, {
            min: 0,
            precision: 0,
            value: record.defaultSort ?? 0,
            style: { width: '86px' },
            'onUpdate:value': (value?: number) => {
              record.defaultSort = value ?? 0;
            },
          }),
      },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 100,
        columnType: 'select' as const,
        selectOptions: workbenchStatusOptions,
      },
    },
    canManage
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 160,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: WorkbenchCardInfo }) =>
              h(Space, { size: 'middle' }, () => [
                h('a', { onClick: () => emit('edit', record) }, '编辑'),
                h(
                  'a',
                  { onClick: () => emit('status', record) },
                  record.status === 'enabled' ? '停用' : '启用',
                ),
              ]),
          },
        }
      : null,
  ];

  return defineTableColumns<WorkbenchCardInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1300, tableKey: 'home_workbench_card' },
  );
}
