<script setup lang="ts">
import { computed } from 'vue';

import { Tag } from 'ant-design-vue';

import { getStatusMeta } from './status';

const props = defineProps<{
  status?: string;
}>();

const meta = computed(() => getStatusMeta(props.status));
</script>

<template>
  <Tag :color="meta.color">
    {{ meta.label }}
  </Tag>
</template>
