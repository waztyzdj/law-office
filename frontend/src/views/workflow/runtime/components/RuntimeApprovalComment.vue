<script setup lang="ts">
import { Textarea } from 'ant-design-vue';

interface Props {
  value: string;
}

defineProps<Props>();

const emit = defineEmits<{
  'update:value': [value: string];
}>();
</script>

<template>
  <div class="approval-comment-panel">
    <label class="approval-comment-label">审批意见</label>
    <div class="approval-comment-control">
      <Textarea
        :maxlength="500"
        :rows="4"
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
  align-items: flex-start;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex: 0 0 auto;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
}

.approval-comment-label {
  color: #1f2937;
  flex: 0 0 92px;
  font-size: 14px;
  font-weight: 500;
  line-height: 32px;
  text-align: right;
}

.approval-comment-control {
  flex: 1;
  min-width: 0;
}
</style>
