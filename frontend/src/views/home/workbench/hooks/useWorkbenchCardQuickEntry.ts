import type { WorkbenchCardItem } from '#/api/home/workbench';
import type { ComputedRef, Ref } from 'vue';
import type {
  WorkbenchQuickEntryActionPayload,
  WorkbenchQuickEntryExpose,
  WorkbenchQuickEntrySortSavePayload,
} from '../types';

import { ref } from 'vue';

interface UseWorkbenchCardQuickEntryOptions {
  items: ComputedRef<WorkbenchCardItem[]>;
  quickEntryCardRef: Ref<WorkbenchQuickEntryExpose | undefined>;
  onAdd: (payload: WorkbenchQuickEntryActionPayload) => void;
  onEdit: (payload: WorkbenchQuickEntryActionPayload) => void;
  onSortSave: (payload: WorkbenchQuickEntrySortSavePayload) => void;
}

export function useWorkbenchCardQuickEntry(
  options: UseWorkbenchCardQuickEntryOptions,
) {
  const editMode = ref(false);

  function getCurrentItems() {
    return options.quickEntryCardRef.value?.getCurrentItems?.() ?? options.items.value;
  }

  function handleSettings() {
    if (!editMode.value) {
      editMode.value = true;
      return;
    }
    const sortedItems = getCurrentItems();
    options.onSortSave({
      items: sortedItems,
      onSaved: () => {
        editMode.value = false;
      },
    });
  }

  function handleCancel() {
    options.quickEntryCardRef.value?.reset?.();
    editMode.value = false;
  }

  function handleAdd() {
    options.onAdd({
      draft: editMode.value,
      items: getCurrentItems(),
    });
  }

  function handleEdit(item: WorkbenchCardItem) {
    options.onEdit({
      draft: editMode.value,
      item,
      items: getCurrentItems(),
    });
  }

  return {
    editMode,
    handleAdd,
    handleCancel,
    handleEdit,
    handleSettings,
  };
}
