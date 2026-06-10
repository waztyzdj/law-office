<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';

import { onMounted } from 'vue';

import { Empty, Spin } from 'ant-design-vue';

import TaskActionBar from './components/TaskActionBar.vue';
import TaskActionModal from './components/TaskActionModal.vue';
import TaskHeaderCard from './components/TaskHeaderCard.vue';
import TaskRuntimeFormCard from './components/TaskRuntimeFormCard.vue';
import type { WorkflowTaskFormExpose } from './hooks/useWorkflowTaskPage';
import { useWorkflowTaskPage } from './hooks/useWorkflowTaskPage';

const {
  actionForm,
  actionModalOpen,
  actionModalTitle,
  actionSubmitting,
  businessKey,
  currentAction,
  formDataJson,
  formOptionJson,
  formSchemaJson,
  handleActionConfirm,
  handleApprove,
  handleBack,
  handleStart,
  hasRequiredParams,
  instanceTitle,
  isStartDraftMode,
  isStartMode,
  loadData,
  loading,
  openActionModal,
  pageTitle,
  setRuntimeFormRef,
  startForm,
  submitting,
  taskForm,
} = useWorkflowTaskPage();

function handleRuntimeFormRef(instance: Element | ComponentPublicInstance | null) {
  setRuntimeFormRef(instance as WorkflowTaskFormExpose | null);
}

onMounted(loadData);
</script>

<template>
  <div class="workflow-task-page">
    <Spin :spinning="loading">
      <Empty
        v-if="!hasRequiredParams"
        description="缺少办理参数"
      />
      <template v-else>
        <TaskHeaderCard
          :page-title="pageTitle"
          :start-form="startForm"
          :task-form="taskForm"
          @back="handleBack"
        />

        <TaskRuntimeFormCard
          :ref="handleRuntimeFormRef"
          v-model:business-key="businessKey"
          v-model:instance-title="instanceTitle"
          class="workflow-section"
          :field-permissions="taskForm?.fieldPermissions ?? []"
          :form-data-json="formDataJson"
          :form-option-json="formOptionJson"
          :form-schema-json="formSchemaJson"
          :is-start-mode="isStartMode"
          :loading="loading"
        />

        <TaskActionBar
          class="workflow-section"
          :action-permissions="taskForm?.actionPermissions"
          :is-start-mode="isStartMode"
          :is-start-draft-mode="isStartDraftMode"
          :submitting="submitting"
          @approve="handleApprove"
          @back="handleBack"
          @open-action="openActionModal"
          @start="handleStart"
        />
      </template>
    </Spin>

    <TaskActionModal
      v-model:form="actionForm"
      v-model:open="actionModalOpen"
      :action="currentAction"
      :confirm-loading="actionSubmitting"
      :return-nodes="taskForm?.returnNodes ?? []"
      :title="actionModalTitle"
      @confirm="handleActionConfirm"
    />
  </div>
</template>

<style scoped>
.workflow-task-page {
  padding: 16px;
}

.workflow-section {
  margin-top: 16px;
}
</style>
