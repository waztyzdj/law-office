import type {
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { ComputedRef, Ref } from 'vue';
import type { LocationQueryRaw } from 'vue-router';
import type {
  WorkbenchCardOpenPayload,
  WorkbenchTodoTabKey,
} from '../types';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { message } from 'ant-design-vue';

interface UseWorkbenchCardNavigationOptions {
  card: ComputedRef<WorkbenchLayoutCard>;
  sourceCard?: ComputedRef<WorkbenchLayoutCard>;
  todoActiveTab: Ref<WorkbenchTodoTabKey>;
  onOpenMessageItem: (payload: WorkbenchCardOpenPayload) => void;
  onOpenWorkflowItem: (payload: WorkbenchCardOpenPayload) => void;
}

export function useWorkbenchCardNavigation(
  options: UseWorkbenchCardNavigationOptions,
) {
  const router = useRouter();
  const hasModuleAction = computed(() =>
    ['cc', 'favorite', 'message', 'todo'].includes(options.card.value.cardCode),
  );
  const moduleActionTitle = computed(() => {
    if (options.card.value.cardCode === 'todo') {
      return options.todoActiveTab.value === 'todo' ? '进入待办' : '进入已办';
    }
    if (options.card.value.cardCode === 'cc') {
      return '进入我的抄送';
    }
    if (options.card.value.cardCode === 'message') {
      return '进入我的消息';
    }
    if (options.card.value.cardCode === 'favorite') {
      return '进入文档中心';
    }
    return '进入模块';
  });

  function openItem(item: WorkbenchCardItem) {
    if (options.card.value.cardCode === 'favorite') {
      openCardModule();
      return;
    }
    const rawTargetPath = typeof item.targetPath === 'string' ? item.targetPath : '';
    if (!rawTargetPath) {
      message.warning('该事项暂未配置跳转路径');
      return;
    }
    if (options.card.value.cardCode === 'message') {
      options.onOpenMessageItem({ card: getPayloadCard(), item });
      return;
    }
    if (shouldOpenWorkflowDrawer(item)) {
      options.onOpenWorkflowItem({ card: getPayloadCard(), item });
      return;
    }
    if (item.targetType === 'link' || isExternalUrl(rawTargetPath)) {
      if (!isExternalUrl(rawTargetPath)) {
        message.warning('外部链接地址不合法');
        return;
      }
      window.open(rawTargetPath, '_blank', 'noopener,noreferrer');
      return;
    }
    const target = splitTargetPath(rawTargetPath);
    if (!target.path) {
      message.warning('该事项暂未配置跳转路径');
      return;
    }
    const query = buildRouteQuery(item, target.query);
    router.push({ path: target.path, query }).catch(() => {
      message.warning('跳转失败，请稍后重试');
    });
  }

  function openCardModule() {
    if (options.card.value.cardCode === 'todo') {
      const path =
        options.todoActiveTab.value === 'todo' ? '/workflow/todo' : '/workflow/done';
      pushRoute({ path });
      return;
    }
    if (options.card.value.cardCode === 'cc') {
      pushRoute({ path: '/workflow/cc' });
      return;
    }
    if (options.card.value.cardCode === 'message') {
      pushRoute({ name: 'MessageCenter', query: { tab: 'inbox' } });
      return;
    }
    if (options.card.value.cardCode === 'favorite') {
      pushRoute({ name: 'DocumentCenter', query: { scope: 'starred' } });
    }
  }

  function pushRoute(location: Parameters<typeof router.push>[0]) {
    router.push(location).catch(() => {
      message.warning('跳转失败，请稍后重试');
    });
  }

  function getPayloadCard() {
    return options.sourceCard?.value ?? options.card.value;
  }

  function shouldOpenWorkflowDrawer(item: WorkbenchCardItem) {
    if (options.card.value.cardCode === 'todo' || options.card.value.cardCode === 'cc') {
      return true;
    }
    return (
      item.type === 'todo' ||
      item.type === 'done' ||
      item.type === 'unread-cc' ||
      item.type === 'read-cc'
    );
  }

  function buildRouteQuery(item: WorkbenchCardItem, baseQuery: LocationQueryRaw) {
    const query: LocationQueryRaw = {
      ...baseQuery,
      ...parseTargetParams(item.targetParamsJson),
    };
    const instanceId = typeof item.instanceId === 'string' ? item.instanceId : '';
    if (instanceId && !query.instanceId) {
      query.instanceId = instanceId;
    }
    const path = typeof item.targetPath === 'string' ? item.targetPath : '';
    const shouldOpenTodoTask =
      options.card.value.cardCode === 'todo' &&
      item.type !== 'quick-entry' &&
      (path.split('?')[0] || '') === '/workflow/todo';
    if (shouldOpenTodoTask) {
      const taskId = item.bizId || item.id;
      if (taskId && !query.taskId) {
        query.taskId = taskId;
      }
    }
    return query;
  }

  return {
    hasModuleAction,
    moduleActionTitle,
    openCardModule,
    openItem,
  };
}

function parseTargetParams(value?: unknown): LocationQueryRaw {
  if (!value || typeof value !== 'string') {
    return {};
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    return Object.entries(parsed as Record<string, unknown>).reduce<LocationQueryRaw>(
      (query, [key, item]) => {
        if (item === undefined || item === null) {
          return query;
        }
        if (Array.isArray(item)) {
          query[key] = item
            .filter(
              (arrayItem) =>
                typeof arrayItem === 'boolean' ||
                typeof arrayItem === 'number' ||
                typeof arrayItem === 'string',
            )
            .map(String);
          return query;
        }
        if (
          typeof item === 'boolean' ||
          typeof item === 'number' ||
          typeof item === 'string'
        ) {
          query[key] = String(item);
        }
        return query;
      },
      {},
    );
  } catch {
    return {};
  }
}

function splitTargetPath(targetPath: string) {
  const [path = '', search = ''] = targetPath.split('?');
  const query = Object.fromEntries(new URLSearchParams(search).entries());
  return {
    path,
    query,
  };
}

function isExternalUrl(targetPath: string) {
  try {
    const parsed = new URL(targetPath);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}
