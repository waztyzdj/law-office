<script setup lang="ts">
import { ref } from 'vue';

import {
  DownOutlined,
  EditOutlined,
  RightOutlined,
} from '@ant-design/icons-vue';

import { Button, Textarea, Tooltip } from 'ant-design-vue';

interface Props {
  value: string;
}

defineProps<Props>();

const emit = defineEmits<{
  'update:value': [value: string];
}>();

const collapsed = ref(false);

function toggleCollapsed() {
  collapsed.value = !collapsed.value;
}
</script>

<template>
  <div class="approval-comment-panel">
    <div class="approval-comment-header">
      <button
        class="approval-comment-title"
        type="button"
        @click="toggleCollapsed"
      >
        <EditOutlined />
        <span>审批意见</span>
      </button>
      <Tooltip :title="collapsed ? '展开审批意见' : '折叠审批意见'">
        <Button
          class="approval-comment-collapse"
          type="text"
          @click="toggleCollapsed"
        >
          <RightOutlined v-if="collapsed" />
          <DownOutlined v-else />
        </Button>
      </Tooltip>
    </div>
    <div
      v-show="!collapsed"
      class="approval-comment-control"
    >
      <Textarea
        :maxlength="500"
        :rows="3"
        :value="value"
        placeholder="请输入审批意见"
        show-count
        @update:value="(nextValue) => emit('update:value', nextValue)"
      />
    </div>
  </div>
</template>

<style scoped>
.approval-comment-panel {
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 10px;
  padding-top: 10px;
}

.approval-comment-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.approval-comment-title {
  align-items: center;
  background: transparent;
  border: 0;
  color: hsl(var(--foreground));
  cursor: pointer;
  display: flex;
  font-size: 15px;
  font-weight: 600;
  gap: 6px;
  min-width: 0;
  padding: 0;
}

.approval-comment-collapse {
  align-items: center;
  display: inline-flex;
  height: 32px;
  justify-content: center;
  width: 32px;
}

.approval-comment-control {
  min-width: 0;
}
</style>
