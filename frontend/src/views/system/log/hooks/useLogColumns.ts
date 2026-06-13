import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { LogInfo } from '#/api/system/log';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

const logTypeOptions = [
  { color: 'green', label: '登录日志', value: 1 },
  { color: 'blue', label: '操作日志', value: 2 },
  { color: 'purple', label: '租户操作日志', value: 3 },
];

const operateTypeOptions = [
  { color: 'blue', label: '查询', value: 1 },
  { color: 'cyan', label: '按ID查询', value: 2 },
  { color: 'green', label: '保存', value: 3 },
  { color: 'geekblue', label: '批量保存', value: 4 },
  { color: 'red', label: '删除', value: 5 },
  { color: 'volcano', label: '批量删除', value: 6 },
  { color: 'purple', label: '导出', value: 7 },
  { color: 'orange', label: '导入', value: 8 },
  { label: '自定义', value: 99 },
];

export function getLogColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditLog = hasAccessByCodes([permissionCodes.log.edit]);

  const columns: any[] = [
    {
      dataIndex: 'logType',
      title: '日志类型',
      options: {
        width: 120,
        columnType: 'select' as const,
        selectOptions: logTypeOptions,
      },
    },
    {
      dataIndex: 'logContent',
      title: '日志内容',
      options: { width: 260 },
    },
    {
      dataIndex: 'operateType',
      title: '操作类型',
      options: {
        width: 130,
        columnType: 'select' as const,
        selectOptions: operateTypeOptions,
      },
    },
    {
      dataIndex: 'username',
      title: '用户名称',
      options: { width: 140 },
    },
    {
      dataIndex: 'userid',
      title: '用户ID',
      options: { width: 160 },
    },
    {
      dataIndex: 'ip',
      title: 'IP地址',
      options: { width: 140 },
    },
    {
      dataIndex: 'requestUrl',
      title: '请求路径',
      options: { width: 220 },
    },
    {
      dataIndex: 'requestType',
      title: '请求方式',
      options: { width: 100 },
    },
    {
      dataIndex: 'costTime',
      title: '耗时(ms)',
      options: { width: 110, columnType: 'number' as const },
    },
    {
      dataIndex: 'createTime',
      title: '操作时间',
      options: { width: 180, columnType: 'datetime' as const },
    },
    {
      dataIndex: 'clientType',
      title: '客户端',
      options: { width: 120 },
    },
    canEditLog
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 120,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: LogInfo }) =>
              h(Space, { size: 'middle' }, () => [
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

  return defineTableColumns<LogInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1680, tableKey: 'system_log' },
  );
}
