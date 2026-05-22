<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Input, Pagination, Popover, Tooltip } from 'ant-design-vue';

import { menuIconOptions } from '#/constants/menu-icons';

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
    icons?: readonly string[];
    modelValue?: string;
    pageSize?: number;
    placeholder?: string;
    value?: string;
  }>(),
  {
    disabled: false,
    icons: () => menuIconOptions,
    modelValue: '',
    pageSize: 60,
    placeholder: '请选择菜单图标',
    value: '',
  },
);

const emit = defineEmits<{
  change: [value: string];
  'update:modelValue': [value: string];
  'update:value': [value: string];
}>();

const open = ref(false);
const keyword = ref('');
const currentPage = ref(1);
const innerValue = ref(props.value || props.modelValue || '');

watch(
  () => [props.value, props.modelValue],
  ([value, modelValue]) => {
    innerValue.value = value || modelValue || '';
  },
);

const selectedIcon = computed({
  get: () => innerValue.value,
  set: (value: string) => {
    innerValue.value = value;
    emit('update:value', value);
    emit('update:modelValue', value);
    emit('change', value);
  },
});

const filteredIcons = computed(() => {
  const keywordValue = keyword.value.trim().toLowerCase();
  if (!keywordValue) {
    return [...props.icons];
  }
  return props.icons.filter((icon) => icon.toLowerCase().includes(keywordValue));
});

const pageIcons = computed(() => {
  const start = (currentPage.value - 1) * props.pageSize;
  return filteredIcons.value.slice(start, start + props.pageSize);
});

function handleSearch(value: string) {
  keyword.value = value;
  currentPage.value = 1;
}

function handleSelect(icon: string) {
  selectedIcon.value = icon;
  open.value = false;
}

function handleClear() {
  selectedIcon.value = '';
}
</script>

<template>
  <Popover
    v-model:open="open"
    overlay-class-name="menu-icon-picker-popover"
    placement="bottomRight"
    trigger="click"
  >
    <template #content>
      <div class="menu-icon-picker">
        <Input
          :value="keyword"
          allow-clear
          class="menu-icon-picker__search"
          placeholder="搜索图标名称"
          @update:value="handleSearch"
        />

        <div v-if="selectedIcon" class="menu-icon-picker__selected">
          <IconifyIcon :icon="selectedIcon" class="menu-icon-picker__selected-icon" />
          <span class="menu-icon-picker__selected-text">{{ selectedIcon }}</span>
        </div>

        <div v-if="pageIcons.length > 0" class="menu-icon-picker__grid">
          <Tooltip v-for="icon in pageIcons" :key="icon" :title="icon">
            <Button
              :class="{ 'is-active': selectedIcon === icon }"
              class="menu-icon-picker__item"
              type="text"
              @click="handleSelect(icon)"
            >
              <IconifyIcon :icon="icon" class="menu-icon-picker__icon" />
              <IconifyIcon
                v-if="selectedIcon === icon"
                class="menu-icon-picker__check"
                icon="lucide:check"
              />
            </Button>
          </Tooltip>
        </div>

        <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" />

        <div class="menu-icon-picker__footer">
          <span class="menu-icon-picker__total">共 {{ filteredIcons.length }} 个</span>
          <Pagination
            v-model:current="currentPage"
            :page-size="pageSize"
            :show-size-changer="false"
            :total="filteredIcons.length"
            size="small"
          />
        </div>
      </div>
    </template>

    <Input
      :disabled="disabled"
      :placeholder="placeholder"
      :value="selectedIcon"
      readonly
    >
      <template #prefix>
        <IconifyIcon
          v-if="selectedIcon"
          :icon="selectedIcon"
          class="menu-icon-picker__preview"
        />
      </template>
      <template #suffix>
        <Button
          v-if="selectedIcon && !disabled"
          class="menu-icon-picker__clear"
          size="small"
          type="text"
          @click.stop="handleClear"
        >
          清空
        </Button>
      </template>
    </Input>
  </Popover>
</template>

<style scoped>
.menu-icon-picker {
  width: min(520px, calc(100vw - 48px));
}

.menu-icon-picker__search {
  margin-bottom: 12px;
}

.menu-icon-picker__selected {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 34px;
  padding: 0 10px;
  margin-bottom: 12px;
  font-size: 13px;
  background: hsl(var(--accent));
  border: 1px solid hsl(var(--primary) / 35%);
  border-radius: 6px;
}

.menu-icon-picker__selected-icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  color: hsl(var(--primary));
}

.menu-icon-picker__selected-text {
  overflow: hidden;
  color: hsl(var(--foreground));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 6px;
  max-height: 300px;
  overflow-y: auto;
}

.menu-icon-picker__item {
  position: relative;
  width: 42px;
  height: 42px;
  padding: 0;
  border: 1px solid transparent;
}

.menu-icon-picker__item.is-active {
  color: hsl(var(--primary));
  background: hsl(var(--accent));
  border-color: hsl(var(--primary));
}

.menu-icon-picker__icon {
  width: 20px;
  height: 20px;
}

.menu-icon-picker__check {
  position: absolute;
  right: 3px;
  bottom: 3px;
  width: 12px;
  height: 12px;
  color: hsl(var(--primary));
}

.menu-icon-picker__preview {
  width: 18px;
  height: 18px;
}

.menu-icon-picker__clear {
  height: 22px;
  padding: 0 4px;
}

.menu-icon-picker__footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid hsl(var(--border));
}

.menu-icon-picker__total {
  flex-shrink: 0;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}
</style>
