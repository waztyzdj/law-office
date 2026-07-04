import type { SelectOption } from '#/composables/Table';

export const messageTypeOptions: SelectOption[] = [
  { color: 'blue', label: '普通消息', value: 1 },
  { color: 'cyan', label: '通知公告', value: 2 },
  { color: 'purple', label: '待办提醒', value: 3 },
  { color: 'geekblue', label: '文件消息', value: 4 },
  { color: 'default', label: '系统消息', value: 9 },
] as SelectOption[];

export const workflowCcMessageTypeOption: SelectOption = {
  color: 'cyan',
  label: '抄送提醒',
  value: 'workflow_cc',
};

export const workflowResultMessageTypeOption: SelectOption = {
  color: 'green',
  label: '审批结果提醒',
  value: 'workflow_result',
};

export const workflowWithdrawMessageTypeOption: SelectOption = {
  color: 'orange',
  label: '撤回提醒',
  value: 'workflow_withdraw',
};

export const workflowUrgeMessageTypeOption: SelectOption = {
  color: 'gold',
  label: '催办提醒',
  value: 'workflow_urge',
};

export const workflowTimeoutMessageTypeOption: SelectOption = {
  color: 'orange',
  label: '超时提醒',
  value: 'workflow_timeout',
};

export const priorityOptions: SelectOption[] = [
  { color: 'default', label: '普通', value: 1 },
  { color: 'orange', label: '重要', value: 2 },
  { color: 'red', label: '紧急', value: 3 },
] as SelectOption[];

export const readStatusOptions: SelectOption[] = [
  { color: 'orange', label: '未读', value: 0 },
  { color: 'green', label: '已读', value: 1 },
] as SelectOption[];

export const sendStatusOptions: SelectOption[] = [
  { color: 'default', label: '草稿', value: 0 },
  { color: 'green', label: '已发送', value: 1 },
  { color: 'red', label: '已撤回', value: 2 },
] as SelectOption[];

export const actionTypeOptions: SelectOption[] = [
  { label: '内部页面', value: 1 },
  { label: '外部链接', value: 2 },
  { label: '待办任务', value: 3 },
  { label: '文件预览', value: 4 },
  { label: '自定义', value: 99 },
] as SelectOption[];

export const openTypeOptions: SelectOption[] = [
  { label: '当前页', value: 1 },
  { label: '新窗口', value: 2 },
] as SelectOption[];

export function getOptionLabel(
  options: readonly SelectOption[],
  value?: number,
) {
  return options.find((option) => option.value === value)?.label ?? '-';
}

export function getMessageTypeMeta(
  messageType?: number,
  bizType?: string,
): SelectOption {
  if (bizType === 'workflow_cc') {
    return workflowCcMessageTypeOption;
  }
  if (bizType === 'workflow_result') {
    return workflowResultMessageTypeOption;
  }
  if (bizType === 'workflow_withdraw') {
    return workflowWithdrawMessageTypeOption;
  }
  if (bizType === 'workflow_urge') {
    return workflowUrgeMessageTypeOption;
  }
  if (bizType === 'workflow_timeout') {
    return workflowTimeoutMessageTypeOption;
  }
  return (
    messageTypeOptions.find((option) => option.value === messageType) ?? {
      label: messageType === undefined || messageType === null ? '-' : String(messageType),
      value: messageType,
    }
  );
}

export function getMessageTypeLabel(messageType?: number, bizType?: string) {
  return getMessageTypeMeta(messageType, bizType).label;
}
