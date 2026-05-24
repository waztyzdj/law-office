import { computed } from 'vue';

import type { DepartInfo } from '#/api/system/depart';

import { listDeparts } from '#/api/system/depart';
import { useDictOptions } from '#/composables/Dict/useDict';
import {
  buildTreeFromFlat,
  buildTreeSelectOptions,
  useTreeData,
} from '#/composables/Tree/useTree';
import { dictCodes } from '#/constants/dict-codes';

export function useDepartTable() {
  const { options: orgTypeOptions, loadOptions: loadOrgTypeOptions } =
    useDictOptions(dictCodes.departOrgType);

  const table = useTreeData<DepartInfo>({
    fetchData: listDeparts,
    storageConfig: {
      filtersKey: 'depart_tree_filters',
    },
  });

  const treeData = computed(() => buildTreeFromFlat(table.dataSource.value));
  const treeOptions = computed(() =>
    buildTreeSelectOptions(
      treeData.value,
      (node) => node.departName || String(node.id ?? ''),
    ),
  );
  const orgTypeSelectOptions = computed(() =>
    orgTypeOptions.value.map((option) => ({
      ...option,
      color: 'blue',
    })),
  );

  return {
    ...table,
    loadOrgTypeOptions,
    orgTypeOptions,
    orgTypeSelectOptions,
    treeData,
    treeOptions,
  };
}
