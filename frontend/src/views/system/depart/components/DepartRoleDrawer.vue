<script setup lang="ts">
import type { DepartInfo, DepartRoleInfo } from '#/api/system/depart';
import type { PermissionInfo } from '#/api/system/permission';
import type { UserInfo } from '#/api/system/user';
import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  Modal,
  Space,
  Spin,
  Transfer,
  Tree,
  message,
} from 'ant-design-vue';

import {
  assignDepartRolePermissions,
  assignDepartRoleUsers,
  deleteDepartRole,
  getDepartGrantablePermissionTree,
  getDepartRolePermissionIds,
  getDepartRoleUserIds,
  getDepartRoles,
  getDepartUserIds,
  getDepartUsers,
  saveDepartRole,
} from '#/api/system/depart';
import {
  buildAntTreeData,
  collectDescendantKeys,
  collectExpandedKeysByDepth,
  collectTreeKeys,
} from '#/composables/Tree/useTree';
import { BaseTable } from '#/components/BaseTable';

import { getDepartRoleColumns } from '../hooks/useDepartRoleColumns';

interface CheckedKeysValue {
  checked: Key[];
  halfChecked: Key[];
}

interface TransferItem {
  key: string;
  title: string;
  description?: string;
}

const DEFAULT_ROLE_DESCRIPTION = '部门默认角色';

const emit = defineEmits<{
  success: [];
}>();

const currentDepart = ref<DepartInfo>();
const loading = ref(false);
const roles = ref<DepartRoleInfo[]>([]);
const selectedRoleId = ref<string>();
const selectedDepartUserIds = ref<string[]>([]);
const selectedRoleUserIds = ref<string[]>([]);
const roleMemberOptions = ref<TransferItem[]>([]);
const permissionTree = ref<PermissionInfo[]>([]);
const roleCheckedKeys = ref<CheckedKeysValue>({ checked: [], halfChecked: [] });
const expandedKeys = ref<Key[]>([]);
const roleModalOpen = ref(false);
const roleModalMode = ref<'create' | 'edit'>('create');
const roleForm = ref<DepartRoleInfo>({});
const roleSaving = ref(false);
const roleUserSaving = ref(false);
const rolePermissionModalOpen = ref(false);
const roleUserModalOpen = ref(false);
const rolePermissionLoading = ref(false);
const roleUserLoading = ref(false);
const roleFilterState = ref({});
const rolePagination = { pageNum: 1, pageSize: 10, total: 0 };

const drawerTitle = computed(() =>
  currentDepart.value?.departName
    ? `部门角色 - ${currentDepart.value.departName}`
    : '部门角色',
);
const treeData = computed<DataNode[]>(() =>
  buildAntTreeData(permissionTree.value, getPermissionTitle) as DataNode[],
);
const allPermissionKeys = computed(() => collectTreeKeys(permissionTree.value));
const selectedRole = computed(() =>
  roles.value.find((role) => role.id === selectedRoleId.value),
);
const selectedRoleIsDefault = computed(() => isDefaultRole(selectedRole.value));
const roleModalTitle = computed(() =>
  roleModalMode.value === 'create' ? '新增部门角色' : '编辑部门角色',
);
const roleTableConfig = computed(() =>
  getDepartRoleColumns(roleFilterState, roleTableEmit, rolePagination),
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[1120px]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'depart-role-content px-5 py-4 sm:px-6',
  footer: false,
  title: drawerTitle.value,
});

function roleTableEmit(event: string, record: DepartRoleInfo) {
  if (event === 'edit') {
    openEditRole(record);
    return;
  }
  if (event === 'delete') {
    handleDeleteRole(record);
    return;
  }
  if (event === 'assign') {
    void openRolePermission(record);
    return;
  }
  if (event === 'members') {
    void openRoleUsers(record);
  }
}

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

function getPermissionTitle(item: PermissionInfo) {
  return [item.name, item.perms ? `(${item.perms})` : undefined]
    .filter(Boolean)
    .join(' ');
}

