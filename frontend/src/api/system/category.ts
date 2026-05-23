import { BaseApi, type BasePageReq, type BaseQueryReq } from '#/framework/api/base.api';

export interface CategoryInfo {
  id?: string;
  pid?: string;
  name?: string;
  code?: string;
  hasChild?: string;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

const categoryApi = new BaseApi('/category');

export const pageCategories = (params: BasePageReq) =>
  categoryApi.page<CategoryInfo>(params);
export const listCategories = (params?: BaseQueryReq) =>
  categoryApi.list<CategoryInfo>(params);
export const getCategoryById = (id: string) =>
  categoryApi.getById<CategoryInfo>({ id });
export const saveCategory = (data: CategoryInfo) =>
  categoryApi.save<CategoryInfo>(data);
export const deleteCategory = (id: string | number) =>
  categoryApi.delete({ id: String(id) });
export const batchDeleteCategories = (ids: (string | number)[]) =>
  categoryApi.batchDelete(ids.map((id) => String(id)));
