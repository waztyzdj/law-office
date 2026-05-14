import { h } from 'vue';
import { Space, Tag } from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { UserInfo } from '#/api/system/user';
import type { TablePaginationConfig } from '#/composables/Table';
import { useTableHeaderFilter, DEFAULT_FILTER_CONDITIONS } from '#/composables/Table';

export function getUserColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig
): ColumnsType<UserInfo> {
  return [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 120,
      align: 'center',
      sorter: true,
      filterDropdown: useTableHeaderFilter('username', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.username && filterState.value.username.value ? ['filtered'] : undefined,
      onFilter: () => true,
    },
    {
      title: '真实姓名',
      dataIndex: 'realname',
      key: 'realname',
      width: 120,
      align: 'center',
      sorter: true,
      filterDropdown: useTableHeaderFilter('realname', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.realname && filterState.value.realname.value ? ['filtered'] : undefined,
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
      filteredValue: undefined,
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
      filterDropdown: useTableHeaderFilter('email', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.email && filterState.value.email.value ? ['filtered'] : undefined,
      onFilter: () => true,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      key: 'phone',
      width: 130,
      align: 'center',
      sorter: true,
      filterDropdown: useTableHeaderFilter('phone', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.phone && filterState.value.phone.value ? ['filtered'] : undefined,
      onFilter: () => true,
    },
    {
      title: '工号',
      dataIndex: 'workNo',
      key: 'workNo',
      width: 120,
      align: 'center',
      sorter: true,
      filterDropdown: useTableHeaderFilter('workNo', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.workNo && filterState.value.workNo.value ? ['filtered'] : undefined,
      onFilter: () => true,
    },
    {
      title: '职务',
      dataIndex: 'post',
      key: 'post',
      width: 120,
      align: 'center',
      filterDropdown: useTableHeaderFilter('post', DEFAULT_FILTER_CONDITIONS).createFilterDropdown(
        filterState,
        emit,
        pagination
      ),
      filteredValue: filterState.value.post && filterState.value.post.value ? ['filtered'] : undefined,
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
      filteredValue: undefined,
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
      filteredValue: undefined,
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