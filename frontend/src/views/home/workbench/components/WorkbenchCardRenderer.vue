<script setup lang="ts">
import type {
  WorkbenchCardData,
  WorkbenchCardItem,
  WorkbenchLayoutCard,
} from '#/api/home/workbench';
import type { LocationQueryRaw } from 'vue-router';
import type { CSSProperties } from 'vue';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  Card,
  Empty,
  List,
  ListItem,
  Popconfirm,
  Skeleton,
  Tooltip,
  message,
} from 'ant-design-vue';

import { recordWorkbenchRecent } from '#/api/home/workbench';

import { getWorkbenchCardMeta } from '../registry';
import WorkbenchCardPagination from './WorkbenchCardPagination.vue';

const props = defineProps<{
  card: WorkbenchLayoutCard;
  data?: WorkbenchCardData;
  error?: string;
  editing?: boolean;
  loading?: boolean;
}>();

const emit = defineEmits<{
  openMessageItem: [payload: { card: WorkbenchLayoutCard; item: WorkbenchCardItem }];
  openWorkflowItem: [payload: { card: WorkbenchLayoutCard; item: WorkbenchCardItem }];
  quickEntryAdd: [];
  quickEntryEdit: [item: WorkbenchCardItem];
  quickEntrySortSave: [items: WorkbenchCardItem[]];
  refresh: [card: WorkbenchLayoutCard];
}>();

const router = useRouter();
const meta = computed(() =>
  getWorkbenchCardMeta(props.card.cardCode, props.card.componentKey),
);
const cardDisplayName = computed(() => {
  if (props.card.cardCode === 'todo') {
    return '\u6211\u7684\u5F85\u529E';
  }
  if (props.card.cardCode === 'cc') {
    return '\u6211\u7684\u6284\u9001';
  }
  if (props.card.cardCode === 'message') {
    return '\u6211\u7684\u6D88\u606F';
  }
  if (props.card.cardCode === 'recent') {
    return '\u5386\u53F2\u64CD\u4F5C';
  }
  return props.card.cardName;
});
const items = computed(() => props.data?.items ?? []);
const summary = computed(() => props.data?.summary ?? {});
const isCc = computed(() => props.card.cardCode === 'cc');
const isMessage = computed(() => props.card.cardCode === 'message');
const isMetrics = computed(() => props.card.cardCode === 'metrics');
const isQuickEntry = computed(() => props.card.cardCode === 'quick-entry');
const isTodo = computed(() => props.card.cardCode === 'todo');
const listPage = ref(1);
const quickEntryDraggingKey = ref('');
const quickEntryEditMode = ref(false);
const localQuickEntryItems = ref<WorkbenchCardItem[]>([]);
const ccActiveTab = ref<'read-cc' | 'unread-cc'>('unread-cc');
const messageActiveTab = ref<'read-message' | 'unread-message' | 'urge-message'>('unread-message');
const todoActiveTab = ref<'done' | 'todo'>('todo');
const listPageSize = computed(() => {
  const limit = readCardLimit();
  if (limit) {
    return limit;
  }
  return Math.max(1, Math.min(8, (props.card.gridH ?? 3) + 1));
});
const currentListItems = computed(() =>
  isTodo.value
    ? items.value.filter((item) => item.type === todoActiveTab.value)
    : isCc.value
      ? items.value.filter((item) => item.type === ccActiveTab.value)
      : isMessage.value
        ? items.value.filter((item) => item.type === messageActiveTab.value)
    : items.value,
);
const pagedItems = computed(() => {
  const start = (listPage.value - 1) * listPageSize.value;
  return currentListItems.value.slice(start, start + listPageSize.value);
});
const quickEntryItems = computed(() =>
  quickEntryEditMode.value ? localQuickEntryItems.value : items.value,
);
const todoTargetTitle = computed(() => (todoActiveTab.value === 'todo' ? '\u8FDB\u5165\u5F85\u529E' : '\u8FDB\u5165\u5DF2\u529E'));
const ccTargetTitle = computed(() => '\u8FDB\u5165\u6211\u7684\u6284\u9001');
const messageTargetTitle = computed(() => '\u8FDB\u5165\u6211\u7684\u6D88\u606F');
const todoTabs = computed(() => [
  { key: 'todo' as const, label: '\u5F85\u529E', total: Number(summary.value.todoTotal ?? 0) },
  { key: 'done' as const, label: '\u5DF2\u529E', total: Number(summary.value.doneTotal ?? 0) },
]);
const ccTabs = computed(() => [
  { key: 'unread-cc' as const, label: '\u672A\u8BFB', total: Number(summary.value.unreadTotal ?? 0) },
  { key: 'read-cc' as const, label: '\u5DF2\u8BFB', total: Number(summary.value.readTotal ?? 0) },
]);
const hasUrgeMessages = computed(() => Number(summary.value.urgeTotal ?? 0) > 0);
const messageTabs = computed(() => {
  const tabs = [
    { key: 'unread-message' as const, label: '\u672A\u8BFB', total: Number(summary.value.unreadTotal ?? 0) },
    { key: 'read-message' as const, label: '\u5DF2\u8BFB', total: Number(summary.value.readTotal ?? 0) },
  ];
  if (hasUrgeMessages.value) {
    return [
      { key: 'urge-message' as const, label: '\u50AC\u529E', total: Number(summary.value.urgeTotal ?? 0) },
      ...tabs,
    ];
  }
  return tabs;
});
const hasPagination = computed(() => currentListItems.value.length > listPageSize.value);
const showListFooter = computed(() =>
  !isMetrics.value &&
  !isQuickEntry.value &&
  hasPagination.value,
);
type MetricToneStyle = { color: string; icon: string };

