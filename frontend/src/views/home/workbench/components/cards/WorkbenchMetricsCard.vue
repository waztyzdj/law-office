<script setup lang="ts">
import type { WorkbenchCardItem } from '#/api/home/workbench';
import type { CSSProperties } from 'vue';

import { IconifyIcon } from '@vben/icons';

type MetricToneStyle = { color: string; icon: string };

const props = defineProps<{
  items: WorkbenchCardItem[];
}>();

const emit = defineEmits<{
  open: [item: WorkbenchCardItem];
}>();

const defaultMetricTone: MetricToneStyle = { color: '#2563eb', icon: 'lucide:check-square' };
const metricToneStyles: Record<string, MetricToneStyle> = {
  blue: defaultMetricTone,
  indigo: { color: '#4f46e5', icon: 'lucide:check-check' },
  cyan: { color: '#0891b2', icon: 'lucide:send' },
  orange: { color: '#ea580c', icon: 'lucide:bell' },
};

function getMetricTone(item: WorkbenchCardItem) {
  const tone = typeof item.tone === 'string' ? item.tone : '';
  return metricToneStyles[tone] ?? defaultMetricTone;
}

function getMetricIcon(item: WorkbenchCardItem) {
  return typeof item.icon === 'string' && item.icon ? item.icon : getMetricTone(item).icon;
}

function getMetricStyle(item: WorkbenchCardItem): CSSProperties {
  return {
    '--metric-color': getMetricTone(item).color,
  } as CSSProperties;
}
</script>

<template>
  <div class="workbench-card__metrics">
    <button
      v-for="item in props.items"
      :key="String(item.id || item.title)"
      class="workbench-card__metric"
      :style="getMetricStyle(item)"
      type="button"
      @click="emit('open', item)"
    >
      <span class="workbench-card__metric-top">
        <span class="workbench-card__metric-title">
          <span class="workbench-card__metric-title-icon">
            <IconifyIcon :icon="getMetricIcon(item)" />
          </span>
          <span class="workbench-card__metric-title-text">{{ item.title }}</span>
        </span>
      </span>
      <span class="workbench-card__metric-value">
        {{ item.value ?? 0 }}
      </span>
    </button>
  </div>
</template>

<style scoped>
.workbench-card__metrics {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  align-content: stretch;
}

.workbench-card__metric {
  position: relative;
  display: flex;
  min-height: 78px;
  align-items: stretch;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 12px;
  background: color-mix(in srgb, var(--metric-color) 6%, white);
  cursor: pointer;
  padding: 14px 14px 14px;
  text-align: left;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.workbench-card__metric:hover {
  border-color: color-mix(in srgb, var(--metric-color) 26%, hsl(var(--border)));
  background: color-mix(in srgb, var(--metric-color) 9%, white);
  box-shadow: 0 10px 22px color-mix(in srgb, var(--metric-color) 8%, transparent);
  transform: translateY(-1px);
}

.workbench-card__metric-top {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: center;
}

.workbench-card__metric-title {
  display: inline-flex;
  min-width: 0;
  max-width: 100%;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
  color: hsl(var(--muted-foreground));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-card__metric-title::before {
  width: 4px;
  height: 16px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--metric-color);
  content: '';
}

.workbench-card__metric-title-icon {
  display: inline-flex;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: color-mix(in srgb, var(--metric-color) 72%, transparent);
  font-size: 14px;
  opacity: 0.65;
}

.workbench-card__metric-title-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-card__metric-value {
  display: inline-flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  color: hsl(var(--foreground));
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  text-align: center;
}

@media (max-width: 640px) {
  .workbench-card__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
