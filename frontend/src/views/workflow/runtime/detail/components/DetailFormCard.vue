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
  <Card title="申请表单">
    <RuntimeFormRenderer
      :form-data-json="formDataJson"
      :option-json="optionJson"
      readonly
      :schema-json="schemaJson"
    />
  </Card>
</template>
