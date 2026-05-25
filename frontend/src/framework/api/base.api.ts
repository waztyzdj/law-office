import { requestClient } from '#/framework/api/request';

/**
 * 通用基础请求参数（对应后端 BaseReq）
 */
export interface BaseReq {
  id?: string;
}

/**
 * 通用查询请求参数（对应后端 BaseQueryReq）
 */
export interface BaseQueryReq {
  queryParams?: Record<string, any>;
  sortField?: string;
  sortOrder?: string;
}

/**
 * 通用分页请求参数（对应后端 BasePageReq）
 */
export interface BasePageReq extends BaseQueryReq {
  pageNum: number;
  pageSize: number;
}

/**
 * 通用 CRUD API 工具类
 * 基于后端 BaseController 提供的标准 REST API
 * 
 * @example
 * // 用户管理
 * const userApi = new BaseApi('/user');
 * await userApi.page({ pageNum: 1, pageSize: 10 });
 * await userApi.save(userData);
 * await userApi.delete({ id: '123' });
 * 
 * @example
 * // 角色管理
 * const roleApi = new BaseApi('/role');
 * await roleApi.list({ queryParams: { status: 1 } });
 */
export class BaseApi {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  /**
   * 列表查询（不分页）
   * POST /{module}/list
   * @param params - 查询条件（BaseQueryReq 格式）
   */
  list<T = any>(params?: BaseQueryReq) {
    return requestClient.post<T[]>(`${this.baseUrl}/list`, params || {});
  }

  /**
   * 分页查询
   * POST /{module}/page
   * @param params - 分页查询参数（BasePageReq 格式）
   */
  page<T = any>(params: BasePageReq) {
    return requestClient.post<{
      pageNum: number;
      pageSize: number;
      records: T[];
      total: number;
    }>(`${this.baseUrl}/page`, params);
  }

  /**
   * 根据ID查询
   * POST /{module}/getById
   * @param params - 包含id的请求参数（BaseReq 格式）
   */
  getById<T = any>(params: BaseReq) {
    return requestClient.post<T>(`${this.baseUrl}/getById`, params);
  }

  /**
   * 保存数据（新增或修改）
   * POST /{module}/save
   * @param data - 要保存的数据
   */
  save<T = any>(data: T) {
    return requestClient.post<T>(`${this.baseUrl}/save`, data);
  }

  /**
   * 批量保存
   * POST /{module}/batchSave
   * @param dataList - 要保存的数据列表
   */
  batchSave<T = any>(dataList: T[]) {
    return requestClient.post<T[]>(`${this.baseUrl}/batchSave`, dataList);
  }

  /**
   * 删除单个数据
   * POST /{module}/delete
   * @param params - 包含id的请求参数（BaseReq 格式）
   */
  delete(params: BaseReq) {
    return requestClient.post<void>(`${this.baseUrl}/delete`, params);
  }

  /**
   * 批量删除
   * POST /{module}/batchDelete
   * @param ids - ID列表
   */
  batchDelete(ids: string[]) {
    return requestClient.post<void>(`${this.baseUrl}/batchDelete`, ids);
  }

  /**
   * 导入 Excel
   * POST /{module}/import
   * @param file - Excel 文件
   */
  importExcel(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return requestClient.post<number>(`${this.baseUrl}/import`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  /**
   * 导出 Excel
   * POST /{module}/export
   * @param params - 查询条件（BaseQueryReq 格式）
   */
  exportExcel(params?: BaseQueryReq) {
    return requestClient.post(`${this.baseUrl}/export`, params || {}, {
      responseType: 'blob',
    });
  }
}
