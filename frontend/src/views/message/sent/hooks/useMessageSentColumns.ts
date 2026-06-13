import type { MessageSentInfo } from '#/api/message/message';
import type {
  FilterCondition,
  TableColumnOptions,
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';
import type { Ref } from 'vue';

import { h } from 'vue';

import { Space } from 'ant-design-vue';

import { defineTableColumns } from '#/composables/Table';

import {
  messageTypeOptions,
  priorityOptions,
  sendStatusOptions,
} from '../../constants';

export function getSentColumns(
  filterState: Ref<Record<string, FilterCondition | unknown>>,
  emit: (
    event: 'delete' | 'recall' | 'view',
    record: MessageSentInfo,
  ) => void,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns: Array<{
    dataIndex: string;
    options?: TableColumnOptions<MessageSentInfo>;
    title: string;
  }> = [
    {
      dataIndex: 'title',
      title: '消息标题',
      options: { width: 280 },
    },
    {
      dataIndex: 'messageType',
      title: '消息类型',
      options: {
        columnType: 'select' as const,
        selectOptions: messageTypeOptions,
        width: 120,
      },
    },
    {
      dataIndex: 'priority',
      title: '优先级',
      options: {
        columnType: 'select' as const,
        selectOptions: priorityOptions,
        width: 100,
      },
    },
    {
      dataIndex: 'sendStatus',
      title: '发送状态',
      options: {
        columnType: 'select' as const,
        selectOptions: sendStatusOptions,
        width: 110,
      },
    },
    {
      dataIndex: 'receiverCount',
      title: '接收人数',
      options: {
        columnType: 'number' as const,
        hasFilter: false,
        sorter: false,
        width: 110,
      },
    },
    {
      dataIndex: 'readCount',
      title: '已读人数',
      options: {
        columnType: 'number' as const,
        hasFilter: false,
        sorter: false,
        width: 110,
      },
    },
    {
      dataIndex: 'sendTime',
      title: '发送时间',
      options: { columnType: 'datetime' as const, width: 180 },
    },
    {
      dataIndex: 'action',
      title: '操作',
      options: {
        fixed: 'right' as const,
        hasFilter: false,
        width: 160,
        customRender: ({ record }: { record: MessageSentInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => emit('view', record) }, '查看'),
            record.sendStatus === 1 && (record.readCount || 0) === 0
              ? h('a', { onClick: () => emit('recall', record) }, '撤回')
              : null,
            h(
              'a',
              { onClick: () => emit('delete', record), style: { color: 'red' } },
              '删除',
            ),
          ]),
      },
    },
  ];

  return defineTableColumns<MessageSentInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1260, tableKey: 'message_sent' },
  );
}
