import { h } from 'vue';

import { Space, Tag } from 'ant-design-vue';

import type { DepartRoleInfo } from '#/api/system/depart';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';

const DEFAULT_ROLE_DESCRIPTION = '部门默认角色';

function isDefaultRole(role?: DepartRoleInfo) {
  return role?.defaultRole === true || role?.description === DEFAULT_ROLE_DESCRIPTION;
}

export function getDepartRoleColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const columns: any[] = [
    {
      dataIndex: 'roleName',
      title: '角色名称',
      options: {
        width: 240,
        customRender: ({ record }: { record: DepartRoleInfo }) =>
          h(Space, { size: 6 }, () => [
            h('span', record.roleName || '-'),
            isDefaultRole(record) ? h(Tag, { color: 'blue' }, () => '默认') : null,
          ]),
      },
    },
    {
      dataIndex: 'roleCode',
      title: '角色编码',
      options: { width: 320 },
    },
    {
      dataIndex: 'workflowEnabled',
      title: '审批岗位',
      options: {
        align: 'center',
        width: 120,
        customRender: ({ record }: { record: DepartRoleInfo }) =>
          record.workflowEnabled === 1
            ? h(Tag, { color: 'green' }, () => '是')
            : h(Tag, () => '否'),
      },
    },
    {
      dataIndex: 'description',
      title: '描述',
      options: { width: 260 },
    },
    {
      dataIndex: 'departRoleAction',
      title: '操作',
      options: {
        align: 'center',
        className: 'depart-role-action-cell',
        fixed: 'right' as const,
        hasFilter: false,
        width: 160,
        customRender: ({ record }: { record: DepartRoleInfo }) =>
          h(Space, { class: 'depart-role-action-links', size: 6 }, () => {
            const actions = [];
            if (!isDefaultRole(record)) {
              actions.push(h('a', { onClick: () => emit('edit', record) }, '编辑'));
              actions.push(
                h(
                  'a',
                  { style: { color: 'red' }, onClick: () => emit('delete', record) },
                  '删除',
                ),
              );
            }
            actions.push(h('a', { onClick: () => emit('assign', record) }, '授权'));
            actions.push(h('a', { onClick: () => emit('members', record) }, '成员'));
            return actions;
          }),
      },
    },
  ];

  return defineTableColumns<DepartRoleInfo>(
    columns,
    filterState,
    emit,
    pagination,
    {
      actionColumnKey: 'departRoleAction',
      minTableWidth: 960,
      tableKey: 'system_depart_role',
    },
  );
}
