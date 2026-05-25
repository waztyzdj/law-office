<script setup lang="ts">
import type { PermissionInfo } from '#/api/system/permission';
import type { TenantInfo } from '#/api/system/tenant';
import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Button, Empty, Space, Spin, Tree, message } from 'ant-design-vue';

import { getGrantablePermissionTree } from '#/api/system/permission';
import {
  assignTenantAdminPermissions,
  getTenantAdminPermissionIds,
} from '#/api/system/tenant';
import {
  buildAntTreeData,
  collectDescendantKeys,
  collectExpandableKeys,
  collectExpandedKeysByDepth,
  collectTreeKeys,
} from '#/composables/Tree/useTree';

interface CheckedKeysValue {
  checked: Key[];
  halfChecked: Key[];
}

const emit = defineEmits<{
  success: [];
}>();

const currentTenant = ref<TenantInfo>();
const loading = ref(false);
const checkedKeys = ref<CheckedKeysValue>({ checked: [], halfChecked: [] });
const expandedKeys = ref<Key[]>([]);
const permissionTree = ref<PermissionInfo[]>([]);

const drawerTitle = computed(() =>
  currentTenant.value?.name
    ? `租户管理员授权 - ${currentTenant.value.name}`
    : '租户管理员授权',
);

const treeData = computed<DataNode[]>(() =>
  buildAntTreeData(permissionTree.value, getPermissionTitle) as DataNode[],
);
const allPermissionKeys = computed(() => collectTreeKeys(permissionTree.value));
const allExpandableKeys = computed(() => collectExpandableKeys(permissionTree.value));

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-1/2! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存授权',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function getCheckedKeyList(keys: CheckedKeysValue | Key[]) {
  return Array.isArray(keys) ? keys : keys.checked;
}

function getPermissionTitle(item: PermissionInfo) {
  return [item.name, item.perms ? `(${item.perms})` : undefined]
    .filter(Boolean)
    .join(' ');
}

function handleCheck(keys: CheckedKeysValue | Key[], event: any) {
  const nextKeys = new Set(getCheckedKeyList(keys));
  const nodeKey = event?.node?.key as Key | undefined;
  if (nodeKey === undefined) {
    checkedKeys.value = { checked: [...nextKeys], halfChecked: [] };
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

  checkedKeys.value = { checked: [...nextKeys], halfChecked: [] };
}

function handleExpandAll() {
  expandedKeys.value = allExpandableKeys.value;
}

function handleCollapseAll() {
  expandedKeys.value = [];
}

function handleCheckAll() {
  checkedKeys.value = { checked: allPermissionKeys.value, halfChecked: [] };
}

function handleUncheckAll() {
  checkedKeys.value = { checked: [], halfChecked: [] };
}

async function loadData(tenant: TenantInfo) {
  if (!tenant.id) {
    return;
  }

  loading.value = true;
  try {
    const [tree, ids] = await Promise.all([
      getGrantablePermissionTree(),
      getTenantAdminPermissionIds(tenant.id),
    ]);
    permissionTree.value = tree;
    const visiblePermissionKeys = new Set(collectTreeKeys(tree).map(String));
    checkedKeys.value = {
      checked: ids.filter((id) => visiblePermissionKeys.has(String(id))),
      halfChecked: [],
    };
    expandedKeys.value = collectExpandedKeysByDepth(tree, 1);
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
    await assignTenantAdminPermissions(
      currentTenant.value.id,
      checkedKeys.value.checked.map(String),
    );
    message.success('租户管理员授权已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(tenant: TenantInfo) {
  currentTenant.value = tenant;
  permissionTree.value = [];
  checkedKeys.value = { checked: [], halfChecked: [] };
  expandedKeys.value = [];
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
      message="保存后会覆盖该租户默认管理员角色的原有权限。可授权范围不会超过当前账号可授予的权限。"
      show-icon
      type="info"
    />
    <Spin :spinning="loading">
      <Tree
        v-if="treeData.length > 0"
        v-model:expanded-keys="expandedKeys"
        :checked-keys="checkedKeys"
        :tree-data="treeData"
        checkable
        check-strictly
        default-expand-all
        @check="handleCheck"
      />
      <Empty v-else description="暂无可授权权限数据" />
    </Spin>

    <template #footer>
      <div class="tenant-admin-permission-footer">
        <Space>
          <Button type="primary" @click="handleExpandAll">全部展开</Button>
          <Button type="primary" @click="handleCollapseAll">全部折叠</Button>
          <Button type="primary" @click="handleCheckAll">全部勾选</Button>
          <Button type="primary" @click="handleUncheckAll">全部取消</Button>
        </Space>
        <Space>
          <Button @click="drawerApi.close()">取消</Button>
          <Button type="primary" @click="handleSubmit">保存授权</Button>
        </Space>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.tenant-admin-permission-footer {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
}
</style>
