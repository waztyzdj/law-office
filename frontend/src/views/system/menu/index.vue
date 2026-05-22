<script setup lang="ts">
import type { PermissionInfo } from '#/api/system/permission';

import { computed, h, onMounted, ref } from 'vue';

import {
  Button,
  Card,
  Modal,
  Space,
  Table,
  Tag,
  message,
} from 'ant-design-vue';
import { useAccess } from '@vben/access';

import { deletePermission, getPermissionTree } from '#/api/system/permission';
import { menuTypeOptionMap, menuTypeValues } from '#/constants/menu-types';
import { permissionCodes } from '#/constants/permissions';
import PermissionFormDrawer from './components/PermissionFormDrawer.vue';

const { hasAccessByCodes } = useAccess();
const canEditPermission = computed(() =>
  hasAccessByCodes([permissionCodes.permission.edit]),
);

const loading = ref(false);
const dataSource = ref<PermissionInfo[]>([]);
const permissionFormDrawerRef = ref();

const treeOptions = computed(() => buildTreeOptions(dataSource.value));

function createHeaderCell() {
  return {
    style: {
      textAlign: 'center',
    },
  };
}

function createBodyCell(textAlign: 'center' | 'left' | 'right') {
  return {
    style: {
      textAlign,
    },
  };
}

const columns = computed(() => {
  const baseColumns: any[] = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      ellipsis: true,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('left'),
    },
    {
      title: '类型',
      dataIndex: 'menuType',
      key: 'menuType',
      width: 90,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('center'),
      customRender: ({ record }: { record: PermissionInfo }) => {
        const item =
          menuTypeOptionMap[record.menuType ?? menuTypeValues.subMenu] ??
          menuTypeOptionMap[menuTypeValues.subMenu];
        return h(Tag, { color: item.color }, () => item.label);
      },
    },
    {
      title: '路径',
      dataIndex: 'url',
      key: 'url',
      width: 180,
      ellipsis: true,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('left'),
    },
    {
      title: '组件',
      dataIndex: 'component',
      key: 'component',
      width: 240,
      ellipsis: true,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('left'),
    },
    {
      title: '权限码',
      dataIndex: 'perms',
      key: 'perms',
      width: 150,
      ellipsis: true,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('left'),
    },
    {
      title: '排序',
      dataIndex: 'sortNo',
      key: 'sortNo',
      width: 80,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('right'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('center'),
      customRender: ({ record }: { record: PermissionInfo }) => {
        const enabled = String(record.status ?? '1') === '1';
        return h(Tag, { color: enabled ? 'green' : 'red' }, () =>
          enabled ? '正常' : '停用',
        );
      },
    },
  ];

  if (canEditPermission.value) {
    baseColumns.push({
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      fixed: 'right',
      width: 220,
      customHeaderCell: createHeaderCell,
      customCell: () => createBodyCell('center'),
      customRender: ({ record }: { record: PermissionInfo }) =>
        h(Space, { size: 'middle' }, () => [
          h('a', { onClick: () => handleAddChild(record) }, '新增下级'),
          h('a', { onClick: () => handleEdit(record) }, '编辑'),
          h(
            'a',
            { style: { color: 'red' }, onClick: () => handleDelete(record) },
            '删除',
          ),
        ]),
    });
  }

  return baseColumns;
});

function buildTreeOptions(list: PermissionInfo[] = []) {
  return list
    .filter((item) => item.id)
    .map((item) => {
      const children = buildTreeOptions(item.children || []);
      return {
        label: item.name || item.id || '',
        value: item.id || '',
        ...(children.length > 0 ? { children } : {}),
      };
    });
}

async function loadData() {
  loading.value = true;
  try {
    dataSource.value = await getPermissionTree();
  } finally {
    loading.value = false;
  }
}

function handleAdd() {
  permissionFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: PermissionInfo) {
  permissionFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: PermissionInfo) {
  permissionFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: PermissionInfo) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除"${record.name}"吗？如存在子级，请先处理子级权限。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      if (!record.id) {
        return;
      }
      await deletePermission(record.id);
      message.success('删除成功');
      await loadData();
    },
  });
}

onMounted(loadData);
</script>

<template>
  <div class="system-menu-container">
    <Card>
      <div class="table-toolbar">
        <Space>
          <Button
            v-access:code="permissionCodes.permission.edit"
            type="primary"
            @click="handleAdd"
          >
            新增菜单
          </Button>
          <Button @click="loadData">刷新</Button>
        </Space>
      </div>

      <Table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1180 }"
        bordered
        row-key="id"
      />
    </Card>

    <PermissionFormDrawer
      ref="permissionFormDrawerRef"
      :tree-options="treeOptions"
      @success="loadData"
    />
  </div>
</template>

<style scoped>
.system-menu-container {
  padding: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>