const defaultMetricTone: MetricToneStyle = { color: '#2563eb', icon: 'lucide:check-square' };
const metricToneStyles: Record<string, MetricToneStyle> = {
  blue: defaultMetricTone,
  indigo: { color: '#4f46e5', icon: 'lucide:check-check' },
  cyan: { color: '#0891b2', icon: 'lucide:send' },
  orange: { color: '#ea580c', icon: 'lucide:bell' },
};
const allowedRecentRecordTypes = ['business', 'document', 'menu', 'message', 'workflow'];

watch(
  () => [
    currentListItems.value.length,
    listPageSize.value,
    props.card.cardCode,
    ccActiveTab.value,
    messageActiveTab.value,
    todoActiveTab.value,
  ],
  () => {
    const maxPage = Math.max(1, Math.ceil(currentListItems.value.length / listPageSize.value));
    if (listPage.value > maxPage) {
      listPage.value = maxPage;
    }
  },
);

watch(
  () => [hasUrgeMessages.value, messageActiveTab.value],
  ([nextHasUrge, activeTab]) => {
    if (!nextHasUrge && activeTab === 'urge-message') {
      messageActiveTab.value = 'unread-message';
      listPage.value = 1;
    }
  },
  { immediate: true },
);

watch(
  items,
  (nextItems) => {
    if (isQuickEntry.value && !quickEntryEditMode.value) {
      localQuickEntryItems.value = nextItems.map((item) => ({ ...item }));
    }
  },
  { immediate: true },
);

function handleTodoTabChange(tabKey: 'done' | 'todo') {
  todoActiveTab.value = tabKey;
  listPage.value = 1;
}

function handleCcTabChange(tabKey: 'read-cc' | 'unread-cc') {
  ccActiveTab.value = tabKey;
  listPage.value = 1;
}

function handleMessageTabChange(tabKey: 'read-message' | 'unread-message' | 'urge-message') {
  messageActiveTab.value = tabKey;
  listPage.value = 1;
}

function readCardLimit() {
  const config = props.card.config;
  const rawLimit =
    config && typeof config === 'object' && !Array.isArray(config)
      ? config.limit
      : undefined;
  if (typeof rawLimit === 'number' && Number.isFinite(rawLimit) && rawLimit > 0) {
    return Math.min(99, Math.floor(rawLimit));
  }
  if (typeof rawLimit === 'string') {
    const parsedLimit = Number(rawLimit);
    if (Number.isFinite(parsedLimit) && parsedLimit > 0) {
      return Math.min(99, Math.floor(parsedLimit));
    }
  }
  const configJson = props.card.configJson;
  if (!configJson) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(configJson) as { limit?: unknown };
    const parsedLimit = Number(parsed.limit);
    return Number.isFinite(parsedLimit) && parsedLimit > 0
      ? Math.min(99, Math.floor(parsedLimit))
      : undefined;
  } catch {
    return undefined;
  }
}