function isDefaultRole(role?: DepartRoleInfo) {
  return role?.defaultRole === true || role?.description === DEFAULT_ROLE_DESCRIPTION;
}

function getCheckedKeyList(keys: CheckedKeysValue | Key[]) {
  return Array.isArray(keys) ? keys : keys.checked;
}

function handleCheck(keys: CheckedKeysValue | Key[], event: any) {
  const nextKeys = new Set(getCheckedKeyList(keys));
  const nodeKey = event?.node?.key as Key | undefined;
  if (nodeKey === undefined) {
    roleCheckedKeys.value = { checked: [...nextKeys], halfChecked: [] };
    return;
  }

  const descendantKeys = collectDescendantKeys(permissionTree.value, nodeKey);
  if (descendantKeys.length > 0) {
    if (event?.checked) {
      nextKeys.add(nodeKey);
      descendantKeys.forEach((key) => nextKeys.add(key));
    } else {
      nextKeys.delete(nodeKey);
      descendantKeys.forEach((key) => nextKeys.delete(key));
    }
  }

  roleCheckedKeys.value = { checked: [...nextKeys], halfChecked: [] };
}

function handleCheckAll() {
  roleCheckedKeys.value = { checked: allPermissionKeys.value, halfChecked: [] };
}

function handleUncheckAll() {
  roleCheckedKeys.value = { checked: [], halfChecked: [] };
}

function handleExpandAll() {
  expandedKeys.value = allPermissionKeys.value;
}

function handleCollapseAll() {
  expandedKeys.value = [];
}

async function loadData(departId: string) {
  loading.value = true;
  try {
    const [departUsers, userIds, roleList] = await Promise.all([
      getDepartUsers(departId),
      getDepartUserIds(departId),
      getDepartRoles(departId),
    ]);
    roleMemberOptions.value = toTransferItems(departUsers);
    selectedDepartUserIds.value = userIds;
    roles.value = roleList;
  } finally {
    loading.value = false;
  }
}

async function loadRoles() {
  if (!currentDepart.value?.id) {
    return;
  }
  roles.value = await getDepartRoles(currentDepart.value.id);
}

async function loadSelectedRolePermissions() {
  if (!selectedRoleId.value) {
    permissionTree.value = [];
    roleCheckedKeys.value = { checked: [], halfChecked: [] };
    expandedKeys.value = [];
    return;
  }

  const [tree, ids] = await Promise.all([
    getDepartGrantablePermissionTree(),
    getDepartRolePermissionIds(selectedRoleId.value),
  ]);
  permissionTree.value = tree;
  roleCheckedKeys.value = { checked: ids, halfChecked: [] };
  expandedKeys.value = collectExpandedKeysByDepth(tree, 1);
}

async function loadSelectedRoleUsers() {
  if (!selectedRoleId.value) {
    selectedRoleUserIds.value = [];
    return;
  }

  selectedRoleUserIds.value = selectedRoleIsDefault.value
    ? selectedDepartUserIds.value
    : await getDepartRoleUserIds(selectedRoleId.value);
}

async function handleSaveRolePermissions() {
  if (!selectedRoleId.value) {
    return;
  }

  await assignDepartRolePermissions(
    selectedRoleId.value,
    roleCheckedKeys.value.checked.map(String),
  );
  message.success('部门角色权限已保存');
  emit('success');
  rolePermissionModalOpen.value = false;
}

async function openRolePermission(role: DepartRoleInfo) {
  if (!role.id) {
    return;
  }

  selectedRoleId.value = role.id;
  rolePermissionModalOpen.value = true;
  rolePermissionLoading.value = true;
  try {
    await loadSelectedRolePermissions();
  } finally {
    rolePermissionLoading.value = false;
  }
}

async function openRoleUsers(role: DepartRoleInfo) {
  if (!role.id) {
    return;
  }

  selectedRoleId.value = role.id;
  roleUserModalOpen.value = true;
  roleUserLoading.value = true;
  try {
    await loadSelectedRoleUsers();
  } finally {
    roleUserLoading.value = false;
  }
}

