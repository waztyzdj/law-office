export const menuTypeValues = {
  buttonPermission: 2,
  firstLevelMenu: 0,
  subMenu: 1,
} as const;

export type MenuType =
  (typeof menuTypeValues)[keyof typeof menuTypeValues];

export const menuTypeOptions: Array<{
  color: string;
  label: string;
  value: MenuType;
}> = [
  { color: 'blue', label: '一级菜单', value: menuTypeValues.firstLevelMenu },
  { color: 'green', label: '子菜单', value: menuTypeValues.subMenu },
  { color: 'purple', label: '按钮权限', value: menuTypeValues.buttonPermission },
];

export const menuTypeOptionMap = Object.fromEntries(
  menuTypeOptions.map((item) => [item.value, item]),
) as Record<MenuType, (typeof menuTypeOptions)[number]>;
