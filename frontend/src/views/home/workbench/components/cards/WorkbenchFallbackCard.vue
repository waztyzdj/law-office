<script setup lang="ts">
import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';

import { computed } from 'vue';

import { useWorkbenchCardPaging } from '../../hooks/useWorkbenchCardPaging';
import { getWorkbenchListPageSize } from '../../utils/workbenchCardFormatters';
import WorkbenchListCard from './WorkbenchListCard.vue';

const props = defineProps<{
  card: WorkbenchLayoutCard;
  cardName: string;
  items: WorkbenchCardItem[];
}>();

const emit = defineEmits<{
  open: [item: WorkbenchCardItem];
}>();

const listPageSize = computed(() => getWorkbenchListPageSize(props.card));
const currentListItems = computed(() => props.items);
const {
  currentPage,
  hasPagination,
  pagedItems,
} = useWorkbenchCardPaging(currentListItems, listPageSize);
const emptyDescription = computed(() => `${props.cardName}暂无数据`);
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
