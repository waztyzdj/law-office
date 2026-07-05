import type { WorkbenchCardItem } from '#/api/home/workbench';
import type { ComputedRef, Ref } from 'vue';
import type { WorkbenchQuickEntryExpose } from '../types';

import { ref } from 'vue';

interface UseWorkbenchCardQuickEntryOptions {
  items: ComputedRef<WorkbenchCardItem[]>;
  quickEntryCardRef: Ref<WorkbenchQuickEntryExpose | undefined>;
  onAdd: () => void;
  onEdit: (item: WorkbenchCardItem) => void;
  onSortSave: (items: WorkbenchCardItem[]) => void;
}

export function useWorkbenchCardQuickEntry(
  options: UseWorkbenchCardQuickEntryOptions,
) {
  const editMode = ref(false);

  function handleSettings() {
    if (!editMode.value) {
      editMode.value = true;
      return;
    }
    const sortedItems =
      options.quickEntryCardRef.value?.getCurrentItems?.() ?? options.items.value;
    editMode.value = false;
    options.onSortSave(sortedItems);
  }

  function handleCancel() {
    options.quickEntryCardRef.value?.reset?.();
    editMode.value = false;
  }

  return {
    editMode,
    handleAdd: options.onAdd,
    handleCancel,
    handleEdit: options.onEdit,
    handleSettings,
  };
}
