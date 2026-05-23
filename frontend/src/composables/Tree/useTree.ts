import { reactive, ref, shallowRef } from 'vue';

import type { BaseQueryReq } from '#/framework/api/base.api';

import { convertTableFiltersToQueryParams } from '#/composables/Table/useTable';

export type TreeKey = string | number;

export interface TreeNodeLike {
  id?: TreeKey;
  name?: string;
  children?: unknown[] | null;
}

export interface TreeFlatNodeLike extends TreeNodeLike {
  parentId?: TreeKey | null;
}

export interface TreeSelectOption {
  label: string;
  value: TreeKey;
  children?: TreeSelectOption[];
}

export interface StringTreeSelectOption {
  label: string;
  value: string;
  children?: StringTreeSelectOption[];
}

export interface AntTreeNode {
  key: TreeKey;
  title: string;
  children?: AntTreeNode[];
}

export interface TreeDataConfig<T extends TreeNodeLike> {
  fetchData: (params?: BaseQueryReq) => Promise<T[] | null | undefined>;
  storageConfig?: {
    filtersKey?: string;
  };
}

export function useTreeData<T extends TreeNodeLike>(config: TreeDataConfig<T>) {
  const filtersKey = config.storageConfig?.filtersKey || 'tree_list_filters';
  const loading = shallowRef(false);
  const dataSource = shallowRef<T[]>([]);
  const activeFilters = ref<Record<string, any>>(loadFiltersFromStorage(filtersKey));
  const pagination = reactive({
    pageNum: 1,
    pageSize: 1000,
    total: 0,
  });
  const currentSort = reactive<{
    sortField?: string;
    sortOrder?: string;
  }>({
    sortField: undefined,
    sortOrder: undefined,
  });

  async function loadData(
    extraSearchParams: Record<string, any> = {},
    extraFilters?: Record<string, any>,
  ) {
    loading.value = true;
    try {
      if (extraFilters) {
        activeFilters.value = {
          ...activeFilters.value,
          ...extraFilters,
        };
        saveFiltersToStorage(activeFilters.value, filtersKey);
      }

      const queryParams = convertTableFiltersToQueryParams(activeFilters.value);
      const params: BaseQueryReq = {
        queryParams: Object.keys(queryParams).length > 0 ? queryParams : undefined,
        ...extraSearchParams,
      };

      if (currentSort.sortField) {
        params.sortField = currentSort.sortField;
        params.sortOrder = currentSort.sortOrder || 'desc';
      }

      dataSource.value = toTreeList(await config.fetchData(params));
    } finally {
      loading.value = false;
    }
  }

  function handleTableChange(_pag: any, filters: Record<string, any>, sorter: any) {
    if (filters) {
      const updatedFilters: Record<string, any> = { ...activeFilters.value };

      Object.keys(filters).forEach((key) => {
        const filterValue = filters[key];

        if (filterValue === undefined) {
          delete updatedFilters[key];
        } else if (
          filterValue === 'filtered' ||
          (Array.isArray(filterValue) && filterValue.includes('filtered'))
        ) {
          // Ant Design Vue 的筛选状态标记，保留已有筛选值。
        } else {
          updatedFilters[key] = filterValue;
        }
      });

      activeFilters.value = updatedFilters;
      saveFiltersToStorage(updatedFilters, filtersKey);
    }

    if (sorter && sorter.field) {
      const orderMap: Record<string, string> = {
        ascend: 'asc',
        descend: 'desc',
      };

      if (!sorter.order) {
        currentSort.sortField = undefined;
        currentSort.sortOrder = undefined;
      } else {
        currentSort.sortField = sorter.field;
        currentSort.sortOrder = orderMap[sorter.order] || 'desc';
      }
    }

    void loadData();
  }

  function clearAllFilters() {
    activeFilters.value = {};
    localStorage.removeItem(filtersKey);
  }

  return {
    activeFilters,
    dataSource,
    pagination,
    loading,
    clearAllFilters,
    handleTableChange,
    loadData,
  };
}

export function buildTreeSelectOptions<T extends TreeNodeLike>(
  list?: T[] | null,
  getLabel: (node: T) => string = (node) => node.name || String(node.id ?? ''),
): StringTreeSelectOption[] {
  return toTreeList(list)
    .filter((item) => item.id !== undefined && item.id !== null)
    .map((item) => {
      const children = buildTreeSelectOptions(
        item.children as T[] | null | undefined,
        getLabel,
      );
      return {
        label: getLabel(item),
        value: String(item.id),
        ...(children.length > 0 ? { children } : {}),
      };
    });
}

