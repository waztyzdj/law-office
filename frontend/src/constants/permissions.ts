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
  category: createBasePermissionCodes('category'),
  log: createBasePermissionCodes('log'),
  message: createBasePermissionCodes('message'),
  permission: createBasePermissionCodes('permission'),
  depart: createBasePermissionCodes('depart'),
  dict: createBasePermissionCodes('dict'),
  dictItem: createBasePermissionCodes('dict-item'),
  role: createBasePermissionCodes('role'),
  tenant: createBasePermissionCodes('tenant'),
  user: createBasePermissionCodes('user'),
  workflowCategory: createBasePermissionCodes('workflow:category'),
  workflowForm: createBasePermissionCodes('workflow:form'),
  workflowMonitor: {
    manage: 'workflow:monitor:manage',
    view: 'workflow:monitor:view',
  },
  workflowProcess: createBasePermissionCodes('workflow:process'),
} as const;
