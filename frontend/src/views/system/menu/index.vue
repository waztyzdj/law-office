<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type { PermissionInfo as MenuInfo } from '#/api/system/permission';

import { deletePermission as deleteMenu } from '#/api/system/permission';

import MenuFormDrawer from './components/MenuFormDrawer.vue';
import MenuTable from './components/MenuTable.vue';
import { useMenuTable } from './hooks/useMenuTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loading,
  pagination,
  treeData,
  treeOptions,
} = useMenuTable();

const menuFormDrawerRef = ref();

function handleAdd() {
  menuFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: MenuInfo) {
  menuFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: MenuInfo) {
  menuFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: MenuInfo) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除"${record.name}"吗？如存在子级，请先处理子级权限。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      if (!record.id) {
        return;
      }
      await deleteMenu(record.id);
      message.success('删除成功');
      await loadData();
    },
  });
}

onMounted(loadData);
</script>

<template>
  <div class="system-menu-container">
    <MenuTable
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

    <MenuFormDrawer
      ref="menuFormDrawerRef"
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
