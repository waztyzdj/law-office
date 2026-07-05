import type {
  WorkbenchCardSize,
  WorkbenchQuickEntryType,
  WorkbenchStatus,
} from '#/api/home/workbench';

export const workbenchStatusOptions: Array<{
  color: string;
  label: string;
  value: WorkbenchStatus;
}> = [
  { color: 'green', label: '启用', value: 'enabled' },
  { color: 'red', label: '停用', value: 'disabled' },
];

export const workbenchCardSizeOptions: Array<{
  label: string;
  value: WorkbenchCardSize;
}> = [
  { label: '小卡片', value: 'small' },
  { label: '中卡片', value: 'medium' },
  { label: '大卡片', value: 'large' },
  { label: '通栏', value: 'full' },
];

export const workbenchQuickEntryTypeOptions: Array<{
  label: string;
  value: WorkbenchQuickEntryType;
}> = [
  { label: '内部菜单', value: 'menu' },
  { label: '外部链接', value: 'link' },
];

export const workbenchCardPermissionOptions = [
  { label: '我的待办卡片', value: 'home:card:todo' },
  { label: '我的抄送卡片', value: 'home:card:cc' },
  { label: '快捷菜单卡片', value: 'home:card:quick-entry' },
  { label: '我的消息卡片', value: 'home:card:message' },
  { label: '我的收藏卡片', value: 'home:card:favorite' },
  { label: '指标概览卡片', value: 'home:card:metrics' },
  { label: '工作台管理', value: 'home:card:manage' },
];

export const workbenchCardComponentOptions = [
  { label: '我的待办', value: 'WorkbenchTodoCard' },
  { label: '我的抄送', value: 'WorkbenchCcCard' },
  { label: '快捷菜单', value: 'WorkbenchQuickEntryCard' },
  { label: '我的消息', value: 'WorkbenchMessageCard' },
  { label: '我的收藏', value: 'WorkbenchFavoriteCard' },
  { label: '指标概览', value: 'WorkbenchMetricsCard' },
];
