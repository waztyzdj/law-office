<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';
import type {
  AvailableProcessInfo,
  AssigneeSelectNodeInfo,
  SelectedAssigneeReq,
  StartFormInfo,
} from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenDrawer } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Button, message } from 'ant-design-vue';

import {
  getStartForm,
  saveWorkflowStartDraft,
  startWorkflowProcess,
} from '#/api/workflow';

import RuntimeFormRenderer from '../../components/RuntimeFormRenderer.vue';
import AssigneeSelectPanel from '../../components/AssigneeSelectPanel.vue';

interface DrawerPayload {
  record: AvailableProcessInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const router = useRouter();
const userStore = useUserStore();

const currentProcess = ref<AvailableProcessInfo>();
const startForm = ref<StartFormInfo>();
const runtimeFormRef = ref<InstanceType<typeof RuntimeFormRenderer>>();
const instanceTitle = ref('');
const businessKey = ref('');
const formDataJson = ref('{}');
const selectedAssignees = ref<SelectedAssigneeReq[]>([]);
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);

const drawerTitle = computed(() =>
  currentProcess.value?.processName
    ? `发起申请：${currentProcess.value.processName}`
    : '发起申请',
);
const formTitle = computed(() =>
  startForm.value?.formName || currentProcess.value?.formName || '',
);
const currentUserName = computed(
  () =>
    userStore.userInfo?.realName ||
    userStore.userInfo?.realname ||
    userStore.userInfo?.username ||
    '',
);
const assigneeSelectNodes = computed<AssigneeSelectNodeInfo[]>(
  () => startForm.value?.assigneeSelectNodes ?? [],
);

const draftKey = computed(() =>
  currentProcess.value?.id
    ? `workflow_start_draft_${currentProcess.value.id}`
    : '',
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[60vw]! sm:max-w-none!',
  closeOnClickModal: true,
  contentClass: 'workflow-start-drawer',
  footer: false,
  title: drawerTitle.value,
});

function handleRuntimeFormRef(instance: Element | ComponentPublicInstance | null) {
  runtimeFormRef.value =
    instance as InstanceType<typeof RuntimeFormRenderer> | undefined;
}

function updateDrawerTitle() {
  drawerApi.setState({ title: drawerTitle.value });
}

function resetState(record: AvailableProcessInfo) {
  currentProcess.value = record;
  startForm.value = undefined;
  runtimeFormRef.value = undefined;
  instanceTitle.value = buildDefaultInstanceTitle(record);
  businessKey.value = '';
  formDataJson.value = '{}';
  selectedAssignees.value = [];
  loading.value = false;
  saving.value = false;
  submitting.value = false;
  updateDrawerTitle();
}

function buildDefaultInstanceTitle(record: AvailableProcessInfo) {
  const processName = record.processName ?? '';
  if (!currentUserName.value) {
    return processName;
  }
  return processName ? `${currentUserName.value}的${processName}` : currentUserName.value;
}

async function open(payload: DrawerPayload) {
  resetState(payload.record);
  drawerApi.open();
  await loadStartForm();
}

async function loadStartForm() {
  if (!currentProcess.value?.id) {
    return;
  }

  loading.value = true;
  try {
    startForm.value = await getStartForm(currentProcess.value.id);
    applyDraft();
    updateDrawerTitle();
    await nextTick();
  } finally {
    loading.value = false;
  }
}

function applyDraft() {
  if (!draftKey.value) {
    return;
  }

  try {
    const raw = localStorage.getItem(draftKey.value);
    if (!raw) {
      return;
    }
    const draft = JSON.parse(raw) as {
      businessKey?: string;
      formDataJson?: string;
    };
    businessKey.value = draft.businessKey ?? '';
    formDataJson.value = draft.formDataJson ?? '{}';
  } catch {
    localStorage.removeItem(draftKey.value);
  }
}

async function collectFormDataJson(validate: boolean) {
  const formData = validate
    ? await runtimeFormRef.value?.getValidatedFormData()
    : runtimeFormRef.value?.getFormData?.();
  return JSON.stringify(formData ?? {});
}

