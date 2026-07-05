<script setup lang="ts">
import type {
  WorkbenchCardData,
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { Component } from 'vue';
import type {
  WorkbenchCardOpenPayload,
  WorkbenchQuickEntryExpose,
} from '../types';

import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Card,
  Skeleton,
} from 'ant-design-vue';

import { useWorkbenchCardActions } from '../hooks/useWorkbenchCardActions';
import { useWorkbenchCardNavigation } from '../hooks/useWorkbenchCardNavigation';
import { useWorkbenchCardQuickEntry } from '../hooks/useWorkbenchCardQuickEntry';
import { useWorkbenchCardTitleTabs } from '../hooks/useWorkbenchCardTitleTabs';
import { getWorkbenchCardMeta } from '../registry';
import WorkbenchCardActions from './WorkbenchCardActions.vue';
import WorkbenchCardTitleTabs from './WorkbenchCardTitleTabs.vue';
import WorkbenchCcCard from './cards/WorkbenchCcCard.vue';
import WorkbenchFallbackCard from './cards/WorkbenchFallbackCard.vue';
import WorkbenchFavoriteCard from './cards/WorkbenchFavoriteCard.vue';
import WorkbenchMessageCard from './cards/WorkbenchMessageCard.vue';
import WorkbenchMetricsCard from './cards/WorkbenchMetricsCard.vue';
import WorkbenchQuickEntryCard from './cards/WorkbenchQuickEntryCard.vue';
import WorkbenchTodoCard from './cards/WorkbenchTodoCard.vue';

const props = defineProps<{
  card: WorkbenchLayoutCard;
  data?: WorkbenchCardData;
  error?: string;
  editing?: boolean;
  loading?: boolean;
}>();

const emit = defineEmits<{
  openMessageItem: [payload: WorkbenchCardOpenPayload];
  openWorkflowItem: [payload: WorkbenchCardOpenPayload];
  quickEntryAdd: [];
  quickEntryEdit: [item: WorkbenchCardItem];
  quickEntrySortSave: [items: WorkbenchCardItem[]];
  refresh: [card: WorkbenchLayoutCard];
}>();

interface WorkbenchCardComponentConfig {
  cardCode: string;
  component: Component;
  componentKey: string;
}

const workbenchCardComponents: WorkbenchCardComponentConfig[] = [
  {
    cardCode: 'metrics',
    component: WorkbenchMetricsCard,
    componentKey: 'WorkbenchMetricsCard',
  },
  {
    cardCode: 'quick-entry',
    component: WorkbenchQuickEntryCard,
    componentKey: 'WorkbenchQuickEntryCard',
  },
  {
    cardCode: 'todo',
    component: WorkbenchTodoCard,
    componentKey: 'WorkbenchTodoCard',
  },
  {
    cardCode: 'cc',
    component: WorkbenchCcCard,
    componentKey: 'WorkbenchCcCard',
  },
  {
    cardCode: 'message',
    component: WorkbenchMessageCard,
    componentKey: 'WorkbenchMessageCard',
  },
  {
    cardCode: 'favorite',
    component: WorkbenchFavoriteCard,
    componentKey: 'WorkbenchFavoriteCard',
  },
];

const contentComponentRef = ref<WorkbenchQuickEntryExpose>();
const sourceCard = computed(() => props.card);
const sourceData = computed(() => props.data);
const items = computed(() => props.data?.items ?? []);
const pageEditing = computed(() => props.editing === true);
const loadingState = computed(() => props.loading === true);
const componentConfig = computed(() =>
  workbenchCardComponents.find(
    (item) =>
      item.cardCode === props.card.cardCode ||
      item.componentKey === props.card.componentKey,
  ),
);
const resolvedCard = computed<WorkbenchLayoutCard>(() => ({
  ...props.card,
  cardCode: componentConfig.value?.cardCode ?? props.card.cardCode,
}));
const meta = computed(() =>
  getWorkbenchCardMeta(resolvedCard.value.cardCode, props.card.componentKey),
);
const cardDisplayName = computed(() => {
  if (resolvedCard.value.cardCode === 'todo') {
    return '我的待办';
  }
  if (resolvedCard.value.cardCode === 'cc') {
    return '我的抄送';
  }
  if (resolvedCard.value.cardCode === 'message') {
    return '我的消息';
  }
  if (resolvedCard.value.cardCode === 'favorite') {
    return '我的收藏';
  }
  return props.card.cardName;
});
const contentComponent = computed<Component>(
  () => componentConfig.value?.component ?? WorkbenchFallbackCard,
);

