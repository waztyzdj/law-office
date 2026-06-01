import { requestClient } from '#/framework/api/request';
import type { BasePageReq } from '#/framework/api/base.api';

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
  id?: string;
  izFolder?: string;
  izRootFolder?: string;
  izStar?: string;
  owner?: string;
  ownerFlag?: boolean;
  parentId?: string;
  readCount?: number;
  sharedFlag?: boolean;
  storeType?: string;
  updateTime?: string;
}

export interface DocumentPageReq extends BasePageReq {
  fileType?: string;
  keyword?: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetId?: string;
  shareTargetType?: DocumentShareTargetType;
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

export interface DocumentShareTarget {
  permission: DocumentPermission;
  targetId: string;
  targetType: DocumentShareTargetType;
}

export const pageDocuments = (params: DocumentPageReq) =>
  requestClient.post<{
    pageNum: number;
    pageSize: number;
    records: DocumentFileInfo[];
    total: number;
  }>('/files/document/page', params);

export const uploadDocument = (
  file: File,
  parentId?: string,
  context?: {
    scope?: DocumentScope;
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
  return requestClient.post<DocumentFileInfo>('/files/document/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const createDocumentFolder = (data: {
  fileName: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetType?: DocumentShareTargetType;
}) => requestClient.post<DocumentFileInfo>('/files/document/folder', data);

export const renameDocument = (data: { fileName: string; id: string }) =>
  requestClient.post<DocumentFileInfo>('/files/document/rename', data);

export const moveDocument = (data: {
  id: string;
  parentId?: string;
  scope?: DocumentScope;
  shareTargetType?: DocumentShareTargetType;
}) =>
  requestClient.post<DocumentFileInfo>('/files/document/move', data);

export const copyDocuments = (data: {
  ids: string[];
  parentId?: string;
  scope?: DocumentScope;
  shareTargetType?: DocumentShareTargetType;
}) =>
  requestClient.post<DocumentFileInfo[]>('/files/document/copy', data);

export const deleteDocument = (fileId: string) =>
  requestClient.post<void>(`/files/document/delete/${fileId}`);

export const restoreDocument = (fileId: string) =>
  requestClient.post<DocumentFileInfo>(`/files/document/restore/${fileId}`);

export const purgeDocument = (fileId: string) =>
  requestClient.post<void>(`/files/document/purge/${fileId}`);

export const clearDocumentTrash = () =>
  requestClient.post<void>('/files/document/trash/clear');

export const toggleDocumentStar = (fileId: string) =>
  requestClient.post<DocumentFileInfo>(`/files/document/star/${fileId}`);

export const shareDocument = (data: {
  enableDown?: string;
  enableUpdat?: string;
  expireTime?: string;
  fileId: string;
  targets: DocumentShareTarget[];
}) => requestClient.post<DocumentShareInfo[]>('/files/document/share', data);

export const listDocumentShares = (fileId: string) =>
  requestClient.get<DocumentShareInfo[]>(`/files/document/shares/${fileId}`);

export const revokeDocumentShare = (aclId: string) =>
  requestClient.post<void>(`/files/document/share/revoke/${aclId}`);

export const downloadDocument = (fileId: string) =>
  requestClient.download<Blob>(`/files/document/download/${fileId}`);
