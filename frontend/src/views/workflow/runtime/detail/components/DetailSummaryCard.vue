<script setup lang="ts">
import { Card, Descriptions } from 'ant-design-vue';

import type { ProcessInstanceInfo } from '#/api/workflow';

import WorkflowStatusTag from '../../../components/WorkflowStatusTag.vue';

defineProps<{
  processInstance?: ProcessInstanceInfo;
}>();
</script>

<template>
  <Card>
    <Descriptions
      bordered
      size="small"
      :column="3"
    >
      <Descriptions.Item label="标题">
        {{ processInstance?.instanceTitle ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="审批编号">
        {{ processInstance?.instanceNo ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="状态">
        <WorkflowStatusTag :status="processInstance?.status" />
      </Descriptions.Item>
      <Descriptions.Item label="发起人">
        {{ processInstance?.starterRealname ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="发起时间">
        {{ processInstance?.startTime ?? '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="结束时间">
        {{ processInstance?.endTime ?? '-' }}
      </Descriptions.Item>
    </Descriptions>
  </Card>
</template>
