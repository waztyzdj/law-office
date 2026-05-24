<script setup lang="ts">
import { onMounted } from 'vue';

import LogTable from './components/LogTable.vue';
import { useLogTable } from './hooks/useLogTable';

const {
  activeFilters,
  dataSource,
  handleDelete,
  handleTableChange,
  loadData,
  loading,
  pagination,
} = useLogTable();

onMounted(loadData);
</script>

<template>
  <div class="system-log-container">
    <LogTable
      :active-filters="activeFilters"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      @change="handleTableChange"
      @delete="handleDelete"
    />
  </div>
</template>

<style scoped>
.system-log-container {
  padding: 16px;
}
</style>
