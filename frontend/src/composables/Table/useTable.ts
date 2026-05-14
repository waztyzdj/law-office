import { ref, reactive } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { Ref } from 'vue';

/**
 * 表格分页配置类型
 */
export interface TablePaginationConfig {
  current: number;
  pageSize: number;
  total: number;
  showSizeChanger?: boolean;
  showQuickJumper?: boolean;
  showTotal?: (total: number) => string;
}

/**
 * 筛选条件类型（用于列头高级筛选）
 */
export interface FilterCondition {
  condition: string; // like, eq, ne, gt, lt, etc.
  value: any;
}

/**
 * 列表数据响应结构
 */
export interface ListResponse<T = any> {
  items: T[];
  total: number;
}

/**
 * 通用列表参数接口
 */
export interface BaseListParams {
  current: number;
  size: number;
  queryParams?: Record<string, any>;
  [key: string]: any;
}

/**
 * 删除操作配置
 */
export interface DeleteConfig {
  title?: string;
  content?: string | ((record: any) => string);
  batchTitle?: string;
  batchContent?: string | ((count: number) => string);
}

/**
 * localStorage 存储键名配置
 */
export interface StorageConfig {
  filtersKey?: string;
}

/**
 * 通用表格列表组合式函数
 * @param fetchData 获取数据的 API 方法
 * @param deleteItem 删除单个项目的 API 方法（可选）
 * @param batchDeleteItems 批量删除的 API 方法（可选）
 * @param storageConfig localStorage 配置（可选）
 * @param deleteConfig 删除对话框配置（可选）
 * @returns 表格相关状态和方法
 */
