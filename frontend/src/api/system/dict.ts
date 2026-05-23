import { requestClient } from '#/framework/api/request';
import { BaseApi, type BasePageReq, type BaseQueryReq } from '#/framework/api/base.api';

export interface SysDictInfo {
  id?: string;
  dictName?: string;
  dictCode?: string;
  description?: string;
  deleteFlag?: number;
  createTime?: string;
  updateTime?: string;
}

export interface SysDictItemInfo {
  id?: string;
  dictId?: string;
  itemText?: string;
  itemValue?: string;
  description?: string;
  sortOrder?: number;
  status?: number;
  deleteFlag?: number;
  createTime?: string;
  updateTime?: string;
}

export interface DictOption {
  label: string;
  value: string;
}

const dictApi = new BaseApi('/dict');
const dictItemApi = new BaseApi('/dictItem');

export const pageDicts = (params: BasePageReq) => dictApi.page<SysDictInfo>(params);
export const listDicts = (params?: BaseQueryReq) => dictApi.list<SysDictInfo>(params);
export const getDictById = (id: string) => dictApi.getById<SysDictInfo>({ id });
export const saveDict = (data: SysDictInfo) => dictApi.save<SysDictInfo>(data);
export const deleteDict = (id: string | number) => dictApi.delete({ id: String(id) });
export const batchDeleteDicts = (ids: (string | number)[]) =>
  dictApi.batchDelete(ids.map((id) => String(id)));

export const pageDictItems = (params: BasePageReq) =>
  dictItemApi.page<SysDictItemInfo>(params);
export const listDictItems = (params?: BaseQueryReq) =>
  dictItemApi.list<SysDictItemInfo>(params);
export const getDictItemById = (id: string) =>
  dictItemApi.getById<SysDictItemInfo>({ id });
export const saveDictItem = (data: SysDictItemInfo) =>
  dictItemApi.save<SysDictItemInfo>(data);
export const deleteDictItem = (id: string | number) =>
  dictItemApi.delete({ id: String(id) });
export const batchDeleteDictItems = (ids: (string | number)[]) =>
  dictItemApi.batchDelete(ids.map((id) => String(id)));

export async function getDictByCode(dictCode: string) {
  const dicts = await listDicts({
    queryParams: {
      dictCode_eq: dictCode,
    },
  });
  return dicts?.[0];
}

export async function listDictItemsByCode(dictCode: string) {
  const dict = await getDictByCode(dictCode);
  if (!dict?.id) {
    return [];
  }

  return listDictItems({
    queryParams: {
      dictId_eq: dict.id,
      status_eq: 1,
    },
    sortField: 'sortOrder',
    sortOrder: 'asc',
  });
}

export async function listDictOptionsByCode(dictCode: string) {
  return requestClient.get<DictOption[]>(`/dict/options/${encodeURIComponent(dictCode)}`);
}

export async function getDictOptionsByCode(dictCode: string) {
  return listDictOptionsByCode(dictCode);
}
