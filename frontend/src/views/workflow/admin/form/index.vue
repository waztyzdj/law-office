<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { message } from 'ant-design-vue';

import type { WorkflowFormDefinitionInfo } from '#/api/workflow';

import WorkflowFormDesignerDrawer from './components/WorkflowFormDesignerDrawer.vue';
import WorkflowFormCopyTemplateDrawer from './components/WorkflowFormCopyTemplateDrawer.vue';
import WorkflowFormDrawer from './components/WorkflowFormDrawer.vue';
import WorkflowFormHistoryDrawer from './components/WorkflowFormHistoryDrawer.vue';
import WorkflowFormTable from './components/WorkflowFormTable.vue';
import { useWorkflowFormTable } from './hooks/useWorkflowFormTable';

const {
  activeFilters,
  categoryMap,
  handleCopyAsDraft,
  handleDelete,
  handlePublish,
  handleRefresh,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
} = useWorkflowFormTable();

const formDrawerRef = ref<InstanceType<typeof WorkflowFormDrawer>>();
const copyTemplateDrawerRef =
  ref<InstanceType<typeof WorkflowFormCopyTemplateDrawer>>();
const designerDrawerRef = ref<InstanceType<typeof WorkflowFormDesignerDrawer>>();
const historyDrawerRef = ref<InstanceType<typeof WorkflowFormHistoryDrawer>>();

function handleAdd() {
  formDrawerRef.value?.open({ mode: 'create' });
}

function handleEdit(record: WorkflowFormDefinitionInfo) {
  if (record.status !== 'draft') {
    message.warning('已发布表单不允许编辑');
    return;
  }
  formDrawerRef.value?.open({ mode: 'edit', record });
}

function handleDesign(record: WorkflowFormDefinitionInfo) {
  if (record.status !== 'draft') {
    message.warning('已发布表单不允许设计');
    return;
  }
  designerDrawerRef.value?.open({ record });
}

function handleViewDesign(record: WorkflowFormDefinitionInfo) {
  designerDrawerRef.value?.open({ readonly: true, record });
}

function handleHistory(record: WorkflowFormDefinitionInfo) {
  historyDrawerRef.value?.open(record);
}

function handleCopyTemplate(record: WorkflowFormDefinitionInfo) {
  copyTemplateDrawerRef.value?.open({ record });
}

onMounted(handleRefresh);
</script>

<template>
  <div class="workflow-form-page">
    <WorkflowFormTable
      :active-filters="activeFilters"
      :category-map="categoryMap"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      @add="handleAdd"
      @change="handleTableChange"
      @copy-as-draft="handleCopyAsDraft"
      @copy-template="handleCopyTemplate"
      @delete="handleDelete"
      @design="handleDesign"
      @edit="handleEdit"
      @history="handleHistory"
      @publish="handlePublish"
      @view-design="handleViewDesign"
    />

    <WorkflowFormDrawer
      ref="formDrawerRef"
      @success="handleRefresh"
    />
    <WorkflowFormCopyTemplateDrawer
      ref="copyTemplateDrawerRef"
      @success="handleRefresh"
    />
    <WorkflowFormDesignerDrawer
      ref="designerDrawerRef"
      @success="loadData"
    />
    <WorkflowFormHistoryDrawer
      ref="historyDrawerRef"
      :category-map="categoryMap"
      @view-design="handleViewDesign"
    />
  </div>
</template>

<style scoped>
.workflow-form-page {
  padding: 16px;
}
</style>
