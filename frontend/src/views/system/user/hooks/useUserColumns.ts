import { h } from 'vue';
import { Space } from 'ant-design-vue';
import { useAccess } from '@vben/access';
import type { UserInfo } from '#/api/system/user';
import type { TablePaginationConfig, TableColumnsResult } from '#/composables/Table';
import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

/**
 * 用户列表列定义配置
 */
export function getUserColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditUser = hasAccessByCodes([permissionCodes.user.edit]);

  // 定义列配置数组
  const columns: any[] = [
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
        columnType: 'select' as const,
        selectOptions: [
          { label: '男', value: 1, color: 'blue' },
          { label: '女', value: 2, color: 'pink' },
          { label: '未知', value: 0 },
        ],
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
        columnType: 'select' as const, // 指定为下拉选择类型
        selectOptions: [
          { label: '正常', value: 1, color: 'green' },
          { label: '冻结', value: 2, color: 'red' },
        ],
      },
    },
    canEditUser
      ? {
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
    }
      : null,
  ];

  // 使用通用列定义函数处理（返回 { columns, scroll }）
  return defineTableColumns(columns.filter(Boolean), filterState, emit, pagination);
}
