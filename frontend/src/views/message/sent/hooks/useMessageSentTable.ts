import type { MessageSentInfo } from '#/api/message/message';

import { ref } from 'vue';

import { deleteSentMessage, pageSentMessages } from '#/api/message/message';
import { useTable } from '#/composables/Table';

export function useMessageSentTable() {
  const table = useTable({
    apiConfig: {
      deleteItem: (id) => deleteSentMessage(String(id)),
      fetchData: pageSentMessages,
    },
    enableRowSelection: true,
    defaultSort: {
      sortField: 'sendTime',
      sortOrder: 'desc',
    },
    deleteConfig: {
      content: (record: MessageSentInfo) =>
        `确认删除发件消息“${record.title ?? ''}”吗？`,
      title: '确认删除',
    },
    storageConfig: {
      filtersKey: 'message_sent_filters',
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
