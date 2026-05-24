import { computed } from 'vue';

import type { PermissionInfo as MenuInfo } from '#/api/system/permission';

import { listPermissions as listMenus } from '#/api/system/permission';
import {
  buildTreeFromFlat,
  buildTreeSelectOptions,
  useTreeData,
} from '#/composables/Tree/useTree';

export function useMenuTable() {
  const table = useTreeData<MenuInfo>({
    fetchData: listMenus,
    storageConfig: {
      filtersKey: 'menu_tree_filters',
    },
  });

  const treeData = computed(() => buildTreeFromFlat(table.dataSource.value));
  const treeOptions = computed(() => buildTreeSelectOptions(treeData.value));

  return {
    ...table,
    treeData,
    treeOptions,
  };
}
