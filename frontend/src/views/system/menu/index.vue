<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type { PermissionInfo } from '#/api/system/permission';

import { deletePermission } from '#/api/system/permission';

import PermissionFormDrawer from './components/PermissionFormDrawer.vue';
import PermissionTable from './components/PermissionTable.vue';
import { usePermissionTable } from './hooks/usePermissionTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  treeData,
  treeOptions,
} = usePermissionTable();

const permissionFormDrawerRef = ref();

function handleAdd() {
  permissionFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: PermissionInfo) {
  permissionFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: PermissionInfo) {
  permissionFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: PermissionInfo) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除"${record.name}"吗？如存在子级，请先处理子级权限。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      if (!record.id) {
        return;
      }
      await deletePermission(record.id);
      message.success('删除成功');
      await loadData();
    },
  });
}

onMounted(loadData);
</script>

<template>
  <div class="system-menu-container">
    <PermissionTable
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

    <PermissionFormDrawer
      ref="permissionFormDrawerRef"
      :tree-options="treeOptions"
      @success="loadData"
    />
  </div>
</template>

<style scoped>
.system-menu-container {
  padding: 16px;
}
</style>
