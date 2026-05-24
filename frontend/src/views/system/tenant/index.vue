<script setup lang="ts">
import { onMounted, ref } from 'vue';

import type { TenantInfo } from '#/api/system/tenant';

import TenantFormDrawer from './components/TenantFormDrawer.vue';
import TenantTable from './components/TenantTable.vue';
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

function handleAdd() {
  tenantFormDrawerRef.value?.open({ mode: 'create' });
}

function handleEdit(record: TenantInfo) {
  tenantFormDrawerRef.value?.open({ mode: 'edit', record });
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
      @change="handleTableChange"
      @delete="handleDelete"
      @edit="handleEdit"
    />

    <TenantFormDrawer ref="tenantFormDrawerRef" @success="handleSaveSuccess" />
  </div>
</template>

<style scoped>
.system-tenant-container {
  padding: 16px;
}
</style>
