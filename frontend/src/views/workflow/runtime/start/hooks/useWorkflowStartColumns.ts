import { h } from 'vue';

import { Space, Tag } from 'ant-design-vue';

import type { AvailableProcessInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import {
  designerTypeMap,
  designerTypeOptions,
  startScopeTypeMap,
  startScopeTypeOptions,
} from '../../../components/status';

export function getWorkflowStartColumns(
  categoryOptions: { label: string; value: string }[],
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    { dataIndex: 'processName', options: { width: 220 }, title: '流程名称' },
    {
      dataIndex: 'processKey',
      options: { width: 180 },
      title: '流程编码',
    },
    {
      dataIndex: 'categoryId',
      options: {
        columnType: 'select' as const,
        selectOptions: categoryOptions,
        width: 160,
      },
      title: '流程分类',
    },
    {
      dataIndex: 'processVersion',
      options: {
        columnType: 'number' as const,
        width: 100,
      },
      title: '流程版本',
    },
    {
      dataIndex: 'formName',
      options: { width: 220 },
      title: '表单名称',
    },
    {
      dataIndex: 'formVersion',
      options: {
        columnType: 'number' as const,
        width: 100,
      },
      title: '表单版本',
    },
    {
      dataIndex: 'designerType',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: AvailableProcessInfo }) =>
          designerTypeMap[record.designerType ?? ''] ??
          record.designerType ??
          '-',
        selectOptions: designerTypeOptions,
        width: 130,
      },
      title: '设计器',
    },
    {
      dataIndex: 'startScopeType',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: AvailableProcessInfo }) =>
          h(Tag, {}, () =>
            startScopeTypeMap[record.startScopeType ?? ''] ??
            record.startScopeType ??
            '-',
          ),
        selectOptions: startScopeTypeOptions,
        width: 120,
      },
      title: '发起范围',
    },
    {
      dataIndex: 'publishedTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '发布时间',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: AvailableProcessInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => emit('start', record) }, '发起'),
          ]),
        fixed: 'right' as const,
        hasFilter: false,
        width: 100,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<AvailableProcessInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1530 },
  );
}
