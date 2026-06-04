<script setup lang="ts">
import type { CSSProperties } from 'vue';

interface Props {
  style?: CSSProperties;
  visible?: boolean;
}

withDefaults(defineProps<Props>(), {
  style: () => ({}),
  visible: false,
});
</script>

<template>
  <div v-if="visible" class="document-selection-box" :style="style" />
</template>

<style scoped>
.document-selection-box {
  position: absolute;
  z-index: 5;
  pointer-events: none;
  border: 1px solid hsl(var(--primary) / 70%);
  background: hsl(var(--primary) / 12%);
}
</style>
