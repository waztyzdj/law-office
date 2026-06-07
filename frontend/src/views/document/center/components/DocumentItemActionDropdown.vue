<script lang="ts">
import { ref } from 'vue';

const activeDocumentItemActionDropdownId = ref<symbol | null>(null);
</script>

<script setup lang="ts">
import type { DocumentFileInfo, DocumentScope } from '#/api/document';
import type { DropdownProps } from 'ant-design-vue';
import type { DocumentBatchAction, DocumentContentViewProps } from '../types';

import { computed } from 'vue';

import { Dropdown } from 'ant-design-vue';

import DocumentItemActionMenu from './DocumentItemActionMenu.vue';

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
    | 'getContextRestorableRecords'
    | 'isSingleContext'
> {
  disabled?: boolean;
  isGlobalSearch?: boolean;
  mode?: 'button' | 'context';
  record: DocumentFileInfo;
  readonlyContext?: boolean;
  scope: DocumentScope;
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  isGlobalSearch: false,
  mode: 'button',
  readonlyContext: false,
});

const emit = defineEmits<{
  action: [event: string, record: DocumentFileInfo];
  batchAction: [event: DocumentBatchAction, record: DocumentFileInfo];
}>();

const dropdownId = Symbol('document-item-action-dropdown');
const isContextMode = computed(() => props.mode === 'context');
const dropdownTrigger = computed<DropdownProps['trigger']>(() =>
  props.disabled ? [] : [isContextMode.value ? 'contextmenu' : 'click'],
);
const singleContext = computed(() =>
  isContextMode.value ? props.isSingleContext(props.record) : true,
);
const dropdownOpen = computed({
  get: () => activeDocumentItemActionDropdownId.value === dropdownId,
  set: (open: boolean) => {
    if (open) {
      activeDocumentItemActionDropdownId.value = dropdownId;
      return;
    }
    if (activeDocumentItemActionDropdownId.value === dropdownId) {
      activeDocumentItemActionDropdownId.value = null;
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
  <slot v-if="disabled"></slot>
  <Dropdown v-else v-model:open="dropdownOpen" :trigger="dropdownTrigger">
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
        :context-restorable-count="getContextRestorableRecords(record).length"
        :record="record"
        :readonly-context="readonlyContext"
        :search-result="isGlobalSearch"
        :scope="scope"
        :single-context="singleContext"
        @action="handleAction"
        @batch-action="handleBatchAction"
      />
    </template>
  </Dropdown>
</template>
