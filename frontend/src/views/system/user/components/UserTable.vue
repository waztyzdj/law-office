<script setup lang="ts">
import { computed, toRef } from 'vue';
import { 
  Table, 
  Button, 
  Space, 
  Card
} from 'ant-design-vue';
import type { UserInfo } from '#/api/system/user';
import type { TablePaginationConfig } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';
import { getUserColumns } from '../hooks/useUserColumns';

interface Props {
  dataSource: UserInfo[];
  loading: boolean;
  pagination: TablePaginationConfig;
  activeFilters: Record<string, any>;
}

interface Emits {
  (e: 'assignRole', record: UserInfo): void;
  (e: 'edit', record: UserInfo): void;
  (e: 'delete', record: UserInfo): void;
  (e: 'change', pag: any, filters: any, sorter: any): void;
  (e: 'add'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// 将父组件传入的activeFilters转换为ref，以便getUserColumns正确使用
const filterStateRef = toRef(props, 'activeFilters');

// 表格列定义（使用配置函数，返回 columns 和 scroll）
const tableConfig = computed(() => getUserColumns(filterStateRef, emit, props.pagination));
</script>

<template>
  <Card class="table-card">
    <div class="table-toolbar">
      <Space>
        <Button
          v-access:code="permissionCodes.user.edit"
          type="primary"
          @click="$emit('add')"
        >
          新增用户
        </Button>
      </Space>
    </div>
    
    <Table
      :columns="tableConfig.columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :scroll="tableConfig.scroll"
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
