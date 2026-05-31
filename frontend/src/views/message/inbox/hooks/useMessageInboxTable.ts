import type { MessageInboxInfo } from '#/api/message/message';

import { ref } from 'vue';

import { deleteInboxMessage, pageInboxMessages } from '#/api/message/message';
import { useTable } from '#/composables/Table';

export function useMessageInboxTable() {
  const table = useTable({
    apiConfig: {
      deleteItem: (id) => deleteInboxMessage(String(id)),
      fetchData: pageInboxMessages,
    },
    enableRowSelection: true,
    defaultSort: {
      sortField: 'sendTime',
      sortOrder: 'desc',
    },
    deleteConfig: {
      content: (record: MessageInboxInfo) =>
        `确认删除消息“${record.title ?? ''}”吗？`,
      title: '确认删除',
    },
    storageConfig: {
      filtersKey: 'message_inbox_filters',
    },
  });

  const loadData = async (extraFilters?: Record<string, unknown>) => {
    await table.loadData({}, extraFilters);
  };
  const selectedRowKeys =
    table.selectedRowKeys ?? ref<(number | string)[]>([]);
  const onSelectChange =
    table.onSelectChange ??
    ((keys: (number | string)[]) => {
      selectedRowKeys.value = keys;
    });

  return {
    ...table,
    loadData,
    onSelectChange,
    selectedRowKeys,
  };
}
