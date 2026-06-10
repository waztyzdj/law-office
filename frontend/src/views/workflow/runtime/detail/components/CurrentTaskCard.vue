<script setup lang="ts">
import { Card, Empty, Timeline } from 'ant-design-vue';

import type { RuntimeTaskInfo } from '#/api/workflow';

defineProps<{
  tasks?: RuntimeTaskInfo[];
}>();
</script>

<template>
  <Card title="当前任务">
    <Timeline v-if="tasks?.length">
      <Timeline.Item
        v-for="task in tasks"
        :key="task.id"
      >
        {{ task.taskName }} /
        {{ task.assigneeRealname ?? task.assigneeUsername ?? '-' }}
      </Timeline.Item>
    </Timeline>
    <Empty
      v-else
      description="暂无当前任务"
    />
  </Card>
</template>