function formatTime(value?: unknown) {
  if (!value || typeof value !== 'string') {
    return '';
  }
  return value.replace('T', ' ').slice(0, 16);
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
    (props.card.cardCode === 'todo' || props.card.cardCode === 'risk') &&
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

function resolveRecentRecordType(item: WorkbenchCardItem) {
  const type = String(item.type || '');
  if (allowedRecentRecordTypes.includes(type)) {
    return type;
  }
  if (props.card.cardCode === 'quick-entry') {
    return item.targetType === 'menu' ? 'menu' : 'business';
  }
  if (props.card.cardCode === 'message') {
    return 'message';
  }
  if (props.card.cardCode === 'todo' || props.card.cardCode === 'risk') {
    return type === 'message' ? 'message' : 'workflow';
  }
  if (props.card.cardCode === 'cc') {
    return 'workflow';
  }
  return 'business';
}

function stringifyTargetParams(query: LocationQueryRaw) {
  const params = Object.entries(query).reduce<Record<string, string | string[]>>(
    (result, [key, value]) => {
      if (value === undefined || value === null) {
        return result;
      }
      if (Array.isArray(value)) {
        const normalized = value
          .filter(
            (item) =>
              typeof item === 'boolean' ||
              typeof item === 'number' ||
              typeof item === 'string',
          )
          .map(String);
        if (normalized.length > 0) {
          result[key] = normalized;
        }
        return result;
      }
      if (
        typeof value === 'boolean' ||
        typeof value === 'number' ||
        typeof value === 'string'
      ) {
        result[key] = String(value);
      }
      return result;
    },
    {},
  );
  return Object.keys(params).length > 0 ? JSON.stringify(params) : undefined;
}

function recordRecentVisit(
  item: WorkbenchCardItem,
  targetPath: string,
  query: LocationQueryRaw,
) {
  const title = typeof item.title === 'string' ? item.title.trim() : '';
  if (!title) {
    return;
  }
  const targetType = item.targetType === 'action' ? 'action' : 'route';
  const targetParamsJson = stringifyTargetParams(query);
  void recordWorkbenchRecent({
    bizId: String(item.bizId || item.id || targetPath || title),
    moduleCode: props.card.cardCode,
    recordType: resolveRecentRecordType(item),
    sourceTime: typeof item.occurTime === 'string' ? item.occurTime : undefined,
    targetParamsJson,
    targetPath,
    targetType,
    title,
  }).catch(() => {
    // Ignore recent-record failures so opening the target stays responsive.
  });
}
function handleOpen(item: WorkbenchCardItem) {
  const rawTargetPath = typeof item.targetPath === 'string' ? item.targetPath : '';
  if (!rawTargetPath) {
    message.warning('\u8BE5\u4E8B\u9879\u6682\u672A\u914D\u7F6E\u8DF3\u8F6C\u8DEF\u5F84');
    return;
  }
  if (props.card.cardCode === 'message') {
    recordRecentVisit(item, rawTargetPath.split('?')[0] || rawTargetPath, {});
    emit('openMessageItem', { card: props.card, item });
    return;
  }
  if (shouldOpenWorkflowDrawer(item, rawTargetPath)) {
    recordRecentVisit(item, rawTargetPath.split('?')[0] || rawTargetPath, {});
    emit('openWorkflowItem', { card: props.card, item });
    return;
  }
  if (item.targetType === 'link' || isExternalUrl(rawTargetPath)) {
    if (!isExternalUrl(rawTargetPath)) {
      message.warning('\u5916\u90E8\u94FE\u63A5\u5730\u5740\u4E0D\u5408\u6CD5');
      return;
    }
    recordRecentVisit(item, rawTargetPath, {});
    window.open(rawTargetPath, '_blank', 'noopener,noreferrer');
    return;
  }
  const target = splitTargetPath(rawTargetPath);
  if (!target.path) {
    message.warning('\u8BE5\u4E8B\u9879\u6682\u672A\u914D\u7F6E\u8DF3\u8F6C\u8DEF\u5F84');
    return;
  }
  const query = buildRouteQuery(item, target.query);
  recordRecentVisit(item, target.path, query);
  router.push({ path: target.path, query }).catch(() => {
    message.warning('\u8DF3\u8F6C\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5');
  });
}

function shouldOpenWorkflowDrawer(item: WorkbenchCardItem, targetPath: string) {
  const path = targetPath.split('?')[0] || '';
  if (props.card.cardCode === 'todo' || props.card.cardCode === 'cc') {
    return true;
  }
  if (props.card.cardCode === 'risk') {
    return path === '/workflow/todo' || path === '/workflow/done';
  }
  return item.type === 'todo' || item.type === 'done' || item.type === 'unread-cc' || item.type === 'read-cc';
}

function handleRefresh() {
  emit('refresh', props.card);
}

function handleQuickEntrySettings() {
  if (!quickEntryEditMode.value) {
    localQuickEntryItems.value = items.value.map((item) => ({ ...item }));
    quickEntryEditMode.value = true;
    return;
  }
  quickEntryEditMode.value = false;
  quickEntryDraggingKey.value = '';
  emit('quickEntrySortSave', localQuickEntryItems.value);
}

function handleQuickEntryCancel() {
  localQuickEntryItems.value = items.value.map((item) => ({ ...item }));
  quickEntryEditMode.value = false;
  quickEntryDraggingKey.value = '';
}

function handleQuickEntryAdd() {
  emit('quickEntryAdd');
}

function getQuickEntryKey(item: WorkbenchCardItem) {
  return String(item.id || item.entryCode || item.title || item.targetPath || '');
}

function canManageQuickEntry(item: WorkbenchCardItem) {
  return item.ownerType === 'user' && Boolean(item.id);
}

function handleQuickEntryItemClick(item: WorkbenchCardItem) {
  if (quickEntryEditMode.value) {
    return;
  }
  handleOpen(item);
}

function handleQuickEntryEdit(item: WorkbenchCardItem) {
  if (!canManageQuickEntry(item)) {
    return;
  }
  emit('quickEntryEdit', item);
}

function handleQuickEntryDelete(item: WorkbenchCardItem) {
  if (!canManageQuickEntry(item)) {
    return;
  }
  const targetKey = getQuickEntryKey(item);
  localQuickEntryItems.value = localQuickEntryItems.value.filter(
    (candidate) => getQuickEntryKey(candidate) !== targetKey,
  );
  if (quickEntryDraggingKey.value === targetKey) {
    quickEntryDraggingKey.value = '';
  }
}

function handleQuickEntryDragStart(event: DragEvent, item: WorkbenchCardItem) {
  if (!quickEntryEditMode.value || !canManageQuickEntry(item)) {
    event.preventDefault();
    return;
  }
  const itemKey = getQuickEntryKey(item);
  quickEntryDraggingKey.value = itemKey;
  event.dataTransfer?.setData('text/plain', itemKey);
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
  }
}

