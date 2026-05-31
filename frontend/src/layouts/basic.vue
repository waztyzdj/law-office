<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';
import type { HistoryState, LocationQueryRaw } from 'vue-router';

import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal, useVbenModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import {
  BasicLayout,
  LockScreen,
  LockScreenModal,
  Notification,
} from '@vben/layouts';
import { Inbox, LockKeyhole, LogOut, UserRoundPen } from '@vben/icons';
import { preferences, usePreferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import {
  Avatar,
  Button,
  Dropdown,
  Menu,
  notification,
  Tag,
} from 'ant-design-vue';

import {
  clearMessageNotifications,
  markMessageNotificationRead,
  pageMessageNotifications,
} from '#/api/message/message';
import type { TenantInfo } from '#/api/system/tenant';
import { listCurrentUserTenants } from '#/api/system/user';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';
import MessageDetailDrawer from '#/views/message/components/MessageDetailDrawer.vue';

const NOTIFICATION_PAGE_SIZE = 5;
const NOTIFICATION_POLL_INTERVAL = 30_000;
const MESSAGE_NOTIFICATION_UPDATED_EVENT =
  'lawoffice:message-notifications-updated';
const MESSAGE_NOTIFICATION_REFRESH_EVENT =
  'lawoffice:message-notifications-refresh';

const notifications = ref<NotificationItem[]>([]);

const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();
const { isDark } = usePreferences();
const tenantOptions = ref<TenantInfo[]>([]);
const currentTenantId = ref('');
const messageDetailDrawerRef = ref<InstanceType<typeof MessageDetailDrawer>>();
const tenantLoading = ref(false);
const userMenuOpen = ref(false);
const showDot = computed(() =>
  notifications.value.some((item) => !item.isRead),
);
let knownNotificationIds = new Set<string>();
let notificationInitialized = false;
let notificationLoading = false;
let notificationPollTimer: ReturnType<typeof setInterval> | undefined;

const [LockModal, lockModalApi] = useVbenModal({
  connectedComponent: LockScreenModal,
});

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

const currentUserInfo = computed(
  () =>
    userStore.userInfo as
      | (NonNullable<typeof userStore.userInfo> & {
          tenantId?: string;
          tenantName?: string;
        })
      | null,
);

const currentTenant = computed(() =>
  tenantOptions.value.find((tenant) => tenant.id === currentTenantId.value),
);

const showTenantSwitcher = computed(() => tenantOptions.value.length > 1);

const currentTenantName = computed(
  () =>
    currentTenant.value?.name ||
    currentUserInfo.value?.tenantName ||
    currentTenantId.value ||
    '当前租户',
);

async function loadTenantOptions() {
  if (!accessStore.accessToken) {
    tenantOptions.value = [];
    currentTenantId.value = '';
    return;
  }

  try {
    const tenants = await listCurrentUserTenants();
    tenantOptions.value = tenants || [];
    const userTenantId = currentUserInfo.value?.tenantId;
    currentTenantId.value =
      tenantOptions.value.find((tenant) => tenant.id === userTenantId)?.id ||
      userTenantId ||
      tenantOptions.value[0]?.id ||
      '';
  } catch {
    tenantOptions.value = [];
  }
}

async function handleSwitchTenant(tenantId?: string) {
  if (!tenantId || tenantId === currentTenantId.value || tenantLoading.value) {
    return;
  }

  tenantLoading.value = true;
  try {
    const result = await authStore.changeTenant(tenantId);
    currentTenantId.value = result.tenantId;
    resetNotificationState();
    notification.success({
      description: result.tenantName || currentTenant.value?.name || tenantId,
      duration: 3,
      message: '租户切换成功',
    });
    await loadTenantOptions();
    await loadInboxNotifications({ reset: true });
  } finally {
    tenantLoading.value = false;
  }
}

function buildNotificationAvatar(name?: string) {
  const displayName = name?.trim() || '消息';
  const text = displayName
    .slice(0, 1)
    .toUpperCase()
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
      <defs>
        <linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stop-color="#1677ff"/>
          <stop offset="1" stop-color="#36cfc9"/>
        </linearGradient>
      </defs>
      <rect width="80" height="80" rx="40" fill="url(#g)"/>
      <text x="40" y="47" text-anchor="middle" dominant-baseline="middle" fill="#fff" font-size="34" font-family="Arial, sans-serif" font-weight="600">${text}</text>
    </svg>
  `;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function formatNotificationDate(value?: string) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }
  return date.toLocaleString('zh-CN', {
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    month: '2-digit',
  });
}

function resetNotificationState() {
  knownNotificationIds = new Set<string>();
  notificationInitialized = false;
  notifications.value = [];
}

function emitMessageNotificationUpdated() {
  window.dispatchEvent(new CustomEvent(MESSAGE_NOTIFICATION_UPDATED_EVENT));
}

function openNewMessageNotice(item: NotificationItem) {
  notification.close(`message-${item.id}`);
  void handleClick(item);
}

function showNewMessageNotice(item: NotificationItem) {
  notification.info({
    class: 'message-notice message-notice-clickable',
    description: () =>
      h('div', { class: 'message-notice-body' }, [
        h('div', { class: 'message-notice-meta' }, item.message),
      ]),
    duration: 5,
    key: `message-${item.id}`,
    message: () =>
      h(
        'div',
        {
          class: 'message-notice-title',
          title: item.title || '收到新消息',
        },
        item.title || '收到新消息',
      ),
    onClick: () => {
      openNewMessageNotice(item);
    },
    placement: 'bottomRight',
    style: {
      width: '320px',
    },
  });
}

async function loadInboxNotifications(options?: {
  notifyNew?: boolean;
  reset?: boolean;
}) {
  if (!accessStore.accessToken) {
    resetNotificationState();
    return;
  }
  if (notificationLoading) {
    return;
  }

  notificationLoading = true;
  try {
    const previousIds = new Set(knownNotificationIds);
    const result = await pageMessageNotifications({
      pageNum: 1,
      pageSize: NOTIFICATION_PAGE_SIZE,
    });
    notifications.value = (result.records || []).map((item) => ({
      id: item.id || item.messageId || '',
      avatar: item.senderAvatar || buildNotificationAvatar(item.senderName),
      date: formatNotificationDate(item.sendTime || item.readTime),
      isRead: item.readStatus === 1,
      link: '/message-center',
      query: {
        detailId: item.id || item.messageId || '',
        tab: 'inbox',
      },
      message: item.senderName ? `来自 ${item.senderName}` : '站内消息',
      title: item.title || '未命名消息',
    }));
    if (options?.reset || !notificationInitialized) {
      knownNotificationIds = new Set(
        notifications.value.map((item) => String(item.id)),
      );
      notificationInitialized = true;
      emitMessageNotificationUpdated();
      return;
    }

    const newNotifications = notifications.value.filter(
      (item) => !item.isRead && !previousIds.has(String(item.id)),
    );
    if (options?.notifyNew && newNotifications.length > 0) {
      newNotifications.slice().reverse().forEach(showNewMessageNotice);
    }
    notifications.value.forEach((item) => {
      knownNotificationIds.add(String(item.id));
    });
    emitMessageNotificationUpdated();
  } catch {
    notifications.value = [];
  } finally {
    notificationLoading = false;
  }
}

function stopNotificationPolling() {
  if (notificationPollTimer) {
    clearInterval(notificationPollTimer);
    notificationPollTimer = undefined;
  }
}

function startNotificationPolling() {
  stopNotificationPolling();
  if (!accessStore.accessToken) {
    return;
  }
  notificationPollTimer = setInterval(() => {
    void loadInboxNotifications({ notifyNew: true });
  }, NOTIFICATION_POLL_INTERVAL);
}

function handleNotificationVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void loadInboxNotifications({ notifyNew: true });
  }
}

function handleNotificationRefresh() {
  void loadInboxNotifications({ notifyNew: true });
}

function handleNotificationOpenChange(open: boolean) {
  if (open) {
    void loadInboxNotifications();
  }
}

async function handleLogout() {
  await authStore.logout(false);
}

function closeUserMenu() {
  userMenuOpen.value = false;
}

function handleOpenProfile() {
  closeUserMenu();
  router.push({ name: 'Profile' });
}

function handleOpenMessageCenter() {
  closeUserMenu();
  router.push({ name: 'MessageCenter' });
}

function handleOpenLock() {
  closeUserMenu();
  lockModalApi.open();
}

function handleSubmitLock(lockScreenPassword: string) {
  lockModalApi.close();
  accessStore.lockScreen(lockScreenPassword);
}

async function handleUserLogout() {
  closeUserMenu();
  await handleLogout();
}

async function handleNoticeClear() {
  await clearMessageNotifications();
  notifications.value = [];
  emitMessageNotificationUpdated();
}

async function markRead(id: number | string) {
  const item = notifications.value.find(
    (item) => String(item.id) === String(id),
  );
  if (item && !item.isRead) {
    await markMessageNotificationRead(String(id));
    notifications.value = notifications.value.filter(
      (item) => String(item.id) !== String(id),
    );
    emitMessageNotificationUpdated();
  }
}

async function handleMakeAll() {
  await clearMessageNotifications();
  notifications.value = [];
  emitMessageNotificationUpdated();
}

const viewAll = () => {
  router.push({ name: 'MessageCenter' });
};

const handleClick = async (item: NotificationItem) => {
  const detailId = item.query?.detailId ?? item.id;
  const detailIdText =
    typeof detailId === 'number' || typeof detailId === 'string'
      ? String(detailId)
      : '';
  if (detailIdText) {
    messageDetailDrawerRef.value?.open({ id: detailIdText, mode: 'inbox' });
    if (item.id && !item.isRead) {
      await markRead(item.id);
    }
    return;
  }

  if (item.id && !item.isRead) {
    await markRead(item.id);
  }
  // 如果通知项有链接，点击时跳转
  if (item.link) {
    navigateTo(item.link, item.query, item.state);
  }
};

function navigateTo(
  link: string,
  query?: LocationQueryRaw,
  state?: HistoryState,
) {
  if (link.startsWith('http://') || link.startsWith('https://')) {
    // 外部链接，在新标签页打开
    window.open(link, '_blank');
  } else {
    // 内部路由链接，支持 query 参数和 state
    if (link === '/message-center') {
      router.push({
        name: 'MessageCenter',
        query: query || {},
        state,
      });
      return;
    }
    router.push({ path: link, query: query || {}, state });
  }
}

onMounted(() => {
  void loadTenantOptions();
  void loadInboxNotifications({ reset: true });
  startNotificationPolling();
  document.addEventListener(
    'visibilitychange',
    handleNotificationVisibilityChange,
  );
  window.addEventListener(
    MESSAGE_NOTIFICATION_REFRESH_EVENT,
    handleNotificationRefresh,
  );
});

onBeforeUnmount(() => {
  stopNotificationPolling();
  document.removeEventListener(
    'visibilitychange',
    handleNotificationVisibilityChange,
  );
  window.removeEventListener(
    MESSAGE_NOTIFICATION_REFRESH_EVENT,
    handleNotificationRefresh,
  );
});

watch(
  () => accessStore.accessToken,
  () => {
    resetNotificationState();
    void loadTenantOptions();
    void loadInboxNotifications({ reset: true });
    startNotificationPolling();
  },
);

watch(
  () => currentUserInfo.value?.tenantId,
  (tenantId) => {
    if (tenantId) {
      currentTenantId.value = tenantId;
      resetNotificationState();
      void loadInboxNotifications({ reset: true });
    }
  },
);

watch(
  () => ({
    enable: preferences.app.watermark,
    content: preferences.app.watermarkContent,
    isDark: isDark.value,
  }),
  async ({ enable, content, isDark: isDarkValue }) => {
    if (enable) {
      const watermarkColor = isDarkValue
        ? 'rgba(255, 255, 255, 0.12)'
        : 'rgba(0, 0, 0, 0.12)';

      await updateWatermark({
        advancedStyle: {
          colorStops: [
            {
              color: watermarkColor,
              offset: 0,
            },
            {
              color: watermarkColor,
              offset: 1,
            },
          ],
          type: 'linear',
        },
        content:
          content ||
          `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #user-dropdown>
      <LockModal
        v-if="preferences.widget.lockScreen"
        :avatar="avatar"
        :text="userStore.userInfo?.realName"
        @submit="handleSubmitLock"
      />
      <Dropdown
        v-model:open="userMenuOpen"
        placement="bottomRight"
        trigger="click"
      >
        <div class="mr-2 ml-1 cursor-pointer rounded-full p-1.5 hover:bg-accent">
          <div class="relative flex items-center">
            <Avatar :src="avatar" :size="32">
              {{ userStore.userInfo?.realName?.slice(0, 1) || '理' }}
            </Avatar>
            <span
              v-if="showDot"
              class="absolute right-0 bottom-0 size-3 rounded-full border-2 border-background bg-green-500"
            ></span>
          </div>
        </div>
        <template #overlay>
          <div
            class="mr-2 min-w-60 rounded-md border border-border bg-background p-1 pb-2 shadow-lg"
          >
            <div class="flex items-center p-3">
              <div class="relative shrink-0">
                <Avatar :src="avatar" :size="48">
                  {{ userStore.userInfo?.realName?.slice(0, 1) || '理' }}
                </Avatar>
                <span
                  v-if="showDot"
                  class="absolute right-1 bottom-0 size-4 rounded-full border-2 border-background bg-green-500"
                ></span>
              </div>
              <div class="ml-2 min-w-0 flex-1">
                <div class="mb-1 flex items-center text-sm font-medium">
                  <span class="truncate">
                    {{ userStore.userInfo?.realName }}
                  </span>
                  <Tag color="green" class="ml-2 mr-0">Pro</Tag>
                </div>
                <div class="truncate text-xs text-muted-foreground">
                  ann.vben@gmail.com
                </div>
              </div>
            </div>
            <button
              class="flex w-full cursor-pointer items-center rounded-sm px-3 py-2 text-left leading-6 hover:bg-accent"
              type="button"
              @click="handleOpenProfile"
            >
              <UserRoundPen class="mr-2 size-4" />
              {{ $t('page.auth.profile') }}
            </button>
            <button
              class="flex w-full cursor-pointer items-center rounded-sm px-3 py-2 text-left leading-6 hover:bg-accent"
              type="button"
              @click="handleOpenMessageCenter"
            >
              <Inbox class="mr-2 size-4" />
              消息中心
            </button>
            <button
              v-if="preferences.widget.lockScreen"
              class="flex w-full cursor-pointer items-center rounded-sm px-3 py-2 text-left leading-6 hover:bg-accent"
              type="button"
              @click="handleOpenLock"
            >
              <LockKeyhole class="mr-2 size-4" />
              {{ $t('ui.widgets.lockScreen.title') }}
            </button>
            <button
              class="flex w-full cursor-pointer items-center rounded-sm px-3 py-2 text-left leading-6 hover:bg-accent"
              type="button"
              @click="handleUserLogout"
            >
              <LogOut class="mr-2 size-4" />
              {{ $t('common.logout') }}
            </button>
          </div>
        </template>
      </Dropdown>
    </template>
    <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNoticeClear"
        @click="handleClick"
        @read="(item) => item.id && markRead(item.id)"
        @make-all="handleMakeAll"
        @open-change="handleNotificationOpenChange"
        @view-all="viewAll"
      />
    </template>
    <template #header-right-120>
      <Dropdown v-if="showTenantSwitcher" placement="bottomRight" trigger="click">
        <Button
          class="mr-1 max-w-[180px]"
          :loading="tenantLoading"
          size="small"
        >
          <span class="block truncate">{{ currentTenantName }}</span>
        </Button>
        <template #overlay>
          <Menu>
            <Menu.Item
              v-for="tenant in tenantOptions"
              :key="tenant.id"
              :disabled="tenant.id === currentTenantId || tenantLoading"
              @click="handleSwitchTenant(tenant.id)"
            >
              {{ tenant.id === currentTenantId ? '当前租户：' : '切换租户：' }}
              {{ tenant.name || tenant.id }}
            </Menu.Item>
          </Menu>
        </template>
      </Dropdown>
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
      <MessageDetailDrawer ref="messageDetailDrawerRef" />
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>

<style scoped>
:global(.message-notice-clickable) {
  cursor: pointer;
}

:global(.message-notice.ant-notification-notice) {
  width: 320px !important;
  min-height: 0;
  padding: 12px 14px 8px;
  border-radius: 8px;
}

:global(.message-notice .ant-notification-notice-icon) {
  margin-top: 1px;
  margin-inline-start: 0;
  font-size: 18px;
}

:global(.message-notice .ant-notification-notice-with-icon .ant-notification-notice-message) {
  margin-bottom: 5px;
  margin-inline-start: 30px;
  padding-right: 20px;
  color: hsl(var(--foreground));
  font-size: 14px;
  line-height: 20px;
}

:global(.message-notice .ant-notification-notice-with-icon .ant-notification-notice-description) {
  margin-inline-start: 30px;
}

:global(.message-notice .ant-notification-notice-close) {
  top: 12px;
  right: 12px;
}

:global(.message-notice-title) {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.message-notice-body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

:global(.message-notice-meta) {
  min-width: 0;
  overflow: hidden;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

</style>
