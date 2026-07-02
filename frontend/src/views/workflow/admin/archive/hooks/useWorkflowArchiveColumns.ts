import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { ArchiveRecordInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import { processInstanceStatusOptions } from '../../../components/status';
import type { WorkflowArchiveTab } from './useWorkflowArchiveTable';

const archiveSourceOptions = [
  { label: '自动归档', value: 'auto' },
  { label: '流程监控归档', value: 'monitor_manual' },
  { label: '流程归档菜单归档', value: 'archive_manual' },
];

export function getWorkflowArchiveColumns(
  tab: WorkflowArchiveTab,
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canManage = hasAccessByCodes([permissionCodes.workflowArchive.manage]);
  const columns = [
    { dataIndex: 'instanceTitle', options: { width: 260 }, title: '标题' },
    {
      dataIndex: 'instanceNo',
      options: { width: 170 },
      title: '流水号',
    },
    {
      dataIndex: 'processName',
      options: { width: 180 },
      title: '流程名称',
    },
    {
      dataIndex: 'processVersion',
      options: {
        align: 'center' as const,
        columnType: 'number' as const,
        customRender: ({ record }: { record: ArchiveRecordInfo }) =>
          record.processVersion ? `v${record.processVersion}` : '-',
        width: 90,
      },
      title: '版本',
    },
    {
      dataIndex: 'starterRealname',
      options: {
        customRender: ({ record }: { record: ArchiveRecordInfo }) =>
          record.starterRealname ?? record.starterUsername ?? '-',
        width: 130,
      },
      title: '发起人',
    },
    {
      dataIndex: 'processStartTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '发起时间',
    },
    {
      dataIndex: 'processEndTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '结束时间',
    },
    {
      dataIndex: 'instanceStatus',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: ArchiveRecordInfo }) =>
          h(WorkflowStatusTag, { status: record.instanceStatus }),
        selectOptions: processInstanceStatusOptions.filter((item) =>
          ['approved', 'rejected', 'terminated'].includes(String(item.value)),
        ),
        width: 110,
      },
      title: '状态',
    },
    ...(tab === 'archived'
      ? [
          {
            dataIndex: 'archiveSource',
            options: {
              align: 'center' as const,
              columnType: 'select' as const,
              customRender: ({ record }: { record: ArchiveRecordInfo }) =>
                archiveSourceOptions.find((item) => item.value === record.archiveSource)
                  ?.label ?? '-',
              selectOptions: archiveSourceOptions,
              width: 130,
            },
            title: '归档来源',
          },
          {
            dataIndex: 'archiverRealname',
            options: {
              customRender: ({ record }: { record: ArchiveRecordInfo }) =>
                record.archiverRealname ?? record.archiverUsername ?? '-',
              width: 130,
            },
            title: '归档人',
          },
          {
            dataIndex: 'archiveTime',
            options: {
              align: 'center' as const,
              columnType: 'datetime' as const,
              width: 180,
            },
            title: '归档时间',
          },
        ]
      : []),
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: ArchiveRecordInfo }) =>
          h(
            Space,
            { size: 'middle' },
            () => [
              h('a', { onClick: () => emit('detail', record) }, '详情'),
              tab === 'archived'
                ? h('a', { onClick: () => emit('download', record) }, '下载')
                : null,
              tab === 'unarchived' && canManage
                ? h('a', { onClick: () => emit('archive', record) }, '归档')
                : null,
            ],
          ),
        fixed: 'right' as const,
        hasFilter: false,
        width: tab === 'unarchived' && canManage ? 150 : 120,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<ArchiveRecordInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: tab === 'archived' ? 1750 : 1370, tableKey: `workflow_archive_${tab}` },
  );
}