const {
  activeTitleTab,
  ccActiveTab,
  handleTitleTabChange,
  messageActiveTab,
  titleTabs,
  todoActiveTab,
} = useWorkbenchCardTitleTabs({
  card: resolvedCard,
  data: sourceData,
});
const {
  hasModuleAction,
  moduleActionTitle,
  openCardModule,
  openItem,
} = useWorkbenchCardNavigation({
  card: resolvedCard,
  onOpenMessageItem: (payload) => emit('openMessageItem', payload),
  onOpenWorkflowItem: (payload) => emit('openWorkflowItem', payload),
  sourceCard,
  todoActiveTab,
});
const quickEntry = useWorkbenchCardQuickEntry({
  items,
  onAdd: () => emit('quickEntryAdd'),
  onEdit: (item) => emit('quickEntryEdit', item),
  onSortSave: (nextItems) => emit('quickEntrySortSave', nextItems),
  quickEntryCardRef: contentComponentRef,
});
const cardActions = useWorkbenchCardActions({
  card: resolvedCard,
  hasModuleAction,
  loading: loadingState,
  moduleActionTitle,
  onOpenModule: openCardModule,
  onQuickEntryAdd: quickEntry.handleAdd,
  onQuickEntryCancel: quickEntry.handleCancel,
  onQuickEntrySettings: quickEntry.handleSettings,
  onRefresh: handleRefresh,
  pageEditing,
  quickEntryEditMode: quickEntry.editMode,
});
const contentProps = computed<Record<string, unknown>>(() => {
  if (resolvedCard.value.cardCode === 'todo') {
    return {
      activeTab: todoActiveTab.value,
      card: props.card,
      items: items.value,
    };
  }
  if (resolvedCard.value.cardCode === 'cc') {
    return {
      activeTab: ccActiveTab.value,
      card: props.card,
      items: items.value,
    };
  }
  if (resolvedCard.value.cardCode === 'message') {
    return {
      activeTab: messageActiveTab.value,
      card: props.card,
      items: items.value,
    };
  }
  if (resolvedCard.value.cardCode === 'favorite') {
    return {
      card: props.card,
      items: items.value,
    };
  }
  if (resolvedCard.value.cardCode === 'quick-entry') {
    return {
      editing: quickEntry.editMode.value,
      items: items.value,
    };
  }
  if (resolvedCard.value.cardCode === 'metrics') {
    return {
      items: items.value,
    };
  }
  return {
    card: props.card,
    cardName: cardDisplayName.value,
    items: items.value,
  };
});

function handleRefresh() {
  emit('refresh', sourceCard.value);
}
</script>

<template>
  <Card
    :bordered="false"
    :body-style="{ padding: '16px' }"
    :class="['workbench-card', `workbench-card--${meta.tone}`]"
  >
    <template #title>
      <div class="workbench-card__title">
        <span class="workbench-card__title-main">
          <span
            class="workbench-card__icon"
            :style="{ color: meta.accent }"
          >
            <IconifyIcon :icon="meta.icon" />
          </span>
          <span>{{ cardDisplayName }}</span>
        </span>
        <WorkbenchCardTitleTabs
          :active-key="activeTitleTab"
          :tabs="titleTabs"
          @change="handleTitleTabChange"
        />
      </div>
    </template>
    <template
      v-if="cardActions.length > 0"
      #extra
    >
      <WorkbenchCardActions :actions="cardActions" />
    </template>

    <Skeleton
      v-if="loading"
      :paragraph="{ rows: 4 }"
      active
    />
    <Alert
      v-else-if="error"
      :message="error"
      show-icon
      type="error"
    />
    <component
      :is="contentComponent"
      v-else
      ref="contentComponentRef"
      v-bind="contentProps"
      @add="quickEntry.handleAdd"
      @edit="quickEntry.handleEdit"
      @open="openItem"
    />
  </Card>
</template>

<style scoped>
.workbench-card {
  height: 100%;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 16px;
  box-shadow: 0 10px 30px rgb(15 23 42 / 6%);
}

.workbench-card :deep(.ant-card-head) {
  min-height: 52px;
  padding: 0 16px;
}

.workbench-card :deep(.ant-card-head-title),
.workbench-card :deep(.ant-card-extra) {
  display: flex;
  min-height: 52px;
  align-items: center;
  padding: 0;
}

.workbench-card :deep(.ant-card-body) {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.workbench-card__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  font-weight: 650;
}

.workbench-card__title-main {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
}

.workbench-card__icon {
  display: inline-flex;
  font-size: 18px;
}
</style>
