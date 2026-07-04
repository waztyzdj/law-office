import { h } from 'vue';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';

import { Space } from 'ant-design-vue';

import type { WorkbenchQuickEntryInfo } from '#/api/home/workbench';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

import {
  workbenchQuickEntryTypeOptions,
  workbenchStatusOptions,
} from '../../constants';

export function getWorkbenchQuickEntryColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canManage = hasAccessByCodes([permissionCodes.homeCard.manage]);

  const columns: any[] = [
    {
      dataIndex: 'entryCode',
      title: '菜单编码',
      options: { width: 150 },
    },
    {
      dataIndex: 'entryName',
      title: '菜单名称',
      options: { width: 160 },
    },
    {
      dataIndex: 'entryType',
      title: '菜单类型',
      options: {
        width: 110,
        columnType: 'select' as const,
        selectOptions: workbenchQuickEntryTypeOptions,
      },
    },
    {
      dataIndex: 'icon',
      title: '图标',
      options: {
        width: 90,
        hasFilter: false,
        customRender: ({ record }: { record: WorkbenchQuickEntryInfo }) =>
          record.icon
            ? h(IconifyIcon, { icon: record.icon, style: { fontSize: '18px' } })
            : '-',
      },
    },
    {
      dataIndex: 'path',
      title: '路径',
      options: { width: 220 },
    },
    {
      dataIndex: 'menuId',
      title: '菜单 ID',
      options: { width: 180 },
    },
    {
      dataIndex: 'permissionCode',
      title: '权限码',
      options: { width: 190 },
    },
    {
      dataIndex: 'sortNo',
      title: '排序',
      options: { width: 90, columnType: 'number' as const },
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
            customRender: ({ record }: { record: WorkbenchQuickEntryInfo }) =>
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

  return defineTableColumns<WorkbenchQuickEntryInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1360, tableKey: 'home_workbench_quick_entry' },
  );
}
