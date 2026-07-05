<script setup lang="ts">
import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { WorkbenchTodoTabKey } from '../../types';

import { computed, watch } from 'vue';

import { useWorkbenchCardPaging } from '../../hooks/useWorkbenchCardPaging';
import { getWorkbenchListPageSize } from '../../utils/workbenchCardFormatters';
import WorkbenchListCard from './WorkbenchListCard.vue';

const props = defineProps<{
  activeTab: WorkbenchTodoTabKey;
  card: WorkbenchLayoutCard;
  items: WorkbenchCardItem[];
}>();

const emit = defineEmits<{
  open: [item: WorkbenchCardItem];
}>();

const listPageSize = computed(() => getWorkbenchListPageSize(props.card));
const currentListItems = computed(() =>
  props.items.filter((item) => item.type === props.activeTab),
);
const {
  currentPage,
  hasPagination,
  pagedItems,
  resetPage,
} = useWorkbenchCardPaging(currentListItems, listPageSize);
const emptyDescription = computed(
  () => `${props.activeTab === 'todo' ? '我的待办' : '我的已办'}暂无数据`,
);

watch(
  () => props.activeTab,
  () => resetPage(),
);
</script>

<template>
  <WorkbenchListCard
    v-model:current-page="currentPage"
    :empty-description="emptyDescription"
    :items="currentListItems"
    :page-items="pagedItems"
    :page-size="listPageSize"
    :show-pagination="hasPagination"
    title-fallback="未命名事项"
    @open="emit('open', $event)"
  />
</template>
