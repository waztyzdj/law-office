import { requestClient } from '#/framework/api/request';
import { BaseApi, type BaseQueryReq } from '#/framework/api/base.api';
import type { MenuType } from '#/constants/menu-types';

export interface PermissionInfo {
  id?: string;
  parentId?: string;
  name?: string;
  url?: string;
  component?: string;
  componentName?: string;
  icon?: string;
  sortNo?: number;
  menuType?: MenuType;
  perms?: string;
  hidden?: number;
  hideTab?: number;
  keepAlive?: boolean;
  redirect?: string;
  status?: number | string;
  children?: PermissionInfo[] | null;
  createTime?: string;
  updateTime?: string;
}

const permissionApi = new BaseApi('/permission');

export const listPermissions = (params?: BaseQueryReq) =>
  permissionApi.list<PermissionInfo>(params);
export const getPermissionTree = () =>
  requestClient.get<PermissionInfo[]>('/permission/tree');
export const getPermissionById = (id: string) =>
  permissionApi.getById<PermissionInfo>({ id });
export const savePermission = (data: PermissionInfo) =>
  permissionApi.save<PermissionInfo>(data);
export const deletePermission = (id: string | number) =>
  permissionApi.delete({ id: String(id) });
