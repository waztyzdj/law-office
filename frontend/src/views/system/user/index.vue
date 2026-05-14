<script setup lang="ts">
import { onMounted, watch } from 'vue';
import { message } from 'ant-design-vue';
import UserTable from './components/UserTable.vue';
import { useUserList } from './hooks/useUserList';

// 使用列表组合式函数（不需要搜索参数）
const {
  dataSource,
  loading,
  pagination,
  selectedRowKeys,
  activeFilters,
  loadData,
  handleDelete,
  handleBatchDelete,
  onSelectChange,
  handleTableChange,
} = useUserList(() => ({}));

// 调试：监听 activeFilters 的变化
watch(activeFilters, (newVal) => {
  console.log('activeFilters 变化:', newVal);
}, { deep: true });

// 编辑用户
const handleEdit = (record: any) => {
  message.info(`编辑用户：${record.username}`);
  // TODO: 打开编辑对话框
};

// 新增用户
const handleAdd = () => {
  message.info('新增用户功能待实现');
  // TODO: 打开新增对话框
};

// 初始化加载
onMounted(() => {
  console.log('页面加载，activeFilters:', activeFilters.value);
  loadData();
});
</script>

<template>
  <div class="user-management">
    <!-- 表格区域 -->
    <UserTable
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :selected-row-keys="selectedRowKeys"
      :active-filters="activeFilters"
      @edit="handleEdit"
      @delete="handleDelete"
      @change="handleTableChange"
      @select-change="onSelectChange"
      @batch-delete="handleBatchDelete"
      @add="handleAdd"
    />
  </div>
</template>

<style scoped>
.user-management {
  padding: 16px;
}
</style>
