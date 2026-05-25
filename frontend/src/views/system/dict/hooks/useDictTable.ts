import { ref } from 'vue';

import { message, Modal } from 'ant-design-vue';

import type { SysDictInfo, SysDictItemInfo } from '#/api/system/dict';

import {
  deleteDict,
  deleteDictItem,
  pageDictItems,
  pageDicts,
} from '#/api/system/dict';
import { useTable } from '#/composables/Table';

export function useDictTable() {
  const currentDict = ref<SysDictInfo>();

  const dictTable = useTable({
    apiConfig: {
      fetchData: pageDicts,
      deleteItem: deleteDict,
    },
    storageConfig: {
      filtersKey: 'dict_list_filters',
    },
    deleteConfig: {
      title: '确认删除',
      content: (record: SysDictInfo) => `确认删除字典“${record.dictName}”吗？`,
    },
  });

  const dictItemTable = useTable({
    apiConfig: {
      fetchData: pageDictItems,
      deleteItem: deleteDictItem,
    },
    storageConfig: {
      filtersKey: 'dict_item_list_filters',
    },
    deleteConfig: {
      title: '确认删除',
      content: (record: SysDictItemInfo) =>
        `确认删除字典项“${record.itemText}”吗？`,
    },
  });

  async function loadDicts(extraFilters?: Record<string, any>) {
    await dictTable.loadData({}, extraFilters);
  }

  async function loadDictItems(extraFilters?: Record<string, any>) {
    if (!currentDict.value?.id) {
      dictItemTable.dataSource.value = [];
      return;
    }

    await dictItemTable.loadData({}, {
      dictId: currentDict.value.id,
      ...extraFilters,
    });
  }

  async function selectDict(record: SysDictInfo) {
    currentDict.value = record;
    dictItemTable.resetPagination();
    await loadDictItems();
  }

  async function refreshDictList(preferredDictId?: string) {
    await loadDicts();

    const nextDictId = preferredDictId ?? currentDict.value?.id;
    const nextDict = nextDictId
      ? dictTable.dataSource.value.find((item) => item.id === nextDictId)
      : undefined;

    if (nextDict) {
      await selectDict(nextDict);
      return;
    }

    currentDict.value = undefined;
    dictItemTable.dataSource.value = [];
  }

  function confirmDeleteDict(record: SysDictInfo) {
    if (!record.id) {
      return;
    }

    Modal.confirm({
      title: '确认删除',
      content: `确认删除字典“${record.dictName}”吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await deleteDict(record.id!);
        message.success('删除成功');
        await refreshDictList(record.id!);
      },
    });
  }

  function confirmDeleteDictItem(record: SysDictItemInfo) {
    if (!record.id) {
      return;
    }

    Modal.confirm({
      title: '确认删除',
      content: `确认删除字典项“${record.itemText}”吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await deleteDictItem(record.id!);
        message.success('删除成功');
        await loadDictItems();
      },
    });
  }

  return {
    currentDict,
    dictTable,
    dictItemTable,
    confirmDeleteDict,
    confirmDeleteDictItem,
    loadDicts,
    loadDictItems,
    refreshDictList,
    selectDict,
  };
}
