<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type { WorkflowCategoryInfo } from '#/api/workflow';

import { deleteWorkflowCategory } from '#/api/workflow';

import WorkflowCategoryFormDrawer from './components/WorkflowCategoryFormDrawer.vue';
import WorkflowCategoryTable from './components/WorkflowCategoryTable.vue';
import { useWorkflowCategoryTable } from './hooks/useWorkflowCategoryTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  treeData,
  treeOptions,
} = useWorkflowCategoryTable();

const categoryFormDrawerRef =
  ref<InstanceType<typeof WorkflowCategoryFormDrawer>>();

function handleAdd() {
  categoryFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: WorkflowCategoryInfo) {
  categoryFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: WorkflowCategoryInfo) {
  categoryFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: WorkflowCategoryInfo) {
  if (!record.id) {
    return;
  }

  Modal.confirm({
    cancelText: '取消',
    content: `确认删除流程分类“${record.categoryName ?? ''}”吗？`,
    okText: '确认',
    onOk: async () => {
      await deleteWorkflowCategory(record.id!);
      message.success('删除成功');
      await loadData();
    },
    title: '确认删除',
  });
}

async function handleSaveSuccess() {
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="workflow-category-page">
    <WorkflowCategoryTable
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

    <WorkflowCategoryFormDrawer
      ref="categoryFormDrawerRef"
      :tree-options="treeOptions"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.workflow-category-page {
  padding: 16px;
}
</style>
