export interface WorkflowMeta {
  color: string;
  label: string;
}

export const workflowStatusMap: Record<string, WorkflowMeta> = {
  approved: { color: 'success', label: '已通过' },
  archived: { color: 'default', label: '已归档' },
  canceled: { color: 'default', label: '已取消' },
  disabled: { color: 'default', label: '已停用' },
  done: { color: 'success', label: '已办' },
  draft: { color: 'default', label: '草稿' },
  enabled: { color: 'success', label: '启用' },
  published: { color: 'processing', label: '已发布' },
  rejected: { color: 'error', label: '不通过' },
  returned: { color: 'warning', label: '已退回' },
  running: { color: 'processing', label: '审批中' },
  terminated: { color: 'error', label: '已终止' },
  todo: { color: 'warning', label: '待办' },
  transferred: { color: 'processing', label: '已转办' },
  withdrawn: { color: 'default', label: '已撤回' },
};

export const designerTypeMap: Record<string, string> = {
  bpmn: 'BPMN',
  simple: '简易设计器',
};

export const designerTypeOptions = [
  { label: '简易设计器', value: 'simple' },
  { label: 'BPMN', value: 'bpmn' },
];

export const processModelStatusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已停用', value: 'disabled' },
];

export const formDefinitionStatusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已停用', value: 'disabled' },
];

export const startScopeTypeMap: Record<string, string> = {
  all: '全部人员',
  specified: '指定范围',
};

export const startScopeTypeOptions = Object.entries(startScopeTypeMap).map(
  ([value, label]) => ({
    label,
    value,
  }),
);

export const todoTaskStatusOptions = [{ label: '待办', value: 'todo' }];

export const doneTaskStatusOptions = [
  { label: '已办', value: 'done' },
  { label: '已退回', value: 'returned' },
  { label: '已转办', value: 'transferred' },
];

export const taskTypeMap: Record<string, string> = {
  add_sign: '加签',
  countersign: '会签',
  normal: '普通',
  orsign: '或签',
  start_draft: '待提交',
  transfer: '转办',
};

export const taskTypeOptions = [
  { label: '普通', value: 'normal' },
  { label: '会签', value: 'countersign' },
  { label: '或签', value: 'orsign' },
  { label: '待提交', value: 'start_draft' },
  { label: '转办', value: 'transfer' },
  { label: '加签', value: 'add_sign' },
];

export const approvalModeMap: Record<string, WorkflowMeta> = {
  countersign: { color: 'purple', label: '会签' },
  orsign: { color: 'cyan', label: '或签' },
  single: { color: 'default', label: '单人审批' },
};

const defaultApprovalModeMeta: WorkflowMeta = { color: 'default', label: '单人审批' };

export const approvalModeOptions = Object.entries(approvalModeMap).map(
  ([value, meta]) => ({
    label: meta.label,
    value,
  }),
);

export const workflowActionMap: Record<string, WorkflowMeta> = {
  add_sign: { color: 'purple', label: '加签' },
  approve: { color: 'green', label: '通过' },
  reject: { color: 'red', label: '不通过' },
  return: { color: 'orange', label: '退回' },
  save_draft: { color: 'default', label: '保存草稿' },
  start: { color: 'blue', label: '发起' },
  system_complete: { color: 'default', label: '系统完成' },
  transfer: { color: 'cyan', label: '转办' },
  urge: { color: 'orange', label: '催办' },
  withdraw: { color: 'default', label: '撤回' },
};

export const processInstanceStatusOptions = [
  { label: '审批中', value: 'running' },
  { label: '已通过', value: 'approved' },
  { label: '不通过', value: 'rejected' },
  { label: '已撤回', value: 'withdrawn' },
  { label: '已终止', value: 'terminated' },
];

export const ccStatusOptions = [
  { label: '未读', value: 'unread' },
  { label: '已读', value: 'read' },
];

export const ccTriggerActionMap: Record<string, string> = {
  approve: '节点通过',
  manual: '手动抄送',
  process_finished: '流程结束',
  start: '发起后',
};

export function getStatusMeta(status?: string) {
  if (!status) {
    return { color: 'default', label: '-' };
  }

  return workflowStatusMap[status] ?? { color: 'default', label: status };
}

export function getWorkflowActionMeta(action?: string) {
  if (!action) {
    return { color: 'default', label: '-' };
  }

  return workflowActionMap[action] ?? { color: 'default', label: action };
}

export function getApprovalModeMeta(approvalMode?: string): WorkflowMeta {
  if (!approvalMode) {
    return defaultApprovalModeMeta;
  }

  return approvalModeMap[approvalMode] ?? { color: 'default', label: approvalMode };
}

export function formatApprovalProgress(record: {
  approvalMode?: string;
  groupCompleted?: number;
  groupTotal?: number;
}) {
  if (!['countersign', 'orsign'].includes(record.approvalMode || '')) {
    return '';
  }
  const completed = Number(record.groupCompleted ?? 0);
  const total = Number(record.groupTotal ?? 0);
  return total > 0 ? `${completed}/${total}` : '';
}
