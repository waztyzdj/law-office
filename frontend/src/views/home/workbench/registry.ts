import type { WorkbenchCardCode } from '#/api/home/workbench';

interface WorkbenchCardMeta {
  accent: string;
  code: WorkbenchCardCode;
  componentKey: string;
  icon: string;
  tone: string;
}

export const workbenchCardRegistry: WorkbenchCardMeta[] = [
  {
    accent: '#2563eb',
    code: 'todo',
    componentKey: 'WorkbenchTodoCard',
    icon: 'lucide:check-square',
    tone: 'blue',
  },
  {
    accent: '#0891b2',
    code: 'cc',
    componentKey: 'WorkbenchCcCard',
    icon: 'lucide:send',
    tone: 'cyan',
  },
  {
    accent: '#0f766e',
    code: 'quick-entry',
    componentKey: 'WorkbenchQuickEntryCard',
    icon: 'lucide:rocket',
    tone: 'teal',
  },
  {
    accent: '#ea580c',
    code: 'message',
    componentKey: 'WorkbenchMessageCard',
    icon: 'lucide:bell',
    tone: 'orange',
  },
  {
    accent: '#4f46e5',
    code: 'recent',
    componentKey: 'WorkbenchRecentCard',
    icon: 'lucide:history',
    tone: 'indigo',
  },
  {
    accent: '#15803d',
    code: 'metrics',
    componentKey: 'WorkbenchMetricsCard',
    icon: 'lucide:activity',
    tone: 'green',
  },
  {
    accent: '#dc2626',
    code: 'risk',
    componentKey: 'WorkbenchRiskCard',
    icon: 'lucide:shield-alert',
    tone: 'red',
  },
];

export function getWorkbenchCardMeta(cardCode?: string, componentKey?: string) {
  return (
    workbenchCardRegistry.find(
      (item) => item.code === cardCode || item.componentKey === componentKey,
    ) ?? {
      accent: '#475569',
      code: cardCode as WorkbenchCardCode,
      componentKey: componentKey || '',
      icon: 'lucide:layout-template',
      tone: 'slate',
    }
  );
}
