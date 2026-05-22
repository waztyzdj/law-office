<script setup lang="ts">
import type { RoleInfo } from '#/api/system/role';
import type { PermissionInfo } from '#/api/system/permission';
import type { DataNode } from 'ant-design-vue/es/tree';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Button, Empty, Space, Spin, Tree, message } from 'ant-design-vue';

import { getPermissionTree } from '#/api/system/permission';
import { assignRolePermissions, getRolePermissionIds } from '#/api/system/role';

interface CheckedKeysValue {
  checked: string[];
  halfChecked?: string[];
}

const emit = defineEmits<{
  success: [];
}>();

const currentRole = ref<RoleInfo>();
const loading = ref(false);
const checkedKeys = ref<CheckedKeysValue>({ checked: [] });
const expandedKeys = ref<string[]>([]);
const permissionTree = ref<PermissionInfo[]>([]);

const drawerTitle = computed(() =>
  currentRole.value?.roleName
    ? `角色授权 - ${currentRole.value.roleName}`
    : '角色授权',
);

const treeData = computed<DataNode[]>(() => buildTreeData(permissionTree.value));
const allPermissionKeys = computed(() => collectPermissionKeys(permissionTree.value));
const allExpandableKeys = computed(() => collectExpandableKeys(permissionTree.value));

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-1/2! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存授权',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildTreeData(list: PermissionInfo[] = []): DataNode[] {
  return list.map((item) => {
    const titleParts = [item.name];
    if (item.perms) {
      titleParts.push(`(${item.perms})`);
    }
    const children = buildTreeData(item.children || []);

    return {
      key: item.id || '',
      title: titleParts.filter(Boolean).join(' '),
      ...(children.length > 0 ? { children } : {}),
    };
  });
}

function collectExpandedKeysByDepth(list: PermissionInfo[] = [], maxDepth = 1) {
  const keys: string[] = [];
  const walk = (nodes: PermissionInfo[], depth: number) => {
    nodes.forEach((node) => {
      if (node.id && node.children?.length && depth <= maxDepth) {
        keys.push(node.id);
      }
      if (node.children?.length) {
        walk(node.children, depth + 1);
      }
    });
  };
  walk(list, 1);
  return keys;
}

function collectPermissionKeys(list: PermissionInfo[] = []) {
  const keys: string[] = [];
  const walk = (nodes: PermissionInfo[]) => {
    nodes.forEach((node) => {
      if (node.id) {
        keys.push(node.id);
      }
      if (node.children?.length) {
        walk(node.children);
      }
    });
  };
  walk(list);
  return keys;
}

function collectExpandableKeys(list: PermissionInfo[] = []) {
  const keys: string[] = [];
  const walk = (nodes: PermissionInfo[]) => {
    nodes.forEach((node) => {
      if (node.id && node.children?.length) {
        keys.push(node.id);
        walk(node.children);
      }
    });
  };
  walk(list);
  return keys;
}

function getCheckedKeyList(keys: CheckedKeysValue | string[]) {
  return Array.isArray(keys) ? keys : keys.checked;
}

function collectDescendantKeys(list: PermissionInfo[] = [], targetKey?: string) {
  const collect = (nodes: PermissionInfo[]) => {
    const keys: string[] = [];
    nodes.forEach((node) => {
      if (node.id) {
        keys.push(node.id);
      }
      if (node.children?.length) {
        keys.push(...collect(node.children));
      }
    });
    return keys;
  };

  const find = (nodes: PermissionInfo[]): PermissionInfo | undefined => {
    for (const node of nodes) {
      if (node.id === targetKey) {
        return node;
      }
      const match = find(node.children || []);
      if (match) {
        return match;
      }
    }
    return undefined;
  };

  const target = find(list);
  return target?.children?.length ? collect(target.children) : [];
}

function handleCheck(keys: CheckedKeysValue | string[], event: any) {
  const nextKeys = new Set(getCheckedKeyList(keys));
  const nodeKey = String(event?.node?.key ?? '');
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

  checkedKeys.value = { checked: [...nextKeys] };
}

function handleExpandAll() {
  expandedKeys.value = allExpandableKeys.value;
}

function handleCollapseAll() {
  expandedKeys.value = [];
}

function handleCheckAll() {
  checkedKeys.value = { checked: allPermissionKeys.value };
}

function handleUncheckAll() {
  checkedKeys.value = { checked: [] };
}

async function loadData(role: RoleInfo) {
  if (!role.id) {
    return;
  }

  loading.value = true;
  try {
    const [tree, ids] = await Promise.all([
      getPermissionTree(),
      getRolePermissionIds(role.id),
    ]);
    permissionTree.value = tree;
    checkedKeys.value = { checked: ids };
    expandedKeys.value = collectExpandedKeysByDepth(tree, 1);
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
    await assignRolePermissions(currentRole.value.id, checkedKeys.value.checked);
    message.success('角色授权已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(role: RoleInfo) {
  currentRole.value = role;
  permissionTree.value = [];
  checkedKeys.value = { checked: [] };
  expandedKeys.value = [];
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
      message="保存后会覆盖该角色原有权限。菜单权限用于生成导航，按钮权限用于控制页面操作。"
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
      <Empty v-else description="暂无菜单权限数据" />
    </Spin>

    <template #footer>
      <div class="role-permission-footer">
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
.role-permission-footer {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
}
</style>
