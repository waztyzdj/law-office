import { h } from 'vue';

import { Space } from 'ant-design-vue';

import type { WorkflowCategoryInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';

const statusSelectOptions = [
  { color: 'success', label: '启用', value: 'enabled' },
  { color: 'default', label: '停用', value: 'disabled' },
];

export function getWorkflowCategoryColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    {
      dataIndex: 'categoryCode',
      options: { width: 180 },
      title: '分类编码',
    },
    {
      dataIndex: 'categoryName',
      options: { width: 220 },
      title: '分类名称',
    },
    {
      dataIndex: 'sortOrder',
      options: {
        columnType: 'number' as const,
        width: 100,
      },
      title: '排序',
    },
    {
      dataIndex: 'status',
      options: {
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowCategoryInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: statusSelectOptions,
        width: 110,
      },
      title: '状态',
    },
    {
      dataIndex: 'remark',
      options: { sorter: false, width: 260 },
      title: '备注',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: WorkflowCategoryInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => emit('addChild', record) }, '新增子类'),
            h('a', { onClick: () => emit('edit', record) }, '编辑'),
            h(
              'a',
              {
                onClick: () => emit('delete', record),
                style: { color: '#ff4d4f' },
              },
              '删除',
            ),
          ]),
        fixed: 'right' as const,
        hasFilter: false,
        width: 220,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<WorkflowCategoryInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1090, tableKey: 'workflow_category' },
  );
}
