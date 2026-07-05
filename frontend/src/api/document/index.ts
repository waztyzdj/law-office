import { requestClient } from '#/framework/api/request';
import type { BasePageReq } from '#/framework/api/base.api';

const DOCUMENT_FILES_API_PREFIX = '/document/files';

export type DocumentScope =
  | 'all'
  | 'business'
  | 'my'
  | 'shared'
  | 'sharedByMe'
  | 'starred'
  | 'trash';
export type DocumentShareTargetType = 'depart' | 'role' | 'tenant' | 'user';
export type DocumentPermission = 'download' | 'manage' | 'read' | 'update';
export type OnlyOfficePreviewMode = 'edit' | 'view';

export interface DocumentFileInfo {
  canDownload?: boolean;
  canManage?: boolean;
  canUpdate?: boolean;
  createBy?: string;
  createTime?: string;
  deleteFlag?: number;
  deleteTime?: string;
  downCount?: number;
  enableDown?: string;
  enableUpdat?: string;
  fileName?: string;
  fileSize?: number;
  fileType?: string;
  hasChild?: boolean;
  id?: string;
  izFolder?: string;
  izRootFolder?: string;
  izStar?: string;
  owner?: string;
  ownerFlag?: boolean;
  parentId?: string;
  readCount?: number;
  sharedFlag?: boolean;
  starTime?: string;
  storeType?: string;
  updateTime?: string;
}

export interface DocumentPageReq extends BasePageReq {
  fileType?: string;
  folderOnly?: boolean;
  keyword?: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}

export interface DocumentTreePrefetchReq {
  parentIds: string[];
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}

export interface DocumentTreeBatchItemReq {
  key: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}

export interface DocumentTreeBatchReq {
  items: DocumentTreeBatchItemReq[];
}

export interface DocumentShareInfo {
  createTime?: string;
  expireTime?: string;
  fileId?: string;
  id?: string;
  permission?: DocumentPermission;
  targetId?: string;
  targetName?: string;
  targetType?: DocumentShareTargetType;
}

export interface DocumentShareSourceInfo {
  createTime?: string;
  expireTime?: string;
  fileId?: string;
  fileName?: string;
  inheritedFromFileId?: string;
  inheritedFromFileName?: string;
  permission?: DocumentPermission;
  sharedBy?: string;
  sourceType?: 'direct' | 'inherited' | 'space' | string;
  targetId?: string;
  targetName?: string;
  targetSummary?: string;
  targetType?: DocumentShareTargetType;
}

export interface DocumentFolderStatsInfo {
  fileCount?: number;
  folderCount?: number;
  totalSize?: number;
}

export interface DocumentStatusInfo {
  accessShareSource?: DocumentShareSourceInfo;
  businessBizId?: string;
  businessBizType?: string;
  businessModuleName?: string;
  businessRecordName?: string;
  deleteBy?: string;
  directShares?: DocumentShareInfo[];
  favoriteSource?: DocumentShareSourceInfo;
  file?: DocumentFileInfo;
  folderStats?: DocumentFolderStatsInfo;
  inheritedShareSource?: DocumentShareSourceInfo;
  originalPath?: string;
}

export interface DocumentShareTarget {
  permission: DocumentPermission;
  targetId: string;
  targetType: DocumentShareTargetType;
}

export interface OnlyOfficePreviewConfig {
  config: Record<string, unknown>;
  documentServerApiUrl: string;
}

export interface OnlyOfficeHistoryVersion {
  editTime?: string;
  editor?: string;
  editorName?: string;
  fileId?: string;
  fileSize?: number;
  id?: string;
  remark?: string;
  version?: string;
  versionNo?: number;
  versionType?: 'final' | 'restore' | 'upload' | string;
}

export const pageDocuments = (params: DocumentPageReq) =>
  requestClient.post<{
    pageNum: number;
    pageSize: number;
    records: DocumentFileInfo[];
    total: number;
  }>(`${DOCUMENT_FILES_API_PREFIX}/page`, params);

export const prefetchDocumentTree = (params: DocumentTreePrefetchReq) =>
  requestClient.post<Record<string, DocumentFileInfo[]>>(
    `${DOCUMENT_FILES_API_PREFIX}/tree/prefetch`,
    params,
  );

export const batchLoadDocumentTree = (params: DocumentTreeBatchReq) =>
  requestClient.post<Record<string, DocumentFileInfo[]>>(
    `${DOCUMENT_FILES_API_PREFIX}/tree/batch`,
    params,
  );

