<script setup lang="ts">
import type { SysDictInfo, SysDictItemInfo } from '#/api/system/dict';

import { computed, h, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Button, Card, Empty, Modal, Space, Table, message } from 'ant-design-vue';

import {
  deleteDict,
  deleteDictItem,
  pageDictItems,
  pageDicts,
} from '#/api/system/dict';
import { permissionCodes } from '#/constants/permissions';
import { defineTableColumns, useTable } from '#/composables/Table';
import DictFormDrawer from './components/DictFormDrawer.vue';
import DictItemFormDrawer from './components/DictItemFormDrawer.vue';

const { hasAccessByCodes } = useAccess();
const can编辑Dict = computed(() => hasAccessByCodes([permissionCodes.dict.edit]));
const can编辑DictItem = computed(() =>
  hasAccessByCodes([permissionCodes.dictItem.edit]),
);

const dictFormDrawerRef = ref();
const dictItemFormDrawerRef = ref();
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
    content: (record: SysDictItemInfo) => `确认删除字典项“${record.itemText}”吗？`,
  },
});

const {
  activeFilters: dictActiveFilters,
  dataSource: dictDataSource,
  loading: dictLoading,
  pagination: dictPagination,
  handleTableChange: handleDictTableChange,
  loadData: loadDicts,
} = dictTable;

const {
  activeFilters: dictItemActiveFilters,
  dataSource: dictItemDataSource,
  loading: dictItemLoading,
  pagination: dictItemPagination,
  handleTableChange: handleDictItemTableChange,
  loadData: loadDictItems,
} = dictItemTable;

function emitDictTableChange(event: string, ...args: any[]) {
  if (event === 'change') {
    handleDictTableChange(args[0], args[1], args[2]);
  }
}

function emitDictItemTableChange(event: string, ...args: any[]) {
  if (event === 'change') {
    handleDictItemTableChange(args[0], args[1], args[2]);
  }
}

const dictTableConfig = computed(() => {
  const baseColumns: any[] = [
    {
      dataIndex: 'dictCode',
      title: '字典编码',
      options: { width: 160 },
    },
    {
      dataIndex: 'dictName',
      title: '字典名称',
      options: { width: 180 },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
  ];

  if (can编辑Dict.value) {
    baseColumns.push({
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 260,
        fixed: 'right' as const,
        hasFilter: false,
        customRender: ({ record }: { record: SysDictInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => handleSelectDict(record) }, '管理字典项'),
            h('a', { onClick: () => handle编辑Dict(record) }, '编辑'),
            h(
              'a',
              { style: { color: 'red' }, onClick: () => confirm删除Dict(record) },
              '删除',
            ),
          ]),
      },
    });
  }

  return defineTableColumns<SysDictInfo>(
    baseColumns,
    dictActiveFilters,
    emitDictTableChange,
    dictPagination,
    { minTableWidth: 1120 },
  );
});

const dictItemTableConfig = computed(() => {
  const baseColumns: any[] = [
    {
      dataIndex: 'itemText',
      title: '字典项文本',
      options: { width: 180 },
    },
    {
      dataIndex: 'itemValue',
      title: '字典项值',
      options: { width: 160 },
    },
    {
      dataIndex: 'sortOrder',
      title: '排序',
      options: { width: 100, columnType: 'number' as const },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 100,
        columnType: 'select' as const,
        selectOptions: [
          { label: '正常', value: 1, color: 'green' },
          { label: '冻结', value: 0, color: 'red' },
        ],
      },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
  ];

  if (can编辑DictItem.value) {
    baseColumns.push({
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 180,
        fixed: 'right' as const,
        hasFilter: false,
        customRender: ({ record }: { record: SysDictItemInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => handle编辑DictItem(record) }, '编辑'),
            h(
              'a',
              {
                style: { color: 'red' },
                onClick: () => confirm删除DictItem(record),
              },
              '删除',
            ),
          ]),
      },
    });
  }

  return defineTableColumns<SysDictItemInfo>(
    baseColumns,
    dictItemActiveFilters,
    emitDictItemTableChange,
    dictItemPagination,
    { minTableWidth: 1120 },
  );
});

