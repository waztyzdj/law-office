import { ref, reactive } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { UserInfo, UserListParams } from '#/api/system/user';
import { getUserListApi, deleteUserApi, batchDeleteUserApi } from '#/api/system/user';

/**
 * 用户列表分页配置
 */
export interface PaginationConfig {
  current: number;
  pageSize: number;
  total: number;
  showSizeChanger: boolean;
  showQuickJumper: boolean;
  showTotal: (total: number) => string;
}

/**
 * 用户列表逻辑组合式函数
 */
export function useUserList(getSearchParams: () => Partial<UserListParams>) {
  // 表格数据
  const dataSource = ref<UserInfo[]>([]);
  const loading = ref(false);
  
  // 分页配置
  const pagination = reactive<PaginationConfig>({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`,
  });

  // 选中的行
  const selectedRowKeys = ref<string[]>([]);

  /**
   * 加载用户列表数据
   */
  const loadData = async () => {
    loading.value = true;
    try {
      const params: UserListParams = {
        current: pagination.current,
        size: pagination.pageSize,
        ...getSearchParams(),
      };
      
      const result = await getUserListApi(params);
      dataSource.value = result.items || [];
      pagination.total = result.total || 0;
    } catch (error) {
      message.error('加载数据失败');
      console.error(error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * 删除单个用户
   */
  const handleDelete = (record: UserInfo) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除用户"${record.realname}"吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteUserApi(record.id);
          message.success('删除成功');
          await loadData();
        } catch (error) {
          message.error('删除失败');
          console.error(error);
        }
      },
    });
  };

  /**
   * 批量删除用户
   */
  const handleBatchDelete = () => {
    if (selectedRowKeys.value.length === 0) {
      message.warning('请选择要删除的用户');
      return;
    }
    
    Modal.confirm({
      title: '确认批量删除',
      content: `确定要删除选中的 ${selectedRowKeys.value.length} 个用户吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await batchDeleteUserApi(selectedRowKeys.value);
          message.success('批量删除成功');
          selectedRowKeys.value = [];
          await loadData();
        } catch (error) {
          message.error('批量删除失败');
          console.error(error);
        }
      },
    });
  };

  /**
   * 选择行变化
   */
  const onSelectChange = (keys: (string | number)[]) => {
    selectedRowKeys.value = keys.map(key => String(key));
  };

  /**
   * 分页变化
   */
  const handleTableChange = (pag: any) => {
    pagination.current = pag.current;
    pagination.pageSize = pag.pageSize;
    loadData();
  };

  /**
   * 重置选中状态
   */
  const resetSelection = () => {
    selectedRowKeys.value = [];
  };

  return {
    dataSource,
    loading,
    pagination,
    selectedRowKeys,
    loadData,
    handleDelete,
    handleBatchDelete,
    onSelectChange,
    handleTableChange,
    resetSelection,
  };
}
