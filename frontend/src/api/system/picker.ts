import type { DepartInfo } from './depart';
import type { RoleInfo } from './role';
import type { UserInfo } from './user';

import { requestClient } from '#/framework/api/request';

export const listPickerUsers = () =>
  requestClient.get<UserInfo[]>('/system/picker/users');

export const listPickerDeparts = () =>
  requestClient.get<DepartInfo[]>('/system/picker/departs');

export const listPickerRoles = () =>
  requestClient.get<RoleInfo[]>('/system/picker/roles');

export const listPickerDepartUsers = (id: string) =>
  requestClient.post<UserInfo[]>('/system/picker/depart-users', { id });

export const listPickerRoleUsers = (id: string) =>
  requestClient.post<UserInfo[]>('/system/picker/role-users', { id });
