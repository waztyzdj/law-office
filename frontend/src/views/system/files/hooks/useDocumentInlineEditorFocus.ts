import { nextTick, ref } from 'vue';

interface FocusableInput {
  focus: () => void;
  input?: HTMLInputElement;
}

interface RenameEditorExpose {
  focus: () => Promise<void> | void;
}

type RenameEditorRef = RenameEditorExpose | RenameEditorExpose[] | null;

interface UseDocumentInlineEditorFocusOptions {
  cancel: () => void;
}

export function useDocumentInlineEditorFocus(options: UseDocumentInlineEditorFocusOptions) {
  const createNameInputRef = ref<FocusableInput | null>(null);
  const renameNameInputRef = ref<RenameEditorRef>(null);

  function setCreateNameInputRef(element: unknown) {
    createNameInputRef.value = isFocusableInput(element) ? element : null;
  }

  function setRenameNameInputRef(element: unknown) {
    renameNameInputRef.value = isRenameEditorExpose(element) ? element : null;
  }

  async function focusCreateNameInput() {
    await nextTick();
    createNameInputRef.value?.focus();
    createNameInputRef.value?.input?.select();
  }

  async function focusRenameNameInput() {
    await nextTick();
    const renameEditor = Array.isArray(renameNameInputRef.value)
      ? renameNameInputRef.value[0]
      : renameNameInputRef.value;
    await renameEditor?.focus();
  }

  function handleInlineKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      options.cancel();
    }
  }

  return {
    focusCreateNameInput,
    focusRenameNameInput,
    handleInlineKeydown,
    setCreateNameInputRef,
    setRenameNameInputRef,
  };
}

function isFocusableInput(element: unknown): element is FocusableInput {
  return Boolean(element && typeof (element as FocusableInput).focus === 'function');
}

function isRenameEditorExpose(element: unknown): element is RenameEditorExpose {
  return Boolean(element && typeof (element as RenameEditorExpose).focus === 'function');
}
