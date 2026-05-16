import type { BaseListParams } from '#/composables/Table/useTable';

import { requestClient } from '#/framework/api/request';
import { BaseApi, type BaseQueryReq } from '#/framework/api/base.api';

/**
 * 用户信息接口
 */
export interface UserInfo {
  id?: string;
  username?: string;
  realname?: string;
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

// 延迟创建用户管理的 CRUD API 实例（避免循环依赖）
let _userApi: BaseApi | null = null;
function getUserApi(): BaseApi {
  if (!_userApi) {
    _userApi = new BaseApi('/user');
  }
  return _userApi;
}

/**
 * 获取当前登录用户信息（特殊接口，不在 BaseController 中）
 */
export async function getUserInfoApi() {
  return requestClient.get<any>('/user/info');
}

/**
 * 用户列表查询（分页）- 适配 useTable 的格式
 * 此函数会被 useTable 自动调用
 */
export async function getUserListApi(params: BaseListParams) {
  // 转换参数格式：current -> pageNum, size -> pageSize
  const backendParams = {
    pageNum: params.current || 1,
    pageSize: params.size || 10,
    queryParams: params.queryParams || {},
  };

  // 添加排序参数（在根级别，不在 queryParams 内）
  if (params.sortField) {
    Object.assign(backendParams, {
      sortField: params.sortField,
      sortOrder: params.sortOrder || 'desc',
    });
  }

  // 直接传递参数给后端
  const response = await getUserApi().page(backendParams);

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
  return getUserApi().getById({ id });
}

/**
 * 保存用户（新增或修改）
 */
export async function saveUserApi(data: UserInfo) {
  return getUserApi().save(data);
}

/**
 * 删除用户
 */
export async function deleteUserApi(id: string) {
  return getUserApi().delete({ id });
}

/**
 * 批量删除用户
 */
export async function batchDeleteUserApi(ids: string[]) {
  return getUserApi().batchDelete(ids);
}

/**
 * 导出用户 Excel
 */
export async function exportUserExcel(params?: BaseQueryReq) {
  return getUserApi().exportExcel(params);
}

/**
 * 导入用户 Excel
 */
export async function importUserExcel(file: File) {
  return getUserApi().importExcel(file);
}
