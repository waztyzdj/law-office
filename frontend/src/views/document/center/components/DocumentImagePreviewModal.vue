<script setup lang="ts">
import type { DocumentFileInfo } from '#/api/document';

import { computed, onBeforeUnmount, ref } from 'vue';

import { Empty, Modal, Spin, message } from 'ant-design-vue';

import { downloadDocumentThumbnail } from '#/api/document';

const openState = ref(false);
const loading = ref(false);
const errorText = ref('');
const currentRecord = ref<DocumentFileInfo>();
const imageUrl = ref('');
let imageLoadVersion = 0;

const modalTitle = computed(() => currentRecord.value?.fileName || '图片预览');

function revokeImageUrl() {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = '';
  }
}

async function open(record: DocumentFileInfo) {
  if (!record.id || record.izFolder === '1') {
    return;
  }
  revokeImageUrl();
  currentRecord.value = record;
  errorText.value = '';
  openState.value = true;
  loading.value = true;
  const version = ++imageLoadVersion;
  try {
    const blob = await downloadDocumentThumbnail(record.id);
    if (version !== imageLoadVersion) {
      return;
    }
    imageUrl.value = URL.createObjectURL(blob);
  } catch (error) {
    if (version !== imageLoadVersion) {
      return;
    }
    const text = error instanceof Error ? error.message : '图片预览加载失败';
    errorText.value = text;
    message.error(text);
  } finally {
    if (version === imageLoadVersion) {
      loading.value = false;
    }
  }
}

function handleAfterClose() {
  imageLoadVersion += 1;
  revokeImageUrl();
  errorText.value = '';
  currentRecord.value = undefined;
}

defineExpose({
  open,
});

onBeforeUnmount(() => {
  imageLoadVersion += 1;
  revokeImageUrl();
});
</script>

<template>
  <Modal
    v-model:open="openState"
    :body-style="{ height: 'calc(100vh - 104px)', padding: 0 }"
    class="document-image-preview-modal"
    destroy-on-close
    :footer="null"
    :style="{ maxWidth: 'calc(100vw - 48px)', paddingBottom: 0, top: '24px' }"
    :title="modalTitle"
    width="calc(100vw - 48px)"
    @after-close="handleAfterClose"
  >
    <div class="document-image-preview">
      <Spin :spinning="loading">
        <Empty
          v-if="errorText"
          class="document-image-preview__empty"
          :description="errorText"
        />
        <img
          v-else-if="imageUrl"
          :alt="modalTitle"
          class="document-image-preview__image"
          :src="imageUrl"
        />
      </Spin>
    </div>
  </Modal>
</template>

<style scoped>
.document-image-preview {
  height: 100%;
  min-height: 0;
  background:
    linear-gradient(45deg, hsl(var(--muted) / 35%) 25%, transparent 25%),
    linear-gradient(-45deg, hsl(var(--muted) / 35%) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, hsl(var(--muted) / 35%) 75%),
    linear-gradient(-45deg, transparent 75%, hsl(var(--muted) / 35%) 75%);
  background-color: hsl(var(--background));
  background-position:
    0 0,
    0 8px,
    8px -8px,
    -8px 0;
  background-size: 16px 16px;
}

:global(.document-image-preview-modal) {
  margin: 0 auto;
}

:global(.document-image-preview-modal .ant-modal-content) {
  display: flex;
  height: calc(100vh - 48px);
  flex-direction: column;
  overflow: hidden;
  border-radius: 6px;
}

:global(.document-image-preview-modal .ant-modal-body) {
  flex: 1;
  min-height: 0;
}

.document-image-preview :deep(.ant-spin-nested-loading),
.document-image-preview :deep(.ant-spin-container) {
  height: 100%;
}

.document-image-preview__image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.document-image-preview__empty {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

@media (max-width: 900px) {
  :global(.document-image-preview-modal .ant-modal-body) {
    height: calc(100vh - 104px);
  }
}
</style>
