import { h } from 'vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { TablePaginationConfig } from './useTable';
import { useTableHeaderFilter, DEFAULT_FILTER_CONDITIONS, DATE_FILTER_CONDITIONS, DATETIME_FILTER_CONDITIONS } from './useTableHeaderFilter';

/**
 * 列类型枚举
 */
export type ColumnType = 'text' | 'date' | 'datetime' | 'number' | 'select';

/**
 * 表格列配置选项（简化版）
 */
export interface TableColumnOptions<T = any> {
  /** 列宽度 */
  width?: number;
  /** 是否启用排序，默认 true */
  sorter?: boolean;
  /** 固定列位置 */
  fixed?: 'left' | 'right' | boolean;
  /** 自定义渲染函数 */
  customRender?: (params: { record: T; index: number }) => any;
  /** 枚举过滤选项（用于状态列等） */
  filters?: Array<{ text: string; value: any }>;
  /** 枚举过滤的过滤函数 */
  onFilter?: (value: any, record: T) => boolean;
  /** 是否启用筛选，默认 true（操作列等可设为 false） */
  hasFilter?: boolean;
  /** 列类型，默认为 text */
  columnType?: ColumnType;
  /** 其他 Ant Design Vue 列配置 */
  [key: string]: any;
}

/**
 * 定义表格列的辅助函数
 * 
 * @example
 * // 简单文本列（自动添加筛选、排序）
 * defineTableColumn('username', '用户名', { width: 120 }, filterState, emit, pagination)
 * 
 * @example
 * // 日期列
 * defineTableColumn('createTime', '创建时间', { 
 *   width: 180,
 *   columnType: 'date'
 * }, filterState, emit, pagination)
 * 
 * @example
 * // 枚举/状态列
 * defineTableColumn('status', '状态', {
 *   width: 100,
 *   sorter: false,
 *   filters: [{ text: '正常', value: 1 }, { text: '冻结', value: 2 }],
 *   onFilter: (value, record) => record.status === value,
 *   customRender: ({ record }) => h(Tag, {}, record.status)
 * }, filterState, emit, pagination)
 * 
 * @example
 * // 操作列（无筛选）
 * defineTableColumn('action', '操作', {
 *   width: 150,
 *   fixed: 'right',
 *   hasFilter: false,
 *   customRender: ({ record }) => h(Button, {}, '编辑')
 * }, filterState, emit, pagination)
 * 
 * @param dataIndex 数据字段索引
 * @param title 列标题
 * @param options 列配置选项
 * @param filterState 筛选状态（ref）
 * @param emit 事件触发函数
 * @param pagination 分页配置
 * @returns 完整的列配置对象
 */
export function defineTableColumn<T = any>(
  dataIndex: string,
  title: string,
  options: TableColumnOptions<T> = {},
  filterState?: any,
  emit?: any,
  pagination?: TablePaginationConfig
): any {
  const {
    width,
    sorter = true,
    fixed,
    customRender,
    filters,
    onFilter,
    hasFilter = true,
    columnType = 'text',
    ...restOptions
  } = options;

  // 基础列配置
  const column: any = {
    title,
    dataIndex,
    key: dataIndex,
    align: 'center',
    ...restOptions,
  };

  // 添加宽度
  if (width) {
    column.width = width;
  }

  // 添加固定列
  if (fixed) {
    column.fixed = fixed;
  }

  // 添加自定义渲染
  if (customRender) {
    column.customRender = customRender;
  }

  // 处理筛选和排序
  if (hasFilter && filterState && emit && pagination) {
    // 如果有自定义 filters（枚举类型），使用传统过滤方式
    if (filters) {
      column.filters = filters;
      column.filteredValue = undefined;
      column.onFilter = onFilter || (() => true);
      // 枚举列通常不需要 sorter，但可以手动指定
      if (sorter !== false) {
        column.sorter = sorter;
      }
    } else {
      // 普通列，根据列类型选择筛选条件
      let filterConditions = DEFAULT_FILTER_CONDITIONS;
      
      if (columnType === 'date') {
        filterConditions = DATE_FILTER_CONDITIONS;
      } else if (columnType === 'datetime') {
        filterConditions = DATETIME_FILTER_CONDITIONS;
      }
      
      column.sorter = sorter;
      column.filterDropdown = useTableHeaderFilter(
        dataIndex,
        filterConditions,
        columnType
      ).createFilterDropdown(filterState, emit, pagination);
      column.filteredValue =
        filterState.value[dataIndex] && filterState.value[dataIndex].value
          ? ['filtered']
          : undefined;
      column.onFilter = () => true;
    }
  } else if (!hasFilter) {
    // 不需要筛选的列（如操作列）
    column.filteredValue = undefined;
  }

  return column;
}

/**
 * 批量定义表格列的辅助函数
 * 
 * @example
 * // 在 useUserColumns.ts 中使用
 * export function getUserColumns(filterState, emit, pagination) {
 *   const columns = [
 *     { dataIndex: 'username', title: '用户名', options: { width: 120 } },
 *     { dataIndex: 'email', title: '邮箱', options: { width: 180 } },
 *     { 
 *       dataIndex: 'status', 
 *       title: '状态', 
 *       options: {
 *         width: 100,
 *         filters: [...],
 *         customRender: ({ record }) => {...}
 *       }
 *     },
 *     {
 *       dataIndex: 'action',
 *       title: '操作',
 *       options: {
 *         width: 150,
 *         fixed: 'right' as const,
 *         hasFilter: false,
 *         customRender: ({ record }) => {...}
 *       }
 *     }
 *   ];
 *   return defineTableColumns(columns, filterState, emit, pagination);
 * }
 * 
 * @param columns 列配置数组
 * @param filterState 筛选状态（ref）
 * @param emit 事件触发函数
 * @param pagination 分页配置
 * @returns 完整的列配置数组
 */
export function defineTableColumns<T = any>(
  columns: Array<{
    dataIndex: string;
    title: string;
    options?: TableColumnOptions<T>;
  }>,
  filterState?: any,
  emit?: any,
  pagination?: TablePaginationConfig
): ColumnsType<T> {
  return columns.map(({ dataIndex, title, options = {} }) =>
    defineTableColumn<T>(dataIndex, title, options, filterState, emit, pagination)
  );
}
