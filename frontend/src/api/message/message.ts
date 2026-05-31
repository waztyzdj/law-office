import type { BasePageReq } from '#/framework/api/base.api';

import { requestClient } from '#/framework/api/request';

export interface MessageActionInfo {
  actionName?: string;
  actionType?: number;
  bizId?: string;
  bizType?: string;
  externalUrl?: string;
  id?: string;
  openType?: number;
  routePath?: string;
  routeQuery?: string;
  sortOrder?: number;
}

export interface MessageAttachmentInfo {
  fileId?: string;
  fileName?: string;
  fileSize?: number;
  fileType?: string;
  id?: string;
  sortOrder?: number;
}

export interface SendMessageReq {
  actions?: MessageActionInfo[];
  attachments?: MessageAttachmentInfo[];
  content?: string;
  contentType?: number;
  messageType?: number;
  priority?: number;
  receiverIds: string[];
  sendScene?: number;
  sendScope?: number;
  title: string;
}

export interface MessageInboxInfo {
  archiveFlag?: number;
  id?: string;
  messageId?: string;
  messageType?: number;
  priority?: number;
  readStatus?: number;
  readTime?: string;
  senderId?: string;
  senderAvatar?: string;
  senderName?: string;
  sendStatus?: number;
  sendTime?: string;
  starFlag?: number;
  title?: string;
}

export interface MessageSentInfo {
  id?: string;
  messageType?: number;
  priority?: number;
  readCount?: number;
  receiverCount?: number;
  sendStatus?: number;
  sendTime?: string;
  title?: string;
}

export interface MessageDetailInfo extends MessageSentInfo {
  actions?: MessageActionInfo[];
  archiveFlag?: number;
  attachments?: MessageAttachmentInfo[];
  content?: string;
  contentType?: number;
  readStatus?: number;
  receiverMessageId?: string;
  receiverNames?: string[];
  senderId?: string;
  senderAvatar?: string;
  senderName?: string;
  starFlag?: number;
}

export interface MessageSendResult {
  messageId?: string;
  receiverCount?: number;
  sendBatchNo?: string;
  sendRecordId?: string;
}

export const pageInboxMessages = (params: BasePageReq) =>
  requestClient.post<{
    pageNum: number;
    pageSize: number;
    records: MessageInboxInfo[];
    total: number;
  }>('/message/inbox/page', params);

export const pageMessageNotifications = (params: BasePageReq) =>
  requestClient.post<{
    pageNum: number;
    pageSize: number;
    records: MessageInboxInfo[];
    total: number;
  }>('/message/notifications/page', params);

export const pageSentMessages = (params: BasePageReq) =>
  requestClient.post<{
    pageNum: number;
    pageSize: number;
    records: MessageSentInfo[];
    total: number;
  }>('/message/sent/page', params);

export const sendMessage = (data: SendMessageReq) =>
  requestClient.post<MessageSendResult>('/message/send', data);

export const getInboxMessageDetail = (id: string) =>
  requestClient.post<MessageDetailInfo>('/message/inbox/detail', { id });

export const getSentMessageDetail = (id: string) =>
  requestClient.post<MessageDetailInfo>('/message/sent/detail', { id });

export const downloadMessageAttachment = (fileId: string) =>
  requestClient.download<Blob>(`/message/attachment/download/${fileId}`);

export const markMessageRead = (id: string) =>
  requestClient.post<void>('/message/read', { id });

export const markMessageBatchRead = (ids: string[]) =>
  requestClient.post<void>('/message/read/batch', { ids });

export const markAllTenantMessagesRead = () =>
  requestClient.post<void>('/message/read/all');

export const markMessageNotificationRead = (id: string) =>
  requestClient.post<void>('/message/notifications/read', { id });

export const clearMessageNotifications = () =>
  requestClient.post<void>('/message/notifications/clear');

export const toggleMessageStar = (id: string) =>
  requestClient.post<void>('/message/star', { id });

export const deleteInboxMessage = (id: string) =>
  requestClient.post<void>('/message/inbox/delete', { id });

export const batchDeleteInboxMessages = (ids: string[]) =>
  requestClient.post<void>('/message/inbox/delete/batch', { ids });

export const recallMessage = (id: string) =>
  requestClient.post<void>('/message/recall', { id });

export const deleteSentMessage = (id: string) =>
  requestClient.post<void>('/message/sent/delete', { id });

export const batchDeleteSentMessages = (ids: string[]) =>
  requestClient.post<void>('/message/sent/delete/batch', { ids });
