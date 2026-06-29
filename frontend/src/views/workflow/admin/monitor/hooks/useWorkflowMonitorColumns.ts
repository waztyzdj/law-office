import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { AdminMonitorInstanceInfo } from '#/api/workflow';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { permissionCodes } from '#/constants/permissions';
import { defineTableColumns } from '#/composables/Table';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';
import { processInstanceStatusOptions } from '../../../components/status';

const finishedStatuses = new Set(['approved', 'rejected', 'terminated', 'withdrawn']);

function isFinished(status?: string) {
  return finishedStatuses.has(status || '');
}

export function getWorkflowMonitorColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canManage = hasAccessByCodes([permissionCodes.workflowMonitor.manage]);
  const columns = [
    { dataIndex: 'instanceTitle', options: { width: 260 }, title: '标题' },
    {
      dataIndex: 'processName',
      options: { width: 180 },
      title: '流程名称',
    },
    {
      dataIndex: 'processVersion',
      options: {
        align: 'center' as const,
        customRender: ({ record }: { record: AdminMonitorInstanceInfo }) =>
          record.processVersion ? `v${record.processVersion}` : '-',
        width: 90,
      },
      title: '版本',
    },
    {
      dataIndex: 'starterRealname',
      options: {
        customRender: ({ record }: { record: AdminMonitorInstanceInfo }) =>
          record.starterRealname ?? record.starterUsername ?? '-',
        width: 130,
      },
      title: '发起人',
    },
    {
      dataIndex: 'currentTaskNames',
      options: {
        customRender: ({ record }: { record: AdminMonitorInstanceInfo }) =>
          isFinished(record.status) ? '已结束' : record.currentTaskNames || '-',
        width: 180,
      },
      title: '当前节点',
    },
    {
      dataIndex: 'currentAssigneeNames',
      options: {
        customRender: ({ record }: { record: AdminMonitorInstanceInfo }) =>
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
      dataIndex: 'updateTime',
      options: { align: 'center' as const, columnType: 'datetime' as const, width: 180 },
      title: '更新时间',
    },
    {
      dataIndex: 'status',
      options: {
        align: 'center' as const,
        columnType: 'select' as const,
        customRender: ({ record }: { record: AdminMonitorInstanceInfo }) =>
          h(WorkflowStatusTag, { status: record.status }),
        selectOptions: processInstanceStatusOptions,
        width: 110,
      },
      title: '状态',
    },
    {
      dataIndex: 'action',
      options: {
        customRender: ({ record }: { record: AdminMonitorInstanceInfo }) =>
          h(
            Space,
            { size: 'middle' },
            () => [
              h('a', { onClick: () => emit('detail', record) }, '详情'),
              canManage && record.canMaintain && Number(record.todoTaskCount ?? 0) > 0
                ? h('a', { onClick: () => emit('reassign', record) }, '改派')
                : null,
              canManage && record.canMaintain
                ? h('a', { onClick: () => emit('resendNotice', record) }, '补发通知')
                : null,
              canManage && record.canMaintain
                ? h(
                    'a',
                    { class: 'text-red-500', onClick: () => emit('terminate', record) },
                    '终止',
                  )
                : null,
            ],
          ),
        fixed: 'right' as const,
        hasFilter: false,
        width: canManage ? 260 : 100,
      },
      title: '操作',
    },
  ];

  return defineTableColumns<AdminMonitorInstanceInfo>(
    columns,
    filterState,
    emit,
    pagination,
    { minTableWidth: 1550, tableKey: 'workflow_monitor' },
  );
}
