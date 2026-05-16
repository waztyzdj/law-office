import { ref, reactive, computed, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { getCustomPreferences } from '@vben/preferences';

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
 * 表格配置接口
 */
export interface TableConfig {
  /** 是否启用行选择功能，默认为 false */
  enableRowSelection?: boolean;
  /** 是否启用横向滚动，默认为 true */
  enableScroll?: boolean;
  /** 是否自动冻结操作列，默认为 true */
  autoFreezeActionColumn?: boolean;
  /** 操作列的 dataIndex，默认为 'action' */
  actionColumnKey?: string;
  /** 最小表格宽度，用于判断是否显示滚动条，默认为 800 */
  minTableWidth?: number;
}

/**
 * API 配置接口
 */
export interface ApiConfig<T = any> {
  /** 获取数据的 API 方法（必填） */
  fetchData: (params: BaseListParams) => Promise<ListResponse<T>>;
  /** 删除单个项目的 API 方法（可选） */
  deleteItem?: (id: string | number) => Promise<any>;
  /** 批量删除的 API 方法（可选） */
  batchDeleteItems?: (ids: (string | number)[]) => Promise<any>;
}

/**
 * useTable 配置接口
 */
export interface UseTableConfig<T = any> {
  /** API 配置（必填） */
  apiConfig: ApiConfig<T>;
  /** localStorage 配置（可选） */
  storageConfig?: StorageConfig;
  /** 删除对话框配置（可选） */
  deleteConfig?: DeleteConfig;
  /** 表格配置（可选） */
  tableConfig?: TableConfig;
}

/**
 * 计算表格总宽度（排除固定列）
 * @param columns 列配置数组
 * @returns 非固定列的宽度总和
 */
export function calculateTableWidth(columns: any[]): number {
  return columns.reduce((sum, col) => {
    // 排除固定列（fixed: 'left' 或 'right'）
    if (col.fixed) {
      return sum;
    }
    // 确保宽度是数字类型
    const width = Number(col.width || 0);
    return sum + width;
  }, 0);
}

/**
 * 生成表格 scroll 配置
 * @param columns 列配置数组
 * @param options 配置选项
 * @returns scroll 配置对象
 */
export function generateTableScroll(
  columns: any[],
  options: TableConfig = {}
): { x: number | true } | undefined {
  const {
    enableScroll = true,
    minTableWidth = 800,
  } = options;

  if (!enableScroll) {
    return undefined;
  }

  // 只计算非固定列的总宽度
  const nonFixedWidth = calculateTableWidth(columns);
  
  // 如果有右侧固定列，需要额外增加空间
  const rightFixedWidth = columns
    .filter(col => col.fixed === 'right')
    .reduce((sum, col) => sum + Number(col.width || 0), 0);
  
  // 总滚动宽度 = 非固定列宽度 + 右侧固定列宽度（确保不被遮挡）
  const totalScrollWidth = nonFixedWidth + rightFixedWidth;
  
  // 如果总宽度超过最小表格宽度，启用横向滚动
  if (totalScrollWidth > minTableWidth) {
    return { x: totalScrollWidth };
  }
  
  // 否则使用 true 让表格自适应
  return { x: true };
}

/**
 * 自动处理操作列冻结
 * @param columns 列配置数组
 * @param options 配置选项
 * @returns 处理后的列配置数组
 */
export function autoFreezeActionColumn(
  columns: any[],
  options: TableConfig = {}
): any[] {
  const {
    autoFreezeActionColumn = true,
    actionColumnKey = 'action',
  } = options;

  if (!autoFreezeActionColumn) {
    return columns;
  }

  // 查找操作列并自动冻结
  return columns.map((col) => {
    // 如果列的 dataIndex 或 key 包含 'action' 且未手动设置 fixed
    if (
      (col.dataIndex === actionColumnKey || col.key === actionColumnKey) &&
      !col.fixed
    ) {
      return {
        ...col,
        fixed: 'right' as const,
      };
    }
    return col;
  });
}

/**
 * 通用表格列表组合式函数
 * @param config 配置对象
 * @returns 表格相关状态和方法
 */
export function useTable<T = any>(config: UseTableConfig<T>) {
  const {
    apiConfig,
    storageConfig,
    deleteConfig,
    tableConfig,
  } = config;

  // 从 apiConfig 中解构 API 方法
  const { fetchData, deleteItem, batchDeleteItems } = apiConfig;

  // 从 tableConfig 中解构表格配置，并设置默认值
  const {
    enableRowSelection = false,
  } = tableConfig || {};

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
    const queryParams: Record<string, any> = {};

    Object.keys(filters).forEach((key) => {
      const filterValue = filters[key];

      // 如果筛选值为空，跳过
      if (!filterValue || (Array.isArray(filterValue) && filterValue.length === 0)) {
        return;
      }

      // 处理高级筛选（包含 condition 和 value）
      if (filterValue.condition && filterValue.value !== undefined) {
        const { condition, apiCondition, value } = filterValue;
        
        // 优先使用 apiCondition（转换后的条件），如果不存在则使用 condition
        const effectiveCondition = apiCondition || condition;
        
        // 特殊处理"开头是"和"结尾是"
        if (effectiveCondition === 'like') {
          // 判断是否是"开头是"或"结尾是"的逻辑可以在这里扩展
          // 目前统一使用 like
          queryParams[`${key}_like`] = value;
        } else {
          // 其他条件直接拼接操作符
          queryParams[`${key}_${effectiveCondition}`] = value;
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

      // 如果有排序参数，添加到请求中
      if (currentSort.sortField) {
        params.sortField = currentSort.sortField;
        params.sortOrder = currentSort.sortOrder || 'desc';
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
    pagination.current = 1;
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
