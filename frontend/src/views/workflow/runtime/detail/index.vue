<script setup lang="ts">
import { onMounted } from 'vue';

import { Card, Empty, Spin } from 'ant-design-vue';

import ApprovalRecordTable from './components/ApprovalRecordTable.vue';
import CurrentTaskCard from './components/CurrentTaskCard.vue';
import DetailFormCard from './components/DetailFormCard.vue';
import DetailSummaryCard from './components/DetailSummaryCard.vue';
import { useWorkflowDetailPage } from './hooks/useWorkflowDetailPage';

const {
  detail,
  formSnapshotDataJson,
  formSnapshotOptionJson,
  formSnapshotSchemaJson,
  instanceId,
  loadData,
  loading,
} = useWorkflowDetailPage();

onMounted(loadData);
</script>

<template>
  <div class="workflow-detail-page">
    <Spin :spinning="loading">
      <Empty
        v-if="!instanceId"
        description="缺少审批实例"
      />
      <template v-else>
        <DetailSummaryCard :process-instance="detail?.processInstance" />

        <DetailFormCard
          class="workflow-section"
          :form-data-json="formSnapshotDataJson"
          :option-json="formSnapshotOptionJson"
          :schema-json="formSnapshotSchemaJson"
        />

        <CurrentTaskCard
          class="workflow-section"
          :tasks="detail?.currentTasks ?? []"
        />

        <Card
          class="workflow-section"
          title="审批记录"
        >
          <ApprovalRecordTable :records="detail?.records ?? []" />
        </Card>
      </template>
    </Spin>
  </div>
</template>

<style scoped>
.workflow-detail-page {
  padding: 16px;
}

.workflow-section {
  margin-top: 16px;
}
</style>
