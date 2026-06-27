import type { MessageInboxInfo } from '#/api/message/message';
import type {
  FilterCondition,
  SelectOption,
  TableColumnOptions,
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';
import type { Ref } from 'vue';

import { h } from 'vue';

import { Space, Tag } from 'ant-design-vue';

import { defineTableColumns } from '#/composables/Table';

import {
  getMessageTypeMeta,
  messageTypeOptions,
  priorityOptions,
  readStatusOptions,
} from '../../constants';

export function getInboxColumns(
  filterState: Ref<Record<string, FilterCondition | unknown>>,
  emit: (
    event: 'delete' | 'star' | 'view',
    record: MessageInboxInfo,
  ) => void,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const starFlagOptions: SelectOption[] = [
    { color: 'default', label: '否', value: 0 },
    { color: 'gold', label: '是', value: 1 },
  ];
  const columns: Array<{
    dataIndex: string;
    options?: TableColumnOptions<MessageInboxInfo>;
    title: string;
  }> = [
    {
      dataIndex: 'title',
      title: '消息标题',
      options: {
        sorter: false,
        width: 260,
        customRender: ({ record }: { record: MessageInboxInfo }) =>
          h(
            'span',
            {
              style: {
                fontWeight: record.readStatus === 0 ? 600 : 400,
              },
            },
            record.title || '-',
          ),
      },
    },
    {
      dataIndex: 'senderName',
      title: '发送人',
      options: { sorter: false, width: 140 },
    },
    {
      dataIndex: 'messageType',
      title: '消息类型',
      options: {
        columnType: 'select' as const,
        customRender: ({ record }: { record: MessageInboxInfo }) => {
          const meta = getMessageTypeMeta(record.messageType, record.bizType);
          return meta.color
            ? h(Tag, { color: meta.color }, () => meta.label)
            : h('span', {}, meta.label);
        },
        selectOptions: messageTypeOptions,
        sorter: false,
        width: 120,
      },
    },
    {
      dataIndex: 'priority',
      title: '优先级',
      options: {
        columnType: 'select' as const,
        selectOptions: priorityOptions,
        sorter: false,
        width: 100,
      },
    },
    {
      dataIndex: 'readStatus',
      title: '阅读状态',
      options: {
        columnType: 'select' as const,
        selectOptions: readStatusOptions,
        width: 110,
      },
    },
    {
      dataIndex: 'starFlag',
      title: '收藏',
      options: {
        columnType: 'select' as const,
        selectOptions: starFlagOptions,
        width: 90,
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
        width: 180,
        customRender: ({ record }: { record: MessageInboxInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => emit('view', record) }, '查看'),
            h(
              'a',
              { onClick: () => emit('star', record) },
              record.starFlag === 1 ? '取消收藏' : '收藏',
            ),
            h(
              'a',
              { onClick: () => emit('delete', record), style: { color: 'red' } },
              '删除',
            ),
          ]),
      },
    },
  ];

  return defineTableColumns<MessageInboxInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 1200, tableKey: 'message_inbox' },
  );
}
