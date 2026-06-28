import { h } from 'vue';

import { Space } from 'ant-design-vue';

import type { WorkflowFormDefinitionInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import { formDefinitionStatusOptions } from '../../../components/status';
import { buildVersionActionLinks } from '../../utils/rowActions';

export function getWorkflowFormColumns(
  categoryMap: Record<string, string>,
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    {
      dataIndex: 'formKey',
      options: { width: 180 },
      title: '表单编码',
    },
    {
      dataIndex: 'formName',
      options: { width: 220 },
      title: '表单名称',
    },
    {
      dataIndex: 'categoryId',
      options: {
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowFormDefinitionInfo }) =>
          categoryMap[record.categoryId ?? ''] ?? record.categoryId ?? '-',
        selectOptions: Object.entries(categoryMap).map(([value, label]) => ({
          label,
          value,
        })),
        width: 180,
      },
      title: '流程分类',
    },
    {
      dataIndex: 'version',
      options: {
        columnType: 'number' as const,
        width: 90,
      },
      title: '版本',
    },
    {
      dataIndex: 'status',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowFormDefinitionInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: formDefinitionStatusOptions,
        width: 110,
      },
      title: '状态',
    },
    {
      dataIndex: 'publishedTime',
      options: {
        align: 'center' as const,
        columnType: 'datetime' as const,
        width: 180,
      },
      title: '发布时间',
    },
    {
      dataIndex: 'remark',
      options: {
        sorter: false,
        width: 260,
      },
      title: '备注',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: WorkflowFormDefinitionInfo }) => {
          const actions = buildVersionActionLinks(record, {
            copyAsDraft: (item) => emit('copyAsDraft', item),
            copyTemplate: (item) => emit('copyTemplate', item),
            delete: (item) => emit('delete', item),
            design: (item) => emit('design', item),
            edit: (item) => emit('edit', item),
            history: (item) => emit('history', item),
            publish: (item) => emit('publish', item),
            viewDesign: (item) => emit('viewDesign', item),
          });
          return h(Space, { size: 'middle' }, () => actions);
        },
        fixed: 'right' as const,
        hasFilter: false,
        width: 340,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<WorkflowFormDefinitionInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1500, tableKey: 'workflow_form' },
  );
}
