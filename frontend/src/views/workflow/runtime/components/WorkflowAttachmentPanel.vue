<script setup lang="ts">
import type {
  WorkflowAttachmentInfo,
  WorkflowAttachmentSource,
} from '#/api/workflow';
import type { DocumentFileInfo } from '#/api/document';
import type { FileUploadResult } from '#/api/system/file';
import type { UploadProps } from 'ant-design-vue';

import { computed, ref, watch } from 'vue';

import {
  DeleteOutlined,
  DownOutlined,
  DownloadOutlined,
  PaperClipOutlined,
  RightOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue';

import {
  Button,
  Empty,
  message,
  Popconfirm,
  Spin,
  Tooltip,
  Upload,
} from 'ant-design-vue';

import {
  bindWorkflowAttachment,
  deleteWorkflowAttachment,
  downloadWorkflowAttachment,
  downloadWorkflowAttachmentPackage,
  listWorkflowAttachments,
} from '#/api/workflow';
import {
  IMAGE_PREVIEW_EXTENSIONS,
  ONLYOFFICE_PREVIEW_EXTENSIONS,
} from '#/constants/document';
import {
  uploadFile,
} from '#/api/system/file';

import DocumentImagePreviewModal from '../../../document/center/components/DocumentImagePreviewModal.vue';
import DocumentOnlyOfficePreviewModal from '../../../document/center/components/DocumentOnlyOfficePreviewModal.vue';

interface AttachmentItem extends WorkflowAttachmentInfo {
  fileName?: string;
  fileSize?: number;
  fileType?: string;
  localKey: string;
  pending?: boolean;
}

const props = withDefaults(
  defineProps<{
    attachmentSource?: WorkflowAttachmentSource;
    editable?: boolean;
    instanceNo?: string;
    instanceTitle?: string;
    nodeId?: string;
    nodeName?: string;
    processInstanceId?: string;
    taskId?: string;
  }>(),
  {
    attachmentSource: 'task',
    editable: false,
  },
);

const MAX_ATTACHMENT_SIZE = 50 * 1024 * 1024;

const attachments = ref<AttachmentItem[]>([]);
const loading = ref(false);
const uploadCount = ref(0);
const downloadingAll = ref(false);
const downloadingFileIds = ref<string[]>([]);
const deletingIds = ref<string[]>([]);
const collapsed = ref(false);
const imagePreviewModalRef = ref<InstanceType<typeof DocumentImagePreviewModal>>();
const documentPreviewModalRef = ref<InstanceType<typeof DocumentOnlyOfficePreviewModal>>();

const isUploading = computed(() => uploadCount.value > 0);
const hasAttachments = computed(() => attachments.value.length > 0);
const hasDownloadableAttachments = computed(() =>
  attachments.value.some((item) => item.id && !item.pending),
);
const canUpload = computed(() => props.editable);
const attachmentPackageFileName = computed(() => {
  const title = props.instanceTitle?.trim() || '审批附件';
  const instanceNo = props.instanceNo?.trim();
  return `${sanitizeFileName(instanceNo ? `${title}-${instanceNo}` : title)}.zip`;
});

watch(
  () => props.processInstanceId,
  () => {
    void reload();
  },
  { immediate: true },
);

async function reload() {
  if (!props.processInstanceId) {
    attachments.value = attachments.value.filter((item) => item.pending);
    return;
  }

  loading.value = true;
  try {
    const records = await listWorkflowAttachments(props.processInstanceId);
    attachments.value = records.map(toAttachmentItem);
  } finally {
    loading.value = false;
  }
}

function toAttachmentItem(record: WorkflowAttachmentInfo): AttachmentItem {
  return {
    ...record,
    fileName: record.fileName || record.fileId || '-',
    localKey: record.id || record.fileId || `${Date.now()}-${Math.random()}`,
  };
}

function toPendingAttachment(uploadResult: FileUploadResult, fallbackName: string) {
  return {
    attachmentSource: props.attachmentSource,
    fileId: uploadResult.fileId,
    fileName: uploadResult.fileName || fallbackName,
    fileSize: uploadResult.fileSize,
    fileType: uploadResult.fileType,
    localKey: uploadResult.fileId || `${Date.now()}-${fallbackName}`,
    pending: true,
  } satisfies AttachmentItem;
}

const handleBeforeUpload: UploadProps['beforeUpload'] = async (file) => {
  const nativeFile = file as File;
  if (nativeFile.size > MAX_ATTACHMENT_SIZE) {
    message.error('单个附件不能超过50MB');
    return false;
  }

  uploadCount.value += 1;
  try {
    const uploadResult = await uploadFile(nativeFile);
    if (!uploadResult.fileId) {
      throw new Error('文件上传失败');
    }
    if (props.processInstanceId) {
      await bindUploadedFile(uploadResult.fileId);
      await reload();
    } else {
      attachments.value = [
        ...attachments.value,
        toPendingAttachment(uploadResult, nativeFile.name),
      ];
    }
    message.success(`附件「${uploadResult.fileName || nativeFile.name}」已上传`);
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '文件上传失败';
    message.error(errorMessage);
  } finally {
    uploadCount.value = Math.max(uploadCount.value - 1, 0);
  }
  return false;
};

async function bindUploadedFile(fileId: string, processInstanceId = props.processInstanceId) {
  if (!processInstanceId) {
    return;
  }
  await bindWorkflowAttachment({
    attachmentSource: props.attachmentSource,
    fileId,
    nodeId: props.nodeId,
    nodeName: props.nodeName,
    processInstanceId,
    taskId: props.taskId,
  });
}

async function bindPendingAttachments(processInstanceId: string) {
  const pendingItems = attachments.value.filter((item) => item.pending && item.fileId);
  if (pendingItems.length === 0) {
    return;
  }
  for (const item of pendingItems) {
    await bindUploadedFile(item.fileId as string, processInstanceId);
  }
  attachments.value = [];
}

function reset() {
  attachments.value = [];
  loading.value = false;
  uploadCount.value = 0;
  downloadingAll.value = false;
  downloadingFileIds.value = [];
  deletingIds.value = [];
}

function isDownloading(fileId?: string) {
  return Boolean(fileId && downloadingFileIds.value.includes(fileId));
}

function setDownloading(fileId: string, downloading: boolean) {
  downloadingFileIds.value = downloading
    ? [...downloadingFileIds.value, fileId]
    : downloadingFileIds.value.filter((item) => item !== fileId);
}

function isDeleting(id?: string) {
  return Boolean(id && deletingIds.value.includes(id));
}

function setDeleting(id: string, deleting: boolean) {
  deletingIds.value = deleting
    ? [...deletingIds.value, id]
    : deletingIds.value.filter((item) => item !== id);
}

function resolveFileExtension(item: AttachmentItem) {
  const fileName = item.fileName || '';
  const dotIndex = fileName.lastIndexOf('.');
  return dotIndex >= 0 ? fileName.slice(dotIndex + 1).toLowerCase() : '';
}

function isImageAttachment(item: AttachmentItem) {
  const extension = resolveFileExtension(item);
  if (extension === 'svg') {
    return false;
  }
  const type = String(item.fileType || '').toLowerCase();
  return (
    type === 'image' ||
    type.startsWith('image/') ||
    IMAGE_PREVIEW_EXTENSIONS.has(extension)
  );
}

function toDocumentFileInfo(item: AttachmentItem): DocumentFileInfo {
  return {
    fileName: item.fileName,
    fileSize: item.fileSize,
    fileType: item.fileType,
    id: item.fileId,
    izFolder: '0',
  };
}

function handlePreview(item: AttachmentItem) {
  if (!item.fileId) {
    message.warning('附件文件ID为空');
    return;
  }
  const documentFile = toDocumentFileInfo(item);
  if (isImageAttachment(item)) {
    imagePreviewModalRef.value?.open(documentFile);
    return;
  }
  if (ONLYOFFICE_PREVIEW_EXTENSIONS.has(resolveFileExtension(item))) {
    documentPreviewModalRef.value?.open(documentFile, 'view');
    return;
  }
  message.info('该附件暂不支持在线预览，请下载查看');
}

async function handleDownload(item: AttachmentItem) {
  if (!item.id) {
    message.warning('附件ID为空');
    return;
  }
  setDownloading(item.id, true);
  try {
    const blob = await downloadWorkflowAttachment(item.id);
    downloadBlob(blob, item.fileName?.trim() || 'download');
  } catch {
    message.error('附件下载失败');
  } finally {
    setDownloading(item.id, false);
  }
}

async function handleDownloadAll() {
  if (!props.processInstanceId || !hasDownloadableAttachments.value) {
    return;
  }
  downloadingAll.value = true;
  try {
    const blob = await downloadWorkflowAttachmentPackage(props.processInstanceId);
    downloadBlob(blob, attachmentPackageFileName.value);
    message.success('下载成功');
  } catch {
    message.error('附件打包下载失败');
  } finally {
    downloadingAll.value = false;
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

function sanitizeFileName(fileName: string) {
  return fileName.replace(/[\\/:*?"<>|\r\n]+/g, ' ').trim() || '审批附件';
}

async function handleDelete(item: AttachmentItem) {
  if (item.pending) {
    attachments.value = attachments.value.filter(
      (record) => record.localKey !== item.localKey,
    );
    return;
  }
  if (!item.id) {
    return;
  }
  setDeleting(item.id, true);
  try {
    await deleteWorkflowAttachment(item.id);
    message.success('附件已删除');
    await reload();
  } finally {
    setDeleting(item.id, false);
  }
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value;
}

defineExpose({
  bindPendingAttachments,
  reload,
  reset,
});
</script>

<template>
  <section
    class="workflow-attachment-panel"
    :class="{ 'workflow-attachment-panel--collapsed': collapsed }"
  >
    <div class="workflow-attachment-panel__header">
      <button
        class="workflow-attachment-panel__title"
        type="button"
        @click="toggleCollapsed"
      >
        <PaperClipOutlined />
        <span>附件</span>
        <span
          v-if="hasAttachments"
          class="workflow-attachment-panel__count"
        >
          {{ attachments.length }}
        </span>
      </button>
      <div class="workflow-attachment-panel__tools">
        <Button
          v-if="hasDownloadableAttachments && !collapsed"
          size="small"
          type="link"
          :loading="downloadingAll"
          @click="handleDownloadAll"
        >
          <DownloadOutlined />
          全部下载
        </Button>
        <Upload
          v-if="canUpload && !collapsed"
          :before-upload="handleBeforeUpload"
          :disabled="isUploading"
          :multiple="true"
          :show-upload-list="false"
        >
          <Button :loading="isUploading" type="primary">
            <UploadOutlined />
            上传附件
          </Button>
        </Upload>
        <Tooltip :title="collapsed ? '展开附件' : '折叠附件'">
          <Button
            class="workflow-attachment-panel__collapse"
            type="text"
            @click="toggleCollapsed"
          >
            <RightOutlined v-if="collapsed" />
            <DownOutlined v-else />
          </Button>
        </Tooltip>
      </div>
    </div>

    <div
      v-show="!collapsed"
      class="workflow-attachment-panel__body"
    >
      <Spin :spinning="loading">
        <div v-if="hasAttachments" class="workflow-attachment-panel__list">
          <div
            v-for="item in attachments"
            :key="item.localKey"
            class="workflow-attachment-panel__row"
          >
            <div class="workflow-attachment-panel__file">
            <Tooltip :title="item.fileName">
              <button
                class="workflow-attachment-panel__name"
                type="button"
                @click="handlePreview(item)"
              >
                {{ item.fileName || '-' }}
              </button>
            </Tooltip>
          </div>
            <div class="workflow-attachment-panel__actions">
              <Button
                size="small"
                type="link"
                :disabled="!item.id"
                :loading="isDownloading(item.id)"
                @click="handleDownload(item)"
              >
                <DownloadOutlined />
                下载
              </Button>
              <Popconfirm
                v-if="canUpload"
                title="确认删除该附件？"
                @confirm="handleDelete(item)"
              >
                <Button
                  danger
                  size="small"
                  type="link"
                  :loading="isDeleting(item.id)"
                >
                  <DeleteOutlined />
                  删除
                </Button>
              </Popconfirm>
            </div>
          </div>
        </div>
        <Empty
          v-else
          class="workflow-attachment-panel__empty"
          description="暂无附件"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
      </Spin>
    </div>
    <DocumentImagePreviewModal ref="imagePreviewModalRef" />
    <DocumentOnlyOfficePreviewModal ref="documentPreviewModalRef" />
  </section>
</template>

<style scoped>
.workflow-attachment-panel {
  border-top: 1px solid #f0f0f0;
  padding-top: 10px;
}

.workflow-attachment-panel--collapsed {
  padding-top: 5px;
}

.workflow-attachment-panel__header {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.workflow-attachment-panel__title {
  align-items: center;
  background: transparent;
  border: 0;
  color: hsl(var(--foreground));
  cursor: pointer;
  display: flex;
  font-size: 15px;
  font-weight: 600;
  gap: 6px;
  min-width: 0;
  padding: 0;
}

.workflow-attachment-panel__count {
  align-items: center;
  background: hsl(var(--primary) / 10%);
  border-radius: 999px;
  color: hsl(var(--primary));
  display: inline-flex;
  font-size: 12px;
  font-weight: 500;
  height: 20px;
  justify-content: center;
  min-width: 20px;
  padding: 0 6px;
}

.workflow-attachment-panel__tools {
  align-items: center;
  display: flex;
  gap: 8px;
}

.workflow-attachment-panel__collapse {
  align-items: center;
  display: inline-flex;
  height: 32px;
  justify-content: center;
  width: 32px;
}

.workflow-attachment-panel--collapsed .workflow-attachment-panel__header {
  margin-bottom: 0;
  min-height: 24px;
}

.workflow-attachment-panel--collapsed .workflow-attachment-panel__title {
  font-size: 14px;
  line-height: 22px;
}

.workflow-attachment-panel--collapsed .workflow-attachment-panel__count {
  height: 18px;
  min-width: 18px;
  padding: 0 5px;
}

.workflow-attachment-panel--collapsed .workflow-attachment-panel__collapse {
  height: 24px;
  width: 24px;
}

.workflow-attachment-panel__body {
  min-height: 0;
}

.workflow-attachment-panel__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 108px;
  overflow: auto;
}

.workflow-attachment-panel__row {
  align-items: center;
  background: hsl(var(--muted) / 18%);
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 4px 8px;
}

.workflow-attachment-panel__file {
  min-width: 0;
}

.workflow-attachment-panel__name {
  background: transparent;
  border: 0;
  color: hsl(var(--foreground));
  cursor: pointer;
  display: block;
  font-size: 14px;
  font-weight: 500;
  line-height: 18px;
  overflow: hidden;
  padding: 0;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}

.workflow-attachment-panel__name:hover {
  color: hsl(var(--primary));
}

.workflow-attachment-panel__actions {
  align-items: center;
  display: flex;
  gap: 2px;
}

.workflow-attachment-panel__actions :deep(.ant-btn-sm) {
  height: 24px;
  line-height: 22px;
  padding: 0 4px;
}

.workflow-attachment-panel__empty {
  margin: 0;
  padding: 8px 0 4px;
}

@media (max-width: 640px) {
  .workflow-attachment-panel__row {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .workflow-attachment-panel__header {
    align-items: stretch;
  }

  .workflow-attachment-panel__actions {
    justify-content: flex-end;
  }
}
</style>