function validateTitle() {
  if (!instanceTitle.value.trim()) {
    message.warning('请输入申请标题');
    return false;
  }
  return true;
}

function validateSelectedAssignees() {
  for (const node of assigneeSelectNodes.value) {
    if (!node.nodeId) {
      continue;
    }
    const selected = selectedAssignees.value.find((item) => item.nodeId === node.nodeId);
    if (!selected?.userIds?.[0]) {
      message.warning(`请选择${node.nodeName || node.nodeId}的审批人`);
      return false;
    }
  }
  return true;
}

function collectSelectedAssignees() {
  const result: SelectedAssigneeReq[] = [];
  for (const item of selectedAssignees.value) {
    const nodeId = item.nodeId;
    const userId = item.userIds?.[0];
    if (nodeId && userId) {
      result.push({ nodeId, userIds: [userId] });
    }
  }
  return result;
}

async function handleSave() {
  if (!currentProcess.value?.id || !validateTitle()) {
    return;
  }

  saving.value = true;
  try {
    formDataJson.value = await collectFormDataJson(false);
    await saveWorkflowStartDraft({
      businessKey: businessKey.value.trim() || undefined,
      formDataJson: formDataJson.value,
      instanceTitle: instanceTitle.value.trim(),
      processModelId: currentProcess.value.id,
    });
    if (draftKey.value) {
      localStorage.removeItem(draftKey.value);
    }
    message.success('已保存到我的待办');
    emit('success');
    drawerApi.close();
  } finally {
    saving.value = false;
  }
}

async function handleSubmit() {
  if (!currentProcess.value?.id || !validateTitle()) {
    return;
  }
  if (!validateSelectedAssignees()) {
    return;
  }

  submitting.value = true;
  try {
    const result = await startWorkflowProcess({
      businessKey: businessKey.value.trim() || undefined,
      formDataJson: await collectFormDataJson(true),
      instanceTitle: instanceTitle.value.trim(),
      processModelId: currentProcess.value.id,
      selectedAssignees: collectSelectedAssignees(),
    });
    if (draftKey.value) {
      localStorage.removeItem(draftKey.value);
    }
    message.success('申请已提交');
    emit('success');
    drawerApi.close();
    await router.push({
      name: 'WorkflowInstanceDetail',
      query: { id: result.processInstanceId },
    });
  } finally {
    submitting.value = false;
  }
}

function handleCancel() {
  drawerApi.close();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="start-drawer-body">
      <div class="runtime-form-shell">
        <div
          v-if="formTitle"
          class="runtime-form-title"
        >
          {{ formTitle }}
        </div>
        <RuntimeFormRenderer
          :ref="handleRuntimeFormRef"
          :field-permissions="startForm?.fieldPermissions ?? []"
          :form-data-json="formDataJson"
          :loading="loading"
          :option-json="startForm?.optionJson ?? '{}'"
          :schema-json="startForm?.schemaJson ?? '[]'"
        />
        <AssigneeSelectPanel
          v-if="assigneeSelectNodes.length"
          v-model:value="selectedAssignees"
          :disabled="saving || submitting"
          :nodes="assigneeSelectNodes"
        />
      </div>

      <div class="start-drawer-actions">
        <Button
          :loading="saving"
          :disabled="submitting"
          type="primary"
          @click="handleSave"
        >
          保存
        </Button>
        <Button
          :loading="submitting"
          type="primary"
          @click="handleSubmit"
        >
          提交
        </Button>
        <Button
          :disabled="saving || submitting"
          @click="handleCancel"
        >
          取消
        </Button>
      </div>
    </div>
  </Drawer>
</template>

<style scoped>
:global(.workflow-start-drawer) {
  height: calc(100vh - 110px);
  overflow: auto;
  padding: 24px 32px;
}

.start-drawer-body {
  margin: 0 auto;
  max-width: 960px;
}

.runtime-form-shell {
  padding-top: 4px;
}

.runtime-form-title {
  color: #1f2937;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 18px;
  text-align: center;
}

.start-drawer-actions {
  border-top: 1px solid #f0f0f0;
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
}

@media (max-width: 768px) {
  :global(.workflow-start-drawer) {
    padding: 16px;
  }
}
</style>
