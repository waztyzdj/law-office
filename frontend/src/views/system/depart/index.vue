<script setup lang="ts">
import type { DepartInfo } from '#/api/system/depart';

import { computed, h, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Button, Card, Modal, Space, Table, message } from 'ant-design-vue';

import { deleteDepart, listDeparts } from '#/api/system/depart';
import { defineTableColumns } from '#/composables/Table';
import { dictCodes } from '#/constants/dict-codes';
import { permissionCodes } from '#/constants/permissions';
import { useDictOptions } from '#/composables/Dict/useDict';
import { useTreeData, buildTreeFromFlat, buildTreeSelectOptions } from '#/composables/Tree/useTree';
import DepartFormDrawer from './components/DepartFormDrawer.vue';

const { hasAccessByCodes } = useAccess();
const canEditDepart = computed(() => hasAccessByCodes([permissionCodes.depart.edit]));

const departFormDrawerRef = ref();
const { options: orgTypeOptions, loadOptions: loadDictOrgTypeOptions } = useDictOptions(
  dictCodes.departOrgType,
);
const {
  activeFilters,
  dataSource,
  loading,
  pagination: treePagination,
  handleTableChange,
  loadData,
} = useTreeData<DepartInfo>({
  fetchData: listDeparts,
  storageConfig: {
    filtersKey: 'depart_tree_filters',
  },
});

const treeData = computed(() => buildTreeFromFlat(dataSource.value));
const treeOptions = computed(() =>
  buildTreeSelectOptions(
    treeData.value,
    (node) => node.departName || String(node.id ?? ''),
  ),
);
const orgTypeSelectOptions = computed(() =>
  orgTypeOptions.value.map((option) => ({
    ...option,
    color: 'blue',
  })),
);

function emitTableChange(event: string, ...args: any[]) {
  if (event === 'change') {
    handleTableChange(args[0], args[1], args[2]);
  }
}

const tableConfig = computed(() => {
  const baseColumns: any[] = [
    {
      dataIndex: 'orgCode',
      title: '机构编码',
      options: { width: 160 },
    },
    {
      dataIndex: 'departName',
      title: '机构名称',
      options: { width: 220 },
    },
    {
      dataIndex: 'departNameEn',
      title: '英文名',
      options: { width: 180 },
    },
    {
      dataIndex: 'departNameAbbr',
      title: '缩写',
      options: { width: 160 },
    },
    {
      dataIndex: 'orgType',
      title: '机构类型',
      options: {
        width: 140,
        columnType: 'select' as const,
        selectOptions: orgTypeSelectOptions.value,
      },
    },
    {
      dataIndex: 'departOrder',
      title: '排序',
      options: { width: 100, columnType: 'number' as const },
    },
    {
      dataIndex: 'mobile',
      title: '手机号',
      options: { width: 140 },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 100,
        columnType: 'select' as const,
        selectOptions: [
          { label: '正常', value: '1', color: 'green' },
          { label: '停用', value: '0', color: 'red' },
        ],
      },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
  ];

  if (canEditDepart.value) {
    baseColumns.push({
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 220,
        fixed: 'right' as const,
        hasFilter: false,
        customRender: ({ record }: { record: DepartInfo }) =>
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

  return defineTableColumns<DepartInfo>(
    baseColumns,
    activeFilters,
    emitTableChange,
    treePagination,
    { minTableWidth: 1720 },
  );
});

async function loadOrgTypeOptions() {
  await loadDictOrgTypeOptions();

  if (orgTypeOptions.value.length === 0) {
    message.warning('机构类型字典为空，请先维护 sys_depart_org_type');
  }
}

function handleAdd() {
  departFormDrawerRef.value?.open({ mode: 'create' });
}

function handleAddChild(record: DepartInfo) {
  departFormDrawerRef.value?.open({
    mode: 'create',
    parentId: record.id,
  });
}

function handleEdit(record: DepartInfo) {
  departFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
}

function handleDelete(record: DepartInfo) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除机构"${record.departName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      if (!record.id) {
        return;
      }
      await deleteDepart(record.id);
      await loadData();
    },
  });
}

function handleSaveSuccess() {
  loadData();
}

onMounted(async () => {
  await Promise.all([loadOrgTypeOptions(), loadData()]);
});
</script>

<template>
  <div class="system-depart-container">
    <Card>
      <div class="table-toolbar">
        <Space>
          <Button
            v-access:code="permissionCodes.depart.edit"
            type="primary"
            @click="handleAdd"
          >
            新增机构
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

    <DepartFormDrawer
      ref="departFormDrawerRef"
      :org-type-options="orgTypeOptions"
      :tree-options="treeOptions"
      @success="handleSaveSuccess"
    />
  </div>
</template>

<style scoped>
.system-depart-container {
  padding: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>
