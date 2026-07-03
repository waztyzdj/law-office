import { h } from 'vue';

import { Space, Tag } from 'ant-design-vue';

import type { WorkflowProcessModelInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import {
  designerTypeMap,
  designerTypeOptions,
  processModelStatusOptions,
  startScopeTypeMap,
  startScopeTypeOptions,
} from '../../../components/status';
import { buildVersionActionLinks } from '../../utils/rowActions';

interface WorkflowProcessColumnContext {
  categoryMap: Record<string, string>;
  formMap: Record<string, string>;
}

export function getWorkflowProcessColumns(
  context: WorkflowProcessColumnContext,
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns = [
    {
      dataIndex: 'processKey',
      options: { width: 180 },
      title: '流程编码',
    },
    {
      dataIndex: 'processName',
      options: { width: 220 },
      title: '流程名称',
    },
    {
      dataIndex: 'categoryId',
      options: {
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowProcessModelInfo }) =>
          context.categoryMap[record.categoryId ?? ''] ?? record.categoryId ?? '-',
        selectOptions: Object.entries(context.categoryMap).map(([value, label]) => ({
          label,
          value,
        })),
        width: 180,
      },
      title: '流程分类',
    },
    {
      dataIndex: 'formDefinitionId',
      options: {
        customRender: ({ record }: { record: WorkflowProcessModelInfo }) =>
          resolveFormLabel(record, context.formMap),
        width: 220,
      },
      title: '绑定表单',
    },
    {
      dataIndex: 'designerType',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowProcessModelInfo }) =>
          h(Tag, {}, () =>
            designerTypeMap[record.designerType ?? ''] ??
            record.designerType ??
            '-',
          ),
        selectOptions: designerTypeOptions,
        width: 130,
      },
      title: '设计器',
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
        customRender: ({ record }: { record: WorkflowProcessModelInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: processModelStatusOptions,
        width: 110,
      },
      title: '状态',
    },
    {
      dataIndex: 'startScopeType',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: WorkflowProcessModelInfo }) =>
          startScopeTypeMap[record.startScopeType ?? ''] ??
          record.startScopeType ??
          '-',
        selectOptions: startScopeTypeOptions,
        width: 120,
      },
      title: '发起范围',
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
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: WorkflowProcessModelInfo }) => {
          const actions = buildVersionActionLinks(
            record,
            {
              copyAsDraft: (item) => emit('copyAsDraft', item),
              copyTemplate: (item) => emit('copyTemplate', item),
              delete: (item) => emit('delete', item),
              design: (item) => emit('design', item),
              edit: (item) => emit('edit', item),
              history: (item) => emit('history', item),
              publish: (item) => emit('publish', item),
              viewDesign: (item) => emit('viewDesign', item),
            },
            [
              h(
                'a',
                { onClick: () => emit('fieldPermission', record) },
                '字段权限',
              ),
            ],
          );
          return h(Space, { size: 'middle' }, () => actions);
        },
        fixed: 'right' as const,
        hasFilter: false,
        width: 400,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<WorkflowProcessModelInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1790, tableKey: 'workflow_process' },
  );
}

function resolveFormLabel(
  record: WorkflowProcessModelInfo,
  formMap: Record<string, string>,
) {
  if (record.formName || record.formKey) {
    return `${record.formName ?? record.formKey} v${record.formVersion ?? 1}`;
  }
  return formMap[record.formDefinitionId ?? ''] ?? record.formDefinitionId ?? '-';
}