export function useTable<T = any>(
  fetchData: (params: BaseListParams) => Promise<ListResponse<T>>,
  deleteItem?: (id: string | number) => Promise<any>,
  batchDeleteItems?: (ids: (string | number)[]) => Promise<any>,
  storageConfig?: StorageConfig,
  deleteConfig?: DeleteConfig
) {
  // 表格数据
  const dataSource = ref<T[]>([]);
  const loading = ref(false);

  // 分页配置
  const pagination = reactive<TablePaginationConfig>({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`,
  });

  // 选中的行
  const selectedRowKeys = ref<(string | number)[]>([]);

  // 筛选状态（用于列头筛选）- 从 localStorage 初始化
  const filtersKey = storageConfig?.filtersKey || 'table_list_filters';
  const activeFilters = ref<Record<string, FilterCondition | any>>(
    loadFiltersFromStorage(filtersKey)
  );

  /**
   * 从 localStorage 加载筛选条件
   */
  function loadFiltersFromStorage(key: string): Record<string, FilterCondition | any> {
    try {
      const stored = localStorage.getItem(key);
      return stored ? JSON.parse(stored) : {};
    } catch {
      return {};
    }
  }

  /**
   * 保存筛选条件到 localStorage
   */
  function saveFiltersToStorage(
    filters: Record<string, FilterCondition | any>,
    key: string
  ) {
    try {
      localStorage.setItem(key, JSON.stringify(filters));
    } catch (error) {
      console.error('保存筛选条件失败:', error);
    }
  }

  /**
   * 将前端筛选条件转换为后端 QueryWrapperBuilderUtils 支持的格式
   * @param filters Table 组件的 filters 参数
   * @returns 符合后端规范的查询参数
   */
  function convertFiltersToQueryParams(filters: Record<string, any>): Record<string, any> {
    const queryParams: Record<string, any> = {};

    Object.keys(filters).forEach((key) => {
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
  }

  /**
   * 加载列表数据
   * @param extraSearchParams 额外的搜索参数（来自搜索表单等）
   * @param extraFilters 额外的筛选条件（会合并到 activeFilters 并持久化）
   */
  async function loadData(
    extraSearchParams: Record<string, any> = {},
    extraFilters?: Record<string, any>
  ) {
    loading.value = true;
    try {
      // 如果传入了额外筛选条件，合并到activeFilters并持久化
      if (extraFilters) {
        // 合并新旧筛选条件，保留未被修改的列的筛选条件
        activeFilters.value = {
          ...activeFilters.value,
          ...extraFilters,
        };
        saveFiltersToStorage(activeFilters.value, filtersKey);
      }

      const tableFilters = activeFilters.value;
      const filterQueryParams = convertFiltersToQueryParams(tableFilters);

      const params: BaseListParams = {
        current: pagination.current,
        size: pagination.pageSize,
        ...extraSearchParams,
      };

      // 如果有筛选条件，添加到 queryParams
      if (Object.keys(filterQueryParams).length > 0) {
        params.queryParams = {
          ...(params.queryParams || {}),
          ...filterQueryParams,
        };
      }

      const result = await fetchData(params);
      dataSource.value = result.items || [];
      pagination.total = result.total || 0;
    } catch (error) {
      message.error('加载数据失败');
      console.error('加载数据错误:', error);
    } finally {
      loading.value = false;
    }
  }

  /**
   * 删除单个项目
   */
  function handleDelete(record: any) {
    if (!deleteItem) {
      message.warning('未配置删除方法');
      return;
    }

    const defaultTitle = deleteConfig?.title || '确认删除';
    const defaultContent =
      typeof deleteConfig?.content === 'function'
        ? deleteConfig.content(record)
        : deleteConfig?.content || `确定要删除该项吗？`;

    Modal.confirm({
      title: defaultTitle,
      content: defaultContent,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteItem(record.id);
          message.success('删除成功');
          await loadData();
        } catch (error) {
          message.error('删除失败');
          console.error('删除错误:', error);
        }
      },
    });
  }

  /**
   * 批量删除项目
   */
  function handleBatchDelete() {
    if (!batchDeleteItems) {
      message.warning('未配置批量删除方法');
      return;
    }

    if (selectedRowKeys.value.length === 0) {
      message.warning('请选择要删除的项目');
      return;
    }

    const count = selectedRowKeys.value.length;
    const defaultBatchTitle = deleteConfig?.batchTitle || '确认批量删除';
    const defaultBatchContent =
      typeof deleteConfig?.batchContent === 'function'
        ? deleteConfig.batchContent(count)
        : deleteConfig?.batchContent || `确定要删除选中的 ${count} 个项目吗？`;

    Modal.confirm({
      title: defaultBatchTitle,
      content: defaultBatchContent,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await batchDeleteItems(selectedRowKeys.value);
          message.success('批量删除成功');
          selectedRowKeys.value = [];
          await loadData();
        } catch (error) {
          message.error('批量删除失败');
          console.error('批量删除错误:', error);
        }
      },
    });
  }

  /**
   * 选择行变化
   */
  function onSelectChange(keys: (string | number)[]) {
    selectedRowKeys.value = keys;
  }

  /**
   * 分页、排序、筛选变化
   */
  function handleTableChange(pag: any, filters: any, sorter: any) {
    pagination.current = pag.current;
    pagination.pageSize = pag.pageSize;

    // 更新筛选状态并持久化 - 使用合并策略
    if (filters) {
      // 合并新旧筛选条件,保留未被修改的列的筛选条件
      // 当filters中某列为undefined时,表示该列被重置,应从activeFilters中移除
      const updatedFilters: Record<string, any> = { ...activeFilters.value };

      Object.keys(filters).forEach((key) => {
        if (filters[key] === undefined) {
          // 重置的列,从activeFilters中移除
          delete updatedFilters[key];
        } else {
          // 更新或新增筛选条件
          updatedFilters[key] = filters[key];
        }
      });

      activeFilters.value = updatedFilters;
      saveFiltersToStorage(updatedFilters, filtersKey);
    }

    // 处理排序参数
    if (sorter && sorter.field) {
      const orderMap: Record<string, string> = {
        ascend: 'asc',
        descend: 'desc',
      };
      const order = orderMap[sorter.order] || '';

      // TODO: 如果需要后端排序,可以将排序信息传递给 loadData
      // 例如: loadData(extraSearchParams, undefined, { field: sorter.field, order })
    }

    // 重新加载数据(带上筛选条件)
    loadData();
  }

  /**
   * 清空所有筛选条件
   */
  function clearAllFilters() {
    activeFilters.value = {};
    localStorage.removeItem(filtersKey);
  }

  /**
   * 重置分页到第一页
   */
  function resetPagination() {
    pagination.current = 1;
  }

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
    clearAllFilters,
    resetPagination,
  };
}
