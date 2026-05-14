import { ref, h } from 'vue';
import { Input, Select, Button, Space } from 'ant-design-vue';
import type { Ref } from 'vue';

/**
 * 筛选条件选项类型
 */
export interface FilterConditionOption {
  label: string;
  value: string;
  title?: string; // 鼠标悬停提示
}

/**
 * 默认筛选条件选项（可复用的常量）
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
 * 高级筛选组合式函数
 * @param dataIndex 字段索引
 * @param defaultConditions 默认筛选条件选项
 * @returns 筛选相关的方法和状态
 */
export function useAdvancedFilter(
  dataIndex: string,
  defaultConditions: FilterConditionOption[] = []
) {
  // 筛选状态
  const condition = ref<string>(defaultConditions[0]?.value || 'like');
  const value = ref<string>('');

  /**
   * 创建高级筛选下拉框组件
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
          value.value = currentFilter.value || '';
        }
        initialized = true;
      }
      
      return h('div', { style: 'padding: 16px; width: 250px;' }, [
        // 条件选择
        h('div', { style: 'margin-bottom: 8px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '条件'),
          h(Select as any, {
            value: condition.value,
            style: { width: '100%' },
            onChange: (val: string) => {
              condition.value = val;
            },
            options: defaultConditions.map((opt) => ({
              label: opt.label,
              value: opt.value,
              title: opt.title || undefined,
            })),
          }),
        ]),
        // 值输入
        h('div', { style: 'margin-bottom: 8px;' }, [
          h('label', { style: 'display: block; margin-bottom: 4px;' }, '值'),
          h(Input, {
            placeholder: '请输入筛选值',
            value: value.value,
            onChange: (e: any) => {
              value.value = e.target.value;
            },
            onPressEnter: () => {
              // 构建筛选条件
              filterState.value[dataIndex] = {
                condition: condition.value,
                value: value.value,
              };

              confirm();

              // 手动触发 change 事件
              emit('change', pagination, filterState.value, {});
            },
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
                  // 构建符合后端 QueryWrapperBuilderUtils 的参数格式
                  filterState.value[dataIndex] = {
                    condition: condition.value,
                    value: value.value,
                  };

                  confirm();

                  // 手动触发 change 事件，将筛选条件传递给父组件
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
                  condition.value = defaultConditions[0]?.value || 'like';
                  value.value = '';
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
    condition,
    value,
    createFilterDropdown,
  };
}
