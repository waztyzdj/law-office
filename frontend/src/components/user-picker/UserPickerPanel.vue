<script setup lang="ts">
import type { DepartInfo } from '#/api/system/depart';
import type { RoleInfo } from '#/api/system/role';
import type { UserInfo } from '#/api/system/user';
import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';
import type { UserPickerMode } from './types';

import { computed, onMounted, ref, watch } from 'vue';

import {
  Checkbox,
  Empty,
  InputSearch,
  List,
  Pagination,
  Radio,
  Spin,
  Tabs,
  Tree,
} from 'ant-design-vue';

import {
  listPickerDeparts,
  listPickerDepartUsers,
  listPickerRoles,
  listPickerRoleUsers,
  listPickerUsers,
} from '#/api/system/picker';
import {
  buildAntTreeData,
  buildTreeFromFlat,
  collectDescendantKeys,
  collectExpandedKeysByDepth,
} from '#/composables/Tree/useTree';

import UserPickerSelectedList from './UserPickerSelectedList.vue';

interface Props {
  excludeUserIds?: string[];
  maxCount?: number;
  mode?: UserPickerMode;
  orgOnly?: boolean;
  selectedUsers?: UserInfo[];
}

const props = withDefaults(defineProps<Props>(), {
  excludeUserIds: () => [],
  maxCount: undefined,
  mode: 'single',
  orgOnly: false,
  selectedUsers: () => [],
});

const emit = defineEmits<{
  'update:selectedUsers': [users: UserInfo[]];
}>();

const activeTab = ref<'depart' | 'role'>('depart');
const loading = ref(false);
const departUserLoading = ref(false);
const roleUserLoading = ref(false);
const keyword = ref('');
const currentPage = ref(1);
const pageSize = ref(10);
const selectedDepartId = ref<string>();
const selectedRoleId = ref<string>();
const expandedDepartKeys = ref<Key[]>([]);
const users = ref<UserInfo[]>([]);
const departs = ref<DepartInfo[]>([]);
const departUsers = ref<UserInfo[]>([]);
const roles = ref<RoleInfo[]>([]);
const roleUsers = ref<UserInfo[]>([]);
const localSelectedUsers = ref<UserInfo[]>([]);

const isMultiple = computed(() => props.mode === 'multiple');
const excludedUserIds = computed(() => new Set(props.excludeUserIds.filter(Boolean)));
const userInfoMap = computed(() =>
  new Map(users.value.filter((user) => user.id).map((user) => [user.id!, user])),
);
const selectedUserIds = computed(() =>
  new Set(localSelectedUsers.value.map((user) => user.id).filter(Boolean) as string[]),
);
const departTree = computed(() => buildTreeFromFlat(departs.value));
const departTreeData = computed<DataNode[]>(() =>
  buildAntTreeData(departTree.value, (node) => node.departName || String(node.id ?? '')) as DataNode[],
);
const selectedDepartIds = computed(() => {
  if (!selectedDepartId.value) {
    return [];
  }
  return [
    selectedDepartId.value,
    ...collectDescendantKeys(departTree.value, selectedDepartId.value).map(String),
  ];
});
const roleOptions = computed(() => roles.value.filter((role) => role.id));
const sourceUsers = computed(() => {
  if (!props.orgOnly && activeTab.value === 'role') {
    return selectedRoleId.value ? normalizeUsers(roleUsers.value) : [];
  }
  return normalizeUsers(selectedDepartId.value ? departUsers.value : users.value);
});
const visibleUsers = computed(() => {
  const target = keyword.value.trim().toLowerCase();
  return sourceUsers.value
    .filter((user) => user.id && !excludedUserIds.value.has(user.id))
    .filter((user) => (target ? buildSearchText(user).toLowerCase().includes(target) : true));
});
const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return visibleUsers.value.slice(start, start + pageSize.value);
});

watch([keyword, activeTab, selectedDepartId, selectedRoleId], () => {
  currentPage.value = 1;
});

watch(
  () => props.selectedUsers,
  (nextUsers) => {
    localSelectedUsers.value = [...nextUsers];
  },
  { immediate: true },
);

onMounted(loadInitialData);

async function loadInitialData() {
  loading.value = true;
  try {
    const [userList, departList, roleList] = await Promise.all([
      listPickerUsers(),
      listPickerDeparts(),
      props.orgOnly ? Promise.resolve([]) : listPickerRoles(),
    ]);
    users.value = userList;
    departs.value = departList;
    roles.value = roleList;
    expandedDepartKeys.value = collectExpandedKeysByDepth(buildTreeFromFlat(departList), 1);
  } finally {
    loading.value = false;
  }
}

