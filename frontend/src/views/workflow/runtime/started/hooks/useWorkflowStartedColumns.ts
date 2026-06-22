import { h } from 'vue';

import { Space } from 'ant-design-vue';

import type { StartedInstanceInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import { processInstanceStatusOptions } from '../../../components/status';

const finishedStatuses = new Set(['approved', 'rejected', 'terminated', 'withdrawn']);

function isFinished(status?: string) {
  return finishedStatuses.has(status || '');
}

export function getWorkflowStartedColumns(
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
      dataIndex: 'currentTaskNames',
      options: {
        customRender: ({ record }: { record: StartedInstanceInfo }) =>
          isFinished(record.status) ? '已结束' : record.currentTaskNames || '-',
        width: 180,
      },
      title: '当前节点',
    },
    {
      dataIndex: 'currentAssigneeNames',
      options: {
        customRender: ({ record }: { record: StartedInstanceInfo }) =>
          isFinished(record.status) ? '' : record.currentAssigneeNames || '-',
        width: 180,
      },
      title: '当前处理人',
    },
    {
      dataIndex: 'startTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '发起时间',
    },
    {
      dataIndex: 'endTime',
      options: {
        align: 'center' as const,
        columnType: 'datetime' as const,
        customRender: ({ record }: { record: StartedInstanceInfo }) =>
          record.endTime || '-',
        width: 180,
      },
      title: '结束时间',
    },
    {
      dataIndex: 'status',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: StartedInstanceInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: processInstanceStatusOptions,
        width: 100,
      },
      title: '状态',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: StartedInstanceInfo }) =>
          h(
            Space,
            { size: 'middle' },
            () => [
              h('a', { onClick: () => emit('detail', record) }, '详情'),
              record.canWithdraw
                ? h(
                    'a',
                    { class: 'text-red-500', onClick: () => emit('withdraw', record) },
                    '撤回',
                  )
                : null,
            ],
          ),
        fixed: 'right' as const,
        hasFilter: false,
        width: 120,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<StartedInstanceInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1380, tableKey: 'workflow_started' },
  );
}
