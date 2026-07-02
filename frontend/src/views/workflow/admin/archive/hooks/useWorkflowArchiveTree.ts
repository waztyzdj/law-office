import type { DataNode, Key } from 'ant-design-vue/es/vc-tree/interface';

import { ref } from 'vue';

import type { ArchiveTreeNodeInfo } from '#/api/workflow';

import { getWorkflowArchiveTree } from '#/api/workflow';

import type { WorkflowArchiveScope } from './useWorkflowArchiveTable';

export interface WorkflowArchiveTreeDataNode extends DataNode {
  children?: WorkflowArchiveTreeDataNode[];
  scope: WorkflowArchiveScope;
  searchText: string;
  title: string;
}

const ALL_TREE_KEY = 'all';

export function useWorkflowArchiveTree() {
  const expandedKeys = ref<Key[]>([ALL_TREE_KEY]);
  const loading = ref(false);
  const selectedKeys = ref<Key[]>([ALL_TREE_KEY]);
  const treeData = ref<WorkflowArchiveTreeDataNode[]>([]);

  async function loadTree() {
    loading.value = true;
    try {
      const records = await getWorkflowArchiveTree();
      treeData.value = normalizeArchiveTree(records ?? []);
      expandedKeys.value = collectInitialExpandedKeys(treeData.value);
      selectedKeys.value = [ALL_TREE_KEY];
    } finally {
      loading.value = false;
    }
  }

  function selectScope(_scope: WorkflowArchiveScope, key: Key) {
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

function normalizeArchiveTree(nodes: ArchiveTreeNodeInfo[]) {
  return nodes.map((node) => normalizeArchiveTreeNode(node));
}

function normalizeArchiveTreeNode(node: ArchiveTreeNodeInfo): WorkflowArchiveTreeDataNode {
  const title = node.title ?? node.processName ?? '未命名流程';
  const type = node.type ?? 'all';
  const children = (node.children ?? []).map((child) => normalizeArchiveTreeNode(child));
  return {
    key: node.key ?? type,
    scope: {
      categoryId: node.categoryId,
      processKey: node.processKey,
      title,
      type,
    },
    searchText: [title, node.processKey].filter(Boolean).join(' '),
    title,
    ...(children.length > 0 ? { children } : {}),
  };
}

function collectInitialExpandedKeys(nodes: WorkflowArchiveTreeDataNode[]) {
  const keys: Key[] = [];
  nodes.forEach((node) => {
    keys.push(node.key as Key);
    node.children?.forEach((child) => keys.push(child.key as Key));
  });
  return keys;
}
