<script setup lang="ts">
import type { UserInfo } from '#/api/system/user';
import type { RoleInfo } from '#/api/system/role';
import type { CSSProperties } from 'vue';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Empty, Spin, Transfer, message } from 'ant-design-vue';

import { listRoles } from '#/api/system/role';
import { assignUserRoles, getUserRoleIds } from '#/api/system/user';

interface TransferItem {
  key: string;
  title: string;
  description?: string;
}

const emit = defineEmits<{
  success: [];
}>();

const currentUser = ref<UserInfo>();
const loading = ref(false);
const roleOptions = ref<TransferItem[]>([]);
const selectedRoleIds = ref<string[]>([]);
const roleTransferListStyle: CSSProperties = {
  height: 'calc(100vh - 174px)',
};

const drawerTitle = computed(() =>
  currentUser.value?.realname
    ? `分配角色 - ${currentUser.value.realname}`
    : '分配角色',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存角色',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function toTransferItems(roles: RoleInfo[]): TransferItem[] {
  return roles
    .filter((role) => role.id)
    .map((role) => ({
      key: role.id || '',
      title: role.roleName || role.roleCode || '',
      description: role.roleCode,
    }));
}

async function loadData(user: UserInfo) {
  if (!user.id) {
    return;
  }

  loading.value = true;
  try {
    const [roles, roleIds] = await Promise.all([
      listRoles(),
      getUserRoleIds(user.id),
    ]);
    roleOptions.value = toTransferItems(roles);
    selectedRoleIds.value = roleIds;
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!currentUser.value?.id) {
    return;
  }

  try {
    drawerApi.lock();
    await assignUserRoles(currentUser.value.id, selectedRoleIds.value);
    message.success('用户角色已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(user: UserInfo) {
  currentUser.value = user;
  roleOptions.value = [];
  selectedRoleIds.value = [];
  drawerApi.setState({ loading: false, title: drawerTitle.value }).open();
  await nextTick();
  drawerApi.setState({ title: drawerTitle.value });
  void loadData(user);
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Alert
      class="mb-4"
      message="保存后会覆盖该用户原有角色。用户重新登录或刷新用户信息后会获得新的权限。"
      show-icon
      type="info"
    />
    <Spin :spinning="loading">
      <Transfer
        v-if="roleOptions.length > 0"
        class="user-role-transfer"
        v-model:target-keys="selectedRoleIds"
        :data-source="roleOptions"
        :list-style="roleTransferListStyle"
        :render="(item: TransferItem) => item.description ? `${item.title}（${item.description}）` : item.title"
        :titles="['可选角色', '已选角色']"
        show-search
      />
      <Empty v-else description="暂无角色数据" />
    </Spin>
  </Drawer>
</template>

<style scoped>
.user-role-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  margin-top: 16px;
  width: 100%;
}

:deep(.user-role-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
}

:deep(.user-role-transfer .ant-transfer-list-body) {
  min-height: 0;
}

:deep(.user-role-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}
</style>
