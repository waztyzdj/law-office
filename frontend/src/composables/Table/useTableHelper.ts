import { h } from 'vue';
import { Tag, Tooltip } from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { TablePaginationConfig } from './useTable';
import { 
  useTableHeaderFilter, 
  useTableHeaderSelectFilter,
  useTableHeaderDateTimeFilter,
  DEFAULT_FILTER_CONDITIONS, 
  DATE_FILTER_CONDITIONS, 
  NUMBER_FILTER_CONDITIONS,
  type SelectOption,
} from './useTableHeaderFilter';

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
  /** Select 选项配置（当 columnType 为 select 时使用） */
  selectOptions?: SelectOption[];
  /** 其他 Ant Design Vue 列配置 */
  [key: string]: any;
}

/**
 * 创建带 Tooltip 的单元格内容
 * @param content 要显示的内容
 * @returns 带 Tooltip 包裹的内容
 */
function createTooltipCell(content: any) {
  // 如果内容是字符串或数字，添加 Tooltip
  if (typeof content === 'string' || typeof content === 'number') {
    return h(
      Tooltip,
      { title: String(content) },
      {
        default: () =>
          h(
            'div',
            {
              style: {
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              },
            },
            String(content)
          ),
      }
    );
  }
  
  // 如果内容已经是 VNode 或其他类型，直接返回
  return content;
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
    selectOptions,
    ...restOptions
  } = options;

  // 基础列配置
  const column: any = {
    title,
    dataIndex,
    key: dataIndex,
    align: 'center',
    ellipsis: true, // 启用省略号显示
    // 列标题不换行，超出显示省略号
    customHeaderCell: () => ({
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      },
    }),
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

  // 添加自定义渲染或默认的 Tooltip 渲染
  if (customRender) {
    // 如果有自定义渲染，包装一层以支持 Tooltip
    const originalCustomRender = customRender;
    column.customRender = (params: { record: T; index: number; text: any }) => {
      const renderedContent = originalCustomRender(params);
      // 对于自定义渲染的内容，如果是简单文本则添加 Tooltip
      return createTooltipCell(renderedContent);
    };
  } else {
    // 没有自定义渲染时，使用默认的 Tooltip 渲染
    column.customRender = ({ record }: { record: T }) => {
      const value = record[dataIndex as keyof T];
      return createTooltipCell(value);
    };
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
    } else if (columnType === 'select' && selectOptions && selectOptions.length > 0) {
      // Select 类型：使用多选下拉框筛选
      column.sorter = sorter;
      column.filterDropdown = useTableHeaderSelectFilter(
        dataIndex,
        selectOptions
      ).createFilterDropdown(filterState, emit, pagination);
      column.filteredValue =
        filterState.value[dataIndex] && filterState.value[dataIndex].value
          ? ['filtered']
          : undefined;
      column.onFilter = () => true;
      
      // 自动生成 customRender，根据 selectOptions 显示带颜色的标签
      if (!customRender) {
        column.customRender = ({ record }: { record: T }) => {
          const value = record[dataIndex as keyof T];
          const option = selectOptions.find(opt => opt.value === value);
          
          if (!option) {
            return h('span', {}, String(value ?? '-'));
          }
          
          // 如果有颜色配置，使用 Tag 组件
          if (option.color) {
            return h(Tag, { color: option.color }, () => option.label);
          }
          
          return h('span', {}, option.label);
        };
      }
    } else if (columnType === 'datetime') {
      // DateTime 类型：使用日期/时间切换筛选器
      column.sorter = sorter;
      column.filterDropdown = useTableHeaderDateTimeFilter(
        dataIndex
      ).createFilterDropdown(filterState, emit, pagination);
      column.filteredValue =
        filterState.value[dataIndex] && filterState.value[dataIndex].value
          ? ['filtered']
          : undefined;
      column.onFilter = () => true;
    } else {
      // 普通列，根据列类型选择筛选条件
      let filterConditions = DEFAULT_FILTER_CONDITIONS;
      
      if (columnType === 'date') {
        filterConditions = DATE_FILTER_CONDITIONS;
      } else if (columnType === 'number') {
        filterConditions = NUMBER_FILTER_CONDITIONS;
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
