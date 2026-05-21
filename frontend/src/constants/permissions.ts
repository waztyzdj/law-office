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
  user: createBasePermissionCodes('user'),
} as const;

