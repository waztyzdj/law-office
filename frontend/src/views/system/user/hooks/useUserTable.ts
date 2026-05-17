import { useTable } from '#/composables/Table';
import type { UserInfo } from '#/api/system/user';
import { pageUsers, deleteUser, batchDeleteUsers } from '#/api/system/user';

/**
 * 用户表格逻辑组合式函数
 * @param getSearchParams 获取搜索表单参数的方法（已废弃，保留兼容）
 */
export function useUserTable(_getSearchParams?: () => any) {
  // 使用通用的 useTable（使用配置对象模式）
  const table = useTable({
    // API 配置（必填）
    apiConfig: {
      // 数据获取方法 - 直接使用便捷方法
      fetchData: pageUsers,
      // 删除单个用户 - 使用便捷方法
      deleteItem: deleteUser,
      // 批量删除用户 - 使用便捷方法
      batchDeleteItems: batchDeleteUsers,
    },
    // localStorage 配置（可选）
    storageConfig: {
      filtersKey: 'user_list_filters',
    },
    // 删除对话框配置（可选）
    deleteConfig: {
      title: '确认删除',
      content: (record: UserInfo) => `确定要删除用户"${record.realname}"吗？`,
      batchTitle: '确认批量删除',
      batchContent: (count: number) => `确定要删除选中的 ${count} 个用户吗？`,
    },
    // 启用行选择功能（默认 false）
    enableRowSelection: true,
  });

  /**
   * 加载用户表格数据（简化版本，不再需要包装层）
   */
  const loadData = async (extraFilters?: Record<string, any>) => {
    await table.loadData({}, extraFilters);
  };

  return {
    ...table,
    loadData,
  };
}
