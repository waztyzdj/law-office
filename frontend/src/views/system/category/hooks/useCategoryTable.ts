import { computed } from 'vue';

import type { CategoryInfo } from '#/api/system/category';

import { listCategories } from '#/api/system/category';
import {
  buildTreeFromFlat,
  buildTreeSelectOptions,
  useTreeData,
} from '#/composables/Tree/useTree';

interface CategoryTreeNode extends CategoryInfo {
  children?: CategoryTreeNode[] | null;
  parentId?: string;
}

export function useCategoryTable() {
  const table = useTreeData<CategoryInfo>({
    fetchData: listCategories,
    storageConfig: {
      filtersKey: 'category_tree_filters',
    },
  });

  const treeData = computed(() =>
    buildTreeFromFlat<CategoryTreeNode>(
      table.dataSource.value.map((item) => ({
        ...item,
        parentId: item.pid,
      })),
    ),
  );

  const treeOptions = computed(() =>
    buildTreeSelectOptions(
      treeData.value,
      (node) => node.name || String(node.id ?? ''),
    ),
  );

  return {
    ...table,
    treeData,
    treeOptions,
  };
}