export const uploadDocument = (
  file: File,
  parentId?: string,
  context?: {
    scope?: DocumentScope;
    shareTargetId?: string;
    shareTargetType?: DocumentShareTargetType;
  },
) => {
  const formData = new FormData();
  formData.append('file', file);
  if (parentId) {
    formData.append('parentId', parentId);
  }
  if (context?.scope) {
    formData.append('scope', context.scope);
  }
  if (context?.shareTargetType) {
    formData.append('shareTargetType', context.shareTargetType);
  }
  if (context?.shareTargetId) {
    formData.append('shareTargetId', context.shareTargetId);
  }
  return requestClient.post<DocumentFileInfo>(`${DOCUMENT_FILES_API_PREFIX}/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const createDocumentFolder = (data: {
  fileName: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}) => requestClient.post<DocumentFileInfo>(`${DOCUMENT_FILES_API_PREFIX}/folder`, data);

export const renameDocument = (data: { fileName: string; id: string }) =>
  requestClient.post<DocumentFileInfo>(`${DOCUMENT_FILES_API_PREFIX}/rename`, data);

export const moveDocument = (data: {
  id: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}) =>
  requestClient.post<DocumentFileInfo>(`${DOCUMENT_FILES_API_PREFIX}/move`, data);

export const batchMoveDocuments = (data: {
  ids: string[];
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}) =>
  requestClient.post<DocumentFileInfo[]>(`${DOCUMENT_FILES_API_PREFIX}/batch-move`, data);

export const copyDocuments = (data: {
  ids: string[];
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
}) =>
  requestClient.post<DocumentFileInfo[]>(`${DOCUMENT_FILES_API_PREFIX}/copy`, data);

export const deleteDocument = (fileId: string) =>
  requestClient.post<void>(`${DOCUMENT_FILES_API_PREFIX}/delete`, { id: fileId });

export const batchDeleteDocuments = (ids: string[]) =>
  requestClient.post<void>(`${DOCUMENT_FILES_API_PREFIX}/batch-delete`, { ids });

export const restoreDocument = (fileId: string) =>
  requestClient.post<DocumentFileInfo>(`${DOCUMENT_FILES_API_PREFIX}/restore`, { id: fileId });

export const batchRestoreDocuments = (ids: string[]) =>
  requestClient.post<DocumentFileInfo[]>(`${DOCUMENT_FILES_API_PREFIX}/batch-restore`, { ids });

export const purgeDocument = (fileId: string) =>
  requestClient.post<void>(`${DOCUMENT_FILES_API_PREFIX}/purge`, { id: fileId });

export const clearDocumentTrash = () =>
  requestClient.post<void>(`${DOCUMENT_FILES_API_PREFIX}/trash/clear`);

export const toggleDocumentStar = (fileId: string) =>
  requestClient.post<DocumentFileInfo>(`${DOCUMENT_FILES_API_PREFIX}/star`, { id: fileId });

export const shareDocument = (data: {
  enableDown?: string;
  enableUpdat?: string;
  expireTime?: string;
  fileId: string;
  targets: DocumentShareTarget[];
}) => requestClient.post<DocumentShareInfo[]>(`${DOCUMENT_FILES_API_PREFIX}/share`, data);

export const listDocumentShares = (fileId: string) =>
  requestClient.post<DocumentShareInfo[]>(`${DOCUMENT_FILES_API_PREFIX}/shares`, { id: fileId });

export const getDocumentStatus = (fileId: string) =>
  requestClient.post<DocumentStatusInfo>(`${DOCUMENT_FILES_API_PREFIX}/status`, { id: fileId });

export const revokeDocumentShare = (aclId: string) =>
  requestClient.post<void>(`${DOCUMENT_FILES_API_PREFIX}/share/revoke`, { id: aclId });

export const downloadDocument = (fileId: string) =>
  requestClient.download<Blob>(`${DOCUMENT_FILES_API_PREFIX}/download`, {
    data: { id: fileId },
    method: 'POST',
  });

export const downloadDocumentThumbnail = (fileId: string) =>
  requestClient.download<Blob>(`${DOCUMENT_FILES_API_PREFIX}/thumbnail`, {
    data: { id: fileId },
    method: 'POST',
  });

export const downloadDocumentImagePreview = (fileId: string) =>
  requestClient.download<Blob>(`${DOCUMENT_FILES_API_PREFIX}/preview/image`, {
    data: { id: fileId },
    method: 'POST',
  });

export const getOnlyOfficePreviewConfig = (
  fileId: string,
  mode: OnlyOfficePreviewMode = 'view',
) =>
  requestClient.post<OnlyOfficePreviewConfig>(`${DOCUMENT_FILES_API_PREFIX}/onlyoffice/config`, {
    fileId,
    mode,
  });

export const listOnlyOfficeHistory = (fileId: string) =>
  requestClient.post<OnlyOfficeHistoryVersion[]>(
    `${DOCUMENT_FILES_API_PREFIX}/onlyoffice/history/list`,
    { id: fileId },
  );

export const getOnlyOfficeHistoryPreviewConfig = (versionId: string) =>
  requestClient.post<OnlyOfficePreviewConfig>(
    `${DOCUMENT_FILES_API_PREFIX}/onlyoffice/history/config`,
    { id: versionId },
  );

export const restoreOnlyOfficeHistoryVersion = (versionId: string) =>
  requestClient.post<OnlyOfficeHistoryVersion>(
    `${DOCUMENT_FILES_API_PREFIX}/onlyoffice/history/restore`,
    { id: versionId },
  );
