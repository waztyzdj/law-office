<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type { CategoryInfo } from '#/api/system/category';

import { deleteCategory } from '#/api/system/category';

import CategoryFormDrawer from './components/CategoryFormDrawer.vue';
import CategoryTable from './components/CategoryTable.vue';
import { useCategoryTable } from './hooks/useCategoryTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  treeData,
  treeOptions,
} = useCategoryTable();

const categoryFormDrawerRef = ref();

function handleAdd() {
  categoryFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: CategoryInfo) {
  categoryFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: CategoryInfo) {
  categoryFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: CategoryInfo) {
  if (!record.id) {
    return;
  }

  Modal.confirm({
    title: '确认删除',
    content: `确认删除类型“${record.name ?? ''}”吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      await deleteCategory(record.id);
      message.success('删除成功');
      await loadData();
    },
  });
}

async function handleSaveSuccess() {
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="system-category-container">
    <CategoryTable
      :active-filters="activeFilters"
      :data-source="treeData"
      :loading="loading"
      :pagination="pagination"
      @add="handleAdd"
      @add-child="handleAddChild"
      @change="handleTableChange"
      @delete="handleDelete"
      @edit="handleEdit"
    />

    <CategoryFormDrawer
      ref="categoryFormDrawerRef"
      :tree-options="treeOptions"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.system-category-container {
  padding: 16px;
}
</style>
