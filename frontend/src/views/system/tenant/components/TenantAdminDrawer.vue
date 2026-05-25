<script setup lang="ts">
import type { TenantInfo } from '#/api/system/tenant';
import type { UserInfo } from '#/api/system/user';
import type { CSSProperties } from 'vue';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Empty, Spin, Transfer, message } from 'ant-design-vue';

import {
  assignTenantAdmins,
  getTenantAdminUserIds,
  getTenantUserIds,
} from '#/api/system/tenant';
import { pageUsers } from '#/api/system/user';

interface TransferItem {
  key: string;
  title: string;
  description?: string;
}

const emit = defineEmits<{
  success: [];
}>();

const currentTenant = ref<TenantInfo>();
const loading = ref(false);
const userOptions = ref<TransferItem[]>([]);
const selectedUserIds = ref<string[]>([]);
const userTransferListStyle: CSSProperties = {
  height: 'calc(100vh - 174px)',
};

const drawerTitle = computed(() =>
  currentTenant.value?.name
    ? `设置管理员 - ${currentTenant.value.name}`
    : '设置管理员',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存管理员',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function toTransferItems(users: UserInfo[], tenantUserIds: string[]): TransferItem[] {
  const tenantUserIdSet = new Set(tenantUserIds);
  return users
    .filter((user) => user.id && tenantUserIdSet.has(user.id))
    .map((user) => ({
      key: user.id || '',
      title: user.realname || user.username || '',
      description: user.username,
    }));
}

function renderTransferItem(item: { description?: string; title?: string }) {
  return item.description ? `${item.title}（${item.description}）` : item.title || '';
}

async function loadData(tenant: TenantInfo) {
  if (!tenant.id) {
    return;
  }

  loading.value = true;
  try {
    const [usersPage, tenantUserIds, adminUserIds] = await Promise.all([
      pageUsers({ pageNum: 1, pageSize: 1000 }),
      getTenantUserIds(tenant.id),
      getTenantAdminUserIds(tenant.id),
    ]);
    userOptions.value = toTransferItems((usersPage as any).records || [], tenantUserIds);
    selectedUserIds.value = adminUserIds.filter((id) => tenantUserIds.includes(id));
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!currentTenant.value?.id) {
    return;
  }

  try {
    drawerApi.lock();
    await assignTenantAdmins(currentTenant.value.id, selectedUserIds.value);
    message.success('租户管理员已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(tenant: TenantInfo) {
  currentTenant.value = tenant;
  userOptions.value = [];
  selectedUserIds.value = [];
  drawerApi.setState({ loading: false, title: drawerTitle.value }).open();
  await nextTick();
  drawerApi.setState({ title: drawerTitle.value });
  void loadData(tenant);
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Alert
      class="mb-4"
      message="管理员从当前租户用户中选择；保存后会同步租户默认管理员角色成员。"
      show-icon
      type="info"
    />
    <Spin :spinning="loading">
      <Transfer
        v-if="userOptions.length > 0"
        class="tenant-admin-transfer"
        v-model:target-keys="selectedUserIds"
        :data-source="userOptions"
        :list-style="userTransferListStyle"
        :render="renderTransferItem"
        :titles="['租户用户', '管理员']"
        show-search
      />
      <Empty v-else description="请先为该租户设置用户" />
    </Spin>
  </Drawer>
</template>

<style scoped>
.tenant-admin-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  margin-top: 16px;
  width: 100%;
}

:deep(.tenant-admin-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
}

:deep(.tenant-admin-transfer .ant-transfer-list-body) {
  min-height: 0;
}

:deep(.tenant-admin-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}
</style>
