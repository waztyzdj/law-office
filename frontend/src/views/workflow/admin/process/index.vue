<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { message } from 'ant-design-vue';

import type { WorkflowProcessModelInfo } from '#/api/workflow';

import WorkflowFieldPermissionDrawer from './components/WorkflowFieldPermissionDrawer.vue';
import WorkflowProcessHistoryDrawer from './components/WorkflowProcessHistoryDrawer.vue';
import WorkflowProcessTable from './components/WorkflowProcessTable.vue';
import BpmnProcessDesignerDrawer from './components/BpmnProcessDesignerDrawer.vue';
import SimpleProcessDesignerDrawer from './components/SimpleProcessDesignerDrawer.vue';
import WorkflowProcessDrawer from './components/WorkflowProcessDrawer.vue';
import { useWorkflowProcessTable } from './hooks/useWorkflowProcessTable';

const {
  activeFilters,
  categoryMap,
  formMap,
  handleCopyAsDraft,
  handleDelete,
  handlePublish,
  handleRefresh,
  handleTableChange,
  loadData,
  loading,
  pagination,
  records,
} = useWorkflowProcessTable();

const processDrawerRef = ref<InstanceType<typeof WorkflowProcessDrawer>>();
const designerDrawerRef = ref<InstanceType<typeof SimpleProcessDesignerDrawer>>();
const bpmnDesignerDrawerRef = ref<InstanceType<typeof BpmnProcessDesignerDrawer>>();
const fieldPermissionDrawerRef =
  ref<InstanceType<typeof WorkflowFieldPermissionDrawer>>();
const historyDrawerRef =
  ref<InstanceType<typeof WorkflowProcessHistoryDrawer>>();

function handleAddSimple() {
  processDrawerRef.value?.open({ designerType: 'simple', mode: 'create' });
}

function handleImportBpmn() {
  processDrawerRef.value?.open({ designerType: 'bpmn', mode: 'create' });
}

function handleEdit(record: WorkflowProcessModelInfo) {
  if (record.status !== 'draft') {
    message.warning('已发布流程不允许编辑');
    return;
  }
  processDrawerRef.value?.open({ mode: 'edit', record });
}

function handleDesign(record: WorkflowProcessModelInfo) {
  if (record.status !== 'draft') {
    message.warning('已发布流程不允许设计');
    return;
  }
  openDesigner(record);
}

function handleViewDesign(record: WorkflowProcessModelInfo) {
  openDesigner(record);
}

function openDesigner(record: WorkflowProcessModelInfo) {
  if (record.designerType === 'bpmn') {
    bpmnDesignerDrawerRef.value?.open({ record });
    return;
  }
  designerDrawerRef.value?.open({ record });
}

function handleFieldPermission(record: WorkflowProcessModelInfo) {
  fieldPermissionDrawerRef.value?.open({ record });
}

function handleHistory(record: WorkflowProcessModelInfo) {
  historyDrawerRef.value?.open(record);
}

onMounted(handleRefresh);
</script>

<template>
  <div class="workflow-process-page">
    <WorkflowProcessTable
      :active-filters="activeFilters"
      :category-map="categoryMap"
      :data-source="records"
      :form-map="formMap"
      :loading="loading"
      :pagination="pagination"
      @add-simple="handleAddSimple"
      @change="handleTableChange"
      @copy-as-draft="handleCopyAsDraft"
      @delete="handleDelete"
      @design="handleDesign"
      @edit="handleEdit"
      @field-permission="handleFieldPermission"
      @history="handleHistory"
      @import-bpmn="handleImportBpmn"
      @publish="handlePublish"
      @view-design="handleViewDesign"
    />

    <WorkflowProcessDrawer
      ref="processDrawerRef"
      @success="handleRefresh"
    />
    <BpmnProcessDesignerDrawer
      ref="bpmnDesignerDrawerRef"
      @success="loadData"
    />
    <SimpleProcessDesignerDrawer
      ref="designerDrawerRef"
      @success="loadData"
    />
    <WorkflowFieldPermissionDrawer
      ref="fieldPermissionDrawerRef"
    />
    <WorkflowProcessHistoryDrawer
      ref="historyDrawerRef"
      :category-map="categoryMap"
      :form-map="formMap"
      @view-design="handleViewDesign"
    />
  </div>
</template>

<style scoped>
.workflow-process-page {
  padding: 16px;
}
</style>