async function loadRoleUsers(roleId?: string) {
  selectedRoleId.value = roleId;
  roleUsers.value = [];
  if (!roleId) {
    return;
  }

  roleUserLoading.value = true;
  try {
    roleUsers.value = await listPickerRoleUsers(roleId);
  } finally {
    roleUserLoading.value = false;
  }
}

async function loadDepartUsers(departId?: string) {
  selectedDepartId.value = departId;
  departUsers.value = [];
  if (!departId) {
    return;
  }

  departUserLoading.value = true;
  try {
    const departIdSet = new Set(selectedDepartIds.value);
    const userMap = new Map<string, UserInfo>();
    const userLists = await Promise.all([...departIdSet].map((id) => listPickerDepartUsers(id)));
    userLists.flat().forEach((user) => {
      if (user.id && !userMap.has(user.id)) {
        userMap.set(user.id, user);
      }
    });
    departUsers.value = [...userMap.values()];
  } finally {
    departUserLoading.value = false;
  }
}

function normalizeUsers(source: UserInfo[]) {
  return source
    .filter((user) => user.id)
    .map((user) => ({
      ...userInfoMap.value.get(user.id!),
      ...user,
    }));
}

function buildSearchText(user: UserInfo) {
  return [
    user.realname,
    user.username,
    user.workNo,
    user.phone,
    user.departIds,
  ]
    .filter(Boolean)
    .join(' ');
}

function formatUserLabel(user: UserInfo) {
  const displayName = user.realname || user.username || user.id || '';
  return user.workNo ? `${displayName}（${user.workNo}）` : displayName;
}

function isSelected(user: UserInfo) {
  return !!user.id && selectedUserIds.value.has(user.id);
}

function updateSelectedUsers(nextUsers: UserInfo[]) {
  localSelectedUsers.value = nextUsers;
  emit('update:selectedUsers', nextUsers);
}

function handleToggleUser(user: UserInfo) {
  if (!user.id || excludedUserIds.value.has(user.id)) {
    return;
  }

  if (!isMultiple.value) {
    updateSelectedUsers([user]);
    return;
  }

  const exists = isSelected(user);
  if (exists) {
    updateSelectedUsers(localSelectedUsers.value.filter((item) => item.id !== user.id));
    return;
  }

  if (props.maxCount && localSelectedUsers.value.length >= props.maxCount) {
    return;
  }
  updateSelectedUsers([...localSelectedUsers.value, user]);
}

function handleRemoveSelected(userId: string) {
  updateSelectedUsers(localSelectedUsers.value.filter((user) => user.id !== userId));
}

function handleClearSelected() {
  updateSelectedUsers([]);
}

function handleDepartSelect(keys: Key[]) {
  activeTab.value = 'depart';
  void loadDepartUsers(keys[0] === undefined ? undefined : String(keys[0]));
}

function handleRoleClick(role: RoleInfo) {
  activeTab.value = 'role';
  if (role.id) {
    void loadRoleUsers(role.id);
  }
}

function handlePageChange(page: number, size: number) {
  currentPage.value = page;
  pageSize.value = size;
}
</script>

