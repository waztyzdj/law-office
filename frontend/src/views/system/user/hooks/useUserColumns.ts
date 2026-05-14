import { h } from 'vue';
import { Space, Tag } from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { UserInfo } from './useUserApi';
import type { PaginationConfig } from './useUserList';
import { useAdvancedFilter, DEFAULT_FILTER_CONDITIONS } from '#/composables/Table/TableHeaderSearch/useAdvancedFilter';

/**
 * 用户表格列配置
 * @param filterState 筛选状态
 * @param emit 事件触发函数
 * @param pagination 分页配置
 * @returns 表格列定义数组
 */
export function getUserColumns(
  filterState: any,
  emit: any,
  pagination: PaginationConfig
): ColumnsType<UserInfo> {
  return [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 120,
      align: 'center',
      sorter: true,
      filterDropdown: useAdvancedFilter('username', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.username ? [filterState.value.username] : undefined,
      onFilter: () => true,
    },
    {
      title: '真实姓名',
      dataIndex: 'realname',
      key: 'realname',
      width: 120,
      align: 'center',
      sorter: true,
      filterDropdown: useAdvancedFilter('realname', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.realname ? [filterState.value.realname] : undefined,
      onFilter: () => true,
    },
    {
      title: '性别',
      dataIndex: 'sex',
      key: 'sex',
      width: 80,
      align: 'center',
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
      align: 'center',
      sorter: true,
      filterDropdown: useAdvancedFilter('email', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.email ? [filterState.value.email] : undefined,
      onFilter: () => true,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      key: 'phone',
      width: 130,
      align: 'center',
      sorter: true,
      filterDropdown: useAdvancedFilter('phone', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.phone ? [filterState.value.phone] : undefined,
      onFilter: () => true,
    },
    {
      title: '工号',
      dataIndex: 'workNo',
      key: 'workNo',
      width: 120,
      align: 'center',
      sorter: true,
      filterDropdown: useAdvancedFilter('workNo', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.workNo ? [filterState.value.workNo] : undefined,
      onFilter: () => true,
    },
    {
      title: '职务',
      dataIndex: 'post',
      key: 'post',
      width: 120,
      align: 'center',
      filterDropdown: useAdvancedFilter('post', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.post ? [filterState.value.post] : undefined,
      onFilter: () => true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      align: 'center',
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
      title: '操作',
      key: 'action',
      width: 150,
      align: 'center',
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
}
