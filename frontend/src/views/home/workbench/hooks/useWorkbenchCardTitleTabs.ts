import type {
  WorkbenchCardData,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { ComputedRef } from 'vue';
import type {
  WorkbenchCcTabKey,
  WorkbenchMessageTabKey,
  WorkbenchTitleTab,
  WorkbenchTitleTabKey,
  WorkbenchTodoTabKey,
} from '../types';

import { computed, ref, watch } from 'vue';

interface UseWorkbenchCardTitleTabsOptions {
  card: ComputedRef<WorkbenchLayoutCard>;
  data: ComputedRef<WorkbenchCardData | undefined>;
}

export function useWorkbenchCardTitleTabs(
  options: UseWorkbenchCardTitleTabsOptions,
) {
  const ccActiveTab = ref<WorkbenchCcTabKey>('unread-cc');
  const messageActiveTab = ref<WorkbenchMessageTabKey>('unread-message');
  const messageTabAutoSelected = ref(true);
  const todoActiveTab = ref<WorkbenchTodoTabKey>('todo');
  const summary = computed(() => options.data.value?.summary ?? {});
  const todoTabs = computed<WorkbenchTitleTab[]>(() => [
    { key: 'todo', label: '待办', total: Number(summary.value.todoTotal ?? 0) },
    { key: 'done', label: '已办', total: Number(summary.value.doneTotal ?? 0) },
  ]);
  const ccTabs = computed<WorkbenchTitleTab[]>(() => [
    { key: 'unread-cc', label: '未读', total: Number(summary.value.unreadTotal ?? 0) },
    { key: 'read-cc', label: '已读', total: Number(summary.value.readTotal ?? 0) },
  ]);
  const hasUrgeMessages = computed(() => Number(summary.value.urgeTotal ?? 0) > 0);
  const hasTimeoutMessages = computed(() => Number(summary.value.timeoutTotal ?? 0) > 0);
  const messageTabs = computed<WorkbenchTitleTab[]>(() => {
    const tabs: WorkbenchTitleTab[] = [
      {
        key: 'unread-message',
        label: '未读',
        total: Number(summary.value.unreadTotal ?? 0),
      },
      {
        key: 'read-message',
        label: '已读',
        total: Number(summary.value.readTotal ?? 0),
      },
    ];
    if (hasTimeoutMessages.value) {
      tabs.unshift({
        key: 'timeout-message',
        label: '超时',
        total: Number(summary.value.timeoutTotal ?? 0),
      });
    }
    if (hasUrgeMessages.value) {
      tabs.unshift({
        key: 'urge-message',
        label: '催办',
        total: Number(summary.value.urgeTotal ?? 0),
      });
    }
    return tabs;
  });
  const titleTabs = computed<WorkbenchTitleTab[]>(() => {
    if (options.card.value.cardCode === 'todo') {
      return todoTabs.value;
    }
    if (options.card.value.cardCode === 'cc') {
      return ccTabs.value;
    }
    if (options.card.value.cardCode === 'message') {
      return messageTabs.value;
    }
    return [];
  });
  const activeTitleTab = computed<undefined | WorkbenchTitleTabKey>(() => {
    if (options.card.value.cardCode === 'todo') {
      return todoActiveTab.value;
    }
    if (options.card.value.cardCode === 'cc') {
      return ccActiveTab.value;
    }
    if (options.card.value.cardCode === 'message') {
      return messageActiveTab.value;
    }
    return undefined;
  });

  watch(
    () => ({
      activeTab: messageActiveTab.value,
      cardCode: options.card.value.cardCode,
      hasTimeout: hasTimeoutMessages.value,
      hasUrge: hasUrgeMessages.value,
    }),
    ({ activeTab, cardCode }) => {
      if (cardCode !== 'message') {
        return;
      }
      const preferredTab = getPreferredMessageTab();
      if (
        !isMessageTabAvailable(activeTab) ||
        (messageTabAutoSelected.value && activeTab !== preferredTab)
      ) {
        messageActiveTab.value = preferredTab;
        messageTabAutoSelected.value = true;
      }
    },
    { immediate: true },
  );

  watch(
    () => options.card.value.cardCode,
    (cardCode) => {
      if (cardCode === 'message') {
        messageTabAutoSelected.value = true;
        messageActiveTab.value = getPreferredMessageTab();
      }
    },
  );

  function handleTitleTabChange(tabKey: WorkbenchTitleTabKey) {
    if (tabKey === 'todo' || tabKey === 'done') {
      todoActiveTab.value = tabKey;
      return;
    }
    if (tabKey === 'unread-cc' || tabKey === 'read-cc') {
      ccActiveTab.value = tabKey;
      return;
    }
    messageActiveTab.value = tabKey;
    messageTabAutoSelected.value = false;
  }

  function getPreferredMessageTab(): WorkbenchMessageTabKey {
    if (hasUrgeMessages.value) {
      return 'urge-message';
    }
    if (hasTimeoutMessages.value) {
      return 'timeout-message';
    }
    return 'unread-message';
  }

  function isMessageTabAvailable(tabKey: WorkbenchMessageTabKey) {
    if (tabKey === 'urge-message') {
      return hasUrgeMessages.value;
    }
    if (tabKey === 'timeout-message') {
      return hasTimeoutMessages.value;
    }
    return true;
  }

  return {
    activeTitleTab,
    ccActiveTab,
    handleTitleTabChange,
    messageActiveTab,
    titleTabs,
    todoActiveTab,
  };
}
