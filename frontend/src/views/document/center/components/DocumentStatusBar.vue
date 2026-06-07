<script setup lang="ts">
import type {
  DocumentFileInfo,
  DocumentScope,
  DocumentShareSourceInfo,
  DocumentStatusInfo,
} from '#/api/document';

import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Tooltip } from 'ant-design-vue';

import { getDocumentStatus } from '#/api/document';

import {
  BUSINESS_MODULE_VIEW_STORE_TYPE,
  BUSINESS_RECORD_VIEW_STORE_TYPE,
} from '#/constants/document';
import {
  formatDateTime,
  formatSize,
} from './documentExplorerUtils';

interface Props {
  isGlobalSearch?: boolean;
  scope: DocumentScope;
  selectedRecords: DocumentFileInfo[];
  statusRefreshKey?: number;
}

const props = withDefaults(defineProps<Props>(), {
  isGlobalSearch: false,
});

const statusDetail = ref<DocumentStatusInfo>();
let requestSeq = 0;

const selectedCount = computed(() => props.selectedRecords.length);
const singleRecord = computed(() => props.selectedRecords.length === 1 ? props.selectedRecords[0] : undefined);
const selectedFileCount = computed(() => props.selectedRecords.filter((record) => record.izFolder !== '1').length);
const selectedFolderCount = computed(() => props.selectedRecords.filter((record) => record.izFolder === '1').length);
const selectedFileSize = computed(() =>
  props.selectedRecords
    .filter((record) => record.izFolder !== '1')
    .reduce((sum, record) => sum + (record.fileSize || 0), 0),
);
const downloadableCount = computed(
  () => props.selectedRecords.filter((record) => record.canDownload && record.izFolder !== '1').length,
);
const manageableCount = computed(
  () => props.selectedRecords.filter((record) => record.canManage).length,
);
const updatableCount = computed(
  () => props.selectedRecords.filter((record) => record.canUpdate && record.izFolder !== '1').length,
);
const singleNeedsStatusDetail = computed(() => {
  const record = singleRecord.value;
  return Boolean(record?.id && !isVirtualRecord(record));
});
const detailRecord = computed(() => {
  if (selectedCount.value !== 1) {
    return undefined;
  }
  if (singleNeedsStatusDetail.value) {
    return statusDetail.value?.file;
  }
  return singleRecord.value;
});

const directShareSummary = computed(() => summarizeDirectShares(statusDetail.value));
const shareSourceText = computed(() => {
  const detail = statusDetail.value;
  const record = detailRecord.value;
  if (!record) {
    return '';
  }
  if (directShareSummary.value) {
    return directShareSummary.value;
  }
  const inherited = detail?.inheritedShareSource;
  if (inherited) {
    const sourceText = formatShareSource(inherited, !record.ownerFlag);
    return `继承自「${inherited.inheritedFromFileName || inherited.fileName || '上级文件夹'}」的共享${sourceText ? `，${sourceText}` : ''}`;
  }
  const accessSource = detail?.accessShareSource;
  if (accessSource) {
    return formatShareSource(accessSource, !record.ownerFlag);
  }
  if (record.ownerFlag && record.sharedFlag) {
    return '已共享';
  }
  if (props.scope === 'shared' && !record.ownerFlag) {
    return '共享给我';
  }
  return '未共享';
});

const favoriteText = computed(() => {
  const detail = statusDetail.value;
  const record = detailRecord.value;
  if (!record || props.scope === 'business' || props.scope === 'trash') {
    return '不适用';
  }
  const favoriteSource = detail?.favoriteSource;
  if (favoriteSource?.sourceType === 'inherited') {
    return `继承自「${favoriteSource.inheritedFromFileName || favoriteSource.fileName || '上级文件夹'}」的收藏`;
  }
  if (favoriteSource?.sourceType === 'direct') {
    return '已收藏';
  }
  if (props.scope === 'sharedByMe' && record.izStar !== '1') {
    return '不适用';
  }
  return record.izStar === '1' ? '已收藏' : '未收藏';
});

const folderStatsText = computed(() => {
  const stats = statusDetail.value?.folderStats;
  if (!stats) {
    return '';
  }
  const sizeText = formatSize(stats.totalSize);
  return `${stats.folderCount || 0} 个文件夹，${stats.fileCount || 0} 个文件${sizeText ? `，${sizeText}` : ''}`;
});

