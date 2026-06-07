import { computed, ref } from 'vue';

import {
  DOCUMENT_SORT_FIELDS,
  DOCUMENT_SORT_OPTIONS,
  DOCUMENT_SORT_ORDERS,
  type DocumentSortField,
  type DocumentSortOption,
  type DocumentSortOrder,
  type DocumentSortState,
} from '#/constants/document';
import type { DocumentViewMode } from '../types';

const DOCUMENT_VIEW_MODE_STORAGE_KEY = 'document_center_view_mode';
const DOCUMENT_SORT_STORAGE_KEY = 'document_center_sort';

function readDocumentViewMode(): DocumentViewMode {
  if (typeof window === 'undefined') {
    return 'grid';
  }
  try {
    const cached = window.localStorage.getItem(DOCUMENT_VIEW_MODE_STORAGE_KEY);
    return cached === 'list' || cached === 'grid' ? cached : 'grid';
  } catch {
    return 'grid';
  }
}

function readDocumentSortState(): DocumentSortState {
  const defaultState: DocumentSortState = {
    field: 'fileName',
    order: 'asc',
  };
  if (typeof window === 'undefined') {
    return defaultState;
  }
  try {
    const cached = window.localStorage.getItem(DOCUMENT_SORT_STORAGE_KEY);
    if (!cached) {
      return defaultState;
    }
    const parsed = JSON.parse(cached) as Partial<DocumentSortState>;
    return {
      field: DOCUMENT_SORT_FIELDS.includes(parsed.field as DocumentSortField)
        ? (parsed.field as DocumentSortField)
        : defaultState.field,
      order: DOCUMENT_SORT_ORDERS.includes(parsed.order as DocumentSortOrder)
        ? (parsed.order as DocumentSortOrder)
        : defaultState.order,
    };
  } catch {
    return defaultState;
  }
}

export function useDocumentSort() {
  const documentViewMode = ref<DocumentViewMode>(readDocumentViewMode());
  const documentSortOptions: DocumentSortOption[] = DOCUMENT_SORT_OPTIONS;
  const documentSortState = ref<DocumentSortState>(readDocumentSortState());

  const currentDocumentSortLabel = computed(
    () =>
      documentSortOptions.find((option) => option.field === documentSortState.value.field)?.label ||
      '名称',
  );

  const documentViewModeModel = computed({
    get: () => documentViewMode.value,
    set: (mode: DocumentViewMode) => handleChangeDocumentViewMode(mode),
  });

  function handleChangeDocumentViewMode(mode: DocumentViewMode) {
    if (mode !== 'list' && mode !== 'grid') {
      return;
    }
    documentViewMode.value = mode;
    if (typeof window === 'undefined') {
      return;
    }
    try {
      window.localStorage.setItem(DOCUMENT_VIEW_MODE_STORAGE_KEY, mode);
    } catch {
      // 本地缓存失败不影响文档浏览。
    }
  }

  function handleChangeDocumentSort(state: DocumentSortState) {
    const nextState: DocumentSortState = {
      field: DOCUMENT_SORT_FIELDS.includes(state.field) ? state.field : 'fileName',
      order: DOCUMENT_SORT_ORDERS.includes(state.order) ? state.order : 'asc',
    };
    documentSortState.value = nextState;
    if (typeof window === 'undefined') {
      return;
    }
    try {
      window.localStorage.setItem(DOCUMENT_SORT_STORAGE_KEY, JSON.stringify(nextState));
    } catch {
      // 本地缓存失败不影响文档浏览。
    }
  }

  function handleChangeDocumentSortField(field: DocumentSortField) {
    handleChangeDocumentSort({
      field,
      order: documentSortState.value.order,
    });
  }

  function handleChangeDocumentSortOrder(order: DocumentSortOrder) {
    handleChangeDocumentSort({
      field: documentSortState.value.field,
      order,
    });
  }

  function isActiveDocumentSort(field: DocumentSortField, order?: DocumentSortOrder) {
    return documentSortState.value.field === field && (!order || documentSortState.value.order === order);
  }

  return {
    currentDocumentSortLabel,
    documentSortOptions,
    documentSortState,
    documentViewMode,
    documentViewModeModel,
    handleChangeDocumentViewMode,
    handleChangeDocumentSort,
    handleChangeDocumentSortField,
    handleChangeDocumentSortOrder,
    isActiveDocumentSort,
  };
}
