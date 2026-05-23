<script setup lang="ts">
import type { LogInfo } from '#/api/system/log';

import { computed, h, onMounted } from 'vue';

import { useAccess } from '@vben/access';

import { Card, Space, Table } from 'ant-design-vue';

import { deleteLog, pageLogs } from '#/api/system/log';
import { permissionCodes } from '#/constants/permissions';
import { defineTableColumns, useTable } from '#/composables/Table';

const { hasAccessByCodes } = useAccess();
const canEditLog = computed(() => hasAccessByCodes([permissionCodes.log.edit]));

const logTypeOptions = [
  { color: 'green', label: '登录日志', value: 1 },
  { color: 'blue', label: '操作日志', value: 2 },
  { color: 'purple', label: '租户操作日志', value: 3 },
];

const operateTypeOptions = [
  { color: 'blue', label: '查询', value: 1 },
  { color: 'cyan', label: '按ID查询', value: 2 },
  { color: 'green', label: '保存', value: 3 },
  { color: 'geekblue', label: '批量保存', value: 4 },
  { color: 'red', label: '删除', value: 5 },
  { color: 'volcano', label: '批量删除', value: 6 },
  { color: 'purple', label: '导出', value: 7 },
  { color: 'orange', label: '导入', value: 8 },
  { label: '自定义', value: 99 },
];

const {
  activeFilters,
  dataSource,
  loading,
  pagination,
  handleDelete,
  handleTableChange,
  loadData,
} = useTable({
  apiConfig: {
    fetchData: pageLogs,
    deleteItem: deleteLog,
  },
  storageConfig: {
    filtersKey: 'log_list_filters',
  },
  deleteConfig: {
    title: '确认删除',
    content: (record: LogInfo) => `确认删除日志“${record.logContent ?? ''}”吗？`,
  },
});

function emitTableChange(event: string, ...args: any[]) {
  if (event === 'change') {
    handleTableChange(args[0], args[1], args[2]);
  }
}

const tableConfig = computed(() => {
  const baseColumns: any[] = [
    {
      dataIndex: 'logType',
      title: '日志类型',
      options: {
        width: 120,
        columnType: 'select' as const,
        selectOptions: logTypeOptions,
      },
    },
    {
      dataIndex: 'logContent',
      title: '日志内容',
      options: { width: 260 },
    },
    {
      dataIndex: 'operateType',
      title: '操作类型',
      options: {
        width: 130,
        columnType: 'select' as const,
        selectOptions: operateTypeOptions,
      },
    },
    {
      dataIndex: 'username',
      title: '用户名称',
      options: { width: 140 },
    },
    {
      dataIndex: 'userid',
      title: '用户ID',
      options: { width: 160 },
    },
    {
      dataIndex: 'ip',
      title: 'IP地址',
      options: { width: 140 },
    },
    {
      dataIndex: 'requestUrl',
      title: '请求路径',
      options: { width: 220 },
    },
    {
      dataIndex: 'requestType',
      title: '请求方式',
      options: { width: 100 },
    },
    {
      dataIndex: 'costTime',
      title: '耗时(ms)',
      options: { width: 110, columnType: 'number' as const },
    },
    {
      dataIndex: 'clientType',
      title: '客户端',
      options: { width: 120 },
    },
  ];

  if (canEditLog.value) {
    baseColumns.push({
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 120,
        fixed: 'right' as const,
        hasFilter: false,
        customRender: ({ record }: { record: LogInfo }) =>
          h(Space, { size: 'middle' }, () => [
            h(
              'a',
              { style: { color: 'red' }, onClick: () => handleDelete(record) },
              '删除',
            ),
          ]),
      },
    });
  }

  return defineTableColumns<LogInfo>(
    baseColumns,
    activeFilters,
    emitTableChange,
    pagination,
    { minTableWidth: 1680 },
  );
});

onMounted(async () => {
  await loadData();
});
</script>

<template>
  <div class="system-log-container">
    <Card>

      <Table
        :columns="tableConfig.columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :scroll="tableConfig.scroll"
        bordered
        row-key="id"
        @change="handleTableChange"
      />
    </Card>
  </div>
</template>

<style scoped>
.system-log-container {
  padding: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}
</style>

