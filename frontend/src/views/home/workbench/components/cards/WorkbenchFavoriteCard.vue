<script setup lang="ts">
import type { DocumentFileInfo } from '#/api/document';
import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';

import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Tooltip, message } from 'ant-design-vue';

import { downloadDocument } from '#/api/document';
import DocumentImagePreviewModal from '#/views/document/center/components/DocumentImagePreviewModal.vue';
import DocumentOnlyOfficePreviewModal from '#/views/document/center/components/DocumentOnlyOfficePreviewModal.vue';
import {
  canPreviewItem as canPreviewDocumentItem,
  fileIcon,
  isImageFile,
} from '#/views/document/center/components/documentExplorerUtils';
import { useWorkbenchCardPaging } from '../../hooks/useWorkbenchCardPaging';
import {
  formatWorkbenchCardTime,
  getWorkbenchListPageSize,
} from '../../utils/workbenchCardFormatters';
import WorkbenchListCard from './WorkbenchListCard.vue';

const props = defineProps<{
  card: WorkbenchLayoutCard;
  items: WorkbenchCardItem[];
}>();

const imagePreviewModalRef = ref<InstanceType<typeof DocumentImagePreviewModal>>();
const previewModalRef = ref<InstanceType<typeof DocumentOnlyOfficePreviewModal>>();
const downloadingId = ref('');

const listPageSize = computed(() => getWorkbenchListPageSize(props.card));
const currentListItems = computed(() => props.items);
const {
  currentPage,
  hasPagination,
  pagedItems,
} = useWorkbenchCardPaging(currentListItems, listPageSize);

function toFavoriteDocumentRecord(item: WorkbenchCardItem): DocumentFileInfo {
  const fileSize = Number(item.fileSize);
  return {
    canDownload: item.canDownload !== false,
    canManage: item.canManage === true,
    canUpdate: item.canUpdate === true,
    createTime: typeof item.createTime === 'string' ? item.createTime : undefined,
    fileName: String(item.fileName || item.title || ''),
    fileSize: Number.isFinite(fileSize) ? fileSize : undefined,
    fileType: typeof item.fileType === 'string' ? item.fileType : undefined,
    id: String(item.bizId || item.id || ''),
    izFolder: typeof item.izFolder === 'string' ? item.izFolder : '0',
    izStar: typeof item.izStar === 'string' ? item.izStar : '1',
    starTime: typeof item.starTime === 'string' ? item.starTime : undefined,
    storeType: typeof item.storeType === 'string' ? item.storeType : undefined,
    updateTime: typeof item.updateTime === 'string' ? item.updateTime : undefined,
  };
}

function handlePreview(item: WorkbenchCardItem) {
  const record = toFavoriteDocumentRecord(item);
  if (!record.id) {
    message.warning('文件信息不完整，暂无法预览');
    return;
  }
  if (!canPreviewDocumentItem(record, { scope: 'starred' })) {
    message.warning('该文件暂不支持在线预览，可下载后查看');
    return;
  }
  if (isImageFile(record)) {
    imagePreviewModalRef.value?.open(record);
    return;
  }
  previewModalRef.value?.open(record);
}

async function handleDownload(item: WorkbenchCardItem) {
  const record = toFavoriteDocumentRecord(item);
  if (!record.id) {
    message.warning('文件信息不完整，暂无法下载');
    return;
  }
  if (record.canDownload === false) {
    message.warning('当前文件不允许下载');
    return;
  }
  downloadingId.value = record.id;
  try {
    const blob = await downloadDocument(record.id);
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = record.fileName || 'download';
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '文件下载失败');
  } finally {
    downloadingId.value = '';
  }
}

function isDownloading(item: WorkbenchCardItem) {
  return downloadingId.value === toFavoriteDocumentRecord(item).id;
}

function getFileIcon(item: WorkbenchCardItem) {
  if (typeof item.icon === 'string' && item.icon) {
    return item.icon;
  }
  return fileIcon(toFavoriteDocumentRecord(item));
}
</script>

<template>
  <WorkbenchListCard
    v-model:current-page="currentPage"
    empty-description="我的收藏暂无数据"
    :items="currentListItems"
    :page-items="pagedItems"
    :page-size="listPageSize"
    row-title="双击预览"
    :show-pagination="hasPagination"
    title-fallback="未命名文件"
    @row-double-click="handlePreview"
  >
    <template #item="{ item }">
      <Tooltip title="下载">
        <Button
          class="workbench-favorite-card__download"
          :loading="isDownloading(item)"
          size="small"
          type="text"
          @dblclick.stop
          @click.stop="handleDownload(item)"
        >
          <IconifyIcon
            v-if="!isDownloading(item)"
            icon="lucide:download"
          />
        </Button>
      </Tooltip>
      <span class="workbench-favorite-card__item-main">
        <span class="workbench-favorite-card__item-icon">
          <IconifyIcon :icon="getFileIcon(item)" />
        </span>
        <span class="workbench-favorite-card__item-title">
          {{ item.title || '未命名文件' }}
        </span>
      </span>
      <span class="workbench-favorite-card__item-time">
        {{ formatWorkbenchCardTime(item.occurTime) || '-' }}
      </span>
    </template>
  </WorkbenchListCard>
  <DocumentImagePreviewModal ref="imagePreviewModalRef" />
  <DocumentOnlyOfficePreviewModal ref="previewModalRef" />
</template>

<style scoped>
.workbench-favorite-card__item-main {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  flex: 1 1 auto;
  gap: 8px;
}

.workbench-favorite-card__item-icon {
  display: inline-flex;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
  font-size: 14px;
}

.workbench-favorite-card__item-title {
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--foreground));
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-favorite-card__item-time {
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}

.workbench-favorite-card__download {
  display: inline-flex;
  width: 20px;
  height: 20px;
  min-width: 20px;
  align-items: center;
  justify-content: center;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 1;
  padding: 0;
}

.workbench-favorite-card__download:hover {
  color: hsl(var(--primary));
}
</style>
