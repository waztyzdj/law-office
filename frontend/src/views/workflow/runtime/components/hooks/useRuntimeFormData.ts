import type { ComponentPublicInstance, Ref } from 'vue';

import type {
  AvailableProcessInfo,
  InstanceDetailInfo,
  StartFormInfo,
  TaskFormInfo,
} from '#/api/workflow';
import type { DrawerMode } from '../runtimeTypes';

import { computed, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import RuntimeFormRenderer from '../RuntimeFormRenderer.vue';

interface UseRuntimeFormDataOptions {
  currentProcess: Ref<AvailableProcessInfo | undefined>;
  detail: Ref<InstanceDetailInfo | undefined>;
  mode: Ref<DrawerMode>;
  startForm: Ref<StartFormInfo | undefined>;
  taskForm: Ref<TaskFormInfo | undefined>;
}

export function useRuntimeFormData(options: UseRuntimeFormDataOptions) {
  const userStore = useUserStore();
  const runtimeFormRef = ref<InstanceType<typeof RuntimeFormRenderer>>();
  const instanceTitle = ref('');
  const businessKey = ref('');
  const approvalComment = ref('');

  const currentUserName = computed(
    () =>
      userStore.userInfo?.realName ||
      userStore.userInfo?.realname ||
      userStore.userInfo?.username ||
      '',
  );
  const isStartMode = computed(() => options.mode.value === 'start');
  const isTodoMode = computed(() => options.mode.value === 'todo');
  const isStartDraftTask = computed(
    () => options.taskForm.value?.taskType === 'start_draft',
  );
  const readonly = computed(() => !isStartMode.value && !isTodoMode.value);
  const defaultFieldPermission = computed(() =>
    isStartMode.value || isStartDraftTask.value ? 'editable' : 'readonly',
  );
  const showApprovalComment = computed(
    () => isTodoMode.value && !isStartDraftTask.value,
  );
  const showRuntimeActions = computed(() => isStartMode.value || isTodoMode.value);
  const drawerTitle = computed(() => {
    if (isStartMode.value) {
      return options.currentProcess.value?.processName
        ? `发起申请：${options.currentProcess.value.processName}`
        : '发起申请';
    }
    if (isTodoMode.value) {
      return isStartDraftTask.value ? '提交申请' : '办理审批';
    }
    return '审批详情';
  });
  const formSchemaJson = computed(
    () =>
      options.startForm.value?.schemaJson ||
      options.taskForm.value?.schemaJson ||
      options.detail.value?.formInstance?.formSchemaSnapshotJson ||
      '[]',
  );
  const formOptionJson = computed(
    () =>
      options.startForm.value?.optionJson ||
      options.taskForm.value?.optionJson ||
      options.detail.value?.formInstance?.formOptionSnapshotJson ||
      '{}',
  );
  const formDataJson = computed(
    () =>
      options.taskForm.value?.formDataJson ||
      options.detail.value?.formInstance?.formDataJson ||
      '{}',
  );
  const fieldPermissions = computed(() => {
    if (isStartMode.value) {
      return options.startForm.value?.fieldPermissions ?? [];
    }
    return options.taskForm.value?.fieldPermissions ?? [];
  });

  function handleRuntimeFormRef(instance: Element | ComponentPublicInstance | null) {
    runtimeFormRef.value =
      instance as InstanceType<typeof RuntimeFormRenderer> | undefined;
  }

  function resetRuntimeFormData(process?: AvailableProcessInfo) {
    runtimeFormRef.value = undefined;
    instanceTitle.value = process ? buildDefaultInstanceTitle(process) : '';
    businessKey.value = '';
    approvalComment.value = '';
  }

  function buildDefaultInstanceTitle(record: AvailableProcessInfo) {
    const processName = record.processName ?? '';
    if (!currentUserName.value) {
      return processName;
    }
    return processName ? `${currentUserName.value}的${processName}` : currentUserName.value;
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

  function validateApprovalComment() {
    if (!approvalComment.value.trim()) {
      message.warning('请输入审批意见');
      return false;
    }
    return true;
  }

  function resolveApprovalComment(defaultComment?: string) {
    const comment = approvalComment.value.trim();
    if (comment) {
      return comment;
    }
    if (defaultComment) {
      approvalComment.value = defaultComment;
      return defaultComment;
    }
    return undefined;
  }

  return {
    approvalComment,
    businessKey,
    collectFormDataJson,
    defaultFieldPermission,
    drawerTitle,
    fieldPermissions,
    formDataJson,
    formOptionJson,
    formSchemaJson,
    handleRuntimeFormRef,
    instanceTitle,
    isStartDraftTask,
    isStartMode,
    isTodoMode,
    readonly,
    resetRuntimeFormData,
    resolveApprovalComment,
    showApprovalComment,
    showRuntimeActions,
    validateApprovalComment,
    validateTitle,
  };
}
