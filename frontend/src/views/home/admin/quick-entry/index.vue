<script setup lang="ts">
import type { WorkbenchQuickEntryInfo } from '#/api/home/workbench';

import { onMounted, ref } from 'vue';

import WorkbenchQuickEntryFormDrawer from './components/WorkbenchQuickEntryFormDrawer.vue';
import WorkbenchQuickEntryTable from './components/WorkbenchQuickEntryTable.vue';
import { useWorkbenchQuickEntryTable } from './hooks/useWorkbenchQuickEntryTable';

const {
  activeFilters,
  dataSource,
  handleStatus,
  handleTableChange,
  loadData,
  loading,
  pagination,
} = useWorkbenchQuickEntryTable();

const formDrawerRef = ref<InstanceType<typeof WorkbenchQuickEntryFormDrawer>>();

function handleAdd() {
  formDrawerRef.value?.open({ mode: 'create' });
}

function handleEdit(record: WorkbenchQuickEntryInfo) {
  formDrawerRef.value?.open({ mode: 'edit', record });
}

async function handleSaveSuccess() {
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="workbench-quick-entry-admin-page">
    <WorkbenchQuickEntryTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      @add="handleAdd"
      @change="handleTableChange"
      @edit="handleEdit"
      @status="handleStatus"
    />
    <WorkbenchQuickEntryFormDrawer
      ref="formDrawerRef"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.workbench-quick-entry-admin-page {
  padding: 16px;
}
</style>
