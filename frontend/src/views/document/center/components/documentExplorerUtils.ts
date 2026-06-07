import type { DocumentFileInfo, DocumentScope } from '#/api/document';

import {
  BUSINESS_MODULE_VIEW_STORE_TYPE,
  BUSINESS_RECORD_VIEW_STORE_TYPE,
  IMAGE_PREVIEW_EXTENSIONS,
  ONLYOFFICE_EDIT_EXTENSIONS,
  ONLYOFFICE_PREVIEW_EXTENSIONS,
  type DocumentSortField,
  type DocumentSortState,
} from '#/constants/document';

export interface DocumentExplorerActionContext {
  globalSearch?: boolean;
  personalizeShared?: boolean;
  scope: DocumentScope;
}

export function isSharedReadonlyScope(scope: DocumentScope) {
  return scope === 'shared';
}

export function isReadonlyCollectionScope(scope: DocumentScope) {
  return scope === 'sharedByMe' || scope === 'starred';
}

export function isReadonlyBrowseScope(scope: DocumentScope) {
  return scope === 'business' || isSharedReadonlyScope(scope) || isReadonlyCollectionScope(scope);
}

function isReadonlyActionContext(context: DocumentExplorerActionContext) {
  return (
    Boolean(context.globalSearch) ||
    context.scope === 'business' ||
    (context.scope === 'shared' && Boolean(context.personalizeShared)) ||
    isReadonlyCollectionScope(context.scope)
  );
}

export function isActualStarredItem(record?: DocumentFileInfo) {
  return record?.izStar === '1';
}

export function isActualSharedItem(record?: DocumentFileInfo) {
  return Boolean(record?.id && record.ownerFlag && record.sharedFlag);
}

export const documentListColumns: Array<{
  className: string;
  field: DocumentSortField;
  label: string;
}> = [
  { className: 'document-list__cell--name', field: 'fileName', label: '名称' },
  { className: 'document-list__cell--type', field: 'fileType', label: '类型' },
  { className: 'document-list__cell--size', field: 'fileSize', label: '大小' },
  { className: 'document-list__cell--time', field: 'modifiedTime', label: '修改时间' },
];

export function compareDocuments(
  left: DocumentFileInfo,
  right: DocumentFileInfo,
  sort: DocumentSortState,
) {
  const leftValue = sortValue(left, sort.field);
  const rightValue = sortValue(right, sort.field);
  const direction = sort.order === 'asc' ? 1 : -1;
  let result = 0;
  if (typeof leftValue === 'number' && typeof rightValue === 'number') {
    result = leftValue - rightValue;
  } else {
    result = String(leftValue).localeCompare(String(rightValue), 'zh-CN', {
      numeric: true,
      sensitivity: 'base',
    });
  }
  if (result === 0 && sort.field !== 'fileName') {
    result = String(left.fileName || '').localeCompare(String(right.fileName || ''), 'zh-CN', {
      numeric: true,
      sensitivity: 'base',
    });
  }
  return result * direction;
}

