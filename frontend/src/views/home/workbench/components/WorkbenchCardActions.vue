<script setup lang="ts">
import type { WorkbenchCardAction } from '../types';

import { IconifyIcon } from '@vben/icons';

import { Button, Tooltip } from 'ant-design-vue';

defineProps<{
  actions: WorkbenchCardAction[];
}>();
</script>

<template>
  <div class="workbench-card-actions">
    <Tooltip
      v-for="action in actions"
      :key="action.key"
      :title="action.title"
    >
      <Button
        :disabled="action.disabled"
        :loading="action.loading"
        size="small"
        type="text"
        @click="action.onClick"
      >
        <IconifyIcon
          v-if="!action.loading"
          :icon="action.icon"
        />
      </Button>
    </Tooltip>
  </div>
</template>

<style scoped>
.workbench-card-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.workbench-card-actions :deep(.ant-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>
