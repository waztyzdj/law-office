<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { SysDictInfo, SysDictItemInfo } from '#/api/system/dict';

import DictFormDrawer from './components/DictFormDrawer.vue';
import DictItemFormDrawer from './components/DictItemFormDrawer.vue';
import DictItemTable from './components/DictItemTable.vue';
import DictTable from './components/DictTable.vue';
import { useDictTable } from './hooks/useDictTable';

const {
  confirmDeleteDict,
  confirmDeleteDictItem,
  currentDict,
  dictItemTable,
  dictTable,
  loadDictItems,
  loadDicts,
  refreshDictList,
  selectDict,
} = useDictTable();

const {
  activeFilters: dictActiveFilters,
  dataSource: dictDataSource,
  handleTableChange: handleDictTableChange,
  loading: dictLoading,
  pagination: dictPagination,
} = dictTable;

const {
  activeFilters: dictItemActiveFilters,
  dataSource: dictItemDataSource,
  handleTableChange: handleDictItemTableChange,
  loading: dictItemLoading,
  pagination: dictItemPagination,
} = dictItemTable;

const dictFormDrawerRef = ref();
const dictItemFormDrawerRef = ref();

function openDictCreate() {
  dictFormDrawerRef.value?.open({ mode: 'create' });
}

function handleEditDict(record: SysDictInfo) {
  dictFormDrawerRef.value?.open({ mode: 'edit', record });
}

async function handleDictSaveSuccess() {
  await refreshDictList(currentDict.value?.id);
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

function handleEditDictItem(record: SysDictItemInfo) {
  dictItemFormDrawerRef.value?.open({
    dictId: currentDict.value?.id || record.dictId,
    dictName: currentDict.value?.dictName,
    mode: 'edit',
    record,
  });
}

async function handleDictItemSaveSuccess() {
  await loadDictItems();
}

onMounted(loadDicts);
</script>

<template>
  <div class="system-dict-container">
    <DictTable
      :active-filters="dictActiveFilters"
      :data-source="dictDataSource"
      :loading="dictLoading"
      :pagination="dictPagination"
      @add="openDictCreate"
      @change="handleDictTableChange"
      @delete="confirmDeleteDict"
      @edit="handleEditDict"
      @select="selectDict"
    />

    <DictItemTable
      :active-filters="dictItemActiveFilters"
      :current-dict="currentDict"
      :data-source="dictItemDataSource"
      :loading="dictItemLoading"
      :pagination="dictItemPagination"
      @add="openDictItemCreate"
      @change="handleDictItemTableChange"
      @delete="confirmDeleteDictItem"
      @edit="handleEditDictItem"
    />

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
</style>
