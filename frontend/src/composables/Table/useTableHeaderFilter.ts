import { ref, h } from 'vue';
import { Input, InputNumber, Select, Button, Space, DatePicker, message } from 'ant-design-vue';
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
  { label: '在...之中', value: 'in', title: '用逗号进行分隔，例如：张三,李四,王五' },
  { label: '开头是', value: 'likestart' },
  { label: '结尾是', value: 'likeend' },
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
 * 日期时间筛选条件选项（日期模式）
 */
export const DATETIME_DATE_FILTER_CONDITIONS: FilterConditionOption[] = [
  { label: '在两者之间', value: 'between' },
  { label: '等于', value: 'eq' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'ge' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'le' },
];

/**
 * 日期时间筛选条件选项（时间模式）
 */
export const DATETIME_TIME_FILTER_CONDITIONS: FilterConditionOption[] = [
  { label: '在两者之间', value: 'between' },
  { label: '大于', value: 'gt' },
  { label: '小于', value: 'lt' },
];

/**
 * 数值类型筛选条件选项
 */
export const NUMBER_FILTER_CONDITIONS: FilterConditionOption[] = [
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
  const numberValue = ref<string>('');
  const numberRangeValue = ref<[string, string] | null>(null);

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
          } else if (columnType === 'number') {
            // 恢复数值
            if (currentFilter.value) {
              if (Array.isArray(currentFilter.value)) {
                // 范围查询
                numberRangeValue.value = currentFilter.value;
              } else {
                // 单值查询
                numberValue.value = String(currentFilter.value);
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
        } else if (columnType === 'number') {
          const isRange = condition.value === 'between';
          
          if (isRange) {
            // 数值范围输入
            return h('div', { style: 'display: flex; gap: 8px;' }, [
              h(InputNumber as any, {
                value: numberRangeValue.value?.[0] !== undefined ? Number(numberRangeValue.value[0]) : undefined,
                style: { flex: 1 },
                placeholder: '最小值',
                onChange: (val: number | null) => {
                  if (numberRangeValue.value) {
                    numberRangeValue.value = [String(val ?? ''), numberRangeValue.value[1]];
                  } else {
                    numberRangeValue.value = [String(val ?? ''), ''];
                  }
                },
              }),
              h('span', { style: 'display: flex; align-items: center;' }, '至'),
              h(InputNumber as any, {
                value: numberRangeValue.value?.[1] !== undefined ? Number(numberRangeValue.value[1]) : undefined,
                style: { flex: 1 },
                placeholder: '最大值',
                onChange: (val: number | null) => {
                  if (numberRangeValue.value) {
                    numberRangeValue.value = [numberRangeValue.value[0], String(val ?? '')];
                  } else {
                    numberRangeValue.value = ['', String(val ?? '')];
                  }
                },
              }),
            ]);
          } else {
            // 单数值输入
            return h(InputNumber as any, {
              value: numberValue.value !== '' ? Number(numberValue.value) : undefined,
              style: { width: '100%' },
              placeholder: '请输入数值',
              onChange: (val: number | null) => {
                numberValue.value = val !== null ? String(val) : '';
              },
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
                  // 重置所有状态
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
    } else if (columnType === 'number') {
      const isRange = condition.value === 'between';
      
      if (isRange) {
        // 范围查询
        if (numberRangeValue.value && numberRangeValue.value[0] && numberRangeValue.value[1]) {
          filterValue = numberRangeValue.value;
        } else {
          message.warning('请输入完整的数值范围');
          return;
        }
      } else {
        // 单值查询
        if (numberValue.value) {
          filterValue = numberValue.value;
        } else {
          message.warning('请输入数值');
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
    numberValue,
    numberRangeValue,
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
                  // 重置所有状态
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

/**
 * DateTime 类型表头筛选组合式函数（支持日期/时间切换）
 * @param dataIndex 字段索引
 * @returns 筛选相关的方法和状态
 */
export function useTableHeaderDateTimeFilter(dataIndex: string) {
  // 模式：'date' | 'time'，默认为 date
  const mode = ref<'date' | 'time'>('date');
  // 筛选条件
  const condition = ref<string>(DATETIME_DATE_FILTER_CONDITIONS[0]?.value || 'eq');
  // 日期值
  const dateValue = ref<any>(null);
  // 日期范围值
  const dateRangeValue = ref<[any, any] | null>(null);
  // 时间值
  const timeValue = ref<any>(null);
  // 时间范围值
  const timeRangeValue = ref<[any, any] | null>(null);

  /**
   * 根据当前模式和条件生成搜索参数
   */
  const generateSearchParams = () => {
    if (mode.value === 'date') {
      // 日期模式：自动拼接时分秒
      if (condition.value === 'eq' && dateValue.value) {
        // 等于：转换为 between，范围为当天 00:00:00 到 23:59:59
        const start = dayjs(dateValue.value).format('YYYY-MM-DD') + ' 00:00:00';
        const end = dayjs(dateValue.value).format('YYYY-MM-DD') + ' 23:59:59';
        return {
          condition: 'between',
          value: [start, end],
        };
      } else if (condition.value === 'gt' && dateValue.value) {
        // 大于：使用当天 23:59:59
        const value = dayjs(dateValue.value).format('YYYY-MM-DD') + ' 23:59:59';
        return { condition: 'gt', value };
      } else if (condition.value === 'ge' && dateValue.value) {
        // 大于等于：使用当天 00:00:00
        const value = dayjs(dateValue.value).format('YYYY-MM-DD') + ' 00:00:00';
        return { condition: 'ge', value };
      } else if (condition.value === 'lt' && dateValue.value) {
        // 小于：使用当天 00:00:00
        const value = dayjs(dateValue.value).format('YYYY-MM-DD') + ' 00:00:00';
        return { condition: 'lt', value };
      } else if (condition.value === 'le' && dateValue.value) {
        // 小于等于：使用当天 23:59:59
        const value = dayjs(dateValue.value).format('YYYY-MM-DD') + ' 23:59:59';
        return { condition: 'le', value };
      } else if (condition.value === 'between' && dateRangeValue.value) {
        // 在两者之间：开始日期 00:00:00，结束日期 23:59:59
        const start = dayjs(dateRangeValue.value[0]).format('YYYY-MM-DD') + ' 00:00:00';
        const end = dayjs(dateRangeValue.value[1]).format('YYYY-MM-DD') + ' 23:59:59';
        return {
          condition: 'between',
          value: [start, end],
        };
      }
    } else {
      // 时间模式：直接使用时间值（字符串格式）
      if (condition.value === 'gt' && timeValue.value) {
        return { condition: 'gt', value: timeValue.value };
      } else if (condition.value === 'lt' && timeValue.value) {
        return { condition: 'lt', value: timeValue.value };
      } else if (condition.value === 'between' && timeRangeValue.value) {
        return {
          condition: 'between',
          value: timeRangeValue.value,
        };
      }
    }
    
    return null;
  };

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
      // 只在首次渲染时从 filterState 同步
      if (!initialized) {
        const currentFilter = filterState.value[dataIndex];
        if (currentFilter) {
          // 恢复模式
          if (currentFilter.mode) {
            mode.value = currentFilter.mode;
          }
          // 恢复条件（优先使用 condition 字段，用于 UI 显示）
          if (currentFilter.condition) {
            condition.value = currentFilter.condition;
          }
          // 恢复值
          if (currentFilter.value) {
            if (mode.value === 'date') {
              if (condition.value === 'between' && Array.isArray(currentFilter.value)) {
                // 在两者之间：直接使用数组
                dateRangeValue.value = [
                  dayjs(currentFilter.value[0]),
                  dayjs(currentFilter.value[1]),
                ];
              } else if (Array.isArray(currentFilter.value)) {
                // 其他条件但值是数组（如 eq 转换后的 between）：提取第一个元素作为日期
                dateValue.value = dayjs(currentFilter.value[0]);
              } else {
                // 单个值
                dateValue.value = dayjs(currentFilter.value);
              }
            } else {
              if (condition.value === 'between' && Array.isArray(currentFilter.value)) {
                // 时间范围：保持字符串格式
                timeRangeValue.value = currentFilter.value;
              } else if (Array.isArray(currentFilter.value)) {
                // 其他条件但值是数组：提取第一个元素
                timeValue.value = currentFilter.value[0];
              } else {
                // 单个时间：保持字符串格式
                timeValue.value = currentFilter.value;
              }
            }
          }
        }
        initialized = true;
      }
      
      // 根据模式选择筛选条件
      const conditions = mode.value === 'date' 
        ? DATETIME_DATE_FILTER_CONDITIONS 
        : DATETIME_TIME_FILTER_CONDITIONS;
      
      // 根据条件动态计算弹出框宽度
      // 只有时间模式且选择"在两者之间"时才增加宽度
      const isTimeRange = mode.value === 'time' && condition.value === 'between';
      const popupWidth = isTimeRange ? 380 : 280;
      
      return h('div', { style: `padding: 16px; width: ${popupWidth}px;` }, [
        // 日期/时间模式切换开关
        h('div', { style: 'margin-bottom: 12px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '筛选模式'),
          h('div', { style: 'display: flex; gap: 8px;' }, [
            h(
              Button,
              {
                type: mode.value === 'date' ? 'primary' : 'default',
                size: 'small',
                onClick: () => {
                  mode.value = 'date';
                  condition.value = DATETIME_DATE_FILTER_CONDITIONS[0]?.value || 'eq';
                  // 清空值
                  dateValue.value = null;
                  dateRangeValue.value = null;
                },
              },
              () => '日期'
            ),
            h(
              Button,
              {
                type: mode.value === 'time' ? 'primary' : 'default',
                size: 'small',
                onClick: () => {
                  mode.value = 'time';
                  condition.value = DATETIME_TIME_FILTER_CONDITIONS[0]?.value || 'gt';
                  // 清空值
                  timeValue.value = null;
                  timeRangeValue.value = null;
                },
              },
              () => '时间'
            ),
          ]),
        ]),
        // 运算符选择
        h('div', { style: 'margin-bottom: 8px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '条件'),
          h(Select as any, {
            value: condition.value,
            style: { width: '100%' },
            onChange: (val: string) => {
              condition.value = val;
              // 清空之前的值
              dateValue.value = null;
              dateRangeValue.value = null;
              timeValue.value = null;
              timeRangeValue.value = null;
            },
            options: conditions.map((c) => ({ label: c.label, value: c.value })),
          }),
        ]),
        // 日期控件（日期模式）
        ...(mode.value === 'date' ? [
          h('div', { style: 'margin-bottom: 8px;' }, [
            h('label', { style: 'display: block; margin-bottom: 4px;' }, '日期'),
            condition.value === 'between'
              ? h(DatePicker.RangePicker as any, {
                  value: dateRangeValue.value,
                  style: { width: '100%' },
                  placeholder: ['开始日期', '结束日期'],
                  onChange: (dates: any) => {
                    dateRangeValue.value = dates;
                  },
                })
              : h(DatePicker as any, {
                  value: dateValue.value,
                  style: { width: '100%' },
                  placeholder: '请选择日期',
                  onChange: (date: any) => {
                    dateValue.value = date;
                  },
                }),
          ]),
        ] : []),
        // 时间控件（时间模式）
        ...(mode.value === 'time' ? [
          h('div', { style: 'margin-bottom: 8px;' }, [
            h('label', { style: 'display: block; margin-bottom: 4px;' }, '时间'),
            condition.value === 'between'
              ? h(DatePicker.RangePicker as any, {
                  value: timeRangeValue.value ? [
                    dayjs(timeRangeValue.value[0]),
                    dayjs(timeRangeValue.value[1])
                  ] : null,
                  style: { width: '100%' },
                  showTime: true,
                  format: 'YYYY-MM-DD HH:mm:ss',
                  placeholder: ['开始时间', '结束时间'],
                  onChange: (dates: any) => {
                    timeRangeValue.value = dates && dates.length === 2 ? [
                      dates[0]?.format('YYYY-MM-DD HH:mm:ss'),
                      dates[1]?.format('YYYY-MM-DD HH:mm:ss'),
                    ] : null;
                  },
                })
              : h(DatePicker as any, {
                  value: timeValue.value ? dayjs(timeValue.value) : null,
                  style: { width: '100%' },
                  showTime: true,
                  format: 'YYYY-MM-DD HH:mm:ss',
                  placeholder: '请选择时间',
                  onChange: (date: any) => {
                    timeValue.value = date?.format('YYYY-MM-DD HH:mm:ss') || null;
                  },
                }),
          ]),
        ] : []),
        // 提示信息
        ...((mode.value === 'date' && !dateValue.value && !dateRangeValue.value) ||
            (mode.value === 'time' && !timeValue.value && !timeRangeValue.value)
          ? [
              h('div', { 
                style: 'color: #faad14; font-size: 12px; margin-bottom: 8px;',
              }, '⚠️ 请选择日期/时间'),
            ]
          : []),
        // 按钮
        h(Space, { style: 'justify-content: center; display: flex; width: 100%;' }, {
          default: () => [
            h(
              Button,
              {
                type: 'primary',
                size: 'small',
                onClick: () => {
                  const searchParams = generateSearchParams();
                  
                  if (!searchParams) {
                    message.warning(mode.value === 'date' ? '请选择日期' : '请选择时间');
                    return;
                  }

                  // 构建筛选条件，保存模式信息
                  filterState.value[dataIndex] = {
                    mode: mode.value,
                    condition: condition.value,  // 保存原始的 UI 选择（如 'eq'），用于 UI 显示
                    apiCondition: searchParams.condition,  // 保存转换后的条件（如 'between'），用于后端查询
                    value: searchParams.value,
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
                  // 重置所有状态
                  mode.value = 'date';
                  condition.value = DATETIME_DATE_FILTER_CONDITIONS[0]?.value || 'eq';
                  dateValue.value = null;
                  dateRangeValue.value = null;
                  timeValue.value = null;
                  timeRangeValue.value = null;
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
    mode,
    condition,
    dateValue,
    dateRangeValue,
    timeValue,
    timeRangeValue,
    createFilterDropdown,
  };
}
