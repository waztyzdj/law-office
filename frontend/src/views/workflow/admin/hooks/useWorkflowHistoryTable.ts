import type { Ref } from 'vue';

import type { TablePaginationConfig } from '#/composables/Table';

import { computed, reactive, ref } from 'vue';

export interface WorkflowHistoryTableFilter {
  apiCondition?: string;
  condition?: string;
  value?: unknown;
}

export interface WorkflowHistoryTableSorter {
  field?: unknown;
  order?: unknown;
}

export type WorkflowHistoryTableFilters = Record<
  string,
  null | unknown[] | WorkflowHistoryTableFilter
>;

export function useWorkflowHistoryTable<T extends object>(records: Ref<T[]>) {
  const activeFilters = ref<WorkflowHistoryTableFilters>({});
  const activeSorter = ref<WorkflowHistoryTableSorter>({});
  const tablePagination = reactive<TablePaginationConfig>({
    pageNum: 1,
    pageSize: 1000,
    total: 0,
  });

  const displayedRecords = computed(() => {
    const filtered = records.value.filter((record) => matchesFilters(record));
    const sorter = activeSorter.value;
    if (!sorter?.field || !sorter?.order) {
      return filtered;
    }

    const direction = sorter.order === 'ascend' ? 1 : -1;
    const field = String(sorter.field);
    return [...filtered].sort(
      (left, right) =>
        compareValues(resolveFieldValue(left, field), resolveFieldValue(right, field)) *
        direction,
    );
  });

  function handleColumnEmit(
    event: string,
    pagination: unknown,
    filters: WorkflowHistoryTableFilters,
    sorter: WorkflowHistoryTableSorter,
  ) {
    if (event === 'change') {
      handleTableChange(pagination, filters, sorter);
    }
  }

  function handleTableChange(
    _pagination: unknown,
    filters?: WorkflowHistoryTableFilters,
    sorter?: WorkflowHistoryTableSorter | WorkflowHistoryTableSorter[],
  ) {
    activeFilters.value = filters || {};
    const nextSorter = Array.isArray(sorter) ? sorter[0] : sorter;
    if (nextSorter?.field && nextSorter?.order) {
      activeSorter.value = {
        field: String(nextSorter.field),
        order: String(nextSorter.order),
      };
    } else if (nextSorter && Object.keys(nextSorter).length > 0) {
      activeSorter.value = {};
    }
  }

  function resetHistoryTable() {
    activeFilters.value = {};
    activeSorter.value = {};
    tablePagination.total = 0;
  }

  function setHistoryTotal(total: number) {
    tablePagination.total = total;
  }

  function matchesFilters(record: T) {
    return Object.entries(activeFilters.value).every(([field, filter]) => {
      if (
        !isHistoryTableFilter(filter) ||
        filter.value === undefined ||
        filter.value === null ||
        (Array.isArray(filter.value) && filter.value.length === 0)
      ) {
        return true;
      }

      const value = resolveFieldValue(record, field);
      if (filter.condition === 'in') {
        return Array.isArray(filter.value) && filter.value.includes(value);
      }
      return matchesCondition(
        value,
        filter.condition || filter.apiCondition || 'like',
        filter.value,
      );
    });
  }

  return {
    activeFilters,
    displayedRecords,
    handleColumnEmit,
    handleTableChange,
    resetHistoryTable,
    setHistoryTotal,
    tablePagination,
  };
}

function isHistoryTableFilter(
  filter: WorkflowHistoryTableFilters[string],
): filter is WorkflowHistoryTableFilter {
  return Boolean(
    filter && !Array.isArray(filter) && typeof filter === 'object' && 'value' in filter,
  );
}

function matchesCondition(value: unknown, condition: string, filterValue: unknown) {
  const text = String(value ?? '').toLowerCase();
  const target = String(filterValue ?? '').toLowerCase();
  if (condition === 'like') {
    return text.includes(target);
  }
  if (condition === 'ne') {
    return text !== target;
  }
  if (condition === 'eq') {
    return text === target;
  }

  const left = Number(value);
  const right = Number(filterValue);
  if (Number.isFinite(left) && Number.isFinite(right)) {
    if (condition === 'gt') return left > right;
    if (condition === 'ge') return left >= right;
    if (condition === 'lt') return left < right;
    if (condition === 'le') return left <= right;
  }
  return true;
}

function resolveFieldValue<T extends object>(record: T, field: string) {
  return (record as Record<string, unknown>)[field];
}

function compareValues(left: unknown, right: unknown) {
  if (left == null && right == null) return 0;
  if (left == null) return -1;
  if (right == null) return 1;
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }
  return String(left).localeCompare(String(right), 'zh-CN');
}
