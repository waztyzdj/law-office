<script setup lang="ts">
import type { WorkbenchCardItem } from '#/api/home/workbench';
import type { CSSProperties } from 'vue';
import type { WorkbenchQuickEntryDraftChange } from '../../types';

import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Popconfirm } from 'ant-design-vue';

import { getWorkbenchItemConfig } from '../../utils/workbenchCardFormatters';

const props = defineProps<{
  draftChange?: WorkbenchQuickEntryDraftChange;
  editing?: boolean;
  items: WorkbenchCardItem[];
}>();

const emit = defineEmits<{
  add: [];
  edit: [item: WorkbenchCardItem];
  open: [item: WorkbenchCardItem];
}>();

const draggingKey = ref('');
const localItems = ref<WorkbenchCardItem[]>([]);
const quickEntryItems = computed(() =>
  props.editing ? localItems.value : props.items,
);

watch(
  () => props.items,
  (nextItems) => {
    if (!props.editing || localItems.value.length === 0) {
      localItems.value = cloneItems(nextItems);
    }
  },
  { immediate: true },
);

watch(
  () => props.editing,
  (editing) => {
    if (editing) {
      reset();
      return;
    }
    draggingKey.value = '';
  },
  { immediate: true },
);

watch(
  () => props.draftChange?.seq,
  () => {
    if (!props.editing || !props.draftChange) {
      return;
    }
    upsertDraftItem(props.draftChange.item);
  },
);

function cloneItems(items: WorkbenchCardItem[]) {
  return items.map((item) => ({ ...item }));
}

function reset() {
  localItems.value = cloneItems(props.items);
  draggingKey.value = '';
}

function getCurrentItems() {
  return cloneItems(localItems.value);
}

function upsertDraftItem(item: WorkbenchCardItem) {
  const targetKey = getQuickEntryKey(item);
  if (!targetKey) {
    return;
  }
  const targetIndex = localItems.value.findIndex(
    (candidate) => getQuickEntryKey(candidate) === targetKey,
  );
  if (targetIndex >= 0) {
    localItems.value = localItems.value.map((candidate, index) =>
      index === targetIndex ? { ...candidate, ...item } : candidate,
    );
    return;
  }
  localItems.value = [...localItems.value, { ...item }];
}

function getQuickEntryKey(item: WorkbenchCardItem) {
  return String(item.id || item.entryCode || item.title || item.targetPath || '');
}

function canManageQuickEntry(item: WorkbenchCardItem) {
  return item.ownerType === 'user' && Boolean(item.id);
}

function handleItemClick(item: WorkbenchCardItem) {
  if (props.editing) {
    return;
  }
  emit('open', item);
}

function handleEdit(item: WorkbenchCardItem) {
  if (!canManageQuickEntry(item)) {
    return;
  }
  emit('edit', item);
}

function handleDelete(item: WorkbenchCardItem) {
  if (!canManageQuickEntry(item)) {
    return;
  }
  const targetKey = getQuickEntryKey(item);
  localItems.value = localItems.value.filter(
    (candidate) => getQuickEntryKey(candidate) !== targetKey,
  );
  if (draggingKey.value === targetKey) {
    draggingKey.value = '';
  }
}

function handleDragStart(event: DragEvent, item: WorkbenchCardItem) {
  if (!props.editing || !canManageQuickEntry(item)) {
    event.preventDefault();
    return;
  }
  const itemKey = getQuickEntryKey(item);
  draggingKey.value = itemKey;
  event.dataTransfer?.setData('text/plain', itemKey);
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
  }
}

function handleDragOver(event: DragEvent, item: WorkbenchCardItem) {
  if (!props.editing || !draggingKey.value) {
    return;
  }
  if (getQuickEntryKey(item) === draggingKey.value) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
}

function handleDrop(event: DragEvent, targetItem: WorkbenchCardItem) {
  event.preventDefault();
  const sourceKey = draggingKey.value || event.dataTransfer?.getData('text/plain') || '';
  const targetKey = getQuickEntryKey(targetItem);
  draggingKey.value = '';
  if (!sourceKey || !targetKey || sourceKey === targetKey) {
    return;
  }
  const sourceIndex = localItems.value.findIndex(
    (item) => getQuickEntryKey(item) === sourceKey,
  );
  const targetIndex = localItems.value.findIndex(
    (item) => getQuickEntryKey(item) === targetKey,
  );
  if (sourceIndex < 0 || targetIndex < 0) {
    return;
  }
  const nextItems = [...localItems.value];
  const [sourceItem] = nextItems.splice(sourceIndex, 1);
  if (!sourceItem) {
    return;
  }
  nextItems.splice(targetIndex, 0, sourceItem);
  localItems.value = nextItems;
}

function handleDragEnd() {
  draggingKey.value = '';
}

function getQuickEntryColor(item: WorkbenchCardItem) {
  const color = getWorkbenchItemConfig(item).color;
  return typeof color === 'string' && color ? color : '#2563eb';
}

function getQuickEntryStyle(item: WorkbenchCardItem): CSSProperties {
  return {
    '--quick-entry-color': getQuickEntryColor(item),
  } as CSSProperties;
}

