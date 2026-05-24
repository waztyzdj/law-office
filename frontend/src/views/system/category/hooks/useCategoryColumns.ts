import { h } from 'vue';

import { useAccess } from '@vben/access';

import { Space } from 'ant-design-vue';

import type { CategoryInfo } from '#/api/system/category';
import type {
  TableColumnsResult,
  TablePaginationConfig,
} from '#/composables/Table';

import { defineTableColumns } from '#/composables/Table';
import { permissionCodes } from '#/constants/permissions';

export function getCategoryColumns(
  filterState: any,
  emit: any,
  pagination: TablePaginationConfig,
): TableColumnsResult {
  const { hasAccessByCodes } = useAccess();
  const canEditCategory = hasAccessByCodes([permissionCodes.category.edit]);

  const columns: any[] = [
    {
      dataIndex: 'code',
      title: '类型编码',
      options: { width: 180 },
    },
    {
      dataIndex: 'name',
      title: '类型名称',
      options: { width: 220 },
    },
    canEditCategory
      ? {
          dataIndex: 'action',
          title: '操作',
          options: {
            width: 220,
            fixed: 'right' as const,
            hasFilter: false,
            customRender: ({ record }: { record: CategoryInfo }) =>
              h(Space, { size: 'middle' }, () => [
                h('a', { onClick: () => emit('addChild', record) }, '新增子类'),
                h('a', { onClick: () => emit('edit', record) }, '编辑'),
                h(
                  'a',
                  { style: { color: 'red' }, onClick: () => emit('delete', record) },
                  '删除',
                ),
              ]),
          },
        }
      : null,
  ];

  return defineTableColumns<CategoryInfo>(
    columns.filter(Boolean),
    filterState,
    emit,
    pagination,
    { minTableWidth: 860 },
  );
}
