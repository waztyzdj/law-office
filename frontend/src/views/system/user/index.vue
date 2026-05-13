<script setup lang="ts">
import { onMounted } from 'vue';
import { message } from 'ant-design-vue';
import UserSearchForm from './components/UserSearchForm.vue';
import UserTable from './components/UserTable.vue';
import { useUserSearch } from './composables/useUserSearch';
import { useUserList } from './composables/useUserList';

// 使用搜索组合式函数
const { searchForm, resetSearchForm, getSearchParams } = useUserSearch();

// 使用列表组合式函数
const {
  dataSource,
  loading,
  pagination,
  selectedRowKeys,
  loadData,
  handleDelete,
  handleBatchDelete,
  onSelectChange,
  handleTableChange,
} = useUserList(getSearchParams);

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  loadData();
};

// 重置
const handleReset = () => {
  resetSearchForm();
  pagination.current = 1;
  loadData();
};

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
  loadData();
});
</script>

<template>
  <div class="user-management">
    <!-- 搜索区域 -->
    <UserSearchForm 
      :search-form="searchForm"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 表格区域 -->
    <UserTable
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :selected-row-keys="selectedRowKeys"
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
