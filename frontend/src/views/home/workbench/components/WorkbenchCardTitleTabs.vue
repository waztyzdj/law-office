<script setup lang="ts">
import type {
  WorkbenchTitleTab,
  WorkbenchTitleTabKey,
} from '../types';

defineProps<{
  activeKey?: WorkbenchTitleTabKey;
  tabs: WorkbenchTitleTab[];
}>();

const emit = defineEmits<{
  change: [key: WorkbenchTitleTabKey];
}>();
</script>

<template>
  <span
    v-if="tabs.length > 0"
    class="workbench-card-title-tabs"
  >
    <button
      v-for="tab in tabs"
      :key="tab.key"
      class="workbench-card-title-tabs__item"
      :class="{ 'workbench-card-title-tabs__item--active': activeKey === tab.key }"
      type="button"
      @click.stop="emit('change', tab.key)"
    >
      <span>{{ tab.label }}</span>
      <em>{{ tab.total }}</em>
    </button>
  </span>
</template>

<style scoped>
.workbench-card-title-tabs {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  background: hsl(var(--muted));
  padding: 3px;
}

.workbench-card-title-tabs__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  padding: 4px 10px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.workbench-card-title-tabs__item--active {
  background: hsl(var(--background));
  color: hsl(var(--foreground));
  box-shadow: 0 4px 12px rgb(15 23 42 / 8%);
}

.workbench-card-title-tabs__item em {
  min-width: 18px;
  border-radius: 999px;
  background: hsl(var(--accent));
  font-style: normal;
  line-height: 1.4;
  padding: 0 6px;
  text-align: center;
}
</style>
