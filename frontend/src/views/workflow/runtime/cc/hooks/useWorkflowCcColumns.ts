import { h } from 'vue';

import { Space, Tag } from 'ant-design-vue';

import type { WorkflowCcRecordInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import {
  ccStatusOptions,
  processInstanceStatusOptions,
} from '../../../components/status';

export function getWorkflowCcColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    { dataIndex: 'instanceTitle', options: { width: 260 }, title: '标题' },
    {
      dataIndex: 'processName',
      options: { width: 180 },
      title: '流程名称',
    },
    {
      dataIndex: 'starterRealname',
      options: {
        customRender: ({ record }: { record: WorkflowCcRecordInfo }) =>
          record.starterRealname ?? record.starterUsername ?? '-',
        width: 130,
      },
      title: '发起人',
    },
    {
      dataIndex: 'createTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '抄送时间',
    },
    {
      dataIndex: 'processStatus',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowCcRecordInfo }) =>
          h(WorkflowStatusTag, { status: record.processStatus }),
        selectOptions: processInstanceStatusOptions,
        width: 110,
      },
      title: '流程状态',
    },
    {
      dataIndex: 'status',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowCcRecordInfo }) =>
          h(
            Tag,
            { color: record.status === 'read' ? 'default' : 'processing' },
            () => (record.status === 'read' ? '已读' : '未读'),
          ),
        selectOptions: ccStatusOptions,
        width: 100,
      },
      title: '阅读状态',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: WorkflowCcRecordInfo }) =>
          h(
            Space,
            { size: 'middle' },
            () => [h('a', { onClick: () => emit('detail', record) }, '详情')],
          ),
        fixed: 'right' as const,
        hasFilter: false,
        width: 120,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<WorkflowCcRecordInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1160, tableKey: 'workflow_cc' },
  );
}
