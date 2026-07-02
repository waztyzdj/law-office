<script setup lang="ts">
import type { ArchiveRecordInfo } from '#/api/workflow';
import type { Key } from 'ant-design-vue/es/vc-tree/interface';

import { onMounted, ref } from 'vue';

import { Input, message, Modal, TabPane, Tabs } from 'ant-design-vue';

import {
  archiveWorkflowInstance,
  batchArchiveWorkflowByQuery,
  batchArchiveWorkflowInstances,
  downloadWorkflowArchivePackage,
} from '#/api/workflow';

import WorkflowRuntimeFormDrawer from '../../runtime/components/WorkflowRuntimeFormDrawer.vue';
import WorkflowArchiveTable from './components/WorkflowArchiveTable.vue';
import WorkflowArchiveTree from './components/WorkflowArchiveTree.vue';
import { useWorkflowArchiveTable } from './hooks/useWorkflowArchiveTable';
import { useWorkflowArchiveTree } from './hooks/useWorkflowArchiveTree';

const {
  activeFilters,
  activeTab,
  buildCurrentQueryReq,
  handleScopeChange,
  handleTableChange,
  handleTabChange,
  loadData,
  loading,
  onSelectChange,
  pagination,
  records,
  scope,
  selectedRowKeys,
} = useWorkflowArchiveTable();
const {
  expandedKeys,
  loadTree,
  loading: treeLoading,
  selectScope,
  selectedKeys,
  treeData,
} = useWorkflowArchiveTree();

const drawerRef = ref<InstanceType<typeof WorkflowRuntimeFormDrawer>>();
const archiveModalOpen = ref(false);
const archiveSubmitting = ref(false);
const archivingSelected = ref(false);
const archivingByQuery = ref(false);
const archiveMode = ref<'query' | 'selected' | 'single'>('single');
const archiveTarget = ref<ArchiveRecordInfo>();
const archiveReason = ref('');

function handleDetail(record: ArchiveRecordInfo) {
  const processInstanceId = record.processInstanceId ?? record.id;
  if (!processInstanceId) {
    message.warning('流程实例不存在');
    return;
  }
  drawerRef.value?.open({ instanceId: processInstanceId, mode: 'archive' });
}

async function handleDownload(record: ArchiveRecordInfo) {
  const processInstanceId = record.processInstanceId;
  if (!processInstanceId) {
    message.warning('流程实例不存在');
    return;
  }
  const blob = await downloadWorkflowArchivePackage(processInstanceId);
  downloadBlob(blob, `${buildArchiveFileName(record)}.zip`);
}

function handleArchive(record: ArchiveRecordInfo) {
  archiveMode.value = 'single';
  archiveTarget.value = record;
  archiveReason.value = '';
  archiveModalOpen.value = true;
}

function handleArchiveSelected() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择需要归档的流程');
    return;
  }
  archiveMode.value = 'selected';
  archiveTarget.value = undefined;
  archiveReason.value = '';
  archiveModalOpen.value = true;
}

function handleArchiveByQuery() {
  archiveMode.value = 'query';
  archiveTarget.value = undefined;
  archiveReason.value = '';
  archiveModalOpen.value = true;
}

async function handleConfirmArchive() {
  const reason = archiveReason.value.trim() || undefined;
  archiveSubmitting.value = true;
  if (archiveMode.value === 'selected') {
    archivingSelected.value = true;
  }
  if (archiveMode.value === 'query') {
    archivingByQuery.value = true;
  }
  try {
    if (archiveMode.value === 'single') {
      const processInstanceId = archiveTarget.value?.processInstanceId;
      if (!processInstanceId) {
        message.warning('流程实例不存在');
        return;
      }
      await archiveWorkflowInstance({ archiveReason: reason, processInstanceId });
      message.success('归档成功');
    } else if (archiveMode.value === 'selected') {
      await batchArchiveWorkflowInstances({
        archiveReason: reason,
        processInstanceIds: selectedRowKeys.value.map(String),
      });
      selectedRowKeys.value = [];
      message.success('批量归档成功');
    } else {
      await batchArchiveWorkflowByQuery({
        ...buildCurrentQueryReq(),
        archiveReason: reason,
      });
      selectedRowKeys.value = [];
      message.success('按查询条件归档成功');
    }
    archiveModalOpen.value = false;
    await loadData();
  } finally {
    archiveSubmitting.value = false;
    archivingSelected.value = false;
    archivingByQuery.value = false;
  }
}

