import { BaseApi, type BaseQueryReq } from '#/framework/api/base.api';
import { requestClient } from '#/framework/api/request';
import type { PermissionInfo } from './permission';

export interface DepartInfo {
  id?: string;
  parentId?: string | null;
  departName?: string;
  departNameEn?: string;
  departNameAbbr?: string;
  departOrder?: number;
  orgType?: string;
  orgCode?: string;
  mobile?: string;
  fax?: string;
  address?: string;
  description?: string;
  memo?: string;
  status?: string;
  deleteFlag?: number;
  children?: DepartInfo[] | null;
  createTime?: string;
  updateTime?: string;
  izLeaf?: boolean;
}

export interface DepartRoleInfo {
  id?: string;
  departId?: string;
  roleName?: string;
  roleCode?: string;
  description?: string;
  defaultRole?: boolean;
}

export interface DepartUserInfo {
  id?: string;
  username?: string;
  realname?: string;
  avatar?: string;
  email?: string;
  phone?: string;
  status?: number;
  workNo?: string;
  post?: string;
}

const departApi = new BaseApi('/depart');

export const listDeparts = (params?: BaseQueryReq) => departApi.list<DepartInfo>(params);
export const getDepartById = (id: string) => departApi.getById<DepartInfo>({ id });
export const saveDepart = (data: DepartInfo) => departApi.save<DepartInfo>(data);
export const deleteDepart = (id: string | number) =>
  departApi.delete({ id: String(id) });
export const batchDeleteDeparts = (ids: (string | number)[]) =>
  departApi.batchDelete(ids.map((id) => String(id)));

export const getDepartUserIds = (id: string) =>
  requestClient.post<string[]>('/depart/userIds', { id });
export const getDepartUsers = (id: string) =>
  requestClient.post<DepartUserInfo[]>('/depart/users', { id });
export const assignDepartUsers = (id: string, ids: string[]) =>
  requestClient.post<void>('/depart/assignUsers', { id, ids });

export const getDepartRoles = (id: string) =>
  requestClient.post<DepartRoleInfo[]>('/depart/roles', { id });
export const getDepartGrantablePermissionTree = () =>
  requestClient.get<PermissionInfo[]>('/depart/grantablePermissionTree');
export const getDepartPermissionIds = (id: string) =>
  requestClient.post<string[]>('/depart/permissionIds', { id });

export const saveDepartRole = (data: DepartRoleInfo) =>
  requestClient.post<DepartRoleInfo>('/departRole/saveByDepart', data);
export const deleteDepartRole = (id: string | number) =>
  requestClient.post<void>('/departRole/deleteByDepart', { id: String(id) });
export const getDepartRolePermissionIds = (id: string) =>
  requestClient.post<string[]>('/departRole/permissionIds', { id });
export const assignDepartRolePermissions = (id: string, ids: string[]) =>
  requestClient.post<void>('/departRole/assignPermissions', { id, ids });
export const getDepartRoleUserIds = (id: string) =>
  requestClient.post<string[]>('/departRole/userIds', { id });
export const assignDepartRoleUsers = (id: string, ids: string[]) =>
  requestClient.post<void>('/departRole/assignUsers', { id, ids });
