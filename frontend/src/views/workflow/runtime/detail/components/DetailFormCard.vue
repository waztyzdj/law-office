<script setup lang="ts">
import { Card } from 'ant-design-vue';

import RuntimeFormRenderer from '../../components/RuntimeFormRenderer.vue';

defineProps<{
  formDataJson: string;
  optionJson: string;
  schemaJson: string;
}>();
</script>

<template>
  <Card :bordered="false">
    <RuntimeFormRenderer
      :form-data-json="formDataJson"
      :option-json="optionJson"
      readonly
      :schema-json="schemaJson"
    />
  </Card>
</template>
