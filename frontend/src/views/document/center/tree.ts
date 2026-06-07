import type { DocumentFileInfo } from '#/api/document';
import type { CurrentUserOrganization } from '#/api/system/user';

import {
  BUSINESS_MODULE_VIEW_STORE_TYPE,
  BUSINESS_RECORD_VIEW_STORE_TYPE,
} from '#/constants/document';
import type { FolderTreeNode, ScopeOption } from './types';

export const SCOPE_ROOT_PREFIX = 'scope:';
export const TREE_NODE_KEY_SEPARATOR = '::';

export function buildDepartScopeOptions(
  departs: CurrentUserOrganization['departs'],
): ScopeOption[] {
  const optionMap = new Map<string, ScopeOption>();
  const parentIdMap = new Map<string, string>();
  const roots: ScopeOption[] = [];

  for (const depart of departs) {
    if (!depart.id) {
      continue;
    }
    optionMap.set(depart.id, {
      icon: 'lucide:building-2',
      key: `depart:${depart.id}`,
      scope: 'shared',
      shareTargetId: depart.id,
      shareTargetType: 'depart',
      title: depart.departName || depart.orgCode || '部门共享',
    });
    if (depart.parentId) {
      parentIdMap.set(depart.id, depart.parentId);
    }
  }

  for (const [departId, option] of optionMap) {
    const parentId = parentIdMap.get(departId);
    const parent = parentId ? optionMap.get(parentId) : undefined;
    if (parent) {
      parent.children = [...(parent.children || []), option];
    } else {
      roots.push(option);
    }
  }

  return roots;
}

export function getScopeRootKey(scopeValue: string) {
  return `${SCOPE_ROOT_PREFIX}${scopeValue}`;
}

export function shouldRenderFolderTree(option?: ScopeOption) {
  return Boolean(option?.scope && option.shareTargetType !== 'depart');
}

export function getFolderNodeKey(rootKey: string, fileId?: string) {
  return fileId ? `${rootKey}${TREE_NODE_KEY_SEPARATOR}${fileId}` : getScopeRootKey(rootKey);
}

export function isScopeRootKey(key: string) {
  return key.startsWith(SCOPE_ROOT_PREFIX);
}

export function getScopeFromRootKey(key: string) {
  return key.slice(SCOPE_ROOT_PREFIX.length);
}

export function getRootKeyFromFolderNodeKey(key: string, activeRootKey: string) {
  if (isScopeRootKey(key)) {
    return getScopeFromRootKey(key);
  }
  const separatorIndex = key.indexOf(TREE_NODE_KEY_SEPARATOR);
  return separatorIndex > 0 ? key.slice(0, separatorIndex) : activeRootKey;
}

export function findScopeOption(
  key: string,
  options: ScopeOption[],
): ScopeOption | undefined {
  for (const option of options) {
    if (option.key === key) {
      return option;
    }
    const found = option.children ? findScopeOption(key, option.children) : undefined;
    if (found) {
      return found;
    }
  }
  return undefined;
}

export function collectScopeRootKeys(options: ScopeOption[]): string[] {
  const keys: string[] = [];
  for (const option of options) {
    if (option.scope) {
      keys.push(option.key);
    }
    if (option.children?.length) {
      keys.push(...collectScopeRootKeys(option.children));
    }
  }
  return keys;
}

export function buildFolderTreeNode(
  record: DocumentFileInfo,
  rootKey: string,
  children?: FolderTreeNode[],
): FolderTreeNode {
  return {
    children: children && children.length > 0 ? children : undefined,
    file: record,
    isLeaf: children ? children.length === 0 : record.hasChild !== true,
    key: getFolderNodeKey(rootKey, record.id),
    title: record.fileName || '未命名文件夹',
  };
}

export function mergeFolderTreeNodes(
  nextNodes: FolderTreeNode[],
  previousNodes: FolderTreeNode[] = [],
): FolderTreeNode[] {
  const previousNodeMap = new Map(previousNodes.map((node) => [node.key, node]));
  return nextNodes.map((nextNode) => {
    const previousNode = previousNodeMap.get(nextNode.key);
    if (nextNode.isLeaf || !previousNode?.children?.length) {
      return nextNode;
    }
    return {
      ...nextNode,
      children: previousNode.children,
      isLeaf: false,
    };
  });
}

export function updateFolderTreeNodes(
  nodes: FolderTreeNode[],
  targetKey: string,
  children: FolderTreeNode[],
): FolderTreeNode[] {
  return nodes.map((node) => {
    if (node.key === targetKey) {
      return {
        ...node,
        children: children.length > 0 ? children : undefined,
        isLeaf: children.length === 0,
      };
    }
    if (!node.children?.length) {
      return node;
    }
    return {
      ...node,
      children: updateFolderTreeNodes(node.children, targetKey, children),
    };
  });
}

export function updateFolderTreeRecord(
  nodes: FolderTreeNode[],
  targetKey: string,
  record: DocumentFileInfo,
): FolderTreeNode[] {
  return nodes.map((node) => {
    if (node.key === targetKey) {
      const file = {
        ...node.file,
        ...record,
      };
      const hasLoadedChildren = Array.isArray(node.children);
      return {
        ...node,
        file,
        isLeaf: hasLoadedChildren ? node.children?.length === 0 : file.hasChild !== true,
        title: file.fileName || node.title,
      };
    }
    if (!node.children?.length) {
      return node;
    }
    return {
      ...node,
      children: updateFolderTreeRecord(node.children, targetKey, record),
    };
  });
}

export function findPath(
  nodes: FolderTreeNode[],
  key: string,
  parents: DocumentFileInfo[] = [],
): DocumentFileInfo[] | undefined {
  for (const node of nodes) {
    const nextParents = node.file ? [...parents, node.file] : parents;
    if (node.key === key) {
      return nextParents;
    }
    const found = node.children ? findPath(node.children, key, nextParents) : undefined;
    if (found) {
      return found;
    }
  }
  return undefined;
}

export function findFolderTreeNode(
  nodes: FolderTreeNode[],
  key: string,
): FolderTreeNode | undefined {
  for (const node of nodes) {
    if (node.key === key) {
      return node;
    }
    const found = node.children ? findFolderTreeNode(node.children, key) : undefined;
    if (found) {
      return found;
    }
  }
  return undefined;
}

export function getFolderIcon(record?: DocumentFileInfo) {
  if (record?.storeType === BUSINESS_MODULE_VIEW_STORE_TYPE) {
    return 'lucide:briefcase-business';
  }
  if (record?.storeType === BUSINESS_RECORD_VIEW_STORE_TYPE) {
    return 'lucide:database';
  }
  return 'lucide:folder';
}
