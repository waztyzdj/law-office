import { requestClient } from '#/framework/api/request';
import type { BasePageReq } from '#/framework/api/base.api';

export type WorkbenchCardCode =
  | 'cc'
  | 'favorite'
  | 'message'
  | 'metrics'
  | 'quick-entry'
  | 'todo';

export type WorkbenchCardSize = 'full' | 'large' | 'medium' | 'small';

export interface WorkbenchLayoutCard {
  cardCode: WorkbenchCardCode | string;
  cardName: string;
  componentKey: string;
  config?: Record<string, unknown>;
  configJson?: string;
  gridH?: number;
  gridW?: number;
  gridX?: number;
  gridY?: number;
  permissionCode?: string;
  refreshInterval?: number;
  size: WorkbenchCardSize;
  sortNo: number;
  systemVisible?: boolean;
  userCustomized?: boolean;
  visible: boolean;
}

export interface WorkbenchLayout {
  cards: WorkbenchLayoutCard[];
  hiddenCards: WorkbenchLayoutCard[];
}

export interface WorkbenchPageResult<T> {
  pageNum: number;
  pageSize: number;
  records: T[];
  total: number;
}

export interface WorkbenchQuickEntryList {
  entries: WorkbenchQuickEntryInfo[];
}

export interface WorkbenchCardInfo {
  cardCode?: string;
  cardName?: string;
  componentKey?: string;
  config?: Record<string, unknown>;
  configJson?: string;
  defaultRefreshInterval?: number;
  defaultSize?: WorkbenchCardSize;
  defaultSort?: number;
  defaultVisible?: number;
  id?: string;
  permissionCode?: string;
  remark?: string;
  status?: WorkbenchStatus;
}

export interface WorkbenchCardItem {
  bizId?: string;
  icon?: string;
  id?: string;
  level?: 'high' | 'low' | 'medium' | string;
  occurTime?: string;
  source?: string;
  status?: string;
  targetPath?: string;
  targetParamsJson?: string;
  targetType?: string;
  title?: string;
  type?: string;
  value?: number;
  [key: string]: unknown;
}

export interface WorkbenchCardData {
  cardCode: string;
  items: WorkbenchCardItem[];
  summary: Record<string, number | string | boolean | undefined>;
}

export type WorkbenchStatus = 'disabled' | 'enabled';
export type WorkbenchQuickEntryType = 'link' | 'menu';

export interface WorkbenchQuickEntryInfo {
  config?: Record<string, unknown>;
  configJson?: string;
  entryCode?: string;
  entryName?: string;
  entryType?: WorkbenchQuickEntryType;
  icon?: string;
  id?: string;
  menuId?: string;
  ownerType?: string;
  path?: string;
  permissionCode?: string;
  sortNo?: number;
  status?: WorkbenchStatus;
}

export interface WorkbenchCardDataReq {
  cardCode: string;
  limit?: number;
  params?: Record<string, unknown>;
  timeRange?: 'month' | 'today' | 'week' | string;
}

export interface WorkbenchCardPageReq extends BasePageReq {
  cardCode?: string;
  cardName?: string;
  componentKey?: string;
  status?: WorkbenchStatus | string;
}

export interface WorkbenchCardSortReq {
  items: Array<{
    defaultSort?: number;
    id: string;
  }>;
}

export interface WorkbenchQuickEntryPageReq extends BasePageReq {
  entryCode?: string;
  entryName?: string;
  entryType?: WorkbenchQuickEntryType | string;
  status?: WorkbenchStatus | string;
}

export interface WorkbenchQuickEntryListReq {
  includeSystem?: boolean;
}

export interface WorkbenchLayoutSaveReq {
  cards: Array<{
    cardCode: string;
    config?: Record<string, unknown>;
    configJson?: string;
    gridH?: number;
    gridW?: number;
    gridX?: number;
    gridY?: number;
    visible?: boolean;
  }>;
}

export function getWorkbenchLayout() {
  return requestClient.post<WorkbenchLayout>('/home/workbench/layout', {});
}

export function saveWorkbenchLayout(data: WorkbenchLayoutSaveReq) {
  return requestClient.post<void>('/home/workbench/layout/save', data);
}

export function resetWorkbenchLayout() {
  return requestClient.post<void>('/home/workbench/layout/reset', {});
}

export function getWorkbenchCardData(data: WorkbenchCardDataReq) {
  return requestClient.post<WorkbenchCardData>('/home/workbench/card/data', data);
}

export function listWorkbenchQuickEntries(data: WorkbenchQuickEntryListReq = {}) {
  return requestClient.post<WorkbenchQuickEntryList>(
    '/home/workbench/quick-entry/list',
    data,
  );
}

export function saveCurrentWorkbenchQuickEntry(data: WorkbenchQuickEntryInfo) {
  return requestClient.post<WorkbenchQuickEntryInfo>(
    '/home/workbench/quick-entry/save',
    data,
  );
}

export function deleteCurrentWorkbenchQuickEntry(id: string) {
  return requestClient.post<void>('/home/workbench/quick-entry/delete', { id });
}

export function pageWorkbenchCards(data: WorkbenchCardPageReq) {
  return requestClient.post<WorkbenchPageResult<WorkbenchCardInfo>>(
    '/home/admin/workbench/card/page',
    data,
  );
}

export function getWorkbenchCardDetail(id: string) {
  return requestClient.post<WorkbenchCardInfo>(
    '/home/admin/workbench/card/detail',
    { id },
  );
}

export function saveWorkbenchCard(data: WorkbenchCardInfo) {
  return requestClient.post<WorkbenchCardInfo>(
    '/home/admin/workbench/card/save',
    data,
  );
}

export function updateWorkbenchCardStatus(id: string, status: WorkbenchStatus) {
  return requestClient.post<void>('/home/admin/workbench/card/status', {
    id,
    status,
  });
}

export function updateWorkbenchCardSort(data: WorkbenchCardSortReq) {
  return requestClient.post<void>('/home/admin/workbench/card/sort', data);
}

export function pageWorkbenchQuickEntries(data: WorkbenchQuickEntryPageReq) {
  return requestClient.post<WorkbenchPageResult<WorkbenchQuickEntryInfo>>(
    '/home/admin/workbench/quick-entry/page',
    data,
  );
}

export function saveWorkbenchQuickEntry(data: WorkbenchQuickEntryInfo) {
  return requestClient.post<WorkbenchQuickEntryInfo>(
    '/home/admin/workbench/quick-entry/save',
    data,
  );
}

export function updateWorkbenchQuickEntryStatus(
  id: string,
  status: WorkbenchStatus,
) {
  return requestClient.post<void>('/home/admin/workbench/quick-entry/status', {
    id,
    status,
  });
}
