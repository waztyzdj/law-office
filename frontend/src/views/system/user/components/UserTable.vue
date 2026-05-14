<script setup lang="ts">
import { h, ref } from 'vue';
import { 
  Table, 
  Button, 
  Space, 
  Tag, 
  Card,
  Input,
  Select,
  DatePicker,
  Form,
  FormItem
} from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { UserInfo } from '#/api/system/user';
import type { PaginationConfig } from '../composables/useUserList';
import dayjs from 'dayjs';

interface Props {
  dataSource: UserInfo[];
  loading: boolean;
  pagination: PaginationConfig;
  selectedRowKeys: string[];
}

interface Emits {
  (e: 'edit', record: UserInfo): void;
  (e: 'delete', record: UserInfo): void;
  (e: 'change', pag: any, filters: any, sorter: any): void;
  (e: 'select-change', keys: (string | number)[]): void;
  (e: 'batch-delete'): void;
  (e: 'add'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// 自定义筛选状态管理
const filterState = ref<Record<string, any>>({});

/**
 * 创建自定义筛选下拉框
 */
const createFilterDropdown = (
  dataIndex: string,
  placeholder: string = '请输入筛选值'
) => {
  return ({ confirm, clearFilters }: any) => {
    return h('div', { style: 'padding: 8px' }, [
      h(Input, {
        placeholder,
        value: filterState.value[dataIndex],
        onChange: (e: any) => {
          filterState.value[dataIndex] = e.target.value;
        },
        onPressEnter: () => {
          confirm();
        },
        style: 'width: 180px; margin-bottom: 8px;',
      }),
      h(Space, {}, {
        default: () => [
          h(Button, {
            type: 'primary',
            size: 'small',
            onClick: () => {
              confirm();
            },
          }, () => '搜索'),
          h(Button, {
            size: 'small',
            onClick: () => {
              clearFilters?.();
              filterState.value[dataIndex] = undefined;
            },
          }, () => '重置'),
        ],
      }),
    ]);
  };
};

/**
 * 创建高级筛选下拉框（支持多种条件）
 */
const createAdvancedFilterDropdown = (
  dataIndex: string,
  options: Array<{ label: string; value: string; title?: string }>
) => {
  const condition = ref('like'); // 默认条件：模糊查询（对应后端的 like）
  const value = ref('');

  return ({ confirm, clearFilters }: any) => {
    return h('div', { style: 'padding: 8px; width: 250px' }, [
      // 条件选择
      h('div', { style: 'margin-bottom: 8px;' }, [
        h('label', { style: 'display: block; margin-bottom: 4px;' }, '条件'),
        h(Select as any, {
          value: condition.value,
          onChange: (val: string) => {
            condition.value = val;
          },
          style: 'width: 100%;',
          options: options.map(opt => ({ 
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
            emit('change', props.pagination, filterState.value, {});
          },
        }),
      ]),
      // 按钮
      h(Space, {}, {
        default: () => [
          h(Button, {
            type: 'primary',
            size: 'small',
            onClick: () => {
              console.log('=== 点击搜索按钮 ===');
              console.log('dataIndex:', dataIndex);
              console.log('condition:', condition.value);
              console.log('value:', value.value);
              
              // 构建符合后端 QueryWrapperBuilderUtils 的参数格式
              // 例如：realname_like -> 后端会解析为 real_name LIKE '%value%'
              filterState.value[dataIndex] = {
                condition: condition.value,
                value: value.value,
              };
              
              console.log('filterState:', filterState.value);
              console.log('准备调用 confirm()');
              
              confirm();
              
              // 手动触发 change 事件，将筛选条件传递给父组件
              console.log('手动触发 change 事件');
              emit('change', props.pagination, filterState.value, {});
            },
          }, () => '搜索'),
          h(Button, {
            size: 'small',
            onClick: () => {
              clearFilters?.();
              condition.value = 'like';
              value.value = '';
              filterState.value[dataIndex] = undefined;
            },
          }, () => '重置'),
        ],
      }),
    ]);
  };
};

/**
 * 创建日期范围筛选
 */
const createDateRangeFilter = (dataIndex: string) => {
  const dateRange = ref<[any, any] | null>(null);

  return ({ confirm, clearFilters }: any) => {
    return h('div', { style: 'padding: 8px; width: 250px' }, [
      h('div', { style: 'margin-bottom: 8px;' }, [
        h('label', { style: 'display: block; margin-bottom: 4px;' }, '日期范围'),
        h(DatePicker.RangePicker as any, {
          value: dateRange.value,
          onChange: (dates: any) => {
            dateRange.value = dates;
          },
          style: 'width: 100%;',
        }),
      ]),
      h(Space, {}, {
        default: () => [
          h(Button, {
            type: 'primary',
            size: 'small',
            onClick: () => {
              filterState.value[dataIndex] = dateRange.value;
              confirm();
            },
          }, () => '搜索'),
          h(Button, {
            size: 'small',
            onClick: () => {
              clearFilters?.();
              dateRange.value = null;
              filterState.value[dataIndex] = undefined;
            },
          }, () => '重置'),
        ],
      }),
    ]);
  };
};

// 表格列定义
const columns: ColumnsType<UserInfo> = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username',
    width: 120,
    sorter: true, // 启用排序
    filterDropdown: createFilterDropdown('username', '请输入用户名'),
    filteredValue: filterState.value.username ? [filterState.value.username] : undefined,
    onFilter: (value: any, record: UserInfo) => {
      return record.username?.includes(value) || false;
    },
  },
  {
    title: '真实姓名',
    dataIndex: 'realname',
    key: 'realname',
    width: 120,
    sorter: true,
    filterDropdown: createAdvancedFilterDropdown('realname', [
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
    ]),
    filteredValue: filterState.value.realname ? [filterState.value.realname] : undefined,
    // 注意：使用后端筛选时，onFilter 应该返回 true，让所有数据通过，实际筛选由后端完成
    onFilter: () => true,
  },
  {
    title: '性别',
    dataIndex: 'sex',
    key: 'sex',
    width: 80,
    filters: [
      { text: '男', value: 1 },
      { text: '女', value: 2 },
      { text: '未知', value: 0 },
    ],
    onFilter: (value: any, record: UserInfo) => record.sex === value,
    customRender: ({ record }) => {
      const sexMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };
      return sexMap[record.sex ?? 0] || '未知';
    },
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    key: 'email',
    width: 180,
    sorter: true,
    filterDropdown: createFilterDropdown('email', '请输入邮箱'),
    filteredValue: filterState.value.email ? [filterState.value.email] : undefined,
    onFilter: (value: any, record: UserInfo) => {
      return record.email?.includes(value) || false;
    },
  },
  {
    title: '电话',
    dataIndex: 'phone',
    key: 'phone',
    width: 130,
    sorter: true,
    filterDropdown: createFilterDropdown('phone', '请输入电话'),
    filteredValue: filterState.value.phone ? [filterState.value.phone] : undefined,
    onFilter: (value: any, record: UserInfo) => {
      return record.phone?.includes(value) || false;
    },
  },
  {
    title: '工号',
    dataIndex: 'workNo',
    key: 'workNo',
    width: 120,
    sorter: true,
  },
  {
    title: '职务',
    dataIndex: 'post',
    key: 'post',
    width: 120,
    filterDropdown: createFilterDropdown('post', '请输入职务'),
    filteredValue: filterState.value.post ? [filterState.value.post] : undefined,
    onFilter: (value: any, record: UserInfo) => {
      return record.post?.includes(value) || false;
    },
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
    filters: [
      { text: '正常', value: 1 },
      { text: '冻结', value: 2 },
    ],
    onFilter: (value: any, record: UserInfo) => record.status === value,
    customRender: ({ record }) => {
      const statusMap: Record<number, { text: string; color: string }> = {
        1: { text: '正常', color: 'green' },
        2: { text: '冻结', color: 'red' },
      };
      const status = statusMap[record.status ?? 1];
      if (!status) {
        return h(Tag, { color: 'default' }, () => '未知');
      }
      return h(Tag, { color: status.color }, () => status.text);
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
    sorter: true,
    filterDropdown: createDateRangeFilter('createTime'),
    filteredValue: filterState.value.createTime ? [filterState.value.createTime] : undefined,
    onFilter: (value: any, record: UserInfo) => {
      if (!value || !record.createTime) return false;
      
      const [start, end] = value;
      const createTime = dayjs(record.createTime);
      
      if (start && end) {
        return createTime.isAfter(dayjs(start)) && createTime.isBefore(dayjs(end));
      } else if (start) {
        return createTime.isAfter(dayjs(start));
      } else if (end) {
        return createTime.isBefore(dayjs(end));
      }
      return true;
    },
    customRender: ({ record }) => {
      return record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-';
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    fixed: 'right',
    customRender: ({ record }) => {
      return h(Space, { size: 'middle' }, {
        default: () => [
          h('a', { onClick: () => emit('edit', record) }, '编辑'),
          h('a', { style: { color: 'red' }, onClick: () => emit('delete', record) }, '删除'),
        ],
      });
    },
  },
];
</script>

<template>
  <Card class="table-card" style="margin-top: 16px;">
    <div class="table-toolbar">
      <Space>
        <Button type="primary" @click="$emit('add')">新增用户</Button>
        <Button danger @click="$emit('batch-delete')" :disabled="selectedRowKeys.length === 0">
          批量删除
        </Button>
      </Space>
    </div>
    
    <Table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :row-selection="{
        selectedRowKeys: selectedRowKeys,
        onChange: (keys) => $emit('select-change', keys),
      }"
      row-key="id"
      @change="(pag, filters, sorter) => $emit('change', pag, filters, sorter)"
      bordered
    />
  </Card>
</template>

<style scoped>
.table-toolbar {
  margin-bottom: 16px;
}
</style>