const businessText = computed(() => {
  const detail = statusDetail.value;
  const record = detailRecord.value;
  if (!record) {
    return '';
  }
  if (record.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE) {
    return `业务模块：${record.fileName || detail?.businessModuleName || '-'}`;
  }
  if (record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE) {
    return `业务数据：${record.fileName || detail?.businessRecordName || '-'}`;
  }
  if (detail?.businessModuleName || detail?.businessRecordName) {
    return [detail.businessModuleName, detail.businessRecordName].filter(Boolean).join(' / ');
  }
  return '';
});

watch(
  () => [singleRecord.value?.id, props.statusRefreshKey] as const,
  async ([id]) => {
    const seq = ++requestSeq;
    statusDetail.value = undefined;
    const record = singleRecord.value;
    if (!id || !record || isVirtualRecord(record)) {
      return;
    }
    try {
      const detail = await getDocumentStatus(id);
      if (seq === requestSeq) {
        statusDetail.value = detail;
      }
    } catch {
      if (seq === requestSeq) {
        statusDetail.value = undefined;
      }
    }
  },
  { immediate: true },
);

function isVirtualRecord(record: DocumentFileInfo) {
  return (
    record.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE ||
    record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE ||
    record.id?.startsWith('so:') ||
    record.id?.startsWith('bm:') ||
    record.id?.startsWith('br:')
  );
}

function summarizeDirectShares(detail?: DocumentStatusInfo) {
  const shares = detail?.directShares || [];
  if (shares.length === 0) {
    return '';
  }
  const counts = shares.reduce<Record<string, number>>((map, share) => {
    const key = targetTypeText(share.targetType);
    map[key] = (map[key] || 0) + 1;
    return map;
  }, {});
  return `已共享给：${Object.entries(counts)
    .map(([type, count]) => `${type} ${count}`)
    .join('、')}`;
}

function formatShareSource(source: DocumentShareSourceInfo, received = true) {
  if (source.sourceType === 'space') {
    return `位于${targetTypeText(source.targetType)}共享空间${source.targetName ? `「${source.targetName}」` : ''}`;
  }
  const target = source.targetSummary || [targetTypeText(source.targetType), source.targetName].filter(Boolean).join(' ');
  const permission = source.permission ? `，权限：${permissionText(source.permission)}` : '';
  if (received) {
    return `${source.sharedBy || '他人'}通过${target || '共享'}给我${permission}`;
  }
  return `${source.sharedBy || '共享人'}共享给${target || '目标'}${permission}`;
}

function targetTypeText(type?: string) {
  const textMap: Record<string, string> = {
    depart: '部门',
    role: '角色',
    tenant: '租户',
    user: '人员',
  };
  return type ? textMap[type] || type : '目标';
}

function permissionText(permission?: string) {
  const textMap: Record<string, string> = {
    download: '下载',
    manage: '管理',
    read: '阅读',
    update: '编辑',
  };
  return permission ? textMap[permission] || permission : '';
}
</script>

