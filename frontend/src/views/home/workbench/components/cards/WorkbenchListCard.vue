<script setup lang="ts">
import type { WorkbenchCardItem } from '#/api/home/workbench';

import { Empty, List, ListItem } from 'ant-design-vue';

import { formatWorkbenchCardTime } from '../../utils/workbenchCardFormatters';
import WorkbenchCardPagination from '../WorkbenchCardPagination.vue';

const props = withDefaults(
  defineProps<{
    currentPage: number;
    emptyDescription: string;
    items: WorkbenchCardItem[];
    pageItems: WorkbenchCardItem[];
    pageSize: number;
    rowTitle?: string;
    showPagination?: boolean;
    titleFallback?: string;
  }>(),
  {
    rowTitle: undefined,
    showPagination: false,
    titleFallback: '未命名事项',
  },
);

const emit = defineEmits<{
  open: [item: WorkbenchCardItem];
  rowDoubleClick: [item: WorkbenchCardItem];
  'update:currentPage': [value: number];
}>();

defineSlots<{
  item?: (props: { item: WorkbenchCardItem }) => unknown;
}>();

function handleOpen(item: WorkbenchCardItem) {
  emit('open', item);
}
</script>

<template>
  <div class="workbench-list-card">
    <List
      v-if="items.length > 0"
      :data-source="pageItems"
      class="workbench-list-card__list"
      size="small"
    >
      <template #renderItem="{ item }">
        <ListItem
          class="workbench-list-card__item"
          :title="rowTitle"
          @click="handleOpen(item)"
          @dblclick="emit('rowDoubleClick', item)"
        >
          <slot name="item" :item="item">
            <span class="workbench-list-card__item-title">
              {{ item.title || props.titleFallback }}
            </span>
            <span class="workbench-list-card__item-time">
              {{ formatWorkbenchCardTime(item.occurTime) || '-' }}
            </span>
          </slot>
        </ListItem>
      </template>
    </List>
    <Empty
      v-else
      class="workbench-list-card__empty"
      :description="emptyDescription"
      :image="Empty.PRESENTED_IMAGE_SIMPLE"
    />

    <div
      v-if="showPagination"
      class="workbench-list-card__footer"
    >
      <WorkbenchCardPagination
        :current="currentPage"
        :page-size="pageSize"
        :total="items.length"
        @update:current="emit('update:currentPage', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.workbench-list-card {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
}

.workbench-list-card__list {
  min-height: 0;
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
  scrollbar-gutter: stable;
}

.workbench-list-card__list::-webkit-scrollbar {
  width: 6px;
}

.workbench-list-card__list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: hsl(var(--muted-foreground) / 28%);
}

.workbench-list-card__list::-webkit-scrollbar-track {
  background: transparent;
}

.workbench-list-card__item {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  border-radius: 10px;
  padding: 7px 10px !important;
  transition: background-color 0.2s ease;
}

.workbench-list-card__item:hover {
  background: hsl(var(--accent));
}

.workbench-list-card__item-title {
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--foreground));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-list-card__item-time {
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  white-space: nowrap;
}

.workbench-list-card__empty {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}

.workbench-list-card__footer {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 5px;
  transform: translateY(1px);
}
</style>
