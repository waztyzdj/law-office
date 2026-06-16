<script setup lang="ts">
import type { WorkflowFormDefinitionInfo } from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import FcDesigner from '@form-create/antd-designer';

import { getWorkflowFormById, saveWorkflowForm } from '#/api/workflow';

interface DesignerExpose {
  getOption: () => Record<string, unknown>;
  getRule: () => unknown[];
  setOption: (option: Record<string, unknown>) => void;
  setRule: (rule: string | unknown[]) => void;
}

interface DrawerPayload {
  readonly?: boolean;
  record: WorkflowFormDefinitionInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const designerRef = ref<DesignerExpose>();
const currentForm = ref<WorkflowFormDefinitionInfo>();
const hasSyncedDesigner = ref(false);
const designerReady = ref(false);
const readonlyMode = ref(false);
const designerConfig = computed(() => {
  const token = import.meta.env.VITE_FORM_CREATE_AI_TOKEN;
  const api = import.meta.env.VITE_FORM_CREATE_AI_API;

  return {
    ai: token
      ? {
          api: api || undefined,
          token,
        }
      : undefined,
    showAi: false,
  };
});

const drawerTitle = computed(() =>
  currentForm.value?.formName
    ? `${readonlyMode.value ? '查看设计' : '设计表单'}：${currentForm.value.formName}`
    : readonlyMode.value ? '查看设计' : '设计表单',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[92vw]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存设计',
  contentClass: 'workflow-form-designer-drawer',
  onOpened: syncDesigner,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
  zIndex: 1001,
});

function parseJsonValue<T>(value: string | undefined, fallback: T): T {
  if (!value) {
    return fallback;
  }

  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function updateDrawerTitle() {
  drawerApi.setState({ title: drawerTitle.value });
}

async function syncDesigner() {
  if (!designerRef.value || !currentForm.value || hasSyncedDesigner.value) {
    return;
  }

  designerRef.value.setRule(
    parseJsonValue<unknown[]>(currentForm.value.schemaJson, []),
  );
  designerRef.value.setOption(
    parseJsonValue<Record<string, unknown>>(currentForm.value.optionJson, {}),
  );
  hasSyncedDesigner.value = true;
}

function buildSavePayload(): WorkflowFormDefinitionInfo {
  const form = currentForm.value;
  const designer = designerRef.value;
  if (!form || !designer) {
    throw new Error('表单设计器尚未初始化');
  }

  return {
    ...form,
    optionJson: JSON.stringify(designer.getOption() ?? {}),
    schemaJson: JSON.stringify(designer.getRule() ?? []),
  };
}

async function handleSubmit() {
  if (readonlyMode.value) {
    drawerApi.close();
    return;
  }

  try {
    drawerApi.lock();
    await saveWorkflowForm(buildSavePayload());
    message.success('表单设计已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(payload: DrawerPayload) {
  currentForm.value = payload.record;
  readonlyMode.value = payload.readonly === true;
  hasSyncedDesigner.value = false;
  designerReady.value = false;
  updateDrawerTitle();
  drawerApi.setState({ footer: !readonlyMode.value, title: drawerTitle.value });
  drawerApi.setData(payload).open();

  await nextTick();
  if (payload.record.id) {
    try {
      currentForm.value = await getWorkflowFormById(payload.record.id);
      hasSyncedDesigner.value = false;
      updateDrawerTitle();
    } catch {
      currentForm.value = payload.record;
    }
  }
  await nextTick();
  designerReady.value = true;
  await nextTick();
  void syncDesigner();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="designer-shell">
      <FcDesigner
        v-if="designerReady"
        ref="designerRef"
        class="workflow-fc-designer"
        :config="designerConfig"
        height="100%"
        @vue:mounted="syncDesigner"
      />
    </div>
  </Drawer>
</template>

<style scoped>
:global(.workflow-form-designer-drawer) {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 110px);
  overflow: hidden;
  padding: 0;
}

.designer-shell {
  flex: 1;
  height: calc(100vh - 156px);
  min-height: 0;
  overflow: hidden;
}

.workflow-fc-designer {
  height: 100%;
  min-height: 0;
}
</style>
