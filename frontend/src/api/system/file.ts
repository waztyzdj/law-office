import { requestClient } from '#/framework/api/request';

export interface FileUploadResult {
  bizId?: string;
  bizType?: string;
  fileId?: string;
  fileName?: string;
  fileSize?: number;
  fileType?: string;
  fileUrl?: string;
  objectName?: string;
  relationId?: string;
}

export interface FileRelationResult {
  bizId?: string;
  bizType?: string;
  fileId?: string;
  id?: string;
  relationType?: number;
  sortOrder?: number;
}

export const uploadFile = (
  file: File,
  params?: {
    bizId?: string;
    bizType?: string;
  },
) => {
  const formData = new FormData();
  formData.append('file', file);
  if (params?.bizType) {
    formData.append('bizType', params.bizType);
  }
  if (params?.bizId) {
    formData.append('bizId', params.bizId);
  }
  return requestClient.post<FileUploadResult>('/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const bindFile = (data: {
  bizId: string;
  bizType: string;
  fileId: string;
  relationType?: number;
  sortOrder?: number;
}) => requestClient.post<FileRelationResult>('/files/bind', data);

export const unbindFile = (relationId: string) =>
  requestClient.post<void>(`/files/unbind/${relationId}`);

export const listFilesByBiz = (bizType: string, bizId: string) =>
  requestClient.get<FileUploadResult[]>(`/files/biz/${bizType}/${bizId}`);

export const getFileById = (fileId: string) =>
  requestClient.get<FileUploadResult>(`/files/${fileId}`);

export const downloadFile = (fileId: string) =>
  requestClient.download<Blob>(`/files/download/${fileId}`);