function openCreateRole() {
  if (!currentDepart.value?.id) {
    return;
  }
  roleModalMode.value = 'create';
  roleForm.value = {
    departId: currentDepart.value.id,
    roleCode: '',
    roleName: '',
    description: '',
  };
  roleModalOpen.value = true;
}

function openEditRole(role: DepartRoleInfo) {
  if (isDefaultRole(role)) {
    return;
  }
  roleModalMode.value = 'edit';
  roleForm.value = { ...role };
  roleModalOpen.value = true;
}

async function handleSaveRole() {
  const departId = currentDepart.value?.id;
  if (!departId) {
    return;
  }
  if (!roleForm.value.roleName?.trim()) {
    message.warning('请输入角色名称');
    return;
  }
  if (!roleForm.value.roleCode?.trim()) {
    message.warning('请输入角色编码');
    return;
  }
  if (/^(ADMIN|DEPART)/.test(roleForm.value.roleCode.trim())) {
    message.warning('自定义角色编码不能以 DEPART 或 ADMIN 开头');
    return;
  }

  roleSaving.value = true;
  try {
    const payload: DepartRoleInfo = {
      ...roleForm.value,
      departId,
      description: roleForm.value.description?.trim() || undefined,
      roleCode: roleForm.value.roleCode.trim(),
      roleName: roleForm.value.roleName.trim(),
    };
    await saveDepartRole(payload);
    message.success(roleModalMode.value === 'create' ? '部门角色已新增' : '部门角色已保存');
    roleModalOpen.value = false;
    await loadRoles();
  } finally {
    roleSaving.value = false;
  }
}

function handleDeleteRole(role: DepartRoleInfo) {
  if (!role.id || isDefaultRole(role)) {
    return;
  }

  Modal.confirm({
    title: '确认删除',
    content: `确定要删除部门角色"${role.roleName || ''}"吗？`,
    async onOk() {
      await deleteDepartRole(role.id || '');
      message.success('部门角色已删除');
      await loadRoles();
    },
  });
}

async function handleSaveRoleUsers() {
  if (!selectedRoleId.value || selectedRoleIsDefault.value) {
    return;
  }

  roleUserSaving.value = true;
  try {
    await assignDepartRoleUsers(selectedRoleId.value, selectedRoleUserIds.value);
    message.success('部门角色成员已保存');
    emit('success');
    roleUserModalOpen.value = false;
  } finally {
    roleUserSaving.value = false;
  }
}

