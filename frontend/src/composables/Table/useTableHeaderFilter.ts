import { ref, h } from 'vue';
import { Input, Select, Button, Space, DatePicker, message } from 'ant-design-vue';
import type { Ref } from 'vue';
import dayjs from 'dayjs';

/**
 * 筛选条件选项类型
 */
export interface FilterConditionOption {
  label: string;
  value: string;
  title?: string; // 鼠标悬停提示
}

/**
 * 默认筛选条件选项（文本类型）
 */
export const DEFAULT_FILTER_CONDITIONS: FilterConditionOption[] = [
  { label: '包含', value: 'like' },
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'ne' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'ge' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'le' },
  { label: '在...之中', value: 'in', title: '用逗号进行分隔，例如：张三,李四,王五' },
  { label: '区间查询', value: 'between', title: '用逗号进行分隔，例如：A,M' },
  { label: '开头是', value: 'like_start' },
  { label: '结尾是', value: 'like_end' },
];

/**
 * 日期筛选条件选项
 */
export const DATE_FILTER_CONDITIONS: FilterConditionOption[] = [
  { label: '等于', value: 'eq' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'ge' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'le' },
  { label: '在两者之间', value: 'between' },
];

/**
 * 日期时间筛选条件选项
 */
export const DATETIME_FILTER_CONDITIONS: FilterConditionOption[] = [
  { label: '等于', value: 'eq' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'ge' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'le' },
  { label: '在两者之间', value: 'between' },
];

/**
 * 列类型
 */
export type ColumnType = 'text' | 'date' | 'datetime' | 'number' | 'select';

/**
 * Select 选项配置
 */
export interface SelectOption {
  /** 显示文本 */
  label: string;
  /** 值 */
  value: any;
  /** 标签颜色（可选） */
  color?: string;
}

/**
 * 表头筛选组合式函数
 * @param dataIndex 字段索引
 * @param defaultConditions 默认筛选条件选项
 * @param columnType 列类型，默认为 text
 * @returns 筛选相关的方法和状态
 */
export function useTableHeaderFilter(
  dataIndex: string,
  defaultConditions: FilterConditionOption[] = [],
  columnType: ColumnType = 'text'
) {
  // 筛选状态
  const condition = ref<string>(defaultConditions[0]?.value || 'like');
  const value = ref<string>('');
  const dateValue = ref<any>(null);
  const dateRangeValue = ref<[any, any] | null>(null);

  /**
   * 创建表头筛选下拉框组件
   * @param filterState 筛选状态对象（ref）
   * @param emit 事件触发函数
   * @param pagination 分页配置
   * @returns 渲染函数
   */
  const createFilterDropdown = (
    filterState: Ref<Record<string, any>>,
    emit: Function,
    pagination: any
  ) => {
    // 标记是否已经初始化过
    let initialized = false;
    
    return ({ confirm, clearFilters }: any) => {
      // 只在首次渲染时从 filterState 同步，避免覆盖用户输入
      if (!initialized) {
        const currentFilter = filterState.value[dataIndex];
        if (currentFilter && currentFilter.condition) {
          condition.value = currentFilter.condition;
          
          // 根据列类型恢复不同的值
          if (columnType === 'date' || columnType === 'datetime') {
            if (currentFilter.value) {
              if (Array.isArray(currentFilter.value)) {
                // 范围查询
                dateRangeValue.value = currentFilter.value.map((v: string) => 
                  columnType === 'datetime' ? dayjs(v) : dayjs(v).startOf('day')
                );
              } else {
                // 单值查询
                dateValue.value = columnType === 'datetime' 
                  ? dayjs(currentFilter.value) 
                  : dayjs(currentFilter.value).startOf('day');
              }
            }
          } else {
            value.value = currentFilter.value || '';
          }
        }
        initialized = true;
      }
      
      // 根据列类型渲染不同的输入控件
      const renderInputControl = () => {
        if (columnType === 'date' || columnType === 'datetime') {
          const isRange = condition.value === 'between';
          
          if (isRange) {
            // 范围选择器
            return h(DatePicker.RangePicker as any, {
              value: dateRangeValue.value,
              style: { width: '100%' },
              format: columnType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD',
              showTime: columnType === 'datetime',
              onChange: (dates: any) => {
                dateRangeValue.value = dates;
              },
              placeholder: ['开始日期', '结束日期'],
            });
          } else {
            // 单日期选择器
            return h(DatePicker as any, {
              value: dateValue.value,
              style: { width: '100%' },
              format: columnType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD',
              showTime: columnType === 'datetime',
              onChange: (date: any) => {
                dateValue.value = date;
              },
              placeholder: '请选择日期',
            });
          }
        } else {
          // 文本输入框
          return h(Input, {
            placeholder: '请输入筛选值',
            value: value.value,
            onChange: (e: any) => {
              value.value = e.target.value;
            },
            onPressEnter: () => {
              handleSearch(confirm, filterState, pagination, emit);
            },
          });
        }
      };

      // 根据列类型和条件动态计算弹出框宽度
      const isRange = condition.value === 'between';
      const popupWidth = (columnType === 'date' || columnType === 'datetime') && isRange ? 380 : 280;

      return h('div', { style: `padding: 16px; width: ${popupWidth}px;` }, [
        // 条件选择
        h('div', { style: 'margin-bottom: 8px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '条件'),
          h(Select as any, {
            value: condition.value,
            style: { width: '100%' },
            onChange: (val: string) => {
              condition.value = val;
              // 切换条件时清空之前的值
              if (val === 'between') {
                dateValue.value = null;
              } else {
                dateRangeValue.value = null;
              }
            },
            options: defaultConditions.map((opt) => ({
              label: opt.label,
              value: opt.value,
              title: opt.title || undefined,
            })),
          }),
        ]),
        // 值输入（根据类型动态渲染）
        h('div', { style: 'margin-bottom: 8px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '值'),
          renderInputControl(),
        ]),
        // 按钮
        h(Space, { style: 'justify-content: center; display: flex; width: 100%;' }, {
          default: () => [
            h(
              Button,
              {
                type: 'primary',
                size: 'small',
                onClick: () => {
                  handleSearch(confirm, filterState, pagination, emit);
                },
              },
              () => '搜索'
            ),
            h(
              Button,
              {
                size: 'small',
                onClick: () => {
                  clearFilters?.();
                  condition.value = defaultConditions[0]?.value || 'like';
                  value.value = '';
                  dateValue.value = null;
                  dateRangeValue.value = null;
                  filterState.value[dataIndex] = undefined;
                  
                  // 触发 change 事件，更新筛选状态
                  emit('change', pagination, filterState.value, {});
                },
              },
              () => '重置'
            ),
          ],
        }),
      ]);
    };
  };

  /**
   * 处理搜索逻辑
   */
  const handleSearch = (
    confirm: Function,
    filterState: Ref<Record<string, any>>,
    pagination: any,
    emit: Function
  ) => {
    let filterValue: any;

    if (columnType === 'date' || columnType === 'datetime') {
      const isRange = condition.value === 'between';
      
      if (isRange) {
        // 范围查询
        if (dateRangeValue.value && dateRangeValue.value[0] && dateRangeValue.value[1]) {
          const formatStr = columnType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD';
          filterValue = [
            dateRangeValue.value[0].format(formatStr),
            dateRangeValue.value[1].format(formatStr),
          ];
        } else {
          message.warning('请选择完整的日期范围');
          return;
        }
      } else {
        // 单值查询
        if (dateValue.value) {
          const formatStr = columnType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD';
          filterValue = dateValue.value.format(formatStr);
        } else {
          message.warning('请选择日期');
          return;
        }
      }
    } else {
      // 文本查询
      filterValue = value.value;
    }

    // 构建筛选条件
    filterState.value[dataIndex] = {
      condition: condition.value,
      value: filterValue,
    };

    confirm();

    // 手动触发 change 事件
    emit('change', pagination, filterState.value, {});
  };

  return {
    condition,
    value,
    dateValue,
    dateRangeValue,
    createFilterDropdown,
  };
}