function handleQuickEntryDragOver(event: DragEvent, item: WorkbenchCardItem) {
  if (!quickEntryEditMode.value || !quickEntryDraggingKey.value) {
    return;
  }
  if (getQuickEntryKey(item) === quickEntryDraggingKey.value) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move';
  }
}

function handleQuickEntryDrop(event: DragEvent, targetItem: WorkbenchCardItem) {
  event.preventDefault();
  const sourceKey = quickEntryDraggingKey.value || event.dataTransfer?.getData('text/plain') || '';
  const targetKey = getQuickEntryKey(targetItem);
  quickEntryDraggingKey.value = '';
  if (!sourceKey || !targetKey || sourceKey === targetKey) {
    return;
  }
  const sourceIndex = localQuickEntryItems.value.findIndex(
    (item) => getQuickEntryKey(item) === sourceKey,
  );
  const targetIndex = localQuickEntryItems.value.findIndex(
    (item) => getQuickEntryKey(item) === targetKey,
  );
  if (sourceIndex < 0 || targetIndex < 0) {
    return;
  }
  const nextItems = [...localQuickEntryItems.value];
  const [sourceItem] = nextItems.splice(sourceIndex, 1);
  if (!sourceItem) {
    return;
  }
  nextItems.splice(targetIndex, 0, sourceItem);
  localQuickEntryItems.value = nextItems;
}

function handleQuickEntryDragEnd() {
  quickEntryDraggingKey.value = '';
}

function handleOpenTodoModule() {
  const path = todoActiveTab.value === 'todo' ? '/workflow/todo' : '/workflow/done';
  router.push({ path }).catch(() => {
    message.warning('\u8DF3\u8F6C\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5');
  });
}

function handleOpenCcModule() {
  router.push({ path: '/workflow/cc' }).catch(() => {
    message.warning('\u8DF3\u8F6C\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5');
  });
}

function handleOpenMessageModule() {
  router.push({ name: 'MessageCenter', query: { tab: 'inbox' } }).catch(() => {
    message.warning('\u8DF3\u8F6C\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5');
  });
}

