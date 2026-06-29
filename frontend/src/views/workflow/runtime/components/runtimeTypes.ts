import type { TaskActionPermissionInfo } from '#/api/workflow';

export type DrawerMode = 'adminMonitor' | 'detail' | 'done' | 'start' | 'started' | 'todo';

export type WorkflowAction = 'addSign' | 'return' | 'transfer';

export const workflowActionTitleMap: Record<WorkflowAction, string> = {
  addSign: '加签',
  return: '退回',
  transfer: '转办',
};

export interface ProcessProgressNode {
  action?: string;
  approvalMode?: string;
  actor?: string;
  comment?: string;
  groupProgress?: string;
  id: string;
  name: string;
  resultStatus?: string;
  status: 'current' | 'done' | 'end';
  taskId?: string;
  time?: string;
}

export type RuntimeActionPermissions = TaskActionPermissionInfo;
