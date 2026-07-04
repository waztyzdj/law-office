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
  homeWorkbench: {
    view: 'home:workbench:view',
  },
  homeCard: {
    cc: 'home:card:cc',
    manage: 'home:card:manage',
    message: 'home:card:message',
    metrics: 'home:card:metrics',
    quickEntry: 'home:card:quick-entry',
    todo: 'home:card:todo',
  },
  workflowCategory: createBasePermissionCodes('workflow:category'),
  workflowArchive: {
    manage: 'workflow:archive:manage',
    view: 'workflow:archive:view',
  },
  workflowForm: createBasePermissionCodes('workflow:form'),
  workflowMonitor: {
    manage: 'workflow:monitor:manage',
    view: 'workflow:monitor:view',
  },
  workflowProcess: createBasePermissionCodes('workflow:process'),
} as const;
