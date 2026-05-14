import { h } from 'vue';
import { Space, Tag } from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import type { UserInfo } from '#/api/system/user';
import type { TablePaginationConfig } from '#/composables/Table';
import { defineTableColumns, type TableColumnOptions } from '#/composables/Table';

/**
 * 用户列表列定义配置
 */
export function getUserColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig
): ColumnsType<UserInfo> {
  // 定义列配置数组
  const columns = [
    {
      dataIndex: 'username',
      title: '用户名',
      options: { width: 120 },
    },
    {
      dataIndex: 'realname',
      title: '真实姓名',
      options: { width: 120 },
    },
    {
      dataIndex: 'sex',
      title: '性别',
      options: {
        width: 80,
        filters: [
          { text: '男', value: 1 },
          { text: '女', value: 2 },
          { text: '未知', value: 0 },
        ],
        onFilter: (value: any, record: UserInfo) => record.sex === value,
        customRender: ({ record }: { record: UserInfo }) => {
          const sexMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };
          return sexMap[record.sex ?? 0] || '未知';
        },
      },
    },
    {
      dataIndex: 'email',
      title: '邮箱',
      options: { width: 180 },
    },
    {
      dataIndex: 'phone',
      title: '电话',
      options: { width: 130 },
    },
    {
      dataIndex: 'workNo',
      title: '工号',
      options: { width: 120 },
    },
    {
      dataIndex: 'post',
      title: '职务',
      options: { width: 120 },
    },
    {
      dataIndex: 'createTime',
      title: '创建时间',
      options: {
        width: 180,
        columnType: 'datetime' as const, // 指定为日期时间类型
      },
    },
    {
      dataIndex: 'status',
      title: '状态',
      options: {
        width: 80,
        sorter: false,
        filters: [
          { text: '正常', value: 1 },
          { text: '冻结', value: 2 },
        ],
        onFilter: (value: any, record: UserInfo) => record.status === value,
        customRender: ({ record }: { record: UserInfo }) => {
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
    },
    {
      dataIndex: 'action',
      title: '操作',
      options: {
        width: 150,
        fixed: 'right' as const,
        hasFilter: false, // 操作列不需要筛选
        customRender: ({ record }: { record: UserInfo }) => {
          return h(Space, { size: 'middle' }, {
            default: () => [
              h('a', { onClick: () => emit('edit', record) }, '编辑'),
              h('a', { style: { color: 'red' }, onClick: () => emit('delete', record) }, '删除'),
            ],
          });
        },
      },
    },
  ];

  // 使用辅助函数批量生成列配置
  return defineTableColumns<UserInfo>(columns, filterState, emit, pagination);
}
