<script setup lang="ts">
import { h } from 'vue';
import { 
  Table, 
  Button, 
  Space, 
  Tag, 
  Card 
} from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { UserInfo } from '#/api/system/user';
import type { PaginationConfig } from '../composables/useUserList';
import dayjs from 'dayjs';

interface Props {
  dataSource: UserInfo[];
  loading: boolean;
  pagination: PaginationConfig;
  selectedRowKeys: string[];
}

interface Emits {
  (e: 'edit', record: UserInfo): void;
  (e: 'delete', record: UserInfo): void;
  (e: 'change', pag: any): void;
  (e: 'selectChange', keys: (string | number)[]): void;
  (e: 'batchDelete'): void;
  (e: 'add'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// 表格列定义
const columns: ColumnsType<UserInfo> = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username',
    width: 120,
  },
  {
    title: '真实姓名',
    dataIndex: 'realname',
    key: 'realname',
    width: 120,
  },
  {
    title: '性别',
    dataIndex: 'sex',
    key: 'sex',
    width: 80,
    customRender: ({ record }) => {
      const sexMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };
      return sexMap[record.sex ?? 0] || '未知';
    },
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    key: 'email',
    width: 180,
  },
  {
    title: '电话',
    dataIndex: 'phone',
    key: 'phone',
    width: 130,
  },
  {
    title: '工号',
    dataIndex: 'workNo',
    key: 'workNo',
    width: 120,
  },
  {
    title: '职务',
    dataIndex: 'post',
    key: 'post',
    width: 120,
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
    customRender: ({ record }) => {
      const statusMap: Record<number, { text: string; color: string }> = {
        1: { text: '正常', color: 'green' },
        2: { text: '冻结', color: 'red' },
      };
      const status = statusMap[record.status ?? 1];
      if (!status) {
        return h(Tag, { color: 'default' }, () => '未知');
      }
      return h(Tag, { color: status.color }, () => status.text);
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
    customRender: ({ record }) => {
      return record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-';
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    fixed: 'right',
    customRender: ({ record }) => {
      return h(Space, { size: 'middle' }, {
        default: () => [
          h('a', { onClick: () => emit('edit', record) }, '编辑'),
          h('a', { style: { color: 'red' }, onClick: () => emit('delete', record) }, '删除'),
        ],
      });
    },
  },
];
</script>

<template>
  <Card class="table-card" style="margin-top: 16px;">
    <div class="table-toolbar">
      <Space>
        <Button type="primary" @click="$emit('add')">新增用户</Button>
        <Button danger @click="$emit('batchDelete')" :disabled="selectedRowKeys.length === 0">
          批量删除
        </Button>
      </Space>
    </div>
    
    <Table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :row-selection="{
        selectedRowKeys: selectedRowKeys,
        onChange: (keys) => $emit('selectChange', keys),
      }"
      row-key="id"
      @change="(pag) => $emit('change', pag)"
      bordered
    />
  </Card>
</template>

<style scoped>
.table-toolbar {
  margin-bottom: 16px;
}
</style>