function openDictCreate() {
  dictFormDrawerRef.value?.open({ mode: 'create' });
}

function handle编辑Dict(record: SysDictInfo) {
  dictFormDrawerRef.value?.open({ mode: 'edit', record });
}

async function handleDictSaveSuccess() {
  await refreshDictList(currentDict.value?.id);
}

async function handleSelectDict(record: SysDictInfo) {
  currentDict.value = record;
  dictItemTable.resetPagination();
  await loadDictItems({}, record.id ? { dictId: record.id } : {});
}

function openDictItemCreate() {
  if (!currentDict.value?.id) {
    return;
  }

  dictItemFormDrawerRef.value?.open({
    dictId: currentDict.value.id,
    dictName: currentDict.value.dictName,
    mode: 'create',
  });
}

function handle编辑DictItem(record: SysDictItemInfo) {
  dictItemFormDrawerRef.value?.open({
    dictId: currentDict.value?.id || record.dictId,
    dictName: currentDict.value?.dictName,
    mode: 'edit',
    record,
  });
}

async function handleDictItemSaveSuccess() {
  if (currentDict.value?.id) {
    await loadDictItems({}, { dictId: currentDict.value.id });
  }
}

async function refreshDictList(preferredDictId?: string) {
  await loadDicts();

  const nextDictId = preferredDictId ?? currentDict.value?.id;
  const nextDict = nextDictId
    ? dictDataSource.value.find((item) => item.id === nextDictId)
    : undefined;

  if (nextDict) {
    await handleSelectDict(nextDict);
    return;
  }

  currentDict.value = undefined;
  dictItemDataSource.value = [];
}

function confirm删除Dict(record: SysDictInfo) {
  if (!record.id) {
    return;
  }

  Modal.confirm({
    title: '确认删除',
    content: `删除 dictionary "${record.dictName}"?`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      await deleteDict(record.id);
      message.success('删除成功');
      await refreshDictList(record.id);
    },
  });
}

function confirm删除DictItem(record: SysDictItemInfo) {
  if (!record.id) {
    return;
  }

  Modal.confirm({
    title: '确认删除',
    content: `确认删除字典项“${record.itemText}”吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      await deleteDictItem(record.id);
      message.success('删除成功');
      if (currentDict.value?.id) {
        await loadDictItems({}, { dictId: currentDict.value.id });
      }
    },
  });
}

onMounted(async () => {
  await loadDicts();
});
</script>

<template>
  <div class="system-dict-container">
    <Card>
      <div class="table-toolbar">
        <Space>
          <Button
            v-access:code="permissionCodes.dict.edit"
            type="primary"
            @click="openDictCreate"
          >
            新增字典
          </Button>
        </Space>
      </div>

      <Table
        :columns="dictTableConfig.columns"
        :data-source="dictDataSource"
        :loading="dictLoading"
        :pagination="dictPagination"
        :scroll="dictTableConfig.scroll"
        bordered
        row-key="id"
        @change="handleDictTableChange"
      />
    </Card>

    <Card class="dict-item-card">
      <div class="table-toolbar">
        <Space>
          <Button
            v-access:code="permissionCodes.dictItem.edit"
            type="primary"
            :disabled="!currentDict?.id"
            @click="openDictItemCreate"
          >
            新增字典项
          </Button>
        </Space>
      </div>

      <div v-if="currentDict?.id">
        <Table
          :columns="dictItemTableConfig.columns"
          :data-source="dictItemDataSource"
          :loading="dictItemLoading"
          :pagination="dictItemPagination"
          :scroll="dictItemTableConfig.scroll"
          bordered
          row-key="id"
          @change="handleDictItemTableChange"
        />
      </div>
      <Empty
        v-else
        description="请选择一个字典后查看字典项"
      />
    </Card>

    <DictFormDrawer ref="dictFormDrawerRef" @success="handleDictSaveSuccess" />
    <DictItemFormDrawer
      ref="dictItemFormDrawerRef"
      @success="handleDictItemSaveSuccess"
    />
  </div>
</template>

<style scoped>
.system-dict-container {
  padding: 16px;
}

.dict-item-card {
  margin-top: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>