export function buildTreeFromFlat<T extends TreeFlatNodeLike>(
  list?: T[] | null,
  rootParentIds: Array<TreeKey | null | undefined> = [undefined, null, '', '0'],
): T[] {
  const nodes = toTreeList(list).map((item) => ({ ...item })) as Array<
    T & {
      children?: T[];
    }
  >;
  const nodeMap = new Map<TreeKey, (typeof nodes)[number]>();
  const roots: (typeof nodes)[number][] = [];

  nodes.forEach((node) => {
    if (node.id !== undefined && node.id !== null) {
      nodeMap.set(node.id, node);
    }
  });

  nodes.forEach((node) => {
    const parentId = node.parentId;
    const isRootNode = rootParentIds.some((value) => value === parentId);

    if (!isRootNode && parentId !== undefined && parentId !== null) {
      const parent = nodeMap.get(parentId);
      if (parent) {
        parent.children ??= [];
        parent.children.push(node);
        return;
      }
    }

    roots.push(node);
  });

  return roots as T[];
}

export function filterTreeSelectOptions(
  options?: TreeSelectOption[] | null,
  excludedKey?: TreeKey,
): TreeSelectOption[] {
  return toTreeList(options)
    .filter((option) => excludedKey === undefined || option.value !== excludedKey)
    .map((option) => {
      const children = filterTreeSelectOptions(option.children, excludedKey);
      return {
        label: option.label,
        value: option.value,
        ...(children.length > 0 ? { children } : {}),
      };
    });
}

export function collectTreeKeys<T extends TreeNodeLike>(list?: T[] | null) {
  const keys: Array<NonNullable<T['id']>> = [];

  walkTree(list, (node) => {
    if (node.id !== undefined && node.id !== null) {
      keys.push(node.id as NonNullable<T['id']>);
    }
  });

  return keys;
}

export function collectExpandableKeys<T extends TreeNodeLike>(list?: T[] | null) {
  const keys: Array<NonNullable<T['id']>> = [];

  walkTree(list, (node) => {
    if (node.id !== undefined && node.id !== null && node.children?.length) {
      keys.push(node.id as NonNullable<T['id']>);
    }
  });

  return keys;
}

export function collectExpandedKeysByDepth<T extends TreeNodeLike>(
  list?: T[] | null,
  maxDepth = 1,
) {
  const keys: Array<NonNullable<T['id']>> = [];

  walkTree(list, (node, depth) => {
    if (
      node.id !== undefined &&
      node.id !== null &&
      node.children?.length &&
      depth <= maxDepth
    ) {
      keys.push(node.id as NonNullable<T['id']>);
    }
  });

  return keys;
}

export function collectDescendantKeys<T extends TreeNodeLike>(
  list?: T[] | null,
  targetKey?: TreeKey,
) {
  const target = findTreeNode(list, targetKey);
  return target?.children?.length
    ? collectTreeKeys(target.children as T[] | null | undefined)
    : [];
}

export function buildAntTreeData<T extends TreeNodeLike>(
  list?: T[] | null,
  getTitle: (node: T) => string = (node) => node.name || String(node.id ?? ''),
): AntTreeNode[] {
  return toTreeList(list)
    .filter((item) => item.id !== undefined && item.id !== null)
    .map((item) => {
      const children: AntTreeNode[] = buildAntTreeData(
        item.children as T[] | null | undefined,
        getTitle,
      );
      return {
        key: item.id as TreeKey,
        title: getTitle(item),
        ...(children.length > 0 ? { children } : {}),
      };
    });
}

function walkTree<T extends TreeNodeLike>(
  list: T[] | null | undefined,
  visitor: (node: T, depth: number) => void,
  depth = 1,
) {
  toTreeList(list).forEach((node) => {
    visitor(node, depth);
    if (node.children?.length) {
      walkTree(node.children as T[], visitor, depth + 1);
    }
  });
}

function findTreeNode<T extends TreeNodeLike>(
  list?: T[] | null,
  targetKey?: TreeKey,
): T | undefined {
  if (targetKey === undefined) {
    return undefined;
  }

  for (const node of toTreeList(list)) {
    if (node.id === targetKey) {
      return node;
    }

    const match = findTreeNode(
      node.children as T[] | null | undefined,
      targetKey,
    );
    if (match) {
      return match;
    }
  }

  return undefined;
}

function toTreeList<T>(list?: T[] | null) {
  return Array.isArray(list) ? list : [];
}

function loadFiltersFromStorage(key: string): Record<string, any> {
  try {
    const stored = localStorage.getItem(key);
    return stored ? JSON.parse(stored) : {};
  } catch {
    return {};
  }
}

function saveFiltersToStorage(filters: Record<string, any>, key: string) {
  try {
    localStorage.setItem(key, JSON.stringify(filters));
  } catch (error) {
    console.error('保存树形列表筛选条件失败:', error);
  }
}
