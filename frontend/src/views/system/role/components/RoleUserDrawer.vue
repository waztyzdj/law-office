<script setup lang="ts">
import type { RoleInfo } from '#/api/system/role';
import type { UserInfo } from '#/api/system/user';
import type { CSSProperties } from 'vue';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Empty, Spin, Transfer, message } from 'ant-design-vue';

import { listPickerUsers } from '#/api/system/picker';
import { assignRoleUsers, getRoleUserIds } from '#/api/system/role';

interface TransferItem {
  key: string;
  title: string;
  description?: string;
}

const emit = defineEmits<{
  success: [];
}>();

const currentRole = ref<RoleInfo>();
const loading = ref(false);
const userOptions = ref<TransferItem[]>([]);
const selectedUserIds = ref<string[]>([]);
const userTransferListStyle: CSSProperties = {
  height: 'calc(100vh - 174px)',
};

const drawerTitle = computed(() =>
  currentRole.value?.roleName
    ? `角色成员 - ${currentRole.value.roleName}`
    : '角色成员',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存成员',
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

async function loadData(role: RoleInfo) {
  if (!role.id) {
    return;
  }

  loading.value = true;
  try {
    const [users, userIds] = await Promise.all([
      listPickerUsers(),
      getRoleUserIds(role.id),
    ]);
    userOptions.value = toTransferItems(users);
    selectedUserIds.value = userIds;
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!currentRole.value?.id) {
    return;
  }

  try {
    drawerApi.lock();
    await assignRoleUsers(currentRole.value.id, selectedUserIds.value);
    message.success('角色成员已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(role: RoleInfo) {
  currentRole.value = role;
  userOptions.value = [];
  selectedUserIds.value = [];
  drawerApi.setState({ loading: false, title: drawerTitle.value }).open();
  await nextTick();
  drawerApi.setState({ title: drawerTitle.value });
  void loadData(role);
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Alert
      class="mb-4"
      message="保存后会覆盖该角色原有成员，成员重新登录或刷新用户信息后会获得新的权限。"
      show-icon
      type="info"
    />
    <Spin :spinning="loading">
      <Transfer
        v-if="userOptions.length > 0"
        class="role-user-transfer"
        v-model:target-keys="selectedUserIds"
        :data-source="userOptions"
        :list-style="userTransferListStyle"
        :render="renderTransferItem"
        :titles="['可选人员', '角色成员']"
        show-search
      />
      <Empty v-else description="暂无人员数据" />
    </Spin>
  </Drawer>
</template>

<style scoped>
.role-user-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  margin-top: 16px;
  width: 100%;
}

:deep(.role-user-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
}

:deep(.role-user-transfer .ant-transfer-list-body) {
  min-height: 0;
}

:deep(.role-user-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}
</style>
