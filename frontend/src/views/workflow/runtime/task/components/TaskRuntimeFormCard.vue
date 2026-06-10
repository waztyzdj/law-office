<script setup lang="ts">
import { ref } from 'vue';

import { Card, Form, Input } from 'ant-design-vue';

import type { RuntimeFieldPermissionInfo } from '#/api/workflow';

import RuntimeFormRenderer from '../../components/RuntimeFormRenderer.vue';

defineProps<{
  fieldPermissions?: RuntimeFieldPermissionInfo[];
  formDataJson: string;
  formOptionJson: string;
  formSchemaJson: string;
  isStartMode: boolean;
  loading: boolean;
}>();

const instanceTitle = defineModel<string>('instanceTitle', { required: true });
const businessKey = defineModel<string>('businessKey', { required: true });
const runtimeFormRef = ref<InstanceType<typeof RuntimeFormRenderer>>();

async function getValidatedFormData() {
  return runtimeFormRef.value?.getValidatedFormData();
}

function getFormData() {
  return runtimeFormRef.value?.getFormData();
}

defineExpose({
  getFormData,
  getValidatedFormData,
});
</script>

<template>
  <Card title="表单">
    <Form
      v-if="isStartMode"
      class="workflow-title-form"
      layout="vertical"
    >
      <div class="start-form-grid">
        <Form.Item
          label="申请标题"
          required
        >
          <Input
            v-model:value="instanceTitle"
            :maxlength="200"
            placeholder="请输入申请标题"
          />
        </Form.Item>
        <Form.Item label="业务标识">
          <Input
            v-model:value="businessKey"
            :maxlength="64"
            placeholder="可选，用于关联外部业务"
          />
        </Form.Item>
      </div>
    </Form>
    <RuntimeFormRenderer
      ref="runtimeFormRef"
      :field-permissions="fieldPermissions ?? []"
      :form-data-json="formDataJson"
      :loading="loading"
      :option-json="formOptionJson"
      :schema-json="formSchemaJson"
    />
  </Card>
</template>

<style scoped>
.workflow-title-form {
  margin-bottom: 16px;
}

.start-form-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
  max-width: 960px;
}

@media (max-width: 768px) {
  .start-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
