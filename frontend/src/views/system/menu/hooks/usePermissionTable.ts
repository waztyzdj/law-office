import { computed } from 'vue';

import type { PermissionInfo } from '#/api/system/permission';

import { listPermissions } from '#/api/system/permission';
import {
  buildTreeFromFlat,
  buildTreeSelectOptions,
  useTreeData,
} from '#/composables/Tree/useTree';

export function usePermissionTable() {
  const table = useTreeData<PermissionInfo>({
    fetchData: listPermissions,
    storageConfig: {
      filtersKey: 'permission_tree_filters',
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
