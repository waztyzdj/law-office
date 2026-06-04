<script setup lang="ts">
import type { DocumentSortField, DocumentSortState } from '../constants';

import { IconifyIcon } from '@vben/icons';

import { documentListColumns } from './documentExplorerUtils';

interface Props {
  sortState: DocumentSortState;
}

defineProps<Props>();

const emit = defineEmits<{
  sort: [field: DocumentSortField];
}>();

function isActiveSort(sortState: DocumentSortState, field: DocumentSortField) {
  return sortState.field === field;
}

function sortDirectionIcon(sortState: DocumentSortState, field: DocumentSortField) {
  if (!isActiveSort(sortState, field)) {
    return 'lucide:chevrons-up-down';
  }
  return sortState.order === 'asc' ? 'lucide:arrow-up' : 'lucide:arrow-down';
}
</script>

<template>
  <div class="document-list__header">
    <button
      v-for="column in documentListColumns"
      :key="column.field"
      class="document-list__cell document-list__sort"
      :class="column.className"
      type="button"
      @click="emit('sort', column.field)"
    >
      <span>{{ column.label }}</span>
      <IconifyIcon
        class="document-list__sort-icon"
        :class="{ 'document-list__sort-icon--active': isActiveSort(sortState, column.field) }"
        :icon="sortDirectionIcon(sortState, column.field)"
      />
    </button>
    <div class="document-list__cell document-list__cell--actions"></div>
  </div>
</template>

<style scoped>
.document-list__header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  min-width: 720px;
  min-height: 36px;
  align-items: center;
  grid-template-columns: minmax(240px, 1fr) 120px 100px 150px 116px;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--muted) / 45%);
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  font-weight: 500;
}

.document-list__sort {
  display: inline-flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: center;
}

.document-list__cell {
  min-width: 0;
  padding: 0 10px;
  color: hsl(var(--muted-foreground));
}

.document-list__cell--actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.document-list__sort:hover,
.document-list__sort:focus-visible {
  background: hsl(var(--muted) / 70%);
  color: hsl(var(--foreground));
  outline: none;
}

.document-list__sort-icon {
  width: 14px;
  height: 14px;
  color: hsl(var(--muted-foreground));
}

.document-list__sort-icon--active {
  color: hsl(var(--primary));
}

.document-list__header .document-list__cell {
  justify-content: center;
  color: hsl(var(--muted-foreground));
}
</style>
