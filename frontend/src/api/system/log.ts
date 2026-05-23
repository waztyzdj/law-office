import { BaseApi, type BasePageReq, type BaseQueryReq } from '#/framework/api/base.api';

export interface LogInfo {
  id?: string;
  logType?: number;
  logContent?: string;
  operateType?: number;
  userid?: string;
  username?: string;
  ip?: string;
  method?: string;
  requestUrl?: string;
  requestParam?: string;
  requestType?: string;
  costTime?: number;
  clientType?: string;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

const logApi = new BaseApi('/log');

export const pageLogs = (params: BasePageReq) => logApi.page<LogInfo>(params);
export const listLogs = (params?: BaseQueryReq) => logApi.list<LogInfo>(params);
export const getLogById = (id: string) => logApi.getById<LogInfo>({ id });
export const deleteLog = (id: string | number) => logApi.delete({ id: String(id) });
export const batchDeleteLogs = (ids: (string | number)[]) =>
  logApi.batchDelete(ids.map((id) => String(id)));
