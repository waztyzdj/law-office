<script setup lang="ts">
import type {
  AssigneeOptionInfo,
  AssigneeSelectNodeInfo,
  SelectedAssigneeReq,
} from '#/api/workflow';

import { computed } from 'vue';

import { Select, Tag } from 'ant-design-vue';

import { UserPicker } from '#/components/user-picker';

const props = withDefaults(
  defineProps<{
    compact?: boolean;
    disabled?: boolean;
    nodes?: AssigneeSelectNodeInfo[];
    showTitle?: boolean;
    value?: SelectedAssigneeReq[];
  }>(),
  {
    compact: false,
    disabled: false,
    nodes: () => [],
    showTitle: true,
    value: () => [],
  },
);

const emit = defineEmits<{
  'update:value': [value: SelectedAssigneeReq[]];
}>();

const valueMap = computed(() => {
  const map = new Map<string, string[]>();
  for (const item of props.value ?? []) {
    const nodeId = item.nodeId;
    const userIds = item.userIds?.filter(Boolean) ?? [];
    if (nodeId && userIds.length) {
      map.set(nodeId, userIds);
    }
  }
  return map;
});

function formatOptionLabel(option: AssigneeOptionInfo) {
  return option?.displayName || option?.realname || option?.username || option?.userId || '';
}

function buildOptions(node: AssigneeSelectNodeInfo) {
  return (node.options ?? [])
    .filter((option) => option.userId)
    .map((option) => ({
      label: formatOptionLabel(option),
      value: option.userId!,
    }));
}

function isFreeSelectNode(node: AssigneeSelectNodeInfo) {
  return node.assigneeType === 'starter_select';
}

function isMultipleNode(node: AssigneeSelectNodeInfo) {
  return node.selectType === 'multiple';
}

function resolveNodeValue(node: AssigneeSelectNodeInfo) {
  const values = node.nodeId ? valueMap.value.get(node.nodeId) : undefined;
  return isMultipleNode(node) ? values : values?.[0];
}

function handleChange(nodeId: string | undefined, value: unknown) {
  if (!nodeId) {
    return;
  }
  const next = [...(props.value ?? [])].filter((item) => item.nodeId !== nodeId);
  const userIds = Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && !!item)
    : typeof value === 'string' && value
      ? [value]
      : [];
  if (userIds.length) {
    next.push({ nodeId, userIds });
  }
  emit('update:value', next);
}

function handleUserPickerChange(nodeId: string | undefined, value: unknown) {
  if (!nodeId) {
    return;
  }
  const userId = Array.isArray(value) ? value.find((item) => typeof item === 'string' && item) : value;
  handleChange(nodeId, typeof userId === 'string' ? userId : undefined);
}
</script>

<template>
  <section
    v-if="nodes.length"
    :class="['assignee-select-panel', { compact }]"
  >
    <div
      v-if="showTitle"
      class="assignee-select-title"
    >
      下一审批人
    </div>
    <div class="assignee-select-list">
      <div
        v-for="node in nodes"
        :key="node.nodeId"
        class="assignee-select-row"
      >
        <label class="assignee-select-label">
          <span class="required-mark">*</span>
          {{ node.nodeName || node.nodeId }}
        </label>
        <div class="assignee-select-control">
          <UserPicker
            v-if="isFreeSelectNode(node)"
            :disabled="disabled"
            :mode="isMultipleNode(node) ? 'multiple' : 'single'"
            placeholder="请选择下一审批人"
            :value="resolveNodeValue(node)"
            @update:value="(nextValue) => handleUserPickerChange(node.nodeId, nextValue)"
          />
          <Select
            v-else
            :disabled="disabled"
            :mode="isMultipleNode(node) ? 'multiple' : undefined"
            :options="buildOptions(node)"
            option-filter-prop="label"
            placeholder="请选择审批人"
            show-search
            :value="resolveNodeValue(node)"
            @update:value="(nextValue) => handleChange(node.nodeId, nextValue)"
          />
          <Tag
            v-if="isFreeSelectNode(node)"
            class="assignee-select-hint"
            color="blue"
          >
            本单位人员
          </Tag>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.assignee-select-panel {
  border-top: 1px solid #f0f0f0;
  margin-top: 20px;
  padding-top: 18px;
}

.assignee-select-panel.compact {
  border-top: 0;
  margin-top: 0;
  padding-top: 0;
}

.assignee-select-title {
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
}

.assignee-select-list {
  display: grid;
  gap: 12px;
}

.assignee-select-row {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: 132px minmax(0, 1fr);
}

.assignee-select-panel.compact .assignee-select-row {
  gap: 16px;
  grid-template-columns: max-content minmax(0, 1fr);
  padding: 12px 24px;
}

.assignee-select-label {
  color: #4b5563;
  text-align: right;
}

.required-mark {
  color: #ff4d4f;
  margin-right: 4px;
}

.assignee-select-control {
  width: 100%;
}

.assignee-select-control :deep(.ant-select) {
  width: 100%;
}

.assignee-select-panel.compact .assignee-select-control {
  min-width: 0;
}

.assignee-select-hint {
  margin-top: 6px;
}

@media (max-width: 768px) {
  .assignee-select-row {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .assignee-select-label {
    text-align: left;
  }
}
</style>
