import { BaseApi, type BasePageReq, type BaseQueryReq } from '#/framework/api/base.api';

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

const departApi = new BaseApi('/depart');

export const pageDeparts = (params: BasePageReq) => departApi.page<DepartInfo>(params);
export const listDeparts = (params?: BaseQueryReq) => departApi.list<DepartInfo>(params);
export const getDepartById = (id: string) => departApi.getById<DepartInfo>({ id });
export const saveDepart = (data: DepartInfo) => departApi.save<DepartInfo>(data);
export const deleteDepart = (id: string | number) =>
  departApi.delete({ id: String(id) });
export const batchDeleteDeparts = (ids: (string | number)[]) =>
  departApi.batchDelete(ids.map((id) => String(id)));
