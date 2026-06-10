<script setup lang="ts">
import { Button, Card, Descriptions } from 'ant-design-vue';

import type { StartFormInfo, TaskFormInfo } from '#/api/workflow';

defineProps<{
  pageTitle: string;
  startForm?: StartFormInfo;
  taskForm?: TaskFormInfo;
}>();

const emit = defineEmits<{
  back: [];
}>();
</script>

<template>
  <Card>
    <div class="page-header">
      <div>
        <div class="page-title">{{ pageTitle }}</div>
        <div class="page-subtitle">
          {{ startForm?.formName ?? taskForm?.formName ?? '-' }}
          <span v-if="startForm?.formVersion || taskForm?.formVersion">
            · v{{ startForm?.formVersion ?? taskForm?.formVersion }}
          </span>
        </div>
      </div>
      <Button @click="emit('back')">返回</Button>
    </div>
    <Descriptions
      bordered
      size="small"
      :column="3"
    >
      <Descriptions.Item label="标题">
        {{ taskForm?.instanceTitle ?? startForm?.processName ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="审批编号">
        {{ taskForm?.instanceNo ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="节点">
        {{ taskForm?.taskName ?? '发起申请' }}
      </Descriptions.Item>
      <Descriptions.Item label="表单">
        {{ taskForm?.formName ?? startForm?.formName ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="版本">
        {{ taskForm?.formVersion ?? startForm?.formVersion ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="任务类型">
        {{ taskForm?.taskType ?? '-' }}
      </Descriptions.Item>
    </Descriptions>
  </Card>
</template>

<style scoped>
.page-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  color: #111827;
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  color: #6b7280;
  font-size: 13px;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
