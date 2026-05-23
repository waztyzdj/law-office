import { ref, reactive, computed, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { getCustomPreferences } from '@vben/preferences';
import type { BasePageReq } from '#/framework/api/base.api';

/**
 * 表格分页配置类型
 */
export interface TablePaginationConfig {
  pageNum: number;
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
 * API 配置接口
 */
export interface ApiConfig {
  /** 
   * 获取数据的 API 方法（必填）
   * 
   * 重要：必须使用箭头函数包装 BaseApi 方法以保持 this 上下文！
   * 
   * ✅ 正确示例：
   * fetchData: (params) => userApi.page(params)
   * 
   * ❌ 错误示例（会导致 this 丢失）：
   * fetchData: userApi.page
   */
  fetchData: (params: BasePageReq) => Promise<any>;
  /** 删除单个项目的 API 方法（可选） */
  deleteItem?: (id: string | number) => Promise<any>;
  /** 批量删除的 API 方法（可选） */
  batchDeleteItems?: (ids: (string | number)[]) => Promise<any>;
}

/**
 * useTable 配置接口
 */
export interface UseTableConfig {
  /** API 配置（必填） */
  apiConfig: ApiConfig;
  /** localStorage 配置（可选） */
  storageConfig?: StorageConfig;
  /** 删除对话框配置（可选） */
  deleteConfig?: DeleteConfig;
  /** 是否启用行选择功能，默认为 false */
  enableRowSelection?: boolean;
}

/**
 * 将前端筛选条件转换为后端 QueryWrapperBuilderUtils 支持的格式
 * @param filters Table 组件的 filters 参数
 * @returns 符合后端规范的查询参数
 */
export function convertTableFiltersToQueryParams(filters: Record<string, any>): Record<string, any> {
  const queryParams: Record<string, any> = {};

  Object.keys(filters).forEach((key) => {
    const filterValue = filters[key];

    if (!filterValue || (Array.isArray(filterValue) && filterValue.length === 0)) {
      return;
    }

    if (filterValue.condition && filterValue.value !== undefined) {
      const { condition, apiCondition, value } = filterValue;
      const effectiveCondition = apiCondition || condition;

      if (effectiveCondition === 'like') {
        queryParams[`${key}_like`] = value;
      } else {
        queryParams[`${key}_${effectiveCondition}`] = value;
      }
    } else if (Array.isArray(filterValue) && filterValue.length === 2) {
      const [start, end] = filterValue;
      if (start && end) {
        queryParams[`${key}_ge`] =
          typeof start?.format === 'function' ? start.format('YYYY-MM-DD HH:mm:ss') : start;
        queryParams[`${key}_le`] =
          typeof end?.format === 'function' ? end.format('YYYY-MM-DD HH:mm:ss') : end;
      } else if (start) {
        queryParams[`${key}_ge`] =
          typeof start?.format === 'function' ? start.format('YYYY-MM-DD HH:mm:ss') : start;
      } else if (end) {
        queryParams[`${key}_le`] =
          typeof end?.format === 'function' ? end.format('YYYY-MM-DD HH:mm:ss') : end;
      }
    } else {
      const value = Array.isArray(filterValue) ? filterValue[0] : filterValue;
      if (value !== undefined && value !== null) {
        queryParams[`${key}_eq`] = value;
      }
    }
  });

  return queryParams;
}

/**
 * 通用表格列表组合式函数
 * @param config 配置对象
 * @returns 表格相关状态和方法
 */
export function useTable(config: UseTableConfig) {
  const {
    apiConfig,
    storageConfig,
    deleteConfig,
    enableRowSelection = false,
  } = config;

  // 从 apiConfig 中解构 API 方法
  const { fetchData, deleteItem, batchDeleteItems } = apiConfig;

  // 获取扩展偏好设置中的表格行高配置
  const customPreferences = getCustomPreferences<{ tableRowHeight?: number }>();
  
  // 根据行高计算表格尺寸（响应式）
  const tableSize = computed(() => {
    const rowHeight = customPreferences.tableRowHeight || 36; // 默认 36px
    
    if (rowHeight <= 32) {
      return 'small';
    } else if (rowHeight >= 48) {
      return 'large';
    } else {
      return 'middle'; // 默认
    }
  });

  // 监听行高变化，动态更新 CSS 变量以实现全局自动应用
  watch(
    () => customPreferences.tableRowHeight,
    (newHeight) => {
      const height = newHeight || 36;
      document.documentElement.style.setProperty('--table-row-height', `${height}px`);
    },
    { immediate: true }
  );

  // 表格数据
  const dataSource = ref<any[]>([]);
  const loading = ref(false);

  // 分页配置
  const pagination = reactive<TablePaginationConfig>({
    pageNum: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`,
  });

  // 选中的行（仅在启用行选择时初始化）
  const selectedRowKeys = ref<(string | number)[]>(enableRowSelection ? [] : undefined as any);

  // 筛选状态（用于列头筛选）- 从 localStorage 初始化
  const filtersKey = storageConfig?.filtersKey || 'table_list_filters';
  const activeFilters = ref<Record<string, FilterCondition | any>>(
    loadFiltersFromStorage(filtersKey)
  );

  // 当前排序状态
  const currentSort = reactive<{
    sortField?: string;
    sortOrder?: string;
  }>({
    sortField: undefined,
    sortOrder: undefined,
  });

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
    return convertTableFiltersToQueryParams(filters);
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

      // 构建后端期望的参数格式（BasePageReq）
      const backendParams: BasePageReq = {
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        queryParams: Object.keys(filterQueryParams).length > 0 ? filterQueryParams : undefined,
        ...extraSearchParams,
      };

      // 如果有排序参数，添加到请求中（在根级别，不在 queryParams 内）
      if (currentSort.sortField) {
        backendParams.sortField = currentSort.sortField;
        backendParams.sortOrder = currentSort.sortOrder || 'desc';
      }

      // 调用 API 获取数据
      const response = await fetchData(backendParams);
      
      // 转换响应格式：后端返回 { records, total, pageNum, pageSize, pages }
      // 前端期望 { items, total }
      dataSource.value = response.records || [];
      pagination.total = response.total || 0;
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
   * 批量删除项目（仅在启用行选择时可用）
   */
  function handleBatchDelete() {
    if (!enableRowSelection) {
      message.warning('行选择功能未启用');
      return;
    }

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
   * 选择行变化（仅在启用行选择时可用）
   */
  function onSelectChange(keys: (string | number)[]) {
    if (!enableRowSelection) {
      console.warn('行选择功能未启用');
      return;
    }
    selectedRowKeys.value = keys;
  }

  /**
   * 分页、排序、筛选变化
   */
  function handleTableChange(pag: any, filters: any, sorter: any) {
    pagination.pageNum = pag.pageNum;
    pagination.pageSize = pag.pageSize;

    // 更新筛选状态并持久化 - 使用合并策略
    // 关键修复：当点击列头排序时，filters 参数会传入状态标记值（如 ['filtered']），
    // 而不是实际的筛选值。我们需要忽略这些状态标记，保留 activeFilters 中的实际筛选值。
    if (filters) {
      // 合并新旧筛选条件,保留未被修改的列的筛选条件
      // 当filters中某列为undefined时,表示该列被重置,应从activeFilters中移除
      const updatedFilters: Record<string, any> = { ...activeFilters.value };

      Object.keys(filters).forEach((key) => {
        const filterValue = filters[key];
        
        if (filterValue === undefined) {
          // 重置的列,从activeFilters中移除
          delete updatedFilters[key];
        } else if (filterValue === 'filtered' || (Array.isArray(filterValue) && filterValue.includes('filtered'))) {
          // 这是 Ant Design Vue 的状态标记，不是实际的筛选值
          // 保留已有的实际筛选值，不做任何操作
          // 例如：用户输入"员"后点击排序，filters[key] 会是 ['filtered']，而不是实际的筛选值
        } else {
          // 只有当筛选值是实际的数组或对象时，才更新筛选条件
          // 这表示用户通过筛选器明确修改了筛选条件
          updatedFilters[key] = filterValue;
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
      
      // 如果排序方向为 null(第三次点击取消排序),则清空排序
      if (!sorter.order) {
        currentSort.sortField = undefined;
        currentSort.sortOrder = undefined;
      } else {
        // 更新当前排序状态
        currentSort.sortField = sorter.field;
        currentSort.sortOrder = orderMap[sorter.order] || 'desc';
      }
    }

    // 重新加载数据(带上筛选和排序条件)
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
    pagination.pageNum = 1;
  }

  return {
    dataSource,
    loading,
    pagination,
    ...(enableRowSelection ? { selectedRowKeys } : {}),
    activeFilters,
    tableSize, // 新增：返回表格尺寸配置，业务组件可直接使用
    loadData,
    handleDelete,
    ...(enableRowSelection ? { handleBatchDelete, onSelectChange } : {}),
    handleTableChange,
    clearAllFilters,
    resetPagination,
  };
}
