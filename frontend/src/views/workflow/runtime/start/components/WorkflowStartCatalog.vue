<script setup lang="ts">
import { computed } from 'vue';

import type { AvailableProcessInfo } from '#/api/workflow';
import type { WorkflowStartSearchForm } from '../hooks/useWorkflowStartTable';

import { IconifyIcon } from '@vben/icons';
import {
  Button,
  Empty,
  Input,
  Spin,
} from 'ant-design-vue';

interface Props {
  dataSource: AvailableProcessInfo[];
  loading: boolean;
  searchForm: WorkflowStartSearchForm;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  reset: [];
  search: [];
  start: [record: AvailableProcessInfo];
  updateSearchForm: [value: WorkflowStartSearchForm];
}>();

const processName = computed({
  get: () => props.searchForm.processName,
  set: (value?: string) => {
    emit('updateSearchForm', {
      ...props.searchForm,
      processName: value || undefined,
    });
  },
});

const cardThemes = [
  { background: 'rgba(22, 119, 255, 0.10)', borderColor: 'rgba(22, 119, 255, 0.18)' },
  { background: 'rgba(19, 194, 194, 0.10)', borderColor: 'rgba(19, 194, 194, 0.18)' },
  { background: 'rgba(82, 196, 26, 0.10)', borderColor: 'rgba(82, 196, 26, 0.18)' },
  { background: 'rgba(250, 173, 20, 0.12)', borderColor: 'rgba(250, 173, 20, 0.20)' },
  { background: 'rgba(114, 46, 209, 0.10)', borderColor: 'rgba(114, 46, 209, 0.18)' },
  { background: 'rgba(235, 47, 150, 0.09)', borderColor: 'rgba(235, 47, 150, 0.16)' },
];

function getCardTheme(index: number) {
  return cardThemes[index % cardThemes.length];
}
</script>

<template>
  <div class="workflow-start-catalog">
    <div class="workflow-start-search">
      <div class="workflow-start-search__fields">
        <Input
          v-model:value="processName"
          allow-clear
          class="workflow-start-search__keyword"
          placeholder="流程名称"
          @press-enter="emit('search')"
        >
          <template #prefix>
            <IconifyIcon icon="lucide:search" />
          </template>
        </Input>
        <div class="workflow-start-search__actions">
          <Button
            type="primary"
            @click="emit('search')"
          >
            查询
          </Button>
          <Button @click="emit('reset')">重置</Button>
        </div>
      </div>
    </div>

    <Spin :spinning="loading">
      <div
        v-if="dataSource.length > 0"
        class="workflow-start-grid"
      >
        <button
          v-for="(record, index) in dataSource"
          :key="record.id"
          class="workflow-start-card"
          :style="getCardTheme(index)"
          type="button"
          @click="emit('start', record)"
        >
          <span class="workflow-start-card__name">
            {{ record.processName || '-' }}
          </span>
        </button>
      </div>
      <Empty
        v-else
        class="workflow-start-empty"
        description="暂无可发起的流程"
      />
    </Spin>
  </div>
</template>

<style scoped>
.workflow-start-catalog {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 20px;
  padding: 28px;
  background: hsl(var(--card));
  border-radius: 8px;
}

.workflow-start-catalog :deep(.ant-spin-nested-loading),
.workflow-start-catalog :deep(.ant-spin-container) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.workflow-start-search {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.workflow-start-search__fields {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.workflow-start-search__keyword {
  flex: none;
  width: 360px;
}

.workflow-start-search__actions {
  display: inline-flex;
  flex: none;
  gap: 12px;
}

.workflow-start-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.workflow-start-card {
  display: flex;
  min-height: 112px;
  align-items: center;
  justify-content: center;
  padding: 24px;
  border: 1px solid transparent;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgb(15 23 42 / 6%);
  color: hsl(var(--foreground));
  cursor: pointer;
  transition:
    box-shadow 0.18s ease,
    transform 0.18s ease,
    border-color 0.18s ease;
}

.workflow-start-card__name {
  overflow: hidden;
  max-width: 100%;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  text-align: center;
  text-overflow: ellipsis;
  word-break: break-word;
}

.workflow-start-card:hover,
.workflow-start-card:focus-visible {
  box-shadow: 0 10px 24px rgb(15 23 42 / 10%);
  transform: translateY(-2px);
}

.workflow-start-card:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}

.workflow-start-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  background: transparent;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .workflow-start-search__fields {
    flex-wrap: wrap;
  }

  .workflow-start-search__keyword {
    width: min(360px, 100%);
  }
}
</style>
