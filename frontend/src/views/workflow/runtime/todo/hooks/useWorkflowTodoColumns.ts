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
  taskTypeMap,
  taskTypeOptions,
  todoTaskStatusOptions,
} from '../../../components/status';

export function getWorkflowTodoColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    { dataIndex: 'instanceTitle', options: { width: 260 }, title: '标题' },
    {
      dataIndex: 'taskName',
      options: { width: 160 },
      title: '当前节点',
    },
    {
      dataIndex: 'taskType',
      options: {
        columnType: 'select' as const,
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
          taskTypeMap[record.taskType ?? ''] ?? record.taskType ?? '-',
        selectOptions: taskTypeOptions,
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
      title: '当前处理人',
    },
    {
      dataIndex: 'startTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '发起时间',
    },
    {
      dataIndex: 'status',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: RuntimeTaskInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: todoTaskStatusOptions,
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
            () => [
              h(
                'a',
                { onClick: () => emit('handleTask', record) },
                record.taskType === 'start_draft' ? '提交' : '办理',
              ),
            ],
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
    { minTableWidth: 1200, tableKey: 'workflow_todo' },
  );
}