function getItemConfig(item: WorkbenchCardItem): Record<string, unknown> {
  const config = item.config;
  if (config && typeof config === 'object' && !Array.isArray(config)) {
    return config as Record<string, unknown>;
  }
  const configJson = item.configJson;
  if (typeof configJson !== 'string' || !configJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(configJson) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function getQuickEntryColor(item: WorkbenchCardItem) {
  const color = getItemConfig(item).color;
  return typeof color === 'string' && color ? color : '#2563eb';
}

function getQuickEntryStyle(item: WorkbenchCardItem): CSSProperties {
  return {
    '--quick-entry-color': getQuickEntryColor(item),
  } as CSSProperties;
}

function getMetricTone(item: WorkbenchCardItem) {
  const tone = typeof item.tone === 'string' ? item.tone : '';
  return metricToneStyles[tone] ?? defaultMetricTone;
}

function getMetricIcon(item: WorkbenchCardItem) {
  return typeof item.icon === 'string' && item.icon ? item.icon : getMetricTone(item).icon;
}

function getMetricStyle(item: WorkbenchCardItem): CSSProperties {
  return {
    '--metric-color': getMetricTone(item).color,
  } as CSSProperties;
}
</script>

<template>
  <Card
    :bordered="false"
    :class="['workbench-card', `workbench-card--${meta.tone}`]"
    :body-style="{ padding: '16px' }"
  >
    <template #title>
      <div class="workbench-card__title">
        <span class="workbench-card__title-main">
          <span class="workbench-card__icon" :style="{ color: meta.accent }">
            <IconifyIcon :icon="meta.icon" />
          </span>
          <span>{{ cardDisplayName }}</span>
        </span>
        <span v-if="isTodo" class="workbench-card__title-tabs">
          <button
            v-for="tab in todoTabs"
            :key="tab.key"
            class="workbench-card__todo-tab"
            :class="{ 'workbench-card__todo-tab--active': todoActiveTab === tab.key }"
            type="button"
            @click.stop="handleTodoTabChange(tab.key)"
          >
            <span>{{ tab.label }}</span>
            <em>{{ tab.total }}</em>
          </button>
        </span>
        <span v-else-if="isCc" class="workbench-card__title-tabs">
          <button
            v-for="tab in ccTabs"
            :key="tab.key"
            class="workbench-card__todo-tab"
            :class="{ 'workbench-card__todo-tab--active': ccActiveTab === tab.key }"
            type="button"
            @click.stop="handleCcTabChange(tab.key)"
          >
            <span>{{ tab.label }}</span>
            <em>{{ tab.total }}</em>
          </button>
        </span>
        <span v-else-if="isMessage" class="workbench-card__title-tabs">
          <button
            v-for="tab in messageTabs"
            :key="tab.key"
            class="workbench-card__todo-tab"
            :class="{ 'workbench-card__todo-tab--active': messageActiveTab === tab.key }"
            type="button"
            @click.stop="handleMessageTabChange(tab.key)"
          >
            <span>{{ tab.label }}</span>
            <em>{{ tab.total }}</em>
          </button>
        </span>
      </div>
    </template>
    <template #extra>
      <div v-if="!editing" class="workbench-card__actions">
        <Tooltip v-if="isQuickEntry" title="添加快捷菜单">
          <Button
            size="small"
            type="text"
            @click="handleQuickEntryAdd"
          >
            <IconifyIcon icon="lucide:plus" />
          </Button>
        </Tooltip>
        <Tooltip v-if="isQuickEntry && quickEntryEditMode" title="\u53D6\u6D88\u7F16\u8F91">
          <Button
            size="small"
            type="text"
            @click="handleQuickEntryCancel"
          >
            <IconifyIcon icon="lucide:x" />
          </Button>
        </Tooltip>
        <Tooltip v-if="isQuickEntry" :title="quickEntryEditMode ? '\u4FDD\u5B58\u5FEB\u6377\u83DC\u5355' : '\u8BBE\u7F6E\u5FEB\u6377\u83DC\u5355'">
          <Button
            size="small"
            type="text"
            @click="handleQuickEntrySettings"
          >
            <IconifyIcon :icon="quickEntryEditMode ? 'lucide:check' : 'lucide:settings'" />
          </Button>
        </Tooltip>
        <Tooltip v-if="isTodo" :title="todoTargetTitle">
          <Button
            size="small"
            type="text"
            @click="handleOpenTodoModule"
          >
            <IconifyIcon icon="lucide:external-link" />
          </Button>
        </Tooltip>
        <Tooltip v-if="isCc" :title="ccTargetTitle">
          <Button
            size="small"
            type="text"
            @click="handleOpenCcModule"
          >
            <IconifyIcon icon="lucide:external-link" />
          </Button>
        </Tooltip>
        <Tooltip v-if="isMessage" :title="messageTargetTitle">
          <Button
            size="small"
            type="text"
            @click="handleOpenMessageModule"
          >
            <IconifyIcon icon="lucide:external-link" />
          </Button>
        </Tooltip>
        <Tooltip v-if="!isQuickEntry" title="\u5237\u65B0">
          <Button
            :disabled="loading"
            size="small"
            type="text"
            @click="handleRefresh"
          >
            <IconifyIcon icon="lucide:refresh-cw" />
          </Button>
        </Tooltip>
      </div>
    </template>

    <Skeleton v-if="loading" :paragraph="{ rows: 4 }" active />
    <Alert
      v-else-if="error"
      :message="error"
      show-icon
      type="error"
    />
    <template v-else>
      <div v-if="isMetrics" class="workbench-card__metrics">
        <button
          v-for="item in items"
          :key="String(item.id || item.title)"
          class="workbench-card__metric"
          :style="getMetricStyle(item)"
          type="button"
          @click="handleOpen(item)"
        >
          <span class="workbench-card__metric-top">
            <span class="workbench-card__metric-title">
              <span class="workbench-card__metric-title-icon">
                <IconifyIcon :icon="getMetricIcon(item)" />
              </span>
              <span class="workbench-card__metric-title-text">{{ item.title }}</span>
            </span>
          </span>
          <span class="workbench-card__metric-value">
            {{ item.value ?? 0 }}
          </span>
        </button>
      </div>

      <div v-else-if="isQuickEntry" class="workbench-card__quick">
        <div
          v-for="item in quickEntryItems"
          :key="String(item.id || item.title)"
          :class="[
            'workbench-card__quick-item',
            {
              'workbench-card__quick-item--editing': quickEntryEditMode,
              'workbench-card__quick-item--dragging': quickEntryDraggingKey === getQuickEntryKey(item),
            },
          ]"
          :draggable="quickEntryEditMode && canManageQuickEntry(item)"
          :style="getQuickEntryStyle(item)"
          @dragend="handleQuickEntryDragEnd"
          @dragover="handleQuickEntryDragOver($event, item)"
          @dragstart="handleQuickEntryDragStart($event, item)"
          @drop="handleQuickEntryDrop($event, item)"
        >
          <div
            class="workbench-card__quick-main"
            role="button"
            tabindex="0"
            @click="handleQuickEntryItemClick(item)"
          >
            <span class="workbench-card__quick-icon">
              <IconifyIcon :icon="String(item.icon || 'lucide:circle-dot')" />
              <span
                v-if="quickEntryEditMode && canManageQuickEntry(item)"
                class="workbench-card__quick-edit-actions"
              >
                <button
                  class="workbench-card__quick-action"
                  title="\u7F16\u8F91\u5FEB\u6377\u83DC\u5355"
                  type="button"
                  @click.stop="handleQuickEntryEdit(item)"
                >
                  <IconifyIcon icon="lucide:pencil" />
                </button>
                <Popconfirm
                  title="\u786E\u8BA4\u5220\u9664\u8FD9\u4E2A\u5FEB\u6377\u83DC\u5355\uFF1F"
                  @confirm="handleQuickEntryDelete(item)"
                >
                  <button
                    class="workbench-card__quick-action workbench-card__quick-action--danger"
                    title="\u5220\u9664\u5FEB\u6377\u83DC\u5355"
                    type="button"
                    @click.stop
                  >
                    <IconifyIcon icon="lucide:trash-2" />
                  </button>
                </Popconfirm>
              </span>
            </span>
            <span class="workbench-card__quick-title">{{ item.title }}</span>
          </div>
        </div>
        <button
          class="workbench-card__quick-item workbench-card__quick-item--add"
          type="button"
          @click="handleQuickEntryAdd"
        >
          <span class="workbench-card__quick-icon workbench-card__quick-icon--add">
            <IconifyIcon icon="lucide:plus" />
          </span>
          <span class="workbench-card__quick-title">添加</span>
        </button>
      </div>

      <div v-else-if="isTodo" class="workbench-card__todo">
        <List
          v-if="currentListItems.length > 0"
          :data-source="pagedItems"
          class="workbench-card__list"
          size="small"
        >
          <template #renderItem="{ item }">
            <ListItem class="workbench-card__item" @click="handleOpen(item)">
              <span class="workbench-card__item-title">
                {{ item.title || '\u672A\u547D\u540D\u4E8B\u9879' }}
              </span>
              <span class="workbench-card__item-time">
                {{ formatTime(item.occurTime) || '-' }}
              </span>
            </ListItem>
          </template>
        </List>
        <Empty
          v-else
          class="workbench-card__empty"
          :description="`${todoActiveTab === 'todo' ? '\u6211\u7684\u5F85\u529E' : '\u6211\u7684\u5DF2\u529E'}\u6682\u65E0\u6570\u636E`"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
      </div>

      <div v-else-if="isCc" class="workbench-card__todo">
        <List
          v-if="currentListItems.length > 0"
          :data-source="pagedItems"
          class="workbench-card__list"
          size="small"
        >
          <template #renderItem="{ item }">
            <ListItem class="workbench-card__item" @click="handleOpen(item)">
              <span class="workbench-card__item-title">
                {{ item.title || '\u672A\u547D\u540D\u4E8B\u9879' }}
              </span>
              <span class="workbench-card__item-time">
                {{ formatTime(item.occurTime) || '-' }}
              </span>
            </ListItem>
          </template>
        </List>
        <Empty
          v-else
          class="workbench-card__empty"
          :description="`${ccActiveTab === 'unread-cc' ? '\u672A\u8BFB\u6284\u9001' : '\u5DF2\u8BFB\u6284\u9001'}\u6682\u65E0\u6570\u636E`"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
      </div>

      <div v-else-if="isMessage" class="workbench-card__todo">
        <List
          v-if="currentListItems.length > 0"
          :data-source="pagedItems"
          class="workbench-card__list"
          size="small"
        >
          <template #renderItem="{ item }">
            <ListItem class="workbench-card__item" @click="handleOpen(item)">
              <span class="workbench-card__item-title">
                {{ item.title || '\u672A\u547D\u540D\u6D88\u606F' }}
              </span>
              <span class="workbench-card__item-time">
                {{ formatTime(item.occurTime) || '-' }}
              </span>
            </ListItem>
          </template>
        </List>
        <Empty
          v-else
          class="workbench-card__empty"
          :description="`${messageActiveTab === 'unread-message' ? '\u672A\u8BFB\u6D88\u606F' : messageActiveTab === 'urge-message' ? '\u50AC\u529E\u6D88\u606F' : '\u5DF2\u8BFB\u6D88\u606F'}\u6682\u65E0\u6570\u636E`"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
      </div>

      <List
        v-else-if="currentListItems.length > 0"
        :data-source="pagedItems"
        class="workbench-card__list"
        size="small"
      >
        <template #renderItem="{ item }">
          <ListItem class="workbench-card__item" @click="handleOpen(item)">
            <span class="workbench-card__item-title">
              {{ item.title || '\u672A\u547D\u540D\u4E8B\u9879' }}
            </span>
            <span class="workbench-card__item-time">
              {{ formatTime(item.occurTime) || '-' }}
            </span>
          </ListItem>
        </template>
      </List>

      <Empty
        v-else
        class="workbench-card__empty"
        :description="`${cardDisplayName}\u6682\u65E0\u6570\u636E`"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
      />

      <div
        v-if="showListFooter"
        class="workbench-card__list-footer"
      >
        <WorkbenchCardPagination
          v-model:current="listPage"
          :page-size="listPageSize"
          :total="currentListItems.length"
        />
      </div>
    </template>
  </Card>
</template>

<style scoped>
.workbench-card {
  height: 100%;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 16px;
  box-shadow: 0 10px 30px rgb(15 23 42 / 6%);
}

.workbench-card :deep(.ant-card-head) {
  min-height: 52px;
  padding: 0 16px;
}

.workbench-card :deep(.ant-card-head-title),
.workbench-card :deep(.ant-card-extra) {
  display: flex;
  min-height: 52px;
  align-items: center;
  padding: 0;
}

.workbench-card :deep(.ant-card-body) {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.workbench-card__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  font-weight: 650;
}

.workbench-card__title-main {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
}

.workbench-card__title-tabs {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  background: hsl(var(--muted));
  padding: 3px;
}

.workbench-card__actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.workbench-card__actions :deep(.ant-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.workbench-card__icon {
  display: inline-flex;
  font-size: 18px;
}

.workbench-card__list {
  min-height: 0;
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
  scrollbar-gutter: stable;
}

.workbench-card__list::-webkit-scrollbar,
.workbench-card__quick::-webkit-scrollbar {
  width: 6px;
}

.workbench-card__list::-webkit-scrollbar-thumb,
.workbench-card__quick::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: hsl(var(--muted-foreground) / 28%);
}

.workbench-card__list::-webkit-scrollbar-track,
.workbench-card__quick::-webkit-scrollbar-track {
  background: transparent;
}

.workbench-card__todo {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
}

.workbench-card__todo-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  padding: 4px 10px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.workbench-card__todo-tab--active {
  background: hsl(var(--background));
  color: hsl(var(--foreground));
  box-shadow: 0 4px 12px rgb(15 23 42 / 8%);
}

.workbench-card__todo-tab em {
  min-width: 18px;
  border-radius: 999px;
  background: hsl(var(--accent));
  font-style: normal;
  line-height: 1.4;
  padding: 0 6px;
  text-align: center;
}

.workbench-card__item {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  border-radius: 10px;
  padding: 7px 10px !important;
  transition: background-color 0.2s ease;
}

.workbench-card__item:hover {
  background: hsl(var(--accent));
}

.workbench-card__item-title {
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--foreground));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-card__item-time {
  flex: 0 0 auto;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  white-space: nowrap;
}

