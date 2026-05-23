<script setup lang="ts">
import type { CategoryInfo } from '#/api/system/category';

import { computed, h, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Button, Card, Modal, Space, Table, message } from 'ant-design-vue';

import { deleteCategory, listCategories } from '#/api/system/category';
import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';
import { buildTreeFromFlat, buildTreeSelectOptions, useTreeData } from '#/composables/Tree/useTree';
import CategoryFormDrawer from './components/CategoryFormDrawer.vue';

interface CategoryTreeNode extends CategoryInfo {
  children?: CategoryTreeNode[] | null;
  parentId?: string;
}

const { hasAccessByCodes } = useAccess();
const canEditCategory = computed(() => hasAccessByCodes([permissionCodes.category.edit]));

const categoryFormDrawerRef = ref();

const {
  activeFilters,
  dataSource,
  loading,
  pagination: treePagination,
  handleTableChange,
  loadData,
} = useTreeData<CategoryInfo>({
  fetchData: listCategories,
  storageConfig: {
    filtersKey: 'category_tree_filters',
  },
});

const treeData = computed(() =>
  buildTreeFromFlat<CategoryTreeNode>(
    dataSource.value.map((item) => ({
      ...item,
      parentId: item.pid,
    })),
  ),
);

const treeOptions = computed(() =>
  buildTreeSelectOptions(treeData.value, (node) => node.name || String(node.id ?? '')),
);

function emitTableChange(event: string, ...args: any[]) {
  if (event === 'change') {
    handleTableChange(args[0], args[1], args[2]);
  }
}

const tableConfig = computed(() => {
  const baseColumns: any[] = [
    {
      dataIndex: 'code',
      title: '类型编码',
      options: { width: 180 },
    },
    {
      dataIndex: 'name',
      title: '类型名称',
      options: { width: 220 },
    },
  ];

  if (canEditCategory.value) {
    baseColumns.push({
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 220,
        fixed: 'right' as const,
        hasFilter: false,
        customRender: ({ record }: { record: CategoryInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h('a', { onClick: () => handleAddChild(record) }, '新增子类'),
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

  return defineTableColumns<CategoryInfo>(
    baseColumns,
    activeFilters,
    emitTableChange,
    treePagination,
    { minTableWidth: 860 },
  );
});

function handleAdd() {
  categoryFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: CategoryInfo) {
  categoryFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: CategoryInfo) {
  categoryFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: CategoryInfo) {
  if (!record.id) {
    return;
  }

  Modal.confirm({
    title: '确认删除',
    content: `确认删除类型“${record.name ?? ''}”吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      await deleteCategory(record.id);
      message.success('删除成功');
      await loadData();
    },
  });
}

async function handleSaveSuccess() {
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="system-category-container">
    <Card>
      <div class="table-toolbar">
        <Space>
          <Button
            v-access:code="permissionCodes.category.edit"
            type="primary"
            @click="handleAdd"
          >
            新增类型
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
        default-expand-all-rows
        row-key="id"
        @change="handleTableChange"
      />
    </Card>

    <CategoryFormDrawer
      ref="categoryFormDrawerRef"
      :tree-options="treeOptions"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.system-category-container {
  padding: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>
