import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { TenantInfo } from '#/api/system/tenant';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

export function getTenantColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditTenant = hasAccessByCodes([permissionCodes.tenant.edit]);

  const columns: any[] = [
    {
      dataIndex: 'name',
      title: '租户名称',
      options: { width: 180 },
    },
    {
      dataIndex: 'id',
      title: '租户编码',
      options: { width: 150 },
    },
    {
      dataIndex: 'beginDate',
      title: '开始时间',
      options: { width: 180, columnType: 'datetime' as const },
    },
    {
      dataIndex: 'endDate',
      title: '结束时间',
      options: { width: 180, columnType: 'datetime' as const },
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
      dataIndex: 'trade',
      title: '所属行业',
      options: { width: 140 },
    },
    {
      dataIndex: 'companySize',
      title: '公司规模',
      options: { width: 140 },
    },
    {
      dataIndex: 'companyAddress',
      title: '公司地址',
      options: { width: 220 },
    },
    {
      dataIndex: 'secondaryDomain',
      title: '二级域名',
      options: { width: 140 },
    },
    {
      dataIndex: 'applyStatus',
      title: '申请管理者',
      options: {
        width: 120,
        columnType: 'select' as const,
        selectOptions: [
          { label: '允许', value: 1, color: 'blue' },
          { label: '不允许', value: 0 },
        ],
      },
    },
    canEditTenant
      ? {
          dataIndex: 'tenantAction',
          title: '操作',
          options: {
            width: 340,
            align: 'center' as const,
            fixed: 'right' as const,
            hasFilter: false,
            customCell: () => ({
              style: {
                whiteSpace: 'nowrap',
              },
            }),
            customRender: ({ record }: { record: TenantInfo }) =>
              h(
                Space,
                {
                  size: 'small',
                  style: {
                    display: 'inline-flex',
                    flexWrap: 'nowrap',
                    justifyContent: 'center',
                    whiteSpace: 'nowrap',
                    width: '100%',
                  },
                },
                () => [
                h('a', { onClick: () => emit('edit', record) }, '编辑'),
                h('a', { onClick: () => emit('users', record) }, '设置用户'),
                h('a', { onClick: () => emit('admins', record) }, '设置管理员'),
                h('a', { onClick: () => emit('adminPermissions', record) }, '管理员授权'),
                h(
                  'a',
                  { style: { color: 'red' }, onClick: () => emit('delete', record) },
                  '删除',
                ),
              ],
              ),
          },
        }
      : null,
  ];

  return defineTableColumns<TenantInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1660 },
  );
}
