<script setup lang="ts">
import type {
  DocumentFileInfo,
  DocumentPermission,
  DocumentShareInfo,
  DocumentShareTargetType,
} from '#/api/system/document';
import type { DepartInfo } from '#/api/system/depart';
import type { RoleInfo } from '#/api/system/role';
import type { CurrentUserTenant, UserInfo } from '#/api/system/user';
import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Checkbox,
  Button,
  Empty,
  InputSearch,
  Modal,
  Select,
  SelectOption,
  Space,
  Spin,
  Tabs,
  TabPane,
  Transfer,
  Tree,
  message,
} from 'ant-design-vue';

import {
  listDocumentShares,
  shareDocument,
} from '#/api/system/document';
import { listDeparts } from '#/api/system/depart';
import { listRoles } from '#/api/system/role';
import {
  getCurrentTenantUsers,
  getCurrentUserTenantOptions,
} from '#/api/system/user';
import {
  buildAntTreeData,
  buildTreeFromFlat,
  collectExpandableKeys,
} from '#/composables/Tree/useTree';

interface TransferItem {
  key: string;
  title: string;
  description?: string;
}

interface CheckedKeysValue {
  checked: Key[];
  halfChecked: Key[];
}

interface OpenPayload {
  record: DocumentFileInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const currentRecord = ref<DocumentFileInfo>();
const loading = ref(false);
const permission = ref<DocumentPermission>('download');
const activeTargetType = ref<DocumentShareTargetType>('user');
const tenantShared = ref(false);
const selectedUserIds = ref<string[]>([]);
const selectedDepartIds = ref<string[]>([]);
const selectedRoleIds = ref<string[]>([]);
const userOptions = ref<TransferItem[]>([]);
const departOptions = ref<TransferItem[]>([]);
const roleOptions = ref<TransferItem[]>([]);
const departSource = ref<DepartInfo[]>([]);
const departSearchKeyword = ref('');
const departExpandedKeys = ref<Key[]>([]);
const currentTenant = ref<CurrentUserTenant>();

const drawerTitle = computed(() =>
  currentRecord.value?.fileName ? `共享 - ${currentRecord.value.fileName}` : '共享',
);
const transferStyle = {
  height: 'calc(100vh - 250px)',
};
const currentTargetKeys = computed({
  get() {
    if (activeTargetType.value === 'tenant') {
      return [];
    }
    if (activeTargetType.value === 'depart') {
      return selectedDepartIds.value;
    }
    if (activeTargetType.value === 'role') {
      return selectedRoleIds.value;
    }
    return selectedUserIds.value;
  },
  set(keys: string[]) {
    if (activeTargetType.value === 'tenant') {
      return;
    }
    if (activeTargetType.value === 'depart') {
      selectedDepartIds.value = keys;
      return;
    }
    if (activeTargetType.value === 'role') {
      selectedRoleIds.value = keys;
      return;
    }
    selectedUserIds.value = keys;
  },
});
const currentOptions = computed(() => {
  if (activeTargetType.value === 'tenant') {
    return [];
  }
  if (activeTargetType.value === 'depart') {
    return departOptions.value;
  }
  if (activeTargetType.value === 'role') {
    return roleOptions.value;
  }
  return userOptions.value;
});
const departTree = computed(() => buildTreeFromFlat(departSource.value));
const departTreeData = computed<DataNode[]>(() =>
  filterTreeData(
    buildAntTreeData(departTree.value, getDepartTitle) as DataNode[],
    departSearchKeyword.value,
  ),
);
const departCheckedKeys = computed<CheckedKeysValue>(() => ({
  checked: selectedDepartIds.value,
  halfChecked: [],
}));
const selectedDepartItems = computed(() => {
  const itemMap = new Map(departOptions.value.map((item) => [item.key, item]));
  return selectedDepartIds.value
    .map((key) => itemMap.get(key))
    .filter(Boolean) as TransferItem[];
});
const hasAnyShare = computed(
  () =>
    tenantShared.value ||
    selectedUserIds.value.length > 0 ||
    selectedDepartIds.value.length > 0 ||
    selectedRoleIds.value.length > 0,
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[820px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存共享',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

const currentTenantName = computed(() => currentTenant.value?.name || '当前租户');

function toUserItems(users: UserInfo[]): TransferItem[] {
  return users
    .filter((item) => item.id)
    .map((item) => ({
      key: item.id || '',
      title: item.realname || item.username || '',
      description: item.username,
    }));
}

function toDepartItems(departs: DepartInfo[]): TransferItem[] {
  return departs
    .filter((item) => item.id)
    .map((item) => ({
      key: item.id || '',
      title: item.departName || item.orgCode || '',
      description: item.orgCode,
    }));
}

function getDepartTitle(item: DepartInfo) {
  return [item.departName || item.orgCode || '', item.orgCode ? `(${item.orgCode})` : undefined]
    .filter(Boolean)
    .join(' ');
}

function toRoleItems(roles: RoleInfo[]): TransferItem[] {
  return roles
    .filter((item) => item.id)
    .map((item) => ({
      key: item.id || '',
      title: item.roleName || item.roleCode || '',
      description: item.roleCode,
    }));
}

function renderTransferItem(item: { description?: string; title?: string }) {
  return item.description ? `${item.title}（${item.description}）` : item.title;
}

function filterTreeData(nodes: DataNode[], keyword: string): DataNode[] {
  const normalizedKeyword = keyword.trim().toLowerCase();
  if (!normalizedKeyword) {
    return nodes;
  }
  return nodes
    .map((node) => {
      const children = filterTreeData((node.children || []) as DataNode[], normalizedKeyword);
      const title = String(node.title || '').toLowerCase();
      if (title.includes(normalizedKeyword) || children.length > 0) {
        return {
          ...node,
          ...(children.length > 0 ? { children } : {}),
        };
      }
      return undefined;
    })
    .filter(Boolean) as DataNode[];
}

function getCheckedKeyList(keys: CheckedKeysValue | Key[]) {
  return Array.isArray(keys) ? keys : keys.checked;
}

function handleDepartCheck(keys: CheckedKeysValue | Key[]) {
  selectedDepartIds.value = getCheckedKeyList(keys).map(String);
}

function handleRemoveDepart(key: string) {
  selectedDepartIds.value = selectedDepartIds.value.filter((item) => item !== key);
}

function applyShares(shares: DocumentShareInfo[]) {
  tenantShared.value = shares.some((item) => item.targetType === 'tenant');
  selectedUserIds.value = shares
    .filter((item) => item.targetType === 'user' && item.targetId)
    .map((item) => item.targetId || '');
  selectedDepartIds.value = shares
    .filter((item) => item.targetType === 'depart' && item.targetId)
    .map((item) => item.targetId || '');
  selectedRoleIds.value = shares
    .filter((item) => item.targetType === 'role' && item.targetId)
    .map((item) => item.targetId || '');
  permission.value = shares[0]?.permission || 'download';
}

async function loadData(record: DocumentFileInfo) {
  if (!record.id) {
    return;
  }
  loading.value = true;
  try {
    const [users, departs, roles, tenants, shares] = await Promise.all([
      getCurrentTenantUsers(),
      listDeparts(),
      listRoles(),
      getCurrentUserTenantOptions(),
      listDocumentShares(record.id),
    ]);
    userOptions.value = toUserItems(users);
    departSource.value = departs;
    departOptions.value = toDepartItems(departs);
    departExpandedKeys.value = collectExpandableKeys(buildTreeFromFlat(departs));
    roleOptions.value = toRoleItems(roles);
    currentTenant.value = tenants.find((item) => item.current) || tenants[0];
    applyShares(shares);
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!currentRecord.value?.id) {
    return;
  }
  const targets = [
    ...(tenantShared.value && currentTenant.value?.id
      ? [
          {
            permission: permission.value,
            targetId: currentTenant.value.id,
            targetType: 'tenant' as const,
          },
        ]
      : []),
    ...selectedUserIds.value.map((targetId) => ({
      permission: permission.value,
      targetId,
      targetType: 'user' as const,
    })),
    ...selectedDepartIds.value.map((targetId) => ({
      permission: permission.value,
      targetId,
      targetType: 'depart' as const,
    })),
    ...selectedRoleIds.value.map((targetId) => ({
      permission: permission.value,
      targetId,
      targetType: 'role' as const,
    })),
  ];

  try {
    drawerApi.lock();
    await shareDocument({
      enableDown: permission.value === 'read' ? '0' : '1',
      enableUpdat: permission.value === 'update' || permission.value === 'manage' ? '1' : '0',
      fileId: currentRecord.value.id,
      targets,
    });
    message.success('共享已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

function clearShareSelection() {
  tenantShared.value = false;
  selectedUserIds.value = [];
  selectedDepartIds.value = [];
  selectedRoleIds.value = [];
}

function handleCancelShare() {
  if (!currentRecord.value?.id || !hasAnyShare.value) {
    return;
  }
  Modal.confirm({
    cancelText: '取消',
    content: `确认取消“${currentRecord.value.fileName || ''}”的全部共享吗？`,
    okButtonProps: { danger: true },
    okText: '取消共享',
    title: '取消共享',
    async onOk() {
      try {
        drawerApi.lock();
        await shareDocument({
          enableDown: '1',
          enableUpdat: '0',
          fileId: currentRecord.value?.id || '',
          targets: [],
        });
        clearShareSelection();
        message.success('共享已取消');
        emit('success');
        drawerApi.close();
      } finally {
        drawerApi.unlock();
      }
    },
  });
}

async function open(payload: OpenPayload) {
  currentRecord.value = payload.record;
  activeTargetType.value = 'user';
  tenantShared.value = false;
  selectedUserIds.value = [];
  selectedDepartIds.value = [];
  selectedRoleIds.value = [];
  departSource.value = [];
  departSearchKeyword.value = '';
  departExpandedKeys.value = [];
  permission.value = 'download';
  drawerApi.setState({ title: drawerTitle.value }).open();
  await nextTick();
  drawerApi.setState({ title: drawerTitle.value });
  void loadData(payload.record);
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading">
      <Space class="mb-4" align="center">
        <span>权限</span>
        <Select v-model:value="permission" class="w-[180px]">
          <SelectOption value="read">仅预览</SelectOption>
          <SelectOption value="download">允许下载</SelectOption>
          <SelectOption value="update">允许修改</SelectOption>
        </Select>
      </Space>
      <Tabs v-model:active-key="activeTargetType">
        <TabPane key="user" tab="用户" />
        <TabPane key="tenant" tab="租户" />
        <TabPane key="depart" tab="部门" />
        <TabPane key="role" tab="角色" />
      </Tabs>
      <div v-if="activeTargetType === 'tenant'" class="document-share-tenant">
        <Checkbox v-model:checked="tenantShared">
          {{ currentTenantName }}
        </Checkbox>
      </div>
      <div v-else-if="activeTargetType === 'depart'" class="document-share-depart">
        <div class="document-share-depart__panel">
          <div class="document-share-depart__header">
            {{ departOptions.length }} 项可选
          </div>
          <div class="document-share-depart__body">
            <InputSearch
              v-model:value="departSearchKeyword"
              class="document-share-depart__search"
              allow-clear
              placeholder="请输入搜索内容"
            />
            <Tree
              v-if="departTreeData.length > 0"
              v-model:expanded-keys="departExpandedKeys"
              :checked-keys="departCheckedKeys"
              checkable
              check-strictly
              :tree-data="departTreeData"
              @check="handleDepartCheck"
            />
            <Empty v-else class="document-share-depart__empty" description="暂无可选部门" />
          </div>
        </div>
        <div class="document-share-depart__panel">
          <div class="document-share-depart__header">
            {{ selectedDepartItems.length }} 项已选
          </div>
          <div class="document-share-depart__body">
            <InputSearch
              class="document-share-depart__search"
              disabled
              placeholder="请输入搜索内容"
            />
            <div v-if="selectedDepartItems.length > 0" class="document-share-depart__selected">
              <Checkbox
                v-for="item in selectedDepartItems"
                :key="item.key"
                checked
                class="document-share-depart__selected-item"
                @change="handleRemoveDepart(item.key)"
              >
                {{ renderTransferItem(item) }}
              </Checkbox>
            </div>
            <Empty v-else class="document-share-depart__empty" description="暂无已选部门" />
          </div>
        </div>
      </div>
      <Transfer
        v-else-if="currentOptions.length > 0"
        class="document-share-transfer"
        v-model:target-keys="currentTargetKeys"
        :data-source="currentOptions"
        :list-style="transferStyle"
        :render="renderTransferItem"
        :titles="['可选', '已选']"
        show-search
      />
      <Empty v-else description="暂无可选数据" />
    </Spin>
    <template #footer>
      <div class="document-share-footer">
        <Button
          danger
          :disabled="!hasAnyShare || loading"
          @click="handleCancelShare"
        >
          取消共享
        </Button>
        <div class="document-share-footer__actions">
          <Button @click="drawerApi.close()">取消</Button>
          <Button type="primary" @click="handleSubmit">保存共享</Button>
        </div>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.document-share-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  width: 100%;
}

.document-share-tenant {
  padding: 8px 0 16px;
}

.document-share-depart {
  display: grid;
  height: calc(100vh - 250px);
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 48px;
}

.document-share-depart__panel {
  display: flex;
  min-width: 0;
  overflow: hidden;
  flex-direction: column;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  background: hsl(var(--background));
}

.document-share-depart__header {
  flex: 0 0 auto;
  border-bottom: 1px solid hsl(var(--border));
  padding: 9px 12px;
  color: hsl(var(--foreground));
  font-size: 14px;
}

.document-share-depart__body {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 12px;
}

.document-share-depart__search {
  margin-bottom: 12px;
}

.document-share-depart__selected {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.document-share-depart__selected-item {
  margin-inline-start: 0;
}

.document-share-depart__empty {
  padding: 56px 0;
}

:deep(.document-share-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
}

:deep(.document-share-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}

.document-share-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.document-share-footer__actions {
  display: flex;
  gap: 8px;
}
</style>
