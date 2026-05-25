<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';

import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal, useVbenModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import {
  BasicLayout,
  LockScreen,
  LockScreenModal,
  Notification,
} from '@vben/layouts';
import { LockKeyhole, LogOut, UserRoundPen } from '@vben/icons';
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

import { listCurrentUserTenants } from '#/api/system/user';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

const notifications = ref<NotificationItem[]>([
  {
    id: 1,
    avatar: 'https://avatar.vercel.sh/vercel.svg?text=VB',
    date: '3小时前',
    isRead: true,
    message: '描述信息描述信息描述信息',
    title: '收到了 14 份新周报',
  },
  {
    id: 2,
    avatar: 'https://avatar.vercel.sh/1',
    date: '刚刚',
    isRead: false,
    message: '描述信息描述信息描述信息',
    title: '朱偏右 回复了你',
  },
  {
    id: 3,
    avatar: 'https://avatar.vercel.sh/1',
    date: '2024-01-01',
    isRead: false,
    message: '描述信息描述信息描述信息',
    title: '曲丽丽 评论了你',
  },
  {
    id: 4,
    avatar: 'https://avatar.vercel.sh/satori',
    date: '1天前',
    isRead: false,
    message: '描述信息描述信息描述信息',
    title: '代办提醒',
  },
  {
    id: 5,
    avatar: 'https://avatar.vercel.sh/satori',
    date: '1天前',
    isRead: false,
    message: '描述信息描述信息描述信息',
    title: '跳转Workspace示例',
    link: '/workspace',
  },
  {
    id: 6,
    avatar: 'https://avatar.vercel.sh/satori',
    date: '1天前',
    isRead: false,
    message: '描述信息描述信息描述信息',
    title: '跳转外部链接示例',
    link: 'https://doc.vben.pro',
  },
]);

const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();
const { isDark } = usePreferences();
const tenantOptions = ref<any[]>([]);
const currentTenantId = ref('');
const tenantLoading = ref(false);
const userMenuOpen = ref(false);
const showDot = computed(() =>
  notifications.value.some((item) => !item.isRead),
);

const [LockModal, lockModalApi] = useVbenModal({
  connectedComponent: LockScreenModal,
});

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

const currentTenant = computed(() =>
  tenantOptions.value.find((tenant) => tenant.id === currentTenantId.value),
);

const showTenantSwitcher = computed(() => tenantOptions.value.length > 1);

const currentTenantName = computed(
  () =>
    currentTenant.value?.name ||
    (userStore.userInfo as any)?.tenantName ||
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
    const userTenantId = (userStore.userInfo as any)?.tenantId;
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
    notification.success({
      description: result.tenantName || currentTenant.value?.name || tenantId,
      duration: 3,
      message: '租户切换成功',
    });
    await loadTenantOptions();
  } finally {
    tenantLoading.value = false;
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

function handleNoticeClear() {
  notifications.value = [];
}

function markRead(id: number | string) {
  const item = notifications.value.find((item) => item.id === id);
  if (item) {
    item.isRead = true;
  }
}

function remove(id: number | string) {
  notifications.value = notifications.value.filter((item) => item.id !== id);
}

function handleMakeAll() {
  notifications.value.forEach((item) => (item.isRead = true));
}

const viewAll = () => {};

const handleClick = (item: NotificationItem) => {
  // 如果通知项有链接，点击时跳转
  if (item.link) {
    navigateTo(item.link, item.query, item.state);
  }
};

function navigateTo(
  link: string,
  query?: Record<string, any>,
  state?: Record<string, any>,
) {
  if (link.startsWith('http://') || link.startsWith('https://')) {
    // 外部链接，在新标签页打开
    window.open(link, '_blank');
  } else {
    // 内部路由链接，支持 query 参数和 state
    router.push({
      path: link,
      query: query || {},
      state,
    });
  }
}

onMounted(loadTenantOptions);

watch(
  () => accessStore.accessToken,
  () => {
    void loadTenantOptions();
  },
);

watch(
  () => (userStore.userInfo as any)?.tenantId,
  (tenantId) => {
    if (tenantId) {
      currentTenantId.value = tenantId;
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
        @read="(item) => item.id && markRead(item.id)"
        @remove="(item) => item.id && remove(item.id)"
        @make-all="handleMakeAll"
        @on-click="handleClick"
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
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
