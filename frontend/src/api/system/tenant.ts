import { BaseApi, type BasePageReq, type BaseQueryReq } from '#/framework/api/base.api';

export interface TenantInfo {
  id?: string;
  name?: string;
  beginDate?: string;
  endDate?: string;
  status?: number;
  trade?: string;
  companySize?: string;
  companyAddress?: string;
  companyLogo?: string;
  houseNumber?: string;
  workPlace?: string;
  secondaryDomain?: string;
  loginBkgdImg?: string;
  position?: string;
  department?: string;
  applyStatus?: number;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

const tenantApi = new BaseApi('/tenant');

export const pageTenants = (params: BasePageReq) => tenantApi.page<TenantInfo>(params);
export const listTenants = (params?: BaseQueryReq) => tenantApi.list<TenantInfo>(params);
export const getTenantById = (id: string) => tenantApi.getById<TenantInfo>({ id });
export const saveTenant = (data: TenantInfo) => tenantApi.save<TenantInfo>(data);
export const deleteTenant = (id: string | number) =>
  tenantApi.delete({ id: String(id) });
export const batchDeleteTenants = (ids: (string | number)[]) =>
  tenantApi.batchDelete(ids.map((id) => String(id)));
