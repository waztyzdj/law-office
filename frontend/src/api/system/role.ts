import { requestClient } from '#/framework/api/request';
import {
  BaseApi,
  type BasePageReq,
  type BaseQueryReq,
} from '#/framework/api/base.api';

export interface RoleInfo {
  id?: string;
  roleCode?: string;
  roleName?: string;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

const roleApi = new BaseApi('/role');

export const pageRoles = (params: BasePageReq) => roleApi.page(params);
export const listRoles = (params?: BaseQueryReq) => roleApi.list<RoleInfo>(params);
export const getRoleById = (id: string) => roleApi.getById<RoleInfo>({ id });
export const saveRole = (data: RoleInfo) => roleApi.save<RoleInfo>(data);
export const deleteRole = (id: string | number) =>
  roleApi.delete({ id: String(id) });
export const batchDeleteRoles = (ids: (string | number)[]) =>
  roleApi.batchDelete(ids.map((id) => String(id)));

export const getRolePermissionIds = (id: string) =>
  requestClient.post<string[]>('/role/permissionIds', { id });

export const assignRolePermissions = (id: string, ids: string[]) =>
  requestClient.post<void>('/role/assignPermissions', { id, ids });
