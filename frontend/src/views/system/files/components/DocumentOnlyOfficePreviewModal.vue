<script setup lang="ts">
import type {
  DocumentFileInfo,
  OnlyOfficePreviewConfig,
} from '#/api/system/document';

import { computed, nextTick, onBeforeUnmount, ref, shallowRef } from 'vue';

import { loadScript } from '@vben/utils';

import { Empty, Modal, Spin, message } from 'ant-design-vue';

import { getOnlyOfficePreviewConfig } from '#/api/system/document';

const EDITOR_CONTAINER_ID = 'document-onlyoffice-preview-editor';
const ONLYOFFICE_API_SCRIPT_RE =
  /\/web-apps\/apps\/api\/documents\/api\.js(?:[?#].*)?$/i;

interface OnlyOfficeEditor {
  destroyEditor?: () => void;
}

interface OnlyOfficeApi {
  DocEditor: new (
    placeholderId: string,
    config: Record<string, unknown>,
  ) => OnlyOfficeEditor;
}

declare global {
  interface Window {
    DocsAPI?: OnlyOfficeApi;
  }
}

const openState = ref(false);
const loading = ref(false);
const errorText = ref('');
const currentRecord = ref<DocumentFileInfo>();
const editor = shallowRef<OnlyOfficeEditor>();
let previewLoadVersion = 0;

const modalTitle = computed(() => currentRecord.value?.fileName || '文档预览');

function destroyEditor() {
  try {
    editor.value?.destroyEditor?.();
  } finally {
    editor.value = undefined;
  }
}

function resetOnlyOfficeApiScript() {
  document.querySelectorAll<HTMLScriptElement>('script[src]').forEach((script) => {
    if (ONLYOFFICE_API_SCRIPT_RE.test(script.src)) {
      script.remove();
    }
  });
  window.DocsAPI = undefined;
}

function withCacheBuster(src: string) {
  const url = new URL(src, window.location.href);
  url.searchParams.set('_t', Date.now().toString());
  return url.toString();
}

async function mountEditor(preview: OnlyOfficePreviewConfig, version: number) {
  if (!preview.documentServerApiUrl || !preview.config) {
    throw new Error('ONLYOFFICE 预览配置不完整');
  }
  await nextTick();
  if (version !== previewLoadVersion) {
    return;
  }
  destroyEditor();
  resetOnlyOfficeApiScript();
  await loadScript(withCacheBuster(preview.documentServerApiUrl));
  if (version !== previewLoadVersion) {
    return;
  }
  const docsApi = window.DocsAPI;
  if (!docsApi?.DocEditor) {
    throw new Error('ONLYOFFICE 脚本加载失败');
  }
  editor.value = new docsApi.DocEditor(EDITOR_CONTAINER_ID, preview.config);
}

async function open(record: DocumentFileInfo) {
  if (!record.id || record.izFolder === '1') {
    return;
  }
  currentRecord.value = record;
  errorText.value = '';
  openState.value = true;
  loading.value = true;
  const version = ++previewLoadVersion;
  try {
    const preview = await getOnlyOfficePreviewConfig(record.id);
    if (version !== previewLoadVersion) {
      return;
    }
    await mountEditor(preview, version);
  } catch (error) {
    if (version !== previewLoadVersion) {
      return;
    }
    destroyEditor();
    const text = error instanceof Error ? error.message : '文档预览加载失败';
    errorText.value = text;
    message.error(text);
  } finally {
    if (version === previewLoadVersion) {
      loading.value = false;
    }
  }
}

function handleAfterClose() {
  previewLoadVersion += 1;
  destroyEditor();
  errorText.value = '';
  currentRecord.value = undefined;
}

defineExpose({
  open,
});

onBeforeUnmount(() => {
  previewLoadVersion += 1;
  destroyEditor();
});
</script>

<template>
  <Modal
    v-model:open="openState"
    :body-style="{ height: 'calc(100vh - 104px)', padding: 0 }"
    class="document-preview-modal"
    destroy-on-close
    :footer="null"
    :style="{ maxWidth: 'calc(100vw - 48px)', paddingBottom: 0, top: '24px' }"
    :title="modalTitle"
    width="calc(100vw - 48px)"
    @after-close="handleAfterClose"
  >
    <div class="document-preview">
      <Spin :spinning="loading">
        <Empty
          v-if="errorText"
          class="document-preview__empty"
          :description="errorText"
        />
        <div
          v-else
          :id="EDITOR_CONTAINER_ID"
          class="document-preview__editor"
        ></div>
      </Spin>
    </div>
  </Modal>
</template>

<style scoped>
.document-preview {
  height: 100%;
  min-height: 0;
  background: hsl(var(--background));
}

:global(.document-preview-modal) {
  margin: 0 auto;
}

:global(.document-preview-modal .ant-modal-content) {
  display: flex;
  height: calc(100vh - 48px);
  flex-direction: column;
  overflow: hidden;
  border-radius: 6px;
}

:global(.document-preview-modal .ant-modal-body) {
  flex: 1;
  min-height: 0;
}

.document-preview :deep(.ant-spin-nested-loading),
.document-preview :deep(.ant-spin-container) {
  height: 100%;
}

.document-preview__editor {
  width: 100%;
  height: 100%;
}

.document-preview__empty {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

@media (max-width: 900px) {
  :global(.document-preview-modal .ant-modal-body) {
    height: calc(100vh - 104px);
  }
}
</style>
