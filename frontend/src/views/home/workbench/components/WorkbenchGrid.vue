<script setup lang="ts">
import type {
  WorkbenchCardData,
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { CSSProperties } from 'vue';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import WorkbenchCardRenderer from './WorkbenchCardRenderer.vue';

interface CardState {
  data?: WorkbenchCardData;
  error?: string;
  loading?: boolean;
}

interface GridCard extends WorkbenchLayoutCard {
  gridH: number;
  gridW: number;
  gridX: number;
  gridY: number;
}

interface InteractionState {
  cardCode: string;
  gridH: number;
  gridW: number;
  gridX: number;
  gridY: number;
  mode: 'drag' | 'resize';
  pointerX: number;
  pointerY: number;
}

const GRID_COLUMNS = 12;
const GRID_GAP = 16;
const GRID_ROW_HEIGHT = 76;
const MIN_CARD_HEIGHT = 2;
const MIN_CARD_WIDTH = 3;
const MAX_CARD_HEIGHT = 12;

const props = defineProps<{
  cardStates: Record<string, CardState | undefined>;
  cards: WorkbenchLayoutCard[];
  editable?: boolean;
}>();

const emit = defineEmits<{
  hide: [card: WorkbenchLayoutCard];
  layoutChange: [cards: WorkbenchLayoutCard[]];
  openMessageItem: [payload: { card: WorkbenchLayoutCard; item: WorkbenchCardItem }];
  openWorkflowItem: [payload: { card: WorkbenchLayoutCard; item: WorkbenchCardItem }];
  quickEntryAdd: [];
  quickEntryEdit: [item: WorkbenchCardItem];
  quickEntrySortSave: [items: WorkbenchCardItem[]];
  refresh: [card: WorkbenchLayoutCard];
}>();

const gridRef = ref<HTMLElement | null>(null);
const localCards = ref<GridCard[]>([]);
const interaction = ref<InteractionState | null>(null);

const gridStyle = computed<CSSProperties>(() => ({
  '--workbench-grid-gap': `${GRID_GAP}px`,
  '--workbench-grid-row-height': `${GRID_ROW_HEIGHT}px`,
}) as CSSProperties);

watch(
  () => props.cards,
  (cards) => {
    if (!interaction.value) {
      localCards.value = normalizeLayout(cards);
    }
  },
  { deep: true, immediate: true },
);

function defaultWidth(size?: string) {
  if (size === 'full') {
    return 12;
  }
  if (size === 'large') {
    return 6;
  }
  if (size === 'small') {
    return 3;
  }
  return 4;
}

function defaultHeight(size?: string) {
  return size === 'full' || size === 'large' ? 4 : 3;
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function normalizeCard(card: WorkbenchLayoutCard, index: number): GridCard {
  const width = clamp(card.gridW ?? defaultWidth(card.size), MIN_CARD_WIDTH, GRID_COLUMNS);
  const height = clamp(card.gridH ?? defaultHeight(card.size), MIN_CARD_HEIGHT, MAX_CARD_HEIGHT);
  return {
    ...card,
    gridH: height,
    gridW: width,
    gridX: clamp(card.gridX ?? ((index * width) % GRID_COLUMNS), 0, GRID_COLUMNS - width),
    gridY: clamp(card.gridY ?? Math.floor((index * width) / GRID_COLUMNS) * height, 0, 100),
  };
}

function cellKey(x: number, y: number) {
  return `${x}:${y}`;
}

function canPlace(card: GridCard, occupied: Set<string>) {
  if (card.gridX < 0 || card.gridY < 0 || card.gridX + card.gridW > GRID_COLUMNS) {
    return false;
  }
  for (let x = card.gridX; x < card.gridX + card.gridW; x += 1) {
    for (let y = card.gridY; y < card.gridY + card.gridH; y += 1) {
      if (occupied.has(cellKey(x, y))) {
        return false;
      }
    }
  }
  return true;
}

function occupy(card: GridCard, occupied: Set<string>) {
  for (let x = card.gridX; x < card.gridX + card.gridW; x += 1) {
    for (let y = card.gridY; y < card.gridY + card.gridH; y += 1) {
      occupied.add(cellKey(x, y));
    }
  }
}

function firstFreePosition(card: GridCard, occupied: Set<string>) {
  for (let y = 0; y <= 100; y += 1) {
    for (let x = 0; x <= GRID_COLUMNS - card.gridW; x += 1) {
      const candidate = { ...card, gridX: x, gridY: y };
      if (canPlace(candidate, occupied)) {
        return candidate;
      }
    }
  }
  return { ...card, gridX: 0, gridY: 0 };
}

function normalizeLayout(cards: WorkbenchLayoutCard[]) {
  const occupied = new Set<string>();
  return [...cards]
    .map(normalizeCard)
    .sort((left, right) => {
      if (left.gridY !== right.gridY) {
        return left.gridY - right.gridY;
      }
      if (left.gridX !== right.gridX) {
        return left.gridX - right.gridX;
      }
      return (left.sortNo ?? 0) - (right.sortNo ?? 0);
    })
    .map((card, index) => {
      const nextCard = canPlace(card, occupied) ? card : firstFreePosition(card, occupied);
      occupy(nextCard, occupied);
      return {
        ...nextCard,
        sortNo: (index + 1) * 10,
      };
    });
}

function getCardStyle(card: GridCard): CSSProperties {
  return {
    gridColumn: `${card.gridX + 1} / span ${card.gridW}`,
    gridRow: `${card.gridY + 1} / span ${card.gridH}`,
  };
}

function overlapArea(left: GridCard, right: GridCard) {
  const xOverlap = Math.max(
    0,
    Math.min(left.gridX + left.gridW, right.gridX + right.gridW) -
      Math.max(left.gridX, right.gridX),
  );
  const yOverlap = Math.max(
    0,
    Math.min(left.gridY + left.gridH, right.gridY + right.gridH) -
      Math.max(left.gridY, right.gridY),
  );
  return xOverlap * yOverlap;
}

function findSwapTarget(draggedCard: GridCard) {
  return localCards.value
    .filter((card) => card.cardCode !== draggedCard.cardCode)
    .map((card) => ({
      area: overlapArea(draggedCard, card),
      card,
    }))
    .filter((item) => item.area > 0)
    .sort((left, right) => right.area - left.area)[0]?.card;
}

function swapWithTargetIfNeeded(current: InteractionState) {
  if (current.mode !== 'drag') {
    return localCards.value;
  }
  const draggedCard = localCards.value.find((card) => card.cardCode === current.cardCode);
  if (!draggedCard) {
    return localCards.value;
  }
  const targetCard = findSwapTarget(draggedCard);
  if (!targetCard) {
    return localCards.value;
  }
  const targetX = targetCard.gridX;
  const targetY = targetCard.gridY;
  return localCards.value.map((card) => {
    if (card.cardCode === draggedCard.cardCode) {
      return {
        ...card,
        gridX: clamp(targetX, 0, GRID_COLUMNS - card.gridW),
        gridY: targetY,
      };
    }
    if (card.cardCode === targetCard.cardCode) {
      return {
        ...card,
        gridX: clamp(current.gridX, 0, GRID_COLUMNS - card.gridW),
        gridY: current.gridY,
      };
    }
    return card;
  });
}

function columnStep() {
  const width = gridRef.value?.clientWidth ?? 0;
  if (!width) {
    return 1;
  }
  return (width - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS + GRID_GAP;
}

function startInteraction(
  card: GridCard,
  event: PointerEvent,
  mode: InteractionState['mode'],
) {
  if (!props.editable || event.button !== 0) {
    return;
  }
  event.preventDefault();
  interaction.value = {
    cardCode: card.cardCode,
    gridH: card.gridH,
    gridW: card.gridW,
    gridX: card.gridX,
    gridY: card.gridY,
    mode,
    pointerX: event.clientX,
    pointerY: event.clientY,
  };
  window.addEventListener('pointermove', handlePointerMove);
  window.addEventListener('pointerup', handlePointerUp, { once: true });
}

function handlePointerMove(event: PointerEvent) {
  const current = interaction.value;
  if (!current) {
    return;
  }
  const deltaX = Math.round((event.clientX - current.pointerX) / columnStep());
  const deltaY = Math.round(
    (event.clientY - current.pointerY) / (GRID_ROW_HEIGHT + GRID_GAP),
  );
  localCards.value = localCards.value.map((card) => {
    if (card.cardCode !== current.cardCode) {
      return card;
    }
    if (current.mode === 'resize') {
      const gridW = clamp(
        current.gridW + deltaX,
        MIN_CARD_WIDTH,
        GRID_COLUMNS - current.gridX,
      );
      return {
        ...card,
        gridH: clamp(current.gridH + deltaY, MIN_CARD_HEIGHT, MAX_CARD_HEIGHT),
        gridW,
      };
    }
    return {
      ...card,
      gridX: clamp(current.gridX + deltaX, 0, GRID_COLUMNS - card.gridW),
      gridY: clamp(current.gridY + deltaY, 0, 100),
    };
  });
}

function handlePointerUp() {
  const current = interaction.value;
  if (!current) {
    return;
  }
  window.removeEventListener('pointermove', handlePointerMove);
  localCards.value = swapWithTargetIfNeeded(current);
  const normalizedCards = normalizeLayout(localCards.value);
  localCards.value = normalizedCards;
  interaction.value = null;
  emit('layoutChange', normalizedCards);
}

function handleHideCard(card: GridCard) {
  if (!props.editable) {
    return;
  }
  emit('hide', { ...card, visible: false });
}

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handlePointerMove);
});
</script>