.workbench-card__empty {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}

.workbench-card__metrics {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  align-content: stretch;
}

.workbench-card__metric,
.workbench-card__quick-item {
  border: 0;
  cursor: pointer;
  text-align: left;
}

.workbench-card__metric {
  position: relative;
  display: flex;
  min-height: 78px;
  align-items: stretch;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 12px;
  background: color-mix(in srgb, var(--metric-color) 6%, white);
  padding: 14px 14px 14px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.workbench-card__metric:hover {
  border-color: color-mix(in srgb, var(--metric-color) 26%, hsl(var(--border)));
  background: color-mix(in srgb, var(--metric-color) 9%, white);
  box-shadow: 0 10px 22px color-mix(in srgb, var(--metric-color) 8%, transparent);
  transform: translateY(-1px);
}

.workbench-card__metric-top {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: center;
}

.workbench-card__metric-title {
  display: inline-flex;
  min-width: 0;
  max-width: 100%;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
  color: hsl(var(--muted-foreground));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-card__metric-title::before {
  width: 4px;
  height: 16px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--metric-color);
  content: '';
}

.workbench-card__metric-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  color: color-mix(in srgb, var(--metric-color) 72%, transparent);
  font-size: 14px;
  opacity: 0.65;
}

.workbench-card__metric-title-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-card__metric-value {
  display: inline-flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  color: hsl(var(--foreground));
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  text-align: center;
}

