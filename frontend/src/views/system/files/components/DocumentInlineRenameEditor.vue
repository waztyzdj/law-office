<script setup lang="ts">
import { computed, ref } from 'vue';

import { Input } from 'ant-design-vue';

interface Props {
  disabled?: boolean;
  rows?: number;
  value?: string;
  variant?: 'grid' | 'list';
}

interface FocusableInput {
  focus: () => void;
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  rows: 1,
  value: '',
  variant: 'list',
});

const emit = defineEmits<{
  cancel: [];
  submit: [];
  'update:value': [value: string];
}>();

const textareaRef = ref<FocusableInput | null>(null);
const editorClass = computed(() => [
  'document-inline-rename-editor',
  `document-inline-rename-editor--${props.variant}`,
]);

function focus() {
  textareaRef.value?.focus();
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    emit('cancel');
  }
}

function handleEnter(event: KeyboardEvent) {
  event.stopPropagation();
  event.preventDefault();
  emit('submit');
}

defineExpose({
  focus,
});
</script>

<template>
  <div :class="editorClass" @click.stop>
    <Input.TextArea
      ref="textareaRef"
      autofocus
      class="document-inline-rename-editor__textarea"
      :disabled="disabled"
      :maxlength="255"
      :rows="rows"
      :value="value"
      @blur="$emit('submit')"
      @keydown="handleKeydown"
      @keydown.enter="handleEnter"
      @update:value="$emit('update:value', $event)"
    />
  </div>
</template>

<style scoped>
.document-inline-rename-editor {
  display: flex;
  min-width: 0;
  align-items: stretch;
  border: 1px solid hsl(var(--primary) / 70%);
  border-radius: 6px;
  background: hsl(var(--background));
  box-shadow: 0 0 0 2px hsl(var(--primary) / 12%);
}

.document-inline-rename-editor--grid {
  width: 100%;
  text-align: left;
}

.document-inline-rename-editor--list {
  width: min(420px, 100%);
}

.document-inline-rename-editor__textarea {
  flex: 1 1 auto;
  min-width: 0;
}

.document-inline-rename-editor__textarea :deep(textarea.ant-input) {
  resize: none;
  border: 0;
  box-shadow: none;
  color: hsl(var(--foreground));
  font-size: 13px;
  line-height: 20px;
}

.document-inline-rename-editor--grid
  .document-inline-rename-editor__textarea
  :deep(textarea.ant-input) {
  height: 60px;
  min-height: 60px;
  max-height: 60px;
  overflow-wrap: anywhere;
}

.document-inline-rename-editor--list
  .document-inline-rename-editor__textarea
  :deep(textarea.ant-input) {
  height: 28px;
  min-height: 28px;
  max-height: 28px;
  overflow: hidden;
  overflow-wrap: normal;
  white-space: nowrap;
}

.document-inline-rename-editor__textarea :deep(textarea.ant-input:focus) {
  box-shadow: none;
}

</style>
