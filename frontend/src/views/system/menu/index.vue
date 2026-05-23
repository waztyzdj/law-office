<script setup lang="ts">
import type { PermissionInfo } from '#/api/system/permission';

import { computed, h, onMounted, ref } from 'vue';

import { Button, Card, Modal, Space, Table, message } from 'ant-design-vue';
import { useAccess } from '@vben/access';

import { deletePermission, listPermissions } from '#/api/system/permission';
import { defineTableColumns } from '#/composables/Table';
import { buildTreeFromFlat, buildTreeSelectOptions, useTreeData } from '#/composables/Tree/useTree';
import { menuTypeOptions } from '#/constants/menu-types';
import { permissionCodes } from '#/constants/permissions';
import PermissionFormDrawer from './components/PermissionFormDrawer.vue';

const { hasAccessByCodes } = useAccess();
const canEditPermission = computed(() =>
  hasAccessByCodes([permissionCodes.permission.edit]),
);

const permissionFormDrawerRef = ref();
const {
  activeFilters,
  dataSource,
  loading,
  pagination: treePagination,
  handleTableChange,
  loadData,
} = useTreeData<PermissionInfo>({
  fetchData: listPermissions,
  storageConfig: {
    filtersKey: 'permission_tree_filters',
  },
});

const treeData = computed(() => buildTreeFromFlat(dataSource.value));
const treeOptions = computed(() => buildTreeSelectOptions(treeData.value));

function emitTableChange(event: string, ...args: any[]) {
  if (event === 'change') {
    handleTableChange(args[0], args[1], args[2]);
  }
}

const tableConfig = computed(() => {
  const baseColumns: any[] = [
    {
      dataIndex: 'name',
      title: '名称',
      options: { width: 220 },
    },
    {
      dataIndex: 'menuType',
      title: '类型',
      options: {
        width: 90,
        columnType: 'select' as const,
        selectOptions: menuTypeOptions,
      },
    },
    {
      dataIndex: 'url',
      title: '路径',
      options: { width: 180 },
    },
    {
      dataIndex: 'component',
      title: '组件',
      options: { width: 240 },
    },
    {
      dataIndex: 'perms',
      title: '权限码',
      options: { width: 150 },
    },
    {
      dataIndex: 'sortNo',
      title: '排序',
      options: { width: 80, columnType: 'number' as const },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 90,
        columnType: 'select' as const,
        selectOptions: [
          { label: '正常', value: '1', color: 'green' },
          { label: '停用', value: '0', color: 'red' },
        ],
      },
    },
  ];

  if (canEditPermission.value) {
    baseColumns.push({
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 220,
        fixed: 'right' as const,
        hasFilter: false,
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
      },
    });
  }

  return defineTableColumns<PermissionInfo>(
    baseColumns,
    activeFilters,
    emitTableChange,
    treePagination,
    { minTableWidth: 1180 },
  );
});

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
        </Space>
      </div>

      <Table
        :columns="tableConfig.columns"
        :data-source="treeData"
        :loading="loading"
        :pagination="false"
        :scroll="tableConfig.scroll"
        bordered
        row-key="id"
        @change="handleTableChange"
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