export function formatSize(size?: number) {
  if (!size || size <= 0) {
    return '';
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${Math.round(size)} B`;
}

export function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

export function fileIcon(record: DocumentFileInfo) {
  if (record.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE) {
    return 'lucide:briefcase-business';
  }
  if (record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE) {
    return 'lucide:database';
  }
  if (record.izFolder === '1') {
    return 'lucide:folder';
  }
  const extension = getFileExtension(record);
  if (['doc', 'docx'].includes(extension)) {
    return 'vscode-icons:file-type-word';
  }
  if (['xls', 'xlsx'].includes(extension)) {
    return 'vscode-icons:file-type-excel';
  }
  if (['ppt', 'pptx'].includes(extension)) {
    return 'vscode-icons:file-type-powerpoint';
  }
  if (extension === 'pdf') {
    return 'vscode-icons:file-type-pdf2';
  }
  const type = String(record.fileType || '').toLowerCase();
  if (type === 'image') {
    return 'lucide:file-image';
  }
  if (type === 'pdf') {
    return 'vscode-icons:file-type-pdf2';
  }
  if (type === 'excel') {
    return 'vscode-icons:file-type-excel';
  }
  if (type === 'ppt') {
    return 'vscode-icons:file-type-powerpoint';
  }
  if (type === 'doc') {
    return 'vscode-icons:file-type-word';
  }
  if (type === 'archive') {
    return 'lucide:file-archive';
  }
  if (type === 'video') {
    return 'lucide:file-video';
  }
  return 'lucide:file';
}

export function fileTypeText(record: DocumentFileInfo) {
  if (record.izFolder === '1') {
    return '文件夹';
  }
  const typeText: Record<string, string> = {
    archive: '压缩包',
    doc: 'Word',
    excel: 'Excel',
    image: '图片',
    pdf: 'PDF',
    ppt: 'PPT',
    video: '视频',
  };
  return typeText[String(record.fileType)] || record.fileType || '文件';
}

export function getFileExtension(record: DocumentFileInfo) {
  const fileName = record.fileName || '';
  const dotIndex = fileName.lastIndexOf('.');
  return dotIndex >= 0 ? fileName.slice(dotIndex + 1).toLowerCase() : '';
}

export function isImageFile(record: DocumentFileInfo) {
  if (record.izFolder === '1') {
    return false;
  }
  const extension = getFileExtension(record);
  if (extension === 'svg') {
    return false;
  }
  const type = String(record.fileType || '').toLowerCase();
  return (
    type === 'image' ||
    type.startsWith('image/') ||
    IMAGE_PREVIEW_EXTENSIONS.has(extension)
  );
}

export function canMove(record: DocumentFileInfo, context: DocumentExplorerActionContext) {
  if (isReadonlyActionContext(context)) {
    return false;
  }
  return (
    context.scope !== 'trash' &&
    Boolean(record.id) &&
    (Boolean(record.canManage) || context.personalizeShared)
  );
}

export function canEditItem(record: DocumentFileInfo, context: DocumentExplorerActionContext) {
  if (isReadonlyActionContext(context)) {
    return false;
  }
  return context.scope !== 'trash' && Boolean(record.canManage);
}

export function canCreateFolderInItem(record: DocumentFileInfo, context: DocumentExplorerActionContext) {
  if (isReadonlyActionContext(context)) {
    return false;
  }
  if (record.izFolder !== '1' || !record.id) {
    return false;
  }
  return canEditItem(record, context);
}

export function canPreviewItem(record: DocumentFileInfo, _context: DocumentExplorerActionContext) {
  const extension = getFileExtension(record);
  return (
    record.izFolder !== '1' &&
    Boolean(record.id) &&
    (ONLYOFFICE_PREVIEW_EXTENSIONS.has(extension) || isImageFile(record))
  );
}

export function canEditContentItem(record: DocumentFileInfo, context: DocumentExplorerActionContext) {
  if (context.globalSearch || context.scope === 'business') {
    return false;
  }
  const extension = getFileExtension(record);
  return (
    context.scope !== 'trash' &&
    record.izFolder !== '1' &&
    Boolean(record.id) &&
    Boolean(record.canUpdate) &&
    ONLYOFFICE_EDIT_EXTENSIONS.has(extension)
  );
}

export function canViewHistoryItem(record: DocumentFileInfo, context: DocumentExplorerActionContext) {
  if (isReadonlyActionContext(context)) {
    return false;
  }
  const extension = getFileExtension(record);
  return (
    context.scope !== 'trash' &&
    record.izFolder !== '1' &&
    Boolean(record.id) &&
    ONLYOFFICE_EDIT_EXTENSIONS.has(extension)
  );
}

export function canDropOnFolder(target: DocumentFileInfo, context: DocumentExplorerActionContext) {
  if (isReadonlyActionContext(context)) {
    return false;
  }
  if (context.scope === 'trash' || target.izFolder !== '1' || !target.id) {
    return false;
  }
  if (context.personalizeShared) {
    return Boolean(target.ownerFlag) && target.storeType === 'shared_view';
  }
  return Boolean(target.canManage);
}

export function isVirtualBusinessItem(record: DocumentFileInfo) {
  return (
    record.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE ||
    record.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE
  );
}

function sortValue(record: DocumentFileInfo, field: DocumentSortField) {
  if (field === 'fileSize') {
    return record.fileSize || 0;
  }
  if (field === 'fileType') {
    return fileTypeText(record);
  }
  if (field === 'modifiedTime') {
    const time = record.updateTime || record.createTime || '';
    return time ? new Date(time).getTime() || 0 : 0;
  }
  return record.fileName || '';
}