<template>
  <div class="document-status-bar">
    <template v-if="selectedCount === 0">
      <span class="document-status-bar__item document-status-bar__item--muted">
        <IconifyIcon icon="lucide:info" />
        <span class="document-status-bar__value">未选择项目</span>
      </span>
    </template>
    <template v-else-if="selectedCount > 1">
      <span class="document-status-bar__item document-status-bar__item--strong">
        <span class="document-status-bar__label">选择</span>
        <span class="document-status-bar__value">已选 {{ selectedCount }} 项</span>
      </span>
      <span class="document-status-bar__item">
        <span class="document-status-bar__label">类型</span>
        <span class="document-status-bar__value">文件 {{ selectedFileCount }}，文件夹 {{ selectedFolderCount }}</span>
      </span>
      <span class="document-status-bar__item">
        <span class="document-status-bar__label">大小</span>
        <span class="document-status-bar__value">文件合计 {{ formatSize(selectedFileSize) || '0 B' }}</span>
      </span>
      <span class="document-status-bar__item document-status-bar__item--wide">
        <span class="document-status-bar__label">权限</span>
        <span class="document-status-bar__value">
          可下载 {{ downloadableCount }}，可编辑 {{ updatableCount }}，可管理 {{ manageableCount }}
        </span>
      </span>
    </template>
    <template v-else-if="detailRecord">
      <Tooltip
        v-if="detailRecord.izFolder === '1' && folderStatsText"
        :title="folderStatsText"
        overlay-class-name="document-status-bar-tooltip"
        placement="topLeft"
      >
        <span class="document-status-bar__item document-status-bar__item--wide">
          <span class="document-status-bar__label">内容</span>
          <span class="document-status-bar__value">{{ folderStatsText }}</span>
        </span>
      </Tooltip>
      <Tooltip
        :title="favoriteText"
        overlay-class-name="document-status-bar-tooltip"
        placement="topLeft"
      >
        <span class="document-status-bar__item">
          <span class="document-status-bar__label">收藏</span>
          <span class="document-status-bar__value">{{ favoriteText }}</span>
        </span>
      </Tooltip>
      <Tooltip
        :title="shareSourceText"
        overlay-class-name="document-status-bar-tooltip"
        placement="topLeft"
      >
        <span class="document-status-bar__item document-status-bar__item--share">
          <span class="document-status-bar__label">共享</span>
          <span class="document-status-bar__value">{{ shareSourceText }}</span>
        </span>
      </Tooltip>
      <Tooltip
        v-if="businessText"
        :title="businessText"
        overlay-class-name="document-status-bar-tooltip"
        placement="topLeft"
      >
        <span class="document-status-bar__item document-status-bar__item--wide">
          <span class="document-status-bar__label">业务</span>
          <span class="document-status-bar__value">{{ businessText }}</span>
        </span>
      </Tooltip>
      <span class="document-status-bar__item">
        <span class="document-status-bar__label">修改</span>
        <span class="document-status-bar__value">
          {{ formatDateTime(detailRecord.updateTime || detailRecord.createTime) }}
        </span>
      </span>
      <span v-if="detailRecord.izFolder !== '1'" class="document-status-bar__item">
        <span class="document-status-bar__label">活跃</span>
        <span class="document-status-bar__value">
          阅读 {{ detailRecord.readCount || 0 }}，下载 {{ detailRecord.downCount || 0 }}
        </span>
      </span>
      <span v-if="scope === 'trash'" class="document-status-bar__item">
        <span class="document-status-bar__label">删除</span>
        <span class="document-status-bar__value">{{ formatDateTime(detailRecord.deleteTime) }}</span>
      </span>
      <span v-if="scope === 'trash' && statusDetail?.deleteBy" class="document-status-bar__item">
        <span class="document-status-bar__label">删除人</span>
        <span class="document-status-bar__value">{{ statusDetail.deleteBy }}</span>
      </span>
      <Tooltip
        v-if="scope === 'trash' && statusDetail?.originalPath"
        :title="statusDetail.originalPath"
        overlay-class-name="document-status-bar-tooltip"
        placement="topLeft"
      >
        <span class="document-status-bar__item document-status-bar__item--share">
          <span class="document-status-bar__label">原路径</span>
          <span class="document-status-bar__value">{{ statusDetail.originalPath }}</span>
        </span>
      </Tooltip>
    </template>
  </div>
</template>

<style scoped>
.document-status-bar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 38px;
  gap: 0;
  overflow-x: auto;
  overflow-y: hidden;
  border-top: 1px solid hsl(var(--border));
  background: hsl(var(--background));
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 18px;
  padding: 4px 10px;
  white-space: nowrap;
}

.document-status-bar__item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 280px;
  min-height: 26px;
  gap: 6px;
  overflow: hidden;
  border-right: 1px solid hsl(var(--border) / 80%);
  padding: 2px 12px;
  text-overflow: ellipsis;
}

.document-status-bar__item:first-child {
  padding-left: 4px;
}

.document-status-bar__item:last-child {
  border-right: 0;
}

.document-status-bar__item--strong {
  max-width: 240px;
  color: hsl(var(--foreground));
}

.document-status-bar__item--wide {
  max-width: 420px;
}

.document-status-bar__item--share {
  flex: 0 1 auto;
  max-width: min(620px, 44vw);
}

.document-status-bar__item--muted {
  color: hsl(var(--muted-foreground));
}

.document-status-bar__label {
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
}

.document-status-bar__label::after {
  color: hsl(var(--border));
  content: ':';
}

.document-status-bar__value {
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--foreground));
  font-weight: 500;
  text-overflow: ellipsis;
}

.document-status-bar__item svg {
  flex: 0 0 auto;
}

:global(.document-status-bar-tooltip) {
  max-width: min(640px, calc(100vw - 48px));
}

:global(.document-status-bar-tooltip .ant-tooltip-inner) {
  max-height: 240px;
  overflow: auto;
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 20px;
  text-align: left;
  white-space: normal;
  word-break: break-word;
}
</style>
