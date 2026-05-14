import { useTable } from '#/composables/Table';
import type { UserInfo, UserListParams } from '#/api/system/user';
import { getUserListApi, deleteUserApi, batchDeleteUserApi } from '#/api/system/user';

/**
 * 用户表格逻辑组合式函数
 * @param getSearchParams 获取搜索表单参数的方法
 */
export function useUserTable(getSearchParams: () => Partial<UserListParams>) {
  // 使用通用的 useTable
  const table = useTable<UserInfo>(
    // 数据获取方法
    async (params) => {
      const result = await getUserListApi(params as UserListParams);
      return {
        items: result.items || [],
        total: result.total || 0,
      };
    },
    // 删除单个用户（包装以适配类型）
    (id: string | number) => deleteUserApi(String(id)),
    // 批量删除用户（包装以适配类型）
    (ids: (string | number)[]) => batchDeleteUserApi(ids.map(id => String(id))),
    // localStorage 配置
    {
      filtersKey: 'user_list_filters',
    },
    // 删除对话框配置
    {
      title: '确认删除',
      content: (record: UserInfo) => `确定要删除用户"${record.realname}"吗？`,
      batchTitle: '确认批量删除',
      batchContent: (count: number) => `确定要删除选中的 ${count} 个用户吗？`,
    }
  );

  /**
   * 加载用户表格数据（包装一层，自动传入搜索参数）
   */
  const loadData = async (extraFilters?: Record<string, any>) => {
    const searchParams = getSearchParams();
    await table.loadData(searchParams, extraFilters);
  };

  return {
    ...table,
    loadData,
  };
}
