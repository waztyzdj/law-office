import type { WorkbenchLayoutCard } from '#/api/home/workbench';
import type { ComputedRef, Ref } from 'vue';
import type { WorkbenchCardAction } from '../types';

import { computed } from 'vue';

interface UseWorkbenchCardActionsOptions {
  card: ComputedRef<WorkbenchLayoutCard>;
  hasModuleAction: ComputedRef<boolean>;
  loading: ComputedRef<boolean>;
  moduleActionTitle: ComputedRef<string>;
  pageEditing: ComputedRef<boolean>;
  quickEntryEditMode: Ref<boolean>;
  onOpenModule: () => void;
  onQuickEntryAdd: () => void;
  onQuickEntryCancel: () => void;
  onQuickEntrySettings: () => void;
  onRefresh: () => void;
}

export function useWorkbenchCardActions(options: UseWorkbenchCardActionsOptions) {
  return computed<WorkbenchCardAction[]>(() => {
    if (options.pageEditing.value) {
      return [];
    }
    if (options.card.value.cardCode === 'quick-entry') {
      return [
        {
          icon: 'lucide:plus',
          key: 'quick-entry-add',
          onClick: options.onQuickEntryAdd,
          title: '添加快捷菜单',
        },
        ...(options.quickEntryEditMode.value
          ? [
              {
                icon: 'lucide:x',
                key: 'quick-entry-cancel',
                onClick: options.onQuickEntryCancel,
                title: '取消编辑',
              },
            ]
          : []),
        {
          icon: options.quickEntryEditMode.value ? 'lucide:check' : 'lucide:settings',
          key: 'quick-entry-settings',
          onClick: options.onQuickEntrySettings,
          title: options.quickEntryEditMode.value ? '保存快捷菜单' : '设置快捷菜单',
        },
      ];
    }

    const actions: WorkbenchCardAction[] = [
      {
        disabled: options.loading.value,
        icon: 'lucide:refresh-cw',
        key: 'refresh',
        onClick: options.onRefresh,
        title: '刷新',
      },
    ];
    if (options.hasModuleAction.value) {
      actions.push({
        icon: 'lucide:external-link',
        key: 'open-module',
        onClick: options.onOpenModule,
        title: options.moduleActionTitle.value,
      });
    }
    return actions;
  });
}