async function open(record: DepartInfo) {
  currentDepart.value = record;
  roles.value = [];
  selectedRoleId.value = undefined;
  selectedRoleUserIds.value = [];
  selectedDepartUserIds.value = [];
  roleMemberOptions.value = [];
  permissionTree.value = [];
  roleModalOpen.value = false;
  rolePermissionModalOpen.value = false;
  roleUserModalOpen.value = false;
  roleForm.value = {};
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
    <Spin :spinning="loading" wrapper-class-name="depart-role-spinner">
      <div class="depart-role-shell">
        <div class="role-toolbar">
          <Button type="primary" @click="openCreateRole">新增角色</Button>
        </div>
        <BaseTable
          class="fill-table depart-role-table"
          :columns="roleTableConfig.columns"
          :data-source="roles"
          :pagination="false"
          :scroll="roleTableConfig.scroll"
          row-key="id"
          :show-card="false"
          :show-toolbar="false"
          size="small"
        />
      </div>
    </Spin>

    <Modal
      v-model:open="roleModalOpen"
      :confirm-loading="roleSaving"
      :title="roleModalTitle"
      @ok="handleSaveRole"
    >
      <Form :model="roleForm" layout="vertical">
        <FormItem label="角色名称" required>
          <Input v-model:value="roleForm.roleName" :maxlength="1024" />
        </FormItem>
        <FormItem label="角色编码" required>
          <Input
            v-model:value="roleForm.roleCode"
            :disabled="roleModalMode === 'edit'"
            :maxlength="64"
          />
        </FormItem>
        <FormItem label="描述">
          <Input.TextArea v-model:value="roleForm.description" :maxlength="1024" :rows="3" />
        </FormItem>
      </Form>
    </Modal>

    <Modal
      v-model:open="rolePermissionModalOpen"
      :confirm-loading="rolePermissionLoading"
      :footer="null"
      :title="`${selectedRole?.roleName || ''} - 授权`"
      width="820px"
    >
      <Spin :spinning="rolePermissionLoading">
        <div class="modal-tree-area">
          <Tree
            v-if="treeData.length > 0 && selectedRoleId"
            v-model:expanded-keys="expandedKeys"
            :checked-keys="roleCheckedKeys"
            :tree-data="treeData"
            checkable
            check-strictly
            @check="handleCheck"
          />
          <Empty v-else class="fill-empty" description="暂无角色权限数据" />
        </div>
      </Spin>
      <div class="modal-permission-footer">
        <Space wrap>
          <Button type="primary" @click="handleCheckAll">全部勾选</Button>
          <Button type="primary" @click="handleUncheckAll">全部取消</Button>
          <Button type="primary" @click="handleExpandAll">全部展开</Button>
          <Button type="primary" @click="handleCollapseAll">全部折叠</Button>
        </Space>
        <Space>
          <Button @click="rolePermissionModalOpen = false">取消</Button>
          <Button
            type="primary"
            :loading="rolePermissionLoading"
            @click="handleSaveRolePermissions"
          >
            确定
          </Button>
        </Space>
      </div>
    </Modal>

    <Modal
      v-model:open="roleUserModalOpen"
      :confirm-loading="roleUserSaving || roleUserLoading"
      :ok-button-props="{ disabled: selectedRoleIsDefault || !selectedRoleId }"
      :title="`${selectedRole?.roleName || ''} - 成员`"
      width="760px"
      @ok="handleSaveRoleUsers"
    >
      <Spin :spinning="roleUserLoading">
        <Transfer
          v-if="roleMemberOptions.length > 0 && selectedRoleId"
          class="modal-transfer"
          v-model:target-keys="selectedRoleUserIds"
          :data-source="roleMemberOptions"
          :disabled="selectedRoleIsDefault"
          :render="renderTransferItem"
          :titles="['部门成员', selectedRoleIsDefault ? '默认角色成员' : '角色成员']"
          show-search
        />
        <Empty v-else description="暂无部门成员" />
      </Spin>
    </Modal>
  </Drawer>
</template>

<style scoped>
:global(.depart-role-content) {
  display: flex;
  height: calc(100vh - 65px);
  min-height: 0;
  flex-direction: column;
}

:global(.depart-role-spinner),
:global(.depart-role-spinner > .ant-spin-container) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.depart-role-shell {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.role-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.depart-role-table {
  flex: 1;
}

:deep(.depart-role-table .depart-role-action-cell) {
  width: 160px !important;
  min-width: 160px !important;
  max-width: 160px !important;
}

:deep(.depart-role-table .depart-role-action-links) {
  justify-content: center;
  width: 100%;
  white-space: nowrap;
}

.fill-empty {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

.fill-table {
  min-height: 0;
  flex: 1;
}

:deep(.fill-table .ant-table-wrapper),
:deep(.fill-table .ant-spin-nested-loading),
:deep(.fill-table .ant-spin-container) {
  height: 100%;
}

.modal-tree-area {
  height: 640px;
  overflow: auto;
}

.modal-permission-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px -24px -20px;
  padding: 16px 24px;
  border-top: 1px solid rgba(5, 5, 5, 6%);
}

.modal-transfer {
  display: flex;
  align-items: stretch;
  gap: 12px;
  width: 100%;
}

:deep(.modal-transfer .ant-transfer-list) {
  flex: 1 1 0;
  width: auto !important;
  min-width: 0;
  height: 440px !important;
}

:deep(.modal-transfer .ant-transfer-operation) {
  flex: 0 0 auto;
  margin: 0;
  align-self: center;
}
</style>