<template>
  <div class="user-picker-panel">
    <div
      v-if="loading"
      class="user-picker-panel-loading"
    >
      <Spin />
    </div>
      <section class="user-picker-left">
        <Tabs
          v-if="!orgOnly"
          v-model:active-key="activeTab"
          size="small"
        >
          <Tabs.TabPane
            key="depart"
            tab="组织架构"
          >
            <Tree
              v-if="departTreeData.length > 0"
              v-model:expanded-keys="expandedDepartKeys"
              :selected-keys="selectedDepartId ? [selectedDepartId] : []"
              :tree-data="departTreeData"
              block-node
              @select="handleDepartSelect"
            />
            <Empty v-else description="暂无部门" />
          </Tabs.TabPane>
          <Tabs.TabPane
            key="role"
            tab="角色人员"
          >
            <div
              v-if="roleOptions.length > 0"
              class="user-picker-role-list"
            >
              <button
                v-for="role in roleOptions"
                :key="role.id"
                :class="['user-picker-role-item', { active: selectedRoleId === role.id }]"
                type="button"
                @click="handleRoleClick(role)"
              >
                <span>{{ role.roleName || role.roleCode || role.id }}</span>
                <small v-if="role.roleCode">{{ role.roleCode }}</small>
              </button>
            </div>
            <Empty v-else description="暂无角色" />
          </Tabs.TabPane>
        </Tabs>
        <template v-else>
          <div class="user-picker-tree-title">组织架构</div>
          <Tree
            v-if="departTreeData.length > 0"
            v-model:expanded-keys="expandedDepartKeys"
            :selected-keys="selectedDepartId ? [selectedDepartId] : []"
            :tree-data="departTreeData"
            block-node
            @select="handleDepartSelect"
          />
          <Empty v-else description="暂无部门" />
        </template>
      </section>

      <section class="user-picker-middle">
        <InputSearch
          v-model:value="keyword"
          allow-clear
          placeholder="搜索姓名、账号、工号、手机号"
        />
        <Spin
          :spinning="departUserLoading || roleUserLoading"
          class="user-picker-user-spin"
        >
          <List
            v-if="pagedUsers.length > 0"
            :data-source="pagedUsers"
            class="user-picker-user-list"
            item-layout="horizontal"
          >
            <template #renderItem="{ item }">
              <List.Item
                :class="['user-picker-user-item', { selected: isSelected(item) }]"
                @click="handleToggleUser(item)"
              >
                <template #actions>
                  <Checkbox
                    v-if="isMultiple"
                    :checked="isSelected(item)"
                    :disabled="!!maxCount && !isSelected(item) && localSelectedUsers.length >= maxCount"
                  />
                  <Radio
                    v-else
                    :checked="isSelected(item)"
                  />
                </template>
                <List.Item.Meta>
                  <template #title>
                    {{ formatUserLabel(item) }}
                  </template>
                </List.Item.Meta>
              </List.Item>
            </template>
          </List>
          <Empty
            v-else
            class="user-picker-empty"
            description="暂无可选人员"
          />
          <Pagination
            v-if="visibleUsers.length > 0"
            :current="currentPage"
            :page-size="pageSize"
            :show-size-changer="false"
            :total="visibleUsers.length"
            class="user-picker-pagination"
            size="small"
            @change="handlePageChange"
          />
        </Spin>
      </section>

      <section class="user-picker-right">
        <UserPickerSelectedList
          :users="localSelectedUsers"
          @clear="handleClearSelected"
          @remove="handleRemoveSelected"
        />
      </section>
  </div>
</template>

<style scoped>
.user-picker-panel {
  display: grid;
  gap: 12px;
  grid-template-columns: 280px minmax(0, 1fr) 300px;
  height: 100%;
  min-height: 0;
  position: relative;
  width: 100%;
}

.user-picker-panel-loading {
  align-items: center;
  background: rgb(255 255 255 / 65%);
  display: flex;
  inset: 0;
  justify-content: center;
  position: absolute;
  z-index: 2;
}

.user-picker-left,
.user-picker-middle,
.user-picker-right {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  min-height: 0;
  min-width: 0;
  padding: 12px;
}

.user-picker-left {
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.user-picker-tree-title {
  color: #1f2937;
  flex: 0 0 24px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.user-picker-left :deep(.ant-tabs) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.user-picker-left :deep(.ant-tabs-content),
.user-picker-left :deep(.ant-tabs-content-holder),
.user-picker-left :deep(.ant-tabs-tabpane) {
  height: 100%;
  min-height: 0;
}

.user-picker-left :deep(.ant-tabs-tabpane) {
  overflow: auto;
}

.user-picker-middle {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-picker-user-spin,
.user-picker-user-spin :deep(.ant-spin-container) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.user-picker-right {
  display: flex;
  flex-direction: column;
}

.user-picker-role-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.user-picker-role-item {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  padding: 8px 10px;
  text-align: left;
  width: 100%;
}

.user-picker-role-item.active,
.user-picker-role-item:hover {
  background: #e6f4ff;
  border-color: transparent;
}

.user-picker-role-item small {
  color: #6b7280;
  display: block;
  margin-top: 2px;
}

.user-picker-user-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.user-picker-user-list :deep(.ant-list-items) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-picker-user-item {
  border-radius: 6px;
  cursor: pointer;
  min-height: 38px;
  padding: 4px 10px;
}

.user-picker-user-item :deep(.ant-list-item-meta) {
  align-items: center;
}

.user-picker-user-item :deep(.ant-list-item-meta-title) {
  margin-bottom: 0;
}

.user-picker-user-item.selected,
.user-picker-user-item:hover {
  background: #f0f7ff;
}

.user-picker-empty {
  flex: 1;
  padding-top: 120px;
}

.user-picker-pagination {
  align-items: center;
  display: flex;
  flex: 0 0 32px;
  height: 32px;
  justify-content: center;
  padding-top: 4px;
}

@media (max-width: 900px) {
  .user-picker-panel {
    grid-template-columns: 1fr;
  }
}
</style>