<template>
  <section
    ref="gridRef"
    class="workbench-grid"
    :class="{
      'workbench-grid--editable': editable,
      'workbench-grid--interacting': !!interaction,
    }"
    :style="gridStyle"
  >
    <article
      v-for="card in localCards"
      :key="card.cardCode"
      class="workbench-grid__item"
      :class="{ 'workbench-grid__item--active': interaction?.cardCode === card.cardCode }"
      :style="getCardStyle(card)"
    >
      <button
        v-if="editable"
        class="workbench-grid__hide-button"
        title="隐藏卡片"
        type="button"
        @click="handleHideCard(card)"
      >
        <IconifyIcon icon="lucide:x" />
      </button>
      <button
        v-if="editable"
        class="workbench-grid__drag-handle"
        title="拖动卡片"
        type="button"
        @pointerdown="(event) => startInteraction(card, event, 'drag')"
      >
        <IconifyIcon icon="lucide:grip-horizontal" />
      </button>
      <WorkbenchCardRenderer
        :card="card"
        :data="cardStates[card.cardCode]?.data"
        :editing="editable"
        :error="cardStates[card.cardCode]?.error"
        :loading="cardStates[card.cardCode]?.loading"
        @open-message-item="$emit('openMessageItem', $event)"
        @open-workflow-item="$emit('openWorkflowItem', $event)"
        @quick-entry-add="$emit('quickEntryAdd')"
        @quick-entry-edit="$emit('quickEntryEdit', $event)"
        @quick-entry-sort-save="$emit('quickEntrySortSave', $event)"
        @refresh="$emit('refresh', $event)"
      />
      <button
        v-if="editable"
        class="workbench-grid__resize-handle"
        title="调整卡片大小"
        type="button"
        @pointerdown="(event) => startInteraction(card, event, 'resize')"
      >
        <span></span>
      </button>
    </article>
  </section>
