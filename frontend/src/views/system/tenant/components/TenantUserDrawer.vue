<script setup lang="ts">
import type { TenantInfo } from '#/api/system/tenant';
import type { UserInfo } from '#/api/system/user';
import type { CSSProperties } from 'vue';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Empty, Spin, Transfer, message } from 'ant-design-vue';

import { assignTenantUsers, getTenantUserIds } from '#/api/system/tenant';
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
    ? `设置用户 - ${currentTenant.value.name}`
    : '设置用户',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存用户',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function toTransferItems(users: UserInfo[]): TransferItem[] {
  return users
    .filter((user) => user.id)
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
    const [usersPage, userIds] = await Promise.all([
      pageUsers({ pageNum: 1, pageSize: 1000 }),
      getTenantUserIds(tenant.id),
    ]);
    userOptions.value = toTransferItems((usersPage as any).records || []);
    selectedUserIds.value = userIds;
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
    await assignTenantUsers(currentTenant.value.id, selectedUserIds.value);
    message.success('租户用户已保存');
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
      message="保存时只会同步本次勾选变化：已存在的用户保持不变，取消勾选的用户会从该租户移除。"
      show-icon
      type="info"
    />
    <Spin :spinning="loading">
      <Transfer
        v-if="userOptions.length > 0"
        class="tenant-user-transfer"
        v-model:target-keys="selectedUserIds"
        :data-source="userOptions"
        :list-style="userTransferListStyle"
        :render="renderTransferItem"
        :titles="['可选用户', '已选用户']"
        show-search
      />
      <Empty v-else description="暂无用户数据" />
    </Spin>
  </Drawer>
</template>

<style scoped>
.tenant-user-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  margin-top: 16px;
  width: 100%;
}

:deep(.tenant-user-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
}

:deep(.tenant-user-transfer .ant-transfer-list-body) {
  min-height: 0;
}

:deep(.tenant-user-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}
</style>
