import { ref, reactive, onMounted } from 'vue';
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
 * 筛选条件类型
 */
export interface FilterCondition {
  condition: string; // like, eq, ne, gt, lt, etc.
  value: any;
}

// localStorage 的 key
const STORAGE_KEY = 'user_list_filters';

/**
 * 从 localStorage 加载筛选条件
 */
const loadFiltersFromStorage = (): Record<string, FilterCondition | any> => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored ? JSON.parse(stored) : {};
  } catch {
    return {};
  }
};

/**
 * 保存筛选条件到 localStorage
 */
const saveFiltersToStorage = (filters: Record<string, FilterCondition | any>) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filters));
  } catch (error) {
    console.error('保存筛选条件失败:', error);
  }
};

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
  
  // 筛选状态（用于列头筛选）- 从 localStorage 初始化
  const activeFilters = ref<Record<string, FilterCondition | any>>(loadFiltersFromStorage());

  /**
   * 将前端筛选条件转换为后端 QueryWrapperBuilderUtils 支持的格式
   * @param filters Table 组件的 filters 参数
   * @returns 符合后端规范的查询参数
   */
  const convertFiltersToQueryParams = (filters: Record<string, any>): Record<string, any> => {
    const queryParams: Record<string, any> = {};
    
    Object.keys(filters).forEach(key => {
      const filterValue = filters[key];
      
      // 如果筛选值为空，跳过
      if (!filterValue || (Array.isArray(filterValue) && filterValue.length === 0)) {
        return;
      }
      
      // 处理高级筛选（包含 condition 和 value）
      if (filterValue.condition && filterValue.value !== undefined) {
        const { condition, value } = filterValue;
        
        // 特殊处理"开头是"和"结尾是"
        if (condition === 'like') {
          // 判断是否是"开头是"或"结尾是"的逻辑可以在这里扩展
          // 目前统一使用 like
          queryParams[`${key}_like`] = value;
        } else {
          // 其他条件直接拼接操作符
          queryParams[`${key}_${condition}`] = value;
        }
      } 
      // 处理日期范围筛选
      else if (Array.isArray(filterValue) && filterValue.length === 2) {
        const [start, end] = filterValue;
        if (start && end) {
          queryParams[`${key}_ge`] = start.format('YYYY-MM-DD HH:mm:ss');
          queryParams[`${key}_le`] = end.format('YYYY-MM-DD HH:mm:ss');
        } else if (start) {
          queryParams[`${key}_ge`] = start.format('YYYY-MM-DD HH:mm:ss');
        } else if (end) {
          queryParams[`${key}_le`] = end.format('YYYY-MM-DD HH:mm:ss');
        }
      }
      // 处理简单值筛选
      else {
        const value = Array.isArray(filterValue) ? filterValue[0] : filterValue;
        if (value !== undefined && value !== null) {
          queryParams[`${key}_eq`] = value;
        }
      }
    });
    
    return queryParams;
  };

  /**
   * 加载用户列表数据
   */
  const loadData = async (extraFilters?: Record<string, any>) => {
    loading.value = true;
    try {
      // 合并搜索表单参数和列头筛选参数
      const searchParams = getSearchParams();
      
      // 如果传入了额外筛选条件，合并到activeFilters并持久化
      if (extraFilters) {
        // 合并新旧筛选条件，保留未被修改的列的筛选条件
        activeFilters.value = {
          ...activeFilters.value,
          ...extraFilters,
        };
        saveFiltersToStorage(activeFilters.value);
      }
      
      const tableFilters = activeFilters.value;
      
      const filterQueryParams = convertFiltersToQueryParams(tableFilters);
      
      const params: UserListParams = {
        current: pagination.current,
        size: pagination.pageSize,
        ...searchParams,
      };
      
      // 如果有筛选条件，添加到 queryParams
      if (Object.keys(filterQueryParams).length > 0) {
        params.queryParams = {
          ...(params.queryParams || {}),
          ...filterQueryParams,
        };
      }
      
      const result = await getUserListApi(params);
      dataSource.value = result.items || [];
      pagination.total = result.total || 0;
    } catch (error) {
      message.error('加载数据失败');
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
   * 分页、排序、筛选变化
   */
  const handleTableChange = (pag: any, filters: any, sorter: any) => {
    pagination.current = pag.current;
    pagination.pageSize = pag.pageSize;
    
    // 更新筛选状态并持久化
    if (filters) {
      activeFilters.value = filters;
      saveFiltersToStorage(filters);
    }
    
    // 处理排序参数
    if (sorter && sorter.field) {
      const orderMap: Record<string, string> = {
        ascend: 'asc',
        descend: 'desc',
      };
      const order = orderMap[sorter.order] || '';
      
      // TODO: 如果需要后端排序，可以将排序信息传递给 loadData
    }
    
    // 重新加载数据（带上筛选条件）
    loadData(filters);
  };

  return {
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
  };
}
