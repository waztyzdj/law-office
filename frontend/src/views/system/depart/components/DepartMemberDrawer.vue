<script setup lang="ts">
import type { DepartInfo, DepartMemberRelationInfo } from '#/api/system/depart';
import type { UserInfo } from '#/api/system/user';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Checkbox, Empty, Radio, Select, Spin, Table, Transfer, message } from 'ant-design-vue';

import {
  assignDepartUsers,
  getDepartMemberRelations,
  getDepartUserIds,
  saveDepartMemberRelations,
} from '#/api/system/depart';
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
const relationMap = ref<Record<string, DepartMemberRelationInfo>>({});
const leaderUserId = ref<string>();

const relationColumns = [
  { dataIndex: 'realname', title: '成员', width: 180 },
  { align: 'center' as const, dataIndex: 'primaryDepartFlag', title: '主部门', width: 96 },
  { align: 'center' as const, dataIndex: 'departLeaderFlag', title: '部门负责人', width: 120 },
  { dataIndex: 'supervisorUserId', title: '直属上级', width: 220 },
];
const relationMaxVisibleRows = 6;
const relationRowHeight = 48;

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

const userOptionMap = computed(() => {
  const map = new Map<string, TransferItem>();
  userOptions.value.forEach((item) => map.set(item.key, item));
  return map;
});

const selectedRelationRows = computed(() =>
  selectedUserIds.value.map((userId) => {
    const user = userOptionMap.value.get(userId);
    const relation = relationMap.value[userId] || {};
    return {
      departLeaderFlag: leaderUserId.value === userId ? 1 : 0,
      primaryDepartFlag: relation.primaryDepartFlag === 1 ? 1 : 0,
      realname: relation.realname || user?.title || userId,
      supervisorUserId: relation.supervisorUserId,
      userId,
      username: relation.username || user?.description,
    };
  }),
);

const relationTableScroll = computed(() =>
  selectedRelationRows.value.length > relationMaxVisibleRows
    ? { y: relationMaxVisibleRows * relationRowHeight }
    : undefined,
);

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

function formatMemberName(record: DepartMemberRelationInfo) {
  return record.username ? `${record.realname || record.username}（${record.username}）` : record.realname || '-';
}

function getSupervisorOptions(userId: string) {
  return selectedRelationRows.value
    .filter((item) => item.userId !== userId)
    .map((item) => ({
      label: item.username ? `${item.realname}（${item.username}）` : item.realname || item.userId,
      value: item.userId || '',
    }));
}

function syncRelationRows() {
  const selectedSet = new Set(selectedUserIds.value);
  selectedUserIds.value.forEach((userId) => {
    if (!relationMap.value[userId]) {
      const user = userOptionMap.value.get(userId);
      relationMap.value[userId] = {
        primaryDepartFlag: 0,
        departLeaderFlag: 0,
        realname: user?.title,
        supervisorUserId: undefined,
        username: user?.description,
        userId,
      };
    }
  });
  Object.entries(relationMap.value).forEach(([userId, relation]) => {
    if (!selectedSet.has(userId)) {
      return;
    }
    if (relation.supervisorUserId && !selectedSet.has(relation.supervisorUserId)) {
      relation.supervisorUserId = undefined;
    }
  });
  if (leaderUserId.value && !selectedSet.has(leaderUserId.value)) {
    leaderUserId.value = undefined;
  }
}

function handleMemberChange(nextKeys: string[]) {
  selectedUserIds.value = nextKeys;
  syncRelationRows();
}

function handlePrimaryDepartChange(userId: string, checked: boolean) {
  relationMap.value[userId] = {
    ...(relationMap.value[userId] || { userId }),
    primaryDepartFlag: checked ? 1 : 0,
  };
}

function handleSupervisorChange(userId: string, supervisorUserId?: string) {
  relationMap.value[userId] = {
    ...(relationMap.value[userId] || { userId }),
    supervisorUserId,
  };
}

async function loadData(departId: string) {
  loading.value = true;
  try {
    const [userPage, userIds, relations] = await Promise.all([
      pageUsers({ pageNum: 1, pageSize: 1000 }),
      getDepartUserIds(departId),
      getDepartMemberRelations(departId),
    ]);
    userOptions.value = toTransferItems(userPage.records || []);
    selectedUserIds.value = userIds;
    relationMap.value = Object.fromEntries(
      relations
        .filter((relation) => relation.userId)
        .map((relation) => [relation.userId || '', { ...relation }]),
    );
    leaderUserId.value = relations.find((relation) => relation.departLeaderFlag === 1)?.userId;
    syncRelationRows();
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
    if (selectedUserIds.value.length > 0) {
      await saveDepartMemberRelations({
        departId: currentDepart.value.id,
        members: selectedUserIds.value.map((userId) => {
          const relation = relationMap.value[userId] || {};
          return {
            departLeaderFlag: leaderUserId.value === userId ? 1 : 0,
            primaryDepartFlag: relation.primaryDepartFlag === 1 ? 1 : 0,
            supervisorUserId: relation.supervisorUserId || undefined,
            userId,
          };
        }),
      });
    }
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
  relationMap.value = {};
  leaderUserId.value = undefined;
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
        @change="handleMemberChange"
      />
      <Empty v-else description="暂无用户数据" />
      <div class="member-relation-area">
        <Table
          v-if="selectedRelationRows.length > 0"
          :columns="relationColumns"
          :data-source="selectedRelationRows"
          :pagination="false"
          :scroll="relationTableScroll"
          row-key="userId"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'realname'">
              {{ formatMemberName(record) }}
            </template>
            <template v-else-if="column.dataIndex === 'primaryDepartFlag'">
              <Checkbox
                :checked="record.primaryDepartFlag === 1"
                @change="(event) => handlePrimaryDepartChange(record.userId, event.target.checked)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'departLeaderFlag'">
              <Radio
                :checked="leaderUserId === record.userId"
                @change="() => (leaderUserId = record.userId)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'supervisorUserId'">
              <Select
                allow-clear
                class="relation-select"
                :options="getSupervisorOptions(record.userId)"
                :value="record.supervisorUserId"
                @change="(value) => handleSupervisorChange(record.userId, value as string | undefined)"
              />
            </template>
          </template>
        </Table>
        <Empty v-else class="relation-empty" description="请选择部门成员后配置组织关系" />
      </div>
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
  height: 360px !important;
}

:deep(.depart-member-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}

.member-relation-area {
  margin-top: 16px;
}

.relation-select {
  width: 100%;
}

.relation-empty {
  padding: 32px 0;
}
</style>
