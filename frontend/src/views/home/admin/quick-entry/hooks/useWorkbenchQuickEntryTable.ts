import type {
  WorkbenchQuickEntryInfo,
  WorkbenchStatus,
} from '#/api/home/workbench';

import { message, Modal } from 'ant-design-vue';

import {
  pageWorkbenchQuickEntries,
  updateWorkbenchQuickEntryStatus,
} from '#/api/home/workbench';
import { useTable } from '#/composables/Table';

export function useWorkbenchQuickEntryTable() {
  const table = useTable({
    apiConfig: {
      fetchData: pageWorkbenchQuickEntries,
    },
    storageConfig: {
      filtersKey: 'home_workbench_quick_entry_filters',
    },
  });

  async function loadData(extraFilters?: Record<string, any>) {
    await table.loadData({}, extraFilters);
  }

  function handleStatus(record: WorkbenchQuickEntryInfo) {
    if (!record.id || !record.status) {
      return;
    }
    const nextStatus: WorkbenchStatus =
      record.status === 'enabled' ? 'disabled' : 'enabled';
    Modal.confirm({
      title: nextStatus === 'enabled' ? '确认启用' : '确认停用',
      content: `确认${nextStatus === 'enabled' ? '启用' : '停用'}快捷菜单“${
        record.entryName ?? ''
      }”吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await updateWorkbenchQuickEntryStatus(record.id!, nextStatus);
        message.success(nextStatus === 'enabled' ? '启用成功' : '停用成功');
        await loadData();
      },
    });
  }

  return {
    ...table,
    handleStatus,
    loadData,
  };
}
