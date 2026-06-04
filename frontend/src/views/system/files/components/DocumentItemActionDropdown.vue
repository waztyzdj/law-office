<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/system/document';
import type { DropdownProps } from 'ant-design-vue';
import type { DocumentBatchAction, DocumentContentViewProps } from '../types';

import { computed, ref } from 'vue';

import { Dropdown } from 'ant-design-vue';

import DocumentItemActionMenu from './DocumentItemActionMenu.vue';

const activeDropdownId = ref<symbol | null>(null);

interface Props
  extends Pick<
    DocumentContentViewProps,
    | 'canEditContentItem'
    | 'canEditItem'
    | 'canPreviewItem'
    | 'canViewHistoryItem'
    | 'getContextCopyableRecords'
    | 'getContextCuttableRecords'
    | 'getContextDeletableRecords'
    | 'getContextDownloadRecords'
    | 'isSingleContext'
  > {
  mode?: 'button' | 'context';
  record: DocumentFileInfo;
  scope: DocumentScope;
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'button',
});

const emit = defineEmits<{
  action: [event: string, record: DocumentFileInfo];
  batchAction: [event: DocumentBatchAction, record: DocumentFileInfo];
}>();

const dropdownId = Symbol('document-item-action-dropdown');
const isContextMode = computed(() => props.mode === 'context');
const dropdownTrigger = computed<DropdownProps['trigger']>(() => [
  isContextMode.value ? 'contextmenu' : 'click',
]);
const singleContext = computed(() =>
  isContextMode.value ? props.isSingleContext(props.record) : true,
);
const dropdownOpen = computed({
  get: () => activeDropdownId.value === dropdownId,
  set: (open: boolean) => {
    if (open) {
      activeDropdownId.value = dropdownId;
      return;
    }
    if (activeDropdownId.value === dropdownId) {
      activeDropdownId.value = null;
    }
  },
});

function handleAction(event: string) {
  dropdownOpen.value = false;
  emit('action', event, props.record);
}

function handleBatchAction(event: DocumentBatchAction) {
  dropdownOpen.value = false;
  emit('batchAction', event, props.record);
}
</script>

<template>
  <Dropdown v-model:open="dropdownOpen" :trigger="dropdownTrigger">
    <slot></slot>

    <template #overlay>
      <DocumentItemActionMenu
        :can-edit="singleContext && canEditItem(record)"
        :can-edit-content="singleContext && canEditContentItem(record)"
        :can-preview="singleContext && canPreviewItem(record)"
        :can-view-history="singleContext && canViewHistoryItem(record)"
        :context-copyable-count="getContextCopyableRecords(record).length"
        :context-cuttable-count="getContextCuttableRecords(record).length"
        :context-deletable-count="getContextDeletableRecords(record).length"
        :context-downloadable-count="getContextDownloadRecords(record).length"
        :record="record"
        :scope="scope"
        :single-context="singleContext"
        @action="handleAction"
        @batch-action="handleBatchAction"
      />
    </template>
  </Dropdown>
</template>
