<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { RoleInfo } from '#/api/system/role';
import RoleFormDrawer from './components/RoleFormDrawer.vue';
import RolePermissionDrawer from './components/RolePermissionDrawer.vue';
import RoleTable from './components/RoleTable.vue';
import { useRoleTable } from './hooks/useRoleTable';

const {
  activeFilters,
  clearAllFilters,
  dataSource,
  handleDelete,
  handleTableChange,
  loadData,
  loading,
  pagination,
} = useRoleTable();

const roleFormDrawerRef = ref();
const rolePermissionDrawerRef = ref();

function handleAdd() {
  roleFormDrawerRef.value?.open({ mode: 'create' });
}

function handleEdit(record: RoleInfo) {
  roleFormDrawerRef.value?.open({ mode: 'edit', record });
}

function handleAssign(record: RoleInfo) {
  rolePermissionDrawerRef.value?.open(record);
}

function handleSaveSuccess() {
  loadData();
}

onMounted(() => {
  clearAllFilters();
  loadData();
});
</script>

<template>
  <div class="system-role-container">
    <RoleTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      @add="handleAdd"
      @assign="handleAssign"
      @change="handleTableChange"
      @delete="handleDelete"
      @edit="handleEdit"
    />
    <RoleFormDrawer ref="roleFormDrawerRef" @success="handleSaveSuccess" />
    <RolePermissionDrawer ref="rolePermissionDrawerRef" @success="handleSaveSuccess" />
  </div>
</template>

<style scoped>
.system-role-container {
  padding: 16px;
}
</style>
