<script setup lang="ts">
import type { WorkbenchCardInfo } from '#/api/home/workbench';

import { onMounted, ref } from 'vue';

import WorkbenchCardFormDrawer from './components/WorkbenchCardFormDrawer.vue';
import WorkbenchCardTable from './components/WorkbenchCardTable.vue';
import { useWorkbenchCardTable } from './hooks/useWorkbenchCardTable';

const {
  activeFilters,
  dataSource,
  handleStatus,
  handleTableChange,
  loadData,
  loading,
  pagination,
  saveCurrentSort,
} = useWorkbenchCardTable();

const formDrawerRef = ref<InstanceType<typeof WorkbenchCardFormDrawer>>();

function handleAdd() {
  formDrawerRef.value?.open({ mode: 'create' });
}

function handleEdit(record: WorkbenchCardInfo) {
  formDrawerRef.value?.open({ mode: 'edit', record });
}

async function handleSaveSuccess() {
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="workbench-card-admin-page">
    <WorkbenchCardTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      @add="handleAdd"
      @change="handleTableChange"
      @edit="handleEdit"
      @save-sort="saveCurrentSort"
      @status="handleStatus"
    />
    <WorkbenchCardFormDrawer ref="formDrawerRef" @success="handleSaveSuccess" />
  </div>
</template>

<style scoped>
.workbench-card-admin-page {
  padding: 16px;
}
</style>