</template>

<style scoped>
.workbench-grid {
  display: grid;
  grid-auto-rows: var(--workbench-grid-row-height);
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--workbench-grid-gap);
  user-select: none;
}

.workbench-grid__item {
  position: relative;
  min-width: 0;
  min-height: 0;
  transition:
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.workbench-grid--interacting .workbench-grid__item {
  transition: none;
}

.workbench-grid__item--active {
  z-index: 3;
  transform: scale(1.01);
}

.workbench-grid--editable .workbench-grid__item {
  outline: 1px dashed hsl(var(--primary) / 28%);
  outline-offset: 4px;
}

.workbench-grid__item :deep(.ant-card) {
  display: flex;
  height: 100%;
  flex-direction: column;
}

.workbench-grid__item :deep(.ant-card-body) {
  min-height: 0;
  flex: 1 1 auto;
  overflow: hidden;
}

.workbench-grid__hide-button,
.workbench-grid__drag-handle {
  position: absolute;
  z-index: 4;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  color: hsl(var(--muted-foreground));
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    opacity 0.2s ease;
}

.workbench-grid__hide-button {
  top: 12px;
  right: 12px;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: hsl(var(--background) / 92%);
  box-shadow: 0 8px 18px rgb(15 23 42 / 12%);
}

.workbench-grid__hide-button:hover {
  background: hsl(var(--destructive) / 12%);
  color: hsl(var(--destructive));
}

.workbench-grid__drag-handle {
  top: 13px;
  left: 50%;
  width: 32px;
  height: 18px;
  border-radius: 999px;
  background: hsl(var(--muted) / 88%);
  cursor: grab;
  transform: translateX(-50%);
}

.workbench-grid__drag-handle:hover {
  background: hsl(var(--primary) / 12%);
  color: hsl(var(--primary));
}

.workbench-grid__drag-handle:active {
  cursor: grabbing;
}

.workbench-grid__resize-handle {
  position: absolute;
  right: 6px;
  bottom: 6px;
  z-index: 4;
  display: inline-flex;
  align-items: flex-end;
  justify-content: flex-end;
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  cursor: nwse-resize;
  padding: 0;
  transition: opacity 0.2s ease;
}

.workbench-grid__resize-handle span {
  width: 12px;
  height: 12px;
  border-right: 2px solid hsl(var(--primary));
  border-bottom: 2px solid hsl(var(--primary));
  border-radius: 0 0 4px;
}

@media (max-width: 768px) {
  .workbench-grid {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .workbench-grid__item {
    min-height: 260px;
  }

  .workbench-grid__drag-handle,
  .workbench-grid__resize-handle {
    display: none;
  }
}
</style>