defineExpose({
  getCurrentItems,
  reset,
});
</script>

<template>
  <div class="workbench-card__quick">
    <div
      v-for="item in quickEntryItems"
      :key="String(item.id || item.title)"
      :class="[
        'workbench-card__quick-item',
        {
          'workbench-card__quick-item--editing': editing,
          'workbench-card__quick-item--dragging': draggingKey === getQuickEntryKey(item),
        },
      ]"
      :draggable="editing && canManageQuickEntry(item)"
      :style="getQuickEntryStyle(item)"
      @dragend="handleDragEnd"
      @dragover="handleDragOver($event, item)"
      @dragstart="handleDragStart($event, item)"
      @drop="handleDrop($event, item)"
    >
      <div
        class="workbench-card__quick-main"
        role="button"
        tabindex="0"
        @click="handleItemClick(item)"
      >
        <span class="workbench-card__quick-icon">
          <IconifyIcon :icon="String(item.icon || 'lucide:circle-dot')" />
          <span
            v-if="editing && canManageQuickEntry(item)"
            class="workbench-card__quick-edit-actions"
          >
            <button
              class="workbench-card__quick-action"
              title="编辑快捷菜单"
              type="button"
              @click.stop="handleEdit(item)"
            >
              <IconifyIcon icon="lucide:pencil" />
            </button>
            <Popconfirm
              title="确认删除这个快捷菜单？"
              @confirm="handleDelete(item)"
            >
              <button
                class="workbench-card__quick-action workbench-card__quick-action--danger"
                title="删除快捷菜单"
                type="button"
                @click.stop
              >
                <IconifyIcon icon="lucide:trash-2" />
              </button>
            </Popconfirm>
          </span>
        </span>
        <span class="workbench-card__quick-title">{{ item.title }}</span>
      </div>
    </div>
    <button
      class="workbench-card__quick-item workbench-card__quick-item--add"
      type="button"
      @click="emit('add')"
    >
      <span class="workbench-card__quick-icon workbench-card__quick-icon--add">
        <IconifyIcon icon="lucide:plus" />
      </span>
      <span class="workbench-card__quick-title">添加</span>
    </button>
  </div>
</template>

<style scoped>
.workbench-card__quick {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
  gap: 10px 12px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.workbench-card__quick::-webkit-scrollbar {
  width: 6px;
}

.workbench-card__quick::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: hsl(var(--muted-foreground) / 28%);
}

.workbench-card__quick::-webkit-scrollbar-track {
  background: transparent;
}

.workbench-card__quick-item {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 0;
  text-align: left;
  transition:
    color 0.2s ease,
    transform 0.2s ease;
}

.workbench-card__quick-main {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.workbench-card__quick-item:hover {
  color: var(--quick-entry-color);
  transform: translateY(-1px);
}

.workbench-card__quick-item--editing {
  cursor: grab;
}

.workbench-card__quick-item--editing .workbench-card__quick-icon {
  outline: 1px dashed hsl(var(--primary) / 32%);
  outline-offset: 4px;
}

.workbench-card__quick-item--editing:active {
  cursor: grabbing;
}

.workbench-card__quick-item--dragging {
  opacity: 0.45;
  transform: scale(0.96);
}

.workbench-card__quick-item--add {
  color: hsl(var(--muted-foreground));
}

.workbench-card__quick-item--add:hover {
  color: hsl(var(--primary));
}

.workbench-card__quick-icon {
  position: relative;
  display: inline-flex;
  width: 50px;
  height: 50px;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 1px solid color-mix(in srgb, var(--quick-entry-color) 24%, white);
  border-radius: 14px;
  background:
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--quick-entry-color) 88%, white),
      color-mix(in srgb, var(--quick-entry-color) 62%, transparent)
    );
  box-shadow: 0 10px 20px color-mix(in srgb, var(--quick-entry-color) 18%, transparent);
  color: white;
  font-size: 22px;
}

.workbench-card__quick-edit-actions {
  position: absolute;
  right: 4px;
  bottom: 4px;
  left: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.workbench-card__quick-action {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 999px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 4px 10px rgb(15 23 42 / 14%);
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 11px;
  opacity: 0.78;
  padding: 0;
  transition:
    background-color 0.16s ease,
    color 0.16s ease,
    opacity 0.16s ease,
    transform 0.16s ease;
}

.workbench-card__quick-action:hover,
.workbench-card__quick-action:focus-visible {
  background: hsl(var(--background));
  color: hsl(var(--primary));
  opacity: 1;
  transform: translateY(-1px);
}

.workbench-card__quick-action--danger:hover,
.workbench-card__quick-action--danger:focus-visible {
  color: rgb(220 38 38);
}

.workbench-card__quick-icon--add {
  border: 1px dashed hsl(var(--primary) / 38%);
  background: hsl(var(--primary) / 8%);
  box-shadow: none;
  color: hsl(var(--primary));
}

.workbench-card__quick-title {
  display: block;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--foreground));
  line-height: 20px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