/**
 * Select 类型表头筛选组合式函数（多选下拉框）
 * @param dataIndex 字段索引
 * @param options 选项配置数组
 * @returns 筛选相关的方法和状态
 */
export function useTableHeaderSelectFilter(
  dataIndex: string,
  options: SelectOption[] = []
) {
  // 选中的值
  const selectedValues = ref<any[]>([]);

  /**
   * 创建表头筛选下拉框组件（多选）
   * @param filterState 筛选状态对象（ref）
   * @param emit 事件触发函数
   * @param pagination 分页配置
   * @returns 渲染函数
   */
  const createFilterDropdown = (
    filterState: Ref<Record<string, any>>,
    emit: Function,
    pagination: any
  ) => {
    // 标记是否已经初始化过
    let initialized = false;
    
    return ({ confirm, clearFilters }: any) => {
      // 只在首次渲染时从 filterState 同步
      if (!initialized) {
        const currentFilter = filterState.value[dataIndex];
        if (currentFilter && currentFilter.value) {
          // 支持数组或逗号分隔的字符串
          if (Array.isArray(currentFilter.value)) {
            selectedValues.value = currentFilter.value;
          } else if (typeof currentFilter.value === 'string') {
            selectedValues.value = currentFilter.value.split(',').map((v: string) => v.trim());
          }
        }
        initialized = true;
      }
      
      return h('div', { style: 'padding: 16px; width: 280px;' }, [
        // 标签
        h('div', { style: 'margin-bottom: 8px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '选择'),
        ]),
        // 多选下拉框
        h('div', { style: 'margin-bottom: 8px;' }, [
          h(Select as any, {
            mode: 'multiple',
            value: selectedValues.value,
            style: { width: '100%' },
            placeholder: '请选择',
            onChange: (values: any[]) => {
              selectedValues.value = values;
            },
            options: options.map((opt) => ({
              label: opt.label,
              value: opt.value,
            })),
          }),
        ]),
        // 按钮
        h(Space, { style: 'justify-content: center; display: flex; width: 100%;' }, {
          default: () => [
            h(
              Button,
              {
                type: 'primary',
                size: 'small',
                onClick: () => {
                  if (selectedValues.value.length === 0) {
                    message.warning('请至少选择一个选项');
                    return;
                  }

                  // 构建筛选条件，使用 in 操作符
                  filterState.value[dataIndex] = {
                    condition: 'in',
                    value: selectedValues.value,
                  };

                  confirm();

                  // 手动触发 change 事件
                  emit('change', pagination, filterState.value, {});
                },
              },
              () => '搜索'
            ),
            h(
              Button,
              {
                size: 'small',
                onClick: () => {
                  clearFilters?.();
                  selectedValues.value = [];
                  filterState.value[dataIndex] = undefined;
                  
                  // 触发 change 事件，更新筛选状态
                  emit('change', pagination, filterState.value, {});
                },
              },
              () => '重置'
            ),
          ],
        }),
      ]);
    };
  };

  return {
    selectedValues,
    createFilterDropdown,
  };
}