async function handleTreeSelect(nextScope: typeof scope.value, key: Key) {
  selectScope(nextScope, key);
  await handleScopeChange(nextScope);
}

function handleTreeExpand(keys: Key[]) {
  expandedKeys.value = keys;
}

function handleArchiveTabChange(key: string | number) {
  if (key !== 'archived' && key !== 'unarchived') {
    return;
  }
  void handleTabChange(key);
}

function buildArchiveFileName(record: ArchiveRecordInfo) {
  const title = record.instanceTitle?.trim() || '审批单';
  const instanceNo = record.instanceNo?.trim();
  return sanitizeFileName(instanceNo ? `${title}-${instanceNo}` : title);
}

function sanitizeFileName(fileName: string) {
  return fileName.replace(/[\\/:*?"<>|\r\n]+/g, ' ').trim() || '审批单';
}

function downloadBlob(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

onMounted(async () => {
  await Promise.all([loadTree(), loadData()]);
});
</script>

<template>
  <div class="workflow-archive-page">
    <div class="workflow-archive-layout">
      <aside class="workflow-archive-layout__tree">
        <WorkflowArchiveTree
          :expanded-keys="expandedKeys"
          :loading="treeLoading"
          :selected-keys="selectedKeys"
          :tree-data="treeData"
          @expand="handleTreeExpand"
          @select="handleTreeSelect"
        />
      </aside>
      <section class="workflow-archive-layout__content">
        <Tabs
          :active-key="activeTab"
          class="workflow-archive-tabs"
          @change="handleArchiveTabChange"
        >
          <TabPane key="archived" tab="已归档" />
          <TabPane key="unarchived" tab="未归档" />
        </Tabs>
        <WorkflowArchiveTable
          :active-filters="activeFilters"
          :archiving-by-query="archivingByQuery"
          :archiving-selected="archivingSelected"
          :data-source="records"
          :loading="loading"
          :pagination="pagination"
          :scope-title="scope.title"
          :selected-row-keys="selectedRowKeys"
          :tab="activeTab"
          @archive="handleArchive"
          @archive-by-query="handleArchiveByQuery"
          @archive-selected="handleArchiveSelected"
          @change="handleTableChange"
          @detail="handleDetail"
          @download="handleDownload"
          @select-change="onSelectChange"
        />
      </section>
    </div>

    <WorkflowRuntimeFormDrawer ref="drawerRef" />

    <Modal
      v-model:open="archiveModalOpen"
      :confirm-loading="archiveSubmitting"
      :title="archiveMode === 'query' ? '按查询条件归档' : '确认归档'"
      ok-text="确认归档"
      @ok="handleConfirmArchive"
    >
      <div class="workflow-archive-confirm">
        <div>
          {{
            archiveMode === 'single'
              ? '确认归档当前流程实例吗？'
              : archiveMode === 'selected'
                ? `确认归档选中的 ${selectedRowKeys.length} 个流程实例吗？`
                : '确认按当前查询条件批量归档未归档流程吗？单次最多处理 1000 条。'
          }}
        </div>
        <Input.TextArea
          v-model:value="archiveReason"
          :maxlength="500"
          :rows="3"
          placeholder="归档说明，可选"
          show-count
        />
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.workflow-archive-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 88px);
  padding: 16px;
}

.workflow-archive-layout {
  display: flex;
  flex: 1;
  gap: 12px;
  min-height: 0;
}

.workflow-archive-layout__tree {
  flex: 0 0 280px;
  min-width: 240px;
  min-height: 0;
}

.workflow-archive-layout__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 24px 24px 0;
  background: var(--ant-color-bg-container, #fff);
  border: 1px solid var(--ant-color-border, #f0f0f0);
  border-radius: 6px;
}

.workflow-archive-tabs {
  flex: 0 0 auto;
}

.workflow-archive-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 12px;
}

.workflow-archive-layout__content :deep(.workflow-archive-table) {
  flex: 1;
  min-height: 0;
}

.workflow-archive-confirm {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
