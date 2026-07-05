<script setup lang="ts">
import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { WorkbenchMessageTabKey } from '../../types';

import { computed, watch } from 'vue';

import { useWorkbenchCardPaging } from '../../hooks/useWorkbenchCardPaging';
import { getWorkbenchListPageSize } from '../../utils/workbenchCardFormatters';
import WorkbenchListCard from './WorkbenchListCard.vue';

const props = defineProps<{
  activeTab: WorkbenchMessageTabKey;
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
const emptyDescription = computed(() => {
  if (props.activeTab === 'urge-message') {
    return '催办消息暂无数据';
  }
  if (props.activeTab === 'timeout-message') {
    return '超时消息暂无数据';
  }
  if (props.activeTab === 'unread-message') {
    return '未读消息暂无数据';
  }
  return '已读消息暂无数据';
});

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
    title-fallback="未命名消息"
    @open="emit('open', $event)"
  />
</template>
