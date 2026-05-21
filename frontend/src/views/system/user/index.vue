<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { UserInfo } from '#/api/system/user';
import UserTable from './components/UserTable.vue';
import UserFormDrawer from './components/UserFormDrawer.vue';
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
const handleEdit = (record: UserInfo) => {
  userFormDrawerRef.value?.open({
    mode: 'edit',
    record,
  });
};

// 新增用户
const handleAdd = () => {
  userFormDrawerRef.value?.open({
    mode: 'create',
  });
};

const userFormDrawerRef = ref();

const handleSaveSuccess = () => {
  loadData();
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
    <UserFormDrawer ref="userFormDrawerRef" @success="handleSaveSuccess" />
  </div>
</template>

<style scoped>
.user-management {
  padding: 16px;
}
</style>
