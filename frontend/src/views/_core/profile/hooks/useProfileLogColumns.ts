import type { CurrentUserLog } from '#/api/system/user';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

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

export function getProfileLogColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
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
      dataIndex: 'ip',
      title: 'IP地址',
      options: { width: 140 },
    },
    {
      dataIndex: 'requestUrl',
      title: '请求路径',
      options: { hasFilter: false, width: 220 },
    },
    {
      dataIndex: 'clientType',
      title: '客户端',
      options: { width: 120 },
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
  ];

  return defineTableColumns<CurrentUserLog>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1160, tableKey: 'profile_log' },
  );
}
