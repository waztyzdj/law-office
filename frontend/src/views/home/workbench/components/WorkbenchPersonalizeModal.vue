<script setup lang="ts">
import type { WorkbenchLayoutCard } from '#/api/home/workbench';

import { ref, watch } from 'vue';

import {
  Button,
  Modal,
  Switch,
  Table,
} from 'ant-design-vue';

const props = defineProps<{
  cards: WorkbenchLayoutCard[];
  loading?: boolean;
  open: boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  reset: [];
  save: [cards: WorkbenchLayoutCard[]];
}>();

const editableCards = ref<WorkbenchLayoutCard[]>([]);

const columns = [
  { dataIndex: 'cardName', title: '卡片名称' },
  { dataIndex: 'visible', title: '显示', width: 90 },
];

watch(
  () => [props.open, props.cards] as const,
  () => {
    if (!props.open) {
      return;
    }
    editableCards.value = props.cards.map((card) => ({
      ...card,
      visible: Boolean(card.visible),
    }));
  },
  { immediate: true },
);

function close() {
  emit('update:open', false);
}

function handleSave() {
  emit('save', editableCards.value);
}

function handleReset() {
  emit('reset');
}
</script>

<template>
  <Modal
    :confirm-loading="loading"
    :open="open"
    title="个性化工作台"
    width="640px"
    @cancel="close"
    @ok="handleSave"
  >
    <div class="workbench-personalize">
      <p class="workbench-personalize__tip">
        这里只影响你自己的工作台布局。卡片位置和大小在工作台页面直接拖拽调整。
      </p>
      <Table
        :columns="columns"
        :data-source="editableCards"
        :pagination="false"
        row-key="cardCode"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'visible'">
            <Switch v-model:checked="record.visible" size="small" />
          </template>
        </template>
      </Table>
    </div>
    <template #footer>
      <Button @click="handleReset">恢复默认</Button>
      <Button @click="close">取消</Button>
      <Button :loading="loading" type="primary" @click="handleSave">
        保存
      </Button>
    </template>
  </Modal>
</template>

<style scoped>
.workbench-personalize__tip {
  margin-bottom: 12px;
  color: hsl(var(--muted-foreground));
}

</style>