@media (max-width: 640px) {
  .workbench-card__metrics {
    grid-template-columns: 1fr;
  }
}

.workbench-card__quick {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
  gap: 10px 12px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.workbench-card__quick-item {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  background: transparent;
  padding: 0;
  transition:
    color 0.2s ease,
    transform 0.2s ease;
}

.workbench-card__quick-main {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.workbench-card__quick-item:hover {
  color: var(--quick-entry-color);
  transform: translateY(-1px);
}

.workbench-card__quick-item--editing {
  cursor: grab;
}

.workbench-card__quick-item--editing .workbench-card__quick-icon {
  outline: 1px dashed hsl(var(--primary) / 32%);
  outline-offset: 4px;
}

.workbench-card__quick-item--editing:active {
  cursor: grabbing;
}

.workbench-card__quick-item--dragging {
  opacity: 0.45;
  transform: scale(0.96);
}

.workbench-card__quick-item--add {
  color: hsl(var(--muted-foreground));
}

.workbench-card__quick-item--add:hover {
  color: hsl(var(--primary));
}

.workbench-card__quick-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  flex: 0 0 auto;
  border: 1px solid color-mix(in srgb, var(--quick-entry-color) 24%, white);
  border-radius: 14px;
  background:
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--quick-entry-color) 88%, white),
      color-mix(in srgb, var(--quick-entry-color) 62%, transparent)
    );
  box-shadow: 0 10px 20px color-mix(in srgb, var(--quick-entry-color) 18%, transparent);
  color: white;
  font-size: 22px;
}

.workbench-card__quick-edit-actions {
  position: absolute;
  right: 4px;
  bottom: 4px;
  left: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.workbench-card__quick-action {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 999px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 4px 10px rgb(15 23 42 / 14%);
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 11px;
  opacity: 0.78;
  padding: 0;
  transition:
    background-color 0.16s ease,
    color 0.16s ease,
    opacity 0.16s ease,
    transform 0.16s ease;
}

.workbench-card__quick-action:hover,
.workbench-card__quick-action:focus-visible {
  background: hsl(var(--background));
  color: hsl(var(--primary));
  opacity: 1;
  transform: translateY(-1px);
}

.workbench-card__quick-action--danger:hover,
.workbench-card__quick-action--danger:focus-visible {
  color: rgb(220 38 38);
}

.workbench-card__quick-icon--add {
  border: 1px dashed hsl(var(--primary) / 38%);
  background: hsl(var(--primary) / 8%);
  box-shadow: none;
  color: hsl(var(--primary));
}

.workbench-card__quick-title {
  display: block;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--foreground));
  line-height: 20px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-card__list-footer {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 5px;
  transform: translateY(1px);
}
</style>
