<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, InputNumber } from 'ant-design-vue';

const props = withDefaults(
  defineProps<{
    current: number;
    pageSize: number;
    siblingCount?: number;
    total: number;
  }>(),
  {
    siblingCount: 2,
  },
);

const emit = defineEmits<{
  'update:current': [value: number];
}>();

const draftPage = ref(props.current);
const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.total / Math.max(1, props.pageSize))),
);
const normalizedCurrent = computed(() => clampPage(props.current));
const visiblePages = computed(() => {
  const start = Math.max(1, normalizedCurrent.value - props.siblingCount);
  const end = Math.min(totalPages.value, normalizedCurrent.value + props.siblingCount);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
});

watch(
  () => props.current,
  (value) => {
    draftPage.value = clampPage(value);
  },
);

watch(totalPages, () => {
  const nextPage = clampPage(props.current);
  draftPage.value = nextPage;
  if (nextPage !== props.current) {
    emit('update:current', nextPage);
  }
});

function clampPage(value: number) {
  if (!Number.isFinite(value)) {
    return 1;
  }
  return Math.min(totalPages.value, Math.max(1, Math.floor(value)));
}

function handlePageChange(value: number) {
  const nextPage = clampPage(value);
  draftPage.value = nextPage;
  if (nextPage !== props.current) {
    emit('update:current', nextPage);
  }
}

function handleInputChange(value: unknown) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return;
  }
  handlePageChange(numericValue);
}
</script>

<template>
  <nav class="workbench-card-pagination" aria-label="卡片分页">
    <Button
      class="workbench-card-pagination__nav"
      :disabled="normalizedCurrent <= 1"
      size="small"
      type="text"
      @click="handlePageChange(normalizedCurrent - 1)"
    >
      <IconifyIcon icon="lucide:chevron-left" />
    </Button>

    <button
      v-for="page in visiblePages"
      :key="page"
      class="workbench-card-pagination__page"
      :class="{ 'workbench-card-pagination__page--active': page === normalizedCurrent }"
      type="button"
      @click="handlePageChange(page)"
    >
      {{ page }}
    </button>

    <span class="workbench-card-pagination__jump">
      <InputNumber
        v-model:value="draftPage"
        class="workbench-card-pagination__input"
        :controls="false"
        :max="totalPages"
        :min="1"
        size="small"
        @change="handleInputChange"
      />
      <span class="workbench-card-pagination__total">/ {{ totalPages }}</span>
    </span>

    <Button
      class="workbench-card-pagination__nav"
      :disabled="normalizedCurrent >= totalPages"
      size="small"
      type="text"
      @click="handlePageChange(normalizedCurrent + 1)"
    >
      <IconifyIcon icon="lucide:chevron-right" />
    </Button>
  </nav>
</template>

<style scoped>
.workbench-card-pagination {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  border-radius: 999px;
  background: hsl(var(--muted) / 60%);
  padding: 3px;
}

.workbench-card-pagination__nav {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  color: hsl(var(--muted-foreground));
}

.workbench-card-pagination__page {
  min-width: 26px;
  height: 26px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  line-height: 26px;
  padding: 0 8px;
  text-align: center;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.workbench-card-pagination__page:hover {
  color: hsl(var(--foreground));
}

.workbench-card-pagination__page--active {
  background: hsl(var(--background));
  color: hsl(var(--foreground));
  box-shadow: 0 4px 12px rgb(15 23 42 / 8%);
}

.workbench-card-pagination__jump {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 2px;
}

.workbench-card-pagination__input {
  width: 48px;
}

.workbench-card-pagination__input :deep(.ant-input-number-input) {
  height: 24px;
  padding: 0 6px;
  text-align: center;
}

.workbench-card-pagination__total {
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
}
</style>
