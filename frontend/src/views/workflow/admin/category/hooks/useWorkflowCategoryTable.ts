import { computed } from 'vue';

import type { WorkflowCategoryInfo } from '#/api/workflow';

import { listWorkflowCategories } from '#/api/workflow';
import {
  buildTreeFromFlat,
  buildTreeSelectOptions,
  useTreeData,
} from '#/composables/Tree/useTree';

interface WorkflowCategoryTreeNode extends WorkflowCategoryInfo {
  children?: WorkflowCategoryTreeNode[] | null;
}

export function useWorkflowCategoryTable() {
  const table = useTreeData<WorkflowCategoryInfo>({
    fetchData: listWorkflowCategories,
    storageConfig: {
      filtersKey: 'workflow_category_tree_filters',
    },
  });

  const treeData = computed(() =>
    buildTreeFromFlat<WorkflowCategoryTreeNode>(table.dataSource.value),
  );

  const treeOptions = computed(() =>
    buildTreeSelectOptions(
      treeData.value,
      (node) => node.categoryName || node.categoryCode || String(node.id ?? ''),
    ),
  );

  return {
    ...table,
    treeData,
    treeOptions,
  };
}
