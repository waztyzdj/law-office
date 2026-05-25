<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { TenantInfo } from '#/api/system/tenant';

import TenantAdminDrawer from './components/TenantAdminDrawer.vue';
import TenantAdminPermissionDrawer from './components/TenantAdminPermissionDrawer.vue';
import TenantFormDrawer from './components/TenantFormDrawer.vue';
import TenantTable from './components/TenantTable.vue';
import TenantUserDrawer from './components/TenantUserDrawer.vue';
import { useTenantTable } from './hooks/useTenantTable';

const {
  activeFilters,
  dataSource,
  handleDelete,
  handleTableChange,
  loadData,
  loading,
  pagination,
} = useTenantTable();

const tenantFormDrawerRef = ref();
const tenantUserDrawerRef = ref();
const tenantAdminDrawerRef = ref();
const tenantAdminPermissionDrawerRef = ref();

function handleAdd() {
  tenantFormDrawerRef.value?.open({ mode: 'create' });
}

function handleEdit(record: TenantInfo) {
  tenantFormDrawerRef.value?.open({ mode: 'edit', record });
}

function handleUsers(record: TenantInfo) {
  tenantUserDrawerRef.value?.open(record);
}

function handleAdmins(record: TenantInfo) {
  tenantAdminDrawerRef.value?.open(record);
}

function handleAdminPermissions(record: TenantInfo) {
  tenantAdminPermissionDrawerRef.value?.open(record);
}

function handleSaveSuccess() {
  loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="system-tenant-container">
    <TenantTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      @add="handleAdd"
      @admin-permissions="handleAdminPermissions"
      @admins="handleAdmins"
      @change="handleTableChange"
      @delete="handleDelete"
      @edit="handleEdit"
      @users="handleUsers"
    />

    <TenantFormDrawer ref="tenantFormDrawerRef" @success="handleSaveSuccess" />
    <TenantUserDrawer ref="tenantUserDrawerRef" @success="handleSaveSuccess" />
    <TenantAdminDrawer ref="tenantAdminDrawerRef" @success="handleSaveSuccess" />
    <TenantAdminPermissionDrawer
      ref="tenantAdminPermissionDrawerRef"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.system-tenant-container {
  padding: 16px;
}
</style>
