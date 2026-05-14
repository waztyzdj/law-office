import type { UserInfo as VbenUserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 用户信息接口（系统管理用）
 */
export interface UserInfo {
  id: string;
  username: string;
  realname: string;
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
  createTime?: string;
  updateTime?: string;
}

/**
 * 用户列表查询参数
 */
export interface UserListParams {
  current?: number;
  size?: number;
  username?: string;
  realname?: string;
  phone?: string;
  email?: string;
  status?: number;
  queryParams?: Record<string, any>; // 动态查询条件（用于列头筛选）
}

/**
 * 用户列表响应
 */
export interface UserListResult {
  items: UserInfo[];
  total: number;
}

/**
 * 获取当前登录用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<VbenUserInfo>('/user/info');
}

/**
 * 获取用户列表（分页）
 */
export async function getUserListApi(params: UserListParams) {
  // 转换参数格式：current -> pageNum, size -> pageSize
  const requestParams: any = {
    pageNum: params.current || 1,
    pageSize: params.size || 10,
    queryParams: {} as Record<string, any>,
  };

  // 将搜索条件放入 queryParams
  if (params.username) {
    requestParams.queryParams.username = params.username;
  }
  if (params.realname) {
    requestParams.queryParams.realname = params.realname;
  }
  if (params.phone) {
    requestParams.queryParams.phone = params.phone;
  }
  if (params.email) {
    requestParams.queryParams.email = params.email;
  }
  if (params.status !== undefined) {
    requestParams.queryParams.status = params.status;
  }

  // 合并额外的 queryParams（来自列头筛选）
  if (params.queryParams && Object.keys(params.queryParams).length > 0) {
    requestParams.queryParams = {
      ...requestParams.queryParams,
      ...params.queryParams,
    };
  }

  // 调用后端接口
  const response = await requestClient.post<any>('/user/page', requestParams);
  
  // 转换响应格式：后端返回 { records, total, pageNum, pageSize, pages }
  // 前端期望 { items, total }
  return {
    items: response.records || [],
    total: response.total || 0,
  };
}

/**
 * 获取用户详情
 */
export async function getUserDetailApi(id: string) {
  return requestClient.get<UserInfo>(`/user/${id}`);
}

/**
 * 创建用户
 */
export async function createUserApi(data: Partial<UserInfo>) {
  return requestClient.post('/user', data);
}

/**
 * 更新用户
 */
export async function updateUserApi(data: Partial<UserInfo>) {
  return requestClient.put('/user', data);
}

/**
 * 删除用户
 */
export async function deleteUserApi(id: string) {
  return requestClient.delete(`/user/${id}`);
}

/**
 * 批量删除用户
 */
export async function batchDeleteUserApi(ids: string[]) {
  return requestClient.delete('/user/batch', { data: ids });
}
