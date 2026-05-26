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
