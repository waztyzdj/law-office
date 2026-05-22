const baseActions = {
  edit: 'edit',
  view: 'view',
} as const;

function createBasePermissionCodes(module: string) {
  return {
    edit: `${module}:${baseActions.edit}`,
    view: `${module}:${baseActions.view}`,
  } as const;
}

export const permissionCodes = {
  permission: createBasePermissionCodes('permission'),
  role: createBasePermissionCodes('role'),
  user: createBasePermissionCodes('user'),
} as const;
