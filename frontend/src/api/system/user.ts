import { requestClient } from '#/framework/api/request';
import { BaseApi, type BasePageReq, type BaseQueryReq } from '#/framework/api/base.api';
import type { TenantInfo } from './tenant';

/**
 * 用户信息接口
 */
export interface UserInfo {
  id?: string;
  username?: string;
  realname?: string;
  password?: string;
  avatar?: string;
  birthday?: string;
  sex?: number;
  email?: string;
  phone?: string;
  status?: number;
  workNo?: string;
  post?: string;
  telephone?: string;
  idCard?: string;
  userIdentity?: number;
  departIds?: string;
  clientId?: string;
  loginTenantId?: string;
  bpmStatus?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SwitchTenantResult {
  token: string;
  tenantId: string;
  tenantName?: string;
}

export interface CurrentUserProfile {
  id?: string;
  username?: string;
  realname?: string;
  avatar?: string;
  email?: string;
  phone?: string;
  telephone?: string;
  workNo?: string;
  post?: string;
  status?: number;
  tenantId?: string;
  tenantName?: string;
}

export interface CurrentUserOrganization {
  departs: Array<{
    id?: string;
    departName?: string;
    orgCode?: string;
    orgType?: string;
  }>;
  roles: Array<{
    id?: string;
    roleCode?: string;
    roleName?: string;
  }>;
  departRoles: Array<{
    id?: string;
    roleCode?: string;
    roleName?: string;
    defaultRole?: boolean;
  }>;
  menuPermissionCount: number;
  menuPermissions: CurrentUserPermission[];
}

export interface CurrentUserPermission {
  children?: CurrentUserPermission[];
  id?: string;
  menuType?: number;
  name?: string;
  parentId?: string;
}

export interface CurrentUserTenant {
  id?: string;
  name?: string;
  status?: number;
  beginDate?: string;
  endDate?: string;
  current?: boolean;
}

export interface CurrentUserLog {
  id?: string;
  logType?: number;
  logContent?: string;
  operateType?: number;
  ip?: string;
  requestType?: string;
  requestUrl?: string;
  clientType?: string;
  costTime?: number;
  createTime?: string;
}

// 创建并导出用户管理的 CRUD API 实例
export const userApi = new BaseApi('/user');

/**
 * 获取当前登录用户信息（特殊接口，不在 BaseController 中）
 */
export async function getUserInfoApi() {
  return requestClient.get<any>('/user/info');
}

export const getUserRoleIds = (id: string) =>
  requestClient.post<string[]>('/user/roleIds', { id });

export const assignUserRoles = (id: string, ids: string[]) =>
  requestClient.post<void>('/user/assignRoles', { id, ids });

export const listUsers = (params?: BaseQueryReq) =>
  userApi.list<UserInfo>(params);

export const listCurrentUserTenants = () =>
  requestClient.post<TenantInfo[]>('/user/tenants');

export const switchTenant = (tenantId: string) =>
  requestClient.post<SwitchTenantResult>('/user/switchTenant', { tenantId });

export const getCurrentUserProfile = () =>
  requestClient.get<CurrentUserProfile>('/user/profile');

export const updateCurrentUserProfile = (data: CurrentUserProfile) =>
  requestClient.post<CurrentUserProfile>('/user/profile', data);

export const uploadCurrentUserAvatar = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return requestClient.post<CurrentUserProfile>('/user/profile/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const getCurrentUserOrganization = () =>
  requestClient.get<CurrentUserOrganization>('/user/profile/organization');

export const getCurrentUserTenantOptions = () =>
  requestClient.get<CurrentUserTenant[]>('/user/profile/tenants');

export const getCurrentTenantUsers = () =>
  requestClient.get<UserInfo[]>('/user/profile/tenant-users');

export const pageCurrentUserLogs = (params: BasePageReq) =>
  requestClient.post<{
    pageNum: number;
    pageSize: number;
    records: CurrentUserLog[];
    total: number;
  }>('/user/profile/logs/page', params);

export const changeCurrentUserPassword = (data: {
  confirmPassword: string;
  newPassword: string;
  oldPassword: string;
}) => requestClient.post<void>('/auth/changePassword', data);

/**
 * 用户 CRUD API 便捷方法工厂
 * 基于 BaseApi 实例生成类型安全的便捷方法
 * 
 * @example
 * const { deleteUser, batchDeleteUsers } = createUserApiMethods();
 * await deleteUser('123');
 * await batchDeleteUsers(['1', '2']);
 */
export function createUserApiMethods() {
  return {
    /** 分页查询用户列表 */
    pageUsers: (params: BasePageReq) => userApi.page(params),
    
    /** 获取单个用户详情 */
    getUserById: (id: string) => userApi.getById<UserInfo>({ id }),
    
    /** 保存用户（新增或修改） */
    saveUser: (data: UserInfo) => userApi.save<UserInfo>(data),
    
    /** 删除单个用户 - 自动转换 ID 类型 */
    deleteUser: (id: string | number) => userApi.delete({ id: String(id) }),
    
    /** 批量删除用户 - 自动转换 ID 类型 */
    batchDeleteUsers: (ids: (string | number)[]) => userApi.batchDelete(ids.map(id => String(id))),
    
    /** 导出用户 Excel */
    exportUsers: (params?: BaseQueryReq) => userApi.exportExcel(params),
    
    /** 导入用户 Excel */
    importUsers: (file: File) => userApi.importExcel(file),
  };
}

// 导出便捷方法实例（可选，方便直接导入使用）
export const {
  pageUsers,
  getUserById,
  saveUser,
  deleteUser,
  batchDeleteUsers,
  exportUsers,
  importUsers,
} = createUserApiMethods();
