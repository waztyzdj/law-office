<script setup lang="ts">
import { ref, computed } from 'vue';
import { 
  Table, 
  Button, 
  Space, 
  Card
} from 'ant-design-vue';
import type { UserInfo } from '../hooks/useUserApi';
import type { PaginationConfig } from '../hooks/useUserList';
import { getUserColumns } from '../hooks/useUserColumns';

interface Props {
  dataSource: UserInfo[];
  loading: boolean;
  pagination: PaginationConfig;
  selectedRowKeys: string[];
}

interface Emits {
  (e: 'edit', record: UserInfo): void;
  (e: 'delete', record: UserInfo): void;
  (e: 'change', pag: any, filters: any, sorter: any): void;
  (e: 'select-change', keys: (string | number)[]): void;
  (e: 'batch-delete'): void;
  (e: 'add'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// 自定义筛选状态管理
const filterState = ref<Record<string, any>>({});

// 表格列定义（使用配置函数）
const columns = computed(() => getUserColumns(filterState, emit, props.pagination));
</script>

<template>
  <Card class="table-card" style="margin-top: 16px;">
    <div class="table-toolbar">
      <Space>
        <Button type="primary" @click="$emit('add')">新增用户</Button>
        <Button danger @click="$emit('batch-delete')" :disabled="selectedRowKeys.length === 0">
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
        onChange: (keys) => $emit('select-change', keys),
      }"
      row-key="id"
      @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
      bordered
    />
  </Card>
</template>

<style scoped>
.table-toolbar {
  margin-bottom: 16px;
}
</style>
