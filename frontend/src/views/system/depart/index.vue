<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Modal, message } from 'ant-design-vue';

import type { DepartInfo } from '#/api/system/depart';

import { deleteDepart } from '#/api/system/depart';

import DepartFormDrawer from './components/DepartFormDrawer.vue';
import DepartTable from './components/DepartTable.vue';
import { useDepartTable } from './hooks/useDepartTable';

const {
  activeFilters,
  handleTableChange,
  loadData,
  loadOrgTypeOptions,
  loading,
  orgTypeOptions,
  orgTypeSelectOptions,
  pagination,
  treeData,
  treeOptions,
} = useDepartTable();

const departFormDrawerRef = ref();

function handleAdd() {
  departFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: DepartInfo) {
  departFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: DepartInfo) {
  departFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: DepartInfo) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除机构"${record.departName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      if (!record.id) {
        return;
      }
      await deleteDepart(record.id);
      await loadData();
    },
  });
}

function handleSaveSuccess() {
  loadData();
}

async function initPage() {
  await Promise.all([loadOrgTypeOptions(), loadData()]);

  if (orgTypeOptions.value.length === 0) {
    message.warning('机构类型字典为空，请先维护 sys_depart_org_type');
  }
}

onMounted(initPage);
</script>

<template>
  <div class="system-depart-container">
    <DepartTable
      :active-filters="activeFilters"
      :data-source="treeData"
      :loading="loading"
      :org-type-select-options="orgTypeSelectOptions"
      :pagination="pagination"
      @add="handleAdd"
      @add-child="handleAddChild"
      @change="handleTableChange"
      @delete="handleDelete"
      @edit="handleEdit"
    />

    <DepartFormDrawer
      ref="departFormDrawerRef"
      :org-type-options="orgTypeOptions"
      :tree-options="treeOptions"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.system-depart-container {
  padding: 16px;
}
</style>
