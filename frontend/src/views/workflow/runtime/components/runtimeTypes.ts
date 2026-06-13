import type { TaskActionPermissionInfo } from '#/api/workflow';

export type DrawerMode = 'detail' | 'done' | 'start' | 'started' | 'todo';

export type WorkflowAction = 'addSign' | 'return' | 'transfer';

export const workflowActionTitleMap: Record<WorkflowAction, string> = {
  addSign: '加签',
  return: '退回',
  transfer: '转办',
};

export interface ProcessProgressNode {
  action?: string;
  actor?: string;
  comment?: string;
  id: string;
  name: string;
  resultStatus?: string;
  status: 'current' | 'done' | 'end';
  time?: string;
}

export type RuntimeActionPermissions = TaskActionPermissionInfo;
