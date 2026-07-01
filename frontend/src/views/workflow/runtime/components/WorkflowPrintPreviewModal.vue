<script setup lang="ts">
import type {
  InstanceDetailInfo,
  WorkflowAttachmentInfo,
} from '#/api/workflow';

import { nextTick, ref } from 'vue';

import { Button, Modal, Spin } from 'ant-design-vue';

import { listWorkflowAttachments } from '#/api/workflow';
import WorkflowPrintTemplate from './WorkflowPrintTemplate.vue';

interface PrintPayload {
  detail?: InstanceDetailInfo;
}

const openState = ref(false);
const loading = ref(false);
const detail = ref<InstanceDetailInfo>();
const attachments = ref<WorkflowAttachmentInfo[]>([]);
const printContentRef = ref<HTMLElement>();

function sanitizeFileName(value: string) {
  return value.replaceAll(/[\\/:*?"<>|]/g, ' ').replaceAll(/\s+/g, ' ').trim();
}

function resolvePrintFileName() {
  const processInstance = detail.value?.processInstance;
  const title =
    processInstance?.instanceTitle || detail.value?.formInstance?.formName || '审批单';
  const instanceNo = processInstance?.instanceNo;
  return sanitizeFileName([title, instanceNo].filter(Boolean).join('-')) || '审批单';
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

async function open(payload: PrintPayload) {
  detail.value = payload.detail;
  attachments.value = [];
  openState.value = true;
  await loadAttachments();
}

async function loadAttachments() {
  const processInstanceId = detail.value?.processInstance?.id;
  if (!processInstanceId) {
    return;
  }
  loading.value = true;
  try {
    attachments.value = await listWorkflowAttachments(processInstanceId);
  } finally {
    loading.value = false;
  }
}

async function handlePrint() {
  await nextTick();
  const printTemplate = printContentRef.value?.querySelector(
    '.workflow-print-template',
  );
  if (!printTemplate) {
    return;
  }
  const iframe = document.createElement('iframe');
  iframe.style.border = '0';
  iframe.style.height = '0';
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  document.body.append(iframe);

  const printDocument = iframe.contentDocument;
  if (!printDocument) {
    iframe.remove();
    return;
  }
  const styleHtml = Array.from(
    document.querySelectorAll('style, link[rel="stylesheet"]'),
  )
    .map((node) => node.outerHTML)
    .join('\n');
  const rawPrintTitle = resolvePrintFileName();
  const printTitle = escapeHtml(rawPrintTitle);

  printDocument.open();
  printDocument.write(`<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>${printTitle}</title>
  ${styleHtml}
  <style>
    @page {
      margin: 22.5mm 21mm;
      size: A4;
    }
    html,
    body {
      background: #fff;
      margin: 0;
      padding: 0;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
  </style>
</head>
<body>${printTemplate.outerHTML}</body>
</html>`);
  printDocument.close();

  window.setTimeout(() => {
    const originalTitle = document.title;
    document.title = rawPrintTitle;
    iframe.contentWindow?.focus();
    iframe.contentWindow?.print();
    document.title = originalTitle;
    window.setTimeout(() => iframe.remove(), 500);
  }, 50);
}

defineExpose({
  open,
});
</script>

<template>
  <Modal
    v-model:open="openState"
    :footer="null"
    title="打印预览"
    :width="980"
    wrap-class-name="workflow-print-preview-wrap"
  >
    <div class="workflow-print-preview-toolbar">
      <Button
        :disabled="loading"
        type="primary"
        @click="handlePrint"
      >
        打印
      </Button>
    </div>
    <div
      ref="printContentRef"
      class="workflow-print-preview-scroll"
    >
      <Spin :spinning="loading">
        <WorkflowPrintTemplate
          :attachments="attachments"
          :detail="detail"
        />
      </Spin>
    </div>
  </Modal>
</template>

<style scoped>
.workflow-print-preview-toolbar {
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin: -4px -4px 12px;
  padding: 0 4px 12px;
}

:global(.workflow-print-preview-wrap) {
  overflow: hidden;
}

:global(.workflow-print-preview-wrap .ant-modal) {
  max-width: calc(100vw - 48px);
  padding-bottom: 0;
  top: 24px;
}

:global(.workflow-print-preview-wrap .ant-modal-content) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
}

:global(.workflow-print-preview-wrap .ant-modal-body) {
  background: #f5f5f5;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 16px;
}

.workflow-print-preview-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
