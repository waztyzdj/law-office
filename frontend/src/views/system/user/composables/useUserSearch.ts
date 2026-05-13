import { reactive } from 'vue';

/**
 * 用户搜索表单状态
 */
export interface SearchFormState {
  username: string;
  realname: string;
  phone: string;
  email: string;
  status: number | undefined;
}

/**
 * 用户搜索逻辑组合式函数
 */
export function useUserSearch() {
  // 搜索表单状态
  const searchForm = reactive<SearchFormState>({
    username: '',
    realname: '',
    phone: '',
    email: '',
    status: undefined,
  });

  /**
   * 重置搜索表单
   */
  const resetSearchForm = () => {
    searchForm.username = '';
    searchForm.realname = '';
    searchForm.phone = '';
    searchForm.email = '';
    searchForm.status = undefined;
  };

  /**
   * 获取搜索参数（用于API调用）
   */
  const getSearchParams = () => {
    return {
      username: searchForm.username || undefined,
      realname: searchForm.realname || undefined,
      phone: searchForm.phone || undefined,
      email: searchForm.email || undefined,
      status: searchForm.status,
    };
  };

  return {
    searchForm,
    resetSearchForm,
    getSearchParams,
  };
}
