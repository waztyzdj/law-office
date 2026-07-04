import type {
  WorkbenchCardData,
  WorkbenchLayoutCard,
  WorkbenchLayoutSaveReq,
} from '#/api/home/workbench';

import { computed, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  getWorkbenchCardData,
  getWorkbenchLayout,
  resetWorkbenchLayout,
  saveWorkbenchLayout,
} from '#/api/home/workbench';

interface CardState {
  data?: WorkbenchCardData;
  error?: string;
  loading: boolean;
}

interface SaveLayoutOptions {
  reload?: boolean;
  silent?: boolean;
}

function normalizeCards(cards: WorkbenchLayoutCard[]) {
  return [...cards].sort((left, right) => {
    const leftSort = left.sortNo ?? 0;
    const rightSort = right.sortNo ?? 0;
    if (leftSort !== rightSort) {
      return leftSort - rightSort;
    }
    return left.cardCode.localeCompare(right.cardCode);
  });
}

export function useWorkbench() {
  const cards = ref<WorkbenchLayoutCard[]>([]);
  const hiddenCards = ref<WorkbenchLayoutCard[]>([]);
  const layoutLoading = ref(false);
  const savingLayout = ref(false);
  const cardStates = reactive<Record<string, CardState>>({});

  const visibleCards = computed(() => normalizeCards(cards.value));
  const allConfigurableCards = computed(() =>
    normalizeCards([...cards.value, ...hiddenCards.value]),
  );
  const hasAnyCard = computed(() => allConfigurableCards.value.length > 0);

  async function loadCardData(card: WorkbenchLayoutCard) {
    const cardCode = card.cardCode;
    cardStates[cardCode] = {
      ...cardStates[cardCode],
      error: undefined,
      loading: true,
    };
    try {
      const data = await getWorkbenchCardData({
        cardCode,
      });
      cardStates[cardCode] = {
        data,
        error: undefined,
        loading: false,
      };
    } catch (error) {
      cardStates[cardCode] = {
        ...cardStates[cardCode],
        error: error instanceof Error ? error.message : '卡片加载失败',
        loading: false,
      };
    }
  }

  async function loadVisibleCardData() {
    await Promise.all(visibleCards.value.map((card) => loadCardData(card)));
  }

  async function loadLayout() {
    layoutLoading.value = true;
    try {
      const layout = await getWorkbenchLayout();
      cards.value = normalizeCards(layout.cards || []);
      hiddenCards.value = normalizeCards(layout.hiddenCards || []);
      await loadVisibleCardData();
    } finally {
      layoutLoading.value = false;
    }
  }

  async function refreshAllCards() {
    await loadVisibleCardData();
  }

  async function saveLayout(
    cardsToSave: WorkbenchLayoutCard[],
    options: SaveLayoutOptions = {},
  ) {
    const { reload = true, silent = false } = options;
    savingLayout.value = true;
    try {
      const payload: WorkbenchLayoutSaveReq = {
        cards: cardsToSave.map((card) => ({
          cardCode: card.cardCode,
          gridH: card.gridH,
          gridW: card.gridW,
          gridX: card.gridX,
          gridY: card.gridY,
          visible: card.visible,
        })),
      };
      await saveWorkbenchLayout(payload);
      if (!silent) {
        message.success('工作台布局已保存');
      }
      if (reload) {
        await loadLayout();
      } else {
        const savedCardMap = new Map(cardsToSave.map((card) => [card.cardCode, card]));
        cards.value = normalizeCards(
          cards.value.map((card) => savedCardMap.get(card.cardCode) ?? card),
        );
      }
    } finally {
      savingLayout.value = false;
    }
  }

  async function resetLayout() {
    savingLayout.value = true;
    try {
      await resetWorkbenchLayout();
      message.success('已恢复默认布局');
      await loadLayout();
    } finally {
      savingLayout.value = false;
    }
  }

  return {
    allConfigurableCards,
    cardStates,
    hasAnyCard,
    hiddenCards,
    layoutLoading,
    loadCardData,
    loadLayout,
    refreshAllCards,
    resetLayout,
    saveLayout,
    savingLayout,
    visibleCards,
  };
}
