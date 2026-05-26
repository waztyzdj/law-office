<script setup lang="ts">
import type { DepartInfo } from '#/api/system/depart';
import type { UserInfo } from '#/api/system/user';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Empty, Spin, Transfer, message } from 'ant-design-vue';

import { assignDepartUsers, getDepartUserIds } from '#/api/system/depart';
import { pageUsers } from '#/api/system/user';

interface TransferItem {
  key: string;
  title: string;
  description?: string;
}

const emit = defineEmits<{
  success: [];
}>();

const currentDepart = ref<DepartInfo>();
const loading = ref(false);
const userOptions = ref<TransferItem[]>([]);
const selectedUserIds = ref<string[]>([]);

const drawerTitle = computed(() =>
  currentDepart.value?.departName
    ? `部门成员 - ${currentDepart.value.departName}`
    : '部门成员',
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

async function loadData(departId: string) {
  loading.value = true;
  try {
    const [userPage, userIds] = await Promise.all([
      pageUsers({ pageNum: 1, pageSize: 1000 }),
      getDepartUserIds(departId),
    ]);
    userOptions.value = toTransferItems(userPage.records || []);
    selectedUserIds.value = userIds;
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!currentDepart.value?.id) {
    return;
  }

  try {
    drawerApi.lock();
    await assignDepartUsers(currentDepart.value.id, selectedUserIds.value);
    message.success('部门成员已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(record: DepartInfo) {
  currentDepart.value = record;
  userOptions.value = [];
  selectedUserIds.value = [];
  drawerApi.setState({ loading: false, title: drawerTitle.value }).open();
  await nextTick();
  drawerApi.setState({ title: drawerTitle.value });
  if (record.id) {
    void loadData(record.id);
  }
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading">
      <Transfer
        v-if="userOptions.length > 0"
        class="depart-member-transfer"
        v-model:target-keys="selectedUserIds"
        :data-source="userOptions"
        :render="renderTransferItem"
        :titles="['可选用户', '部门成员']"
        show-search
      />
      <Empty v-else description="暂无用户数据" />
    </Spin>
  </Drawer>
</template>

<style scoped>
.depart-member-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  width: 100%;
}

:deep(.depart-member-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
  height: calc(100vh - 174px) !important;
}

:deep(.depart-member-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}
</style>
