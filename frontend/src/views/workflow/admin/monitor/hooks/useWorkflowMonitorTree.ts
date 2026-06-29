import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';

import { ref } from 'vue';

import type {
  WorkflowCategoryInfo,
  WorkflowProcessModelInfo,
} from '#/api/workflow';

import { listWorkflowCategories, listWorkflowProcesses } from '#/api/workflow';

import type { WorkflowMonitorScope } from './useWorkflowMonitorTable';

export interface WorkflowMonitorTreeDataNode extends DataNode {
  children?: WorkflowMonitorTreeDataNode[];
  scope: WorkflowMonitorScope;
  searchText: string;
  title: string;
}

const ALL_TREE_KEY = 'all';

export function useWorkflowMonitorTree() {
  const expandedKeys = ref<Key[]>([ALL_TREE_KEY]);
  const loading = ref(false);
  const selectedKeys = ref<Key[]>([ALL_TREE_KEY]);
  const treeData = ref<WorkflowMonitorTreeDataNode[]>([]);

  async function loadTree() {
    loading.value = true;
    try {
      const [categories, processes] = await Promise.all([
        listWorkflowCategories(),
        listWorkflowProcesses({
          queryParams: { status_eq: 'published' },
          sortField: 'process_key',
          sortOrder: 'asc',
        }),
      ]);
      treeData.value = buildMonitorTree(categories ?? [], processes ?? []);
      expandedKeys.value = collectInitialExpandedKeys(treeData.value);
      selectedKeys.value = [ALL_TREE_KEY];
    } finally {
      loading.value = false;
    }
  }

  function selectScope(_scope: WorkflowMonitorScope, key: Key) {
    selectedKeys.value = [key];
  }

  return {
    expandedKeys,
    loadTree,
    loading,
    selectScope,
    selectedKeys,
    treeData,
  };
}

function buildMonitorTree(
  categories: WorkflowCategoryInfo[],
  processes: WorkflowProcessModelInfo[],
) {
  const categoryMap = new Map(
    categories
      .filter((item) => item.id)
      .map((item) => [item.id!, item]),
  );
  const processesByCategory = groupProcessesByCategory(processes);
  const categoryNodes = Array.from(processesByCategory.entries())
    .map(([categoryId, categoryProcesses]) =>
      buildCategoryNode(categoryId, categoryMap.get(categoryId), categoryProcesses),
    )
    .sort((left, right) => left.title.localeCompare(right.title, 'zh-Hans-CN'));

  const allNode: WorkflowMonitorTreeDataNode = {
    children: categoryNodes,
    key: ALL_TREE_KEY,
    scope: { title: '全部流程', type: 'all' },
    searchText: '全部流程',
    title: '全部流程',
  };

  return [allNode];
}

function buildCategoryNode(
  categoryId: string,
  category: undefined | WorkflowCategoryInfo,
  processes: WorkflowProcessModelInfo[],
): WorkflowMonitorTreeDataNode {
  const title = category?.categoryName ?? category?.categoryCode ?? categoryId;
  const children = processes
    .slice()
    .sort((left, right) =>
      resolveProcessTitle(left).localeCompare(resolveProcessTitle(right), 'zh-Hans-CN'),
    )
    .map((process) => buildProcessNode(process));
  return {
    key: `category:${categoryId}`,
    scope: {
      categoryId,
      title,
      type: 'category',
    },
    searchText: [title, category?.categoryCode].filter(Boolean).join(' '),
    title,
    children,
  };
}

function buildProcessNode(process: WorkflowProcessModelInfo): WorkflowMonitorTreeDataNode {
  const title = resolveProcessTitle(process);
  return {
    key: `process:${process.processKey ?? process.id}`,
    scope: {
      categoryId: process.categoryId,
      processKey: process.processKey,
      title,
      type: 'process',
    },
    searchText: [title, process.processKey].filter(Boolean).join(' '),
    title,
  };
}

function groupProcessesByCategory(processes: WorkflowProcessModelInfo[]) {
  return processes.reduce((map, process) => {
    const categoryId = process.categoryId;
    if (!categoryId) {
      return map;
    }
    const records = map.get(categoryId) ?? [];
    records.push(process);
    map.set(categoryId, records);
    return map;
  }, new Map<string, WorkflowProcessModelInfo[]>());
}

function resolveProcessTitle(process: WorkflowProcessModelInfo) {
  return process.processName ?? process.processKey ?? process.id ?? '-';
}

function collectInitialExpandedKeys(nodes: WorkflowMonitorTreeDataNode[]) {
  const keys: Key[] = [];
  nodes.forEach((node) => {
    keys.push(node.key as Key);
    node.children?.forEach((child) => keys.push(child.key as Key));
  });
  return keys;
}
