import type { ComputedRef } from 'vue';

import { computed, ref, watch } from 'vue';

export function useWorkbenchCardPaging<T>(
  items: ComputedRef<T[]>,
  pageSize: ComputedRef<number>,
) {
  const currentPage = ref(1);
  const totalPages = computed(() =>
    Math.max(1, Math.ceil(items.value.length / pageSize.value)),
  );
  const pagedItems = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value;
    return items.value.slice(start, start + pageSize.value);
  });
  const hasPagination = computed(() => items.value.length > pageSize.value);

  watch(
    () => [items.value.length, pageSize.value],
    () => {
      if (currentPage.value > totalPages.value) {
        currentPage.value = totalPages.value;
      }
    },
  );

  function resetPage() {
    currentPage.value = 1;
  }

  return {
    currentPage,
    hasPagination,
    pagedItems,
    resetPage,
    totalPages,
  };
}
