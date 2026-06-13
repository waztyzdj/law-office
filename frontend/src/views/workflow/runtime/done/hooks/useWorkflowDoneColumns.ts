import { h } from 'vue';

import { Space } from 'ant-design-vue';

import type { RuntimeTaskInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import {
  doneTaskStatusOptions,
  taskTypeMap,
  taskTypeOptions,
} from '../../../components/status';

const doneTaskTypeMap: Record<string, string> = {
  ...taskTypeMap,
  start_draft: '已提交',
};

const doneTaskTypeOptions = taskTypeOptions.map((option) =>
  option.value === 'start_draft' ? { ...option, label: '已提交' } : option,
);

export function getWorkflowDoneColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    { dataIndex: 'instanceTitle', options: { width: 260 }, title: '标题' },
    {
      dataIndex: 'taskName',
      options: { width: 160 },
      title: '办理节点',
    },
    {
      dataIndex: 'taskType',
      options: {
        columnType: 'select' as const,
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
          doneTaskTypeMap[record.taskType ?? ''] ?? record.taskType ?? '-',
        selectOptions: doneTaskTypeOptions,
        width: 110,
      },
      title: '任务类型',
    },
    {
      dataIndex: 'starterRealname',
      options: {
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
          record.starterRealname ?? record.starterUsername ?? '-',
        width: 130,
      },
      title: '发起人',
    },
    {
      dataIndex: 'assigneeRealname',
      options: {
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
          record.assigneeRealname ?? record.assigneeUsername ?? '-',
        width: 140,
      },
      title: '办理人',
    },
    {
      dataIndex: 'completeTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '办理时间',
    },
    {
      dataIndex: 'status',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: doneTaskStatusOptions,
        width: 100,
      },
      title: '状态',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
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

  return defineTableColumns<RuntimeTaskInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1200, tableKey: 'workflow_done' },
  );
}
