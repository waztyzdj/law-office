import type { WorkbenchCardInfo, WorkbenchStatus } from '#/api/home/workbench';

import { message, Modal } from 'ant-design-vue';

import {
  pageWorkbenchCards,
  updateWorkbenchCardSort,
  updateWorkbenchCardStatus,
} from '#/api/home/workbench';
import { useTable } from '#/composables/Table';

export function useWorkbenchCardTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageWorkbenchCards,
    },
    storageConfig: {
      filtersKey: 'home_workbench_card_filters',
    },
  });

  async function loadData(extraFilters?: Record<string, any>) {
    await table.loadData({}, extraFilters);
  }

  function handleStatus(record: WorkbenchCardInfo) {
    if (!record.id || !record.status) {
      return;
    }
    const nextStatus: WorkbenchStatus =
      record.status === 'enabled' ? 'disabled' : 'enabled';
    Modal.confirm({
      title: nextStatus === 'enabled' ? '确认启用' : '确认停用',
      content: `确认${nextStatus === 'enabled' ? '启用' : '停用'}卡片“${
        record.cardName ?? ''
      }”吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await updateWorkbenchCardStatus(record.id!, nextStatus);
        message.success(nextStatus === 'enabled' ? '启用成功' : '停用成功');
        await loadData();
      },
    });
  }

  async function saveCurrentSort() {
    const items = (table.dataSource.value as WorkbenchCardInfo[])
      .filter((item) => item.id)
      .map((item) => ({
        id: item.id!,
        defaultSort: item.defaultSort ?? 0,
      }));
    if (items.length === 0) {
      message.warning('暂无可保存的排序项');
      return;
    }
    await updateWorkbenchCardSort({ items });
    message.success('排序已保存');
    await loadData();
  }

  return {
    ...table,
    handleStatus,
    loadData,
    saveCurrentSort,
  };
}
