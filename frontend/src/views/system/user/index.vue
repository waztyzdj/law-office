<script setup lang="ts">
import { onMounted } from 'vue';
import { message } from 'ant-design-vue';
import UserTable from './components/UserTable.vue';
import { useUserTable } from './hooks/useUserTable';

// 使用表格组合式函数（不需要搜索参数）
const {
  dataSource,
  loading,
  pagination,
  activeFilters,
  loadData,
  handleDelete,
  handleTableChange,
  clearAllFilters,
} = useUserTable(() => ({}));

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
  // 页面首次加载时清除所有筛选缓存
  clearAllFilters();
  // 然后加载数据
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
      :active-filters="activeFilters"
      @edit="handleEdit"
      @delete="handleDelete"
      @change="handleTableChange"
      @add="handleAdd"
    />
  </div>
</template>

<style scoped>
.user-management {
  padding: 16px;
}
</style>
