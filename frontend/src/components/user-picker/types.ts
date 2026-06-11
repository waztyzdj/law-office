import type { UserInfo } from '#/api/system/user';

export type UserPickerMode = 'multiple' | 'single';
export type UserPickerValue = string | string[] | undefined;

export interface UserPickerOption {
  label: string;
  searchText: string;
  user: UserInfo;
  value: string;
}

export interface UserPickerChangePayload {
  users: UserInfo[];
  value: UserPickerValue;
}
