export const workflowStatusMap: Record<string, { color: string; label: string }> = {
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

export const doneTaskStatusOptions = [{ label: '已办', value: 'done' }];

export const taskTypeMap: Record<string, string> = {
  add_sign: '加签',
  normal: '普通',
  start_draft: '待提交',
  transfer: '转办',
};

export const taskTypeOptions = [
  { label: '普通', value: 'normal' },
  { label: '待提交', value: 'start_draft' },
  { label: '转办', value: 'transfer' },
  { label: '加签', value: 'add_sign' },
];

export const workflowActionMap: Record<string, { color: string; label: string }> = {
  add_sign: { color: 'purple', label: '加签' },
  approve: { color: 'green', label: '通过' },
  reject: { color: 'red', label: '不通过' },
  return: { color: 'orange', label: '退回' },
  save_draft: { color: 'default', label: '保存草稿' },
  start: { color: 'blue', label: '发起' },
  system_complete: { color: 'default', label: '系统完成' },
  transfer: { color: 'cyan', label: '转办' },
};

export const processInstanceStatusOptions = [
  { label: '审批中', value: 'running' },
  { label: '已通过', value: 'approved' },
  { label: '不通过', value: 'rejected' },
  { label: '已终止', value: 'terminated' },
];

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
