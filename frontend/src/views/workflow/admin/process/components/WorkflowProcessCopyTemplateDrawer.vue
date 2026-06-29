<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type {
  WorkflowCategoryInfo,
  WorkflowFormDefinitionInfo,
  WorkflowProcessModelInfo,
  WorkflowProcessTemplateCopyReq,
} from '#/api/workflow';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm, z } from '#/adapter/form';
import {
  copyWorkflowProcessTemplate,
  getWorkflowFormById,
  listWorkflowCategories,
  pageLatestWorkflowForms,
} from '#/api/workflow';

interface DrawerPayload {
  record: WorkflowProcessModelInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const currentProcess = ref<WorkflowProcessModelInfo>();
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const formOptions = ref<{ label: string; value: string }[]>([]);
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'categoryId',
    component: 'Select',
    label: '流程分类',
    defaultValue: getDefaultValue('categoryId', ''),
    rules: 'selectRequired',
    componentProps: {
      options: categoryOptions.value,
      placeholder: '请选择流程分类',
      showSearch: true,
      optionFilterProp: 'label',
    },
  },
  {
    fieldName: 'formDefinitionId',
    component: 'Select',
    label: '绑定表单',
    defaultValue: getDefaultValue('formDefinitionId', ''),
    rules: 'selectRequired',
    componentProps: {
      options: formOptions.value,
      placeholder: '请选择绑定表单',
      showSearch: true,
      optionFilterProp: 'label',
    },
  },
  {
    fieldName: 'processKey',
    component: 'Input',
    label: '流程编码',
    defaultValue: getDefaultValue('processKey', ''),
    rules: z
      .string({ required_error: '请输入流程编码' })
      .min(1, '请输入流程编码')
      .max(64, '流程编码不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入流程编码',
    },
  },
  {
    fieldName: 'processName',
    component: 'Input',
    label: '流程名称',
    defaultValue: getDefaultValue('processName', ''),
    rules: z
      .string({ required_error: '请输入流程名称' })
      .min(1, '请输入流程名称')
      .max(100, '流程名称不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入流程名称',
    },
  },
  {
    fieldName: 'designerType',
    component: 'Select',
    label: '设计器',
    defaultValue: getDefaultValue('designerType', 'simple'),
    rules: 'selectRequired',
    componentProps: {
      disabled: true,
      options: [
        { label: '简易设计器', value: 'simple' },
        { label: 'BPMN设计器', value: 'bpmn' },
      ],
    },
  },
  {
    fieldName: 'version',
    component: 'InputNumber',
    label: '版本号',
    defaultValue: getDefaultValue('version', 1),
    rules: z.number().min(1, '版本号不能小于1'),
    componentProps: {
      disabled: true,
      min: 1,
      precision: 0,
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'startScopeType',
    component: 'Select',
    label: '发起范围',
    defaultValue: getDefaultValue('startScopeType', 'all'),
    rules: 'selectRequired',
    componentProps: {
      disabled: true,
      options: [
        { label: '全部人员', value: 'all' },
        { label: '指定范围', value: 'specified' },
      ],
    },
  },
  {
    fieldName: 'status',
    component: 'Select',
    label: '状态',
    defaultValue: getDefaultValue('status', 'draft'),
    rules: 'selectRequired',
    componentProps: {
      disabled: true,
      options: [
        { label: '草稿', value: 'draft' },
        { label: '已发布', value: 'published' },
        { label: '已停用', value: 'disabled' },
      ],
    },
  },
  {
    fieldName: 'remark',
    component: 'Textarea',
    label: '备注',
    defaultValue: getDefaultValue('remark', ''),
    rules: z.string().max(500, '备注不能超过500个字符').optional(),
    componentProps: {
      autoSize: { minRows: 3, maxRows: 5 },
      maxlength: 500,
      placeholder: '请输入备注',
    },
  },
];

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 92,
  },
  layout: 'horizontal',
  schema: buildFormSchema(),
  scrollToFirstError: true,
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[680px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '复制',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: '复制审批模板',
});

function mapCategoryOptions(categories: WorkflowCategoryInfo[]) {
  return categories
    .filter((item) => item.id)
    .map((item) => ({
      label: item.categoryName ?? item.categoryCode ?? item.id!,
      value: item.id!,
    }));
}

function buildFormOptions(form?: WorkflowFormDefinitionInfo) {
  return form?.id
    ? [{
        label: `${form.formName ?? form.formKey ?? form.id} v${form.version ?? 1}`,
        value: form.id,
      }]
    : [];
}

function mapFormOptions(forms: WorkflowFormDefinitionInfo[], source?: WorkflowFormDefinitionInfo) {
  const options = forms
    .filter((item) => item.id)
    .map((item) => ({
      label: `${item.formName ?? item.formKey ?? item.id} v${item.version ?? 1}`,
      value: item.id!,
    }));
  if (source?.id && !options.some((item) => item.value === source.id)) {
    options.unshift(...buildFormOptions(source));
  }
  return options;
}

function buildCopyKey(sourceKey?: string) {
  const baseKey = sourceKey?.trim() || 'workflow';
  const suffix = `_copy_${Date.now().toString(36).slice(-6)}`;
  return `${baseKey}${suffix}`.slice(0, 64);
}

function buildInitialValues(record: WorkflowProcessModelInfo) {
  return {
    categoryId: record.categoryId ?? '',
    designerType: record.designerType ?? 'simple',
    formDefinitionId: record.formDefinitionId ?? '',
    processKey: buildCopyKey(record.processKey),
    processName: `${record.processName ?? '审批流程'}-副本`,
    remark: '',
    startScopeType: record.startScopeType ?? 'all',
    status: 'draft',
    version: 1,
  };
}

async function loadOptions(record: WorkflowProcessModelInfo) {
  const [categories, formPage, formDetail] = await Promise.all([
    listWorkflowCategories({ queryParams: { status: 'enabled' } }),
    pageLatestWorkflowForms({
      pageNum: 1,
      pageSize: 500,
      queryParams: { status: 'published' },
      sortField: 'form_key',
      sortOrder: 'asc',
    }),
    record.formDefinitionId
      ? getWorkflowFormById(record.formDefinitionId)
      : Promise.resolve(undefined),
  ]);
  categoryOptions.value = mapCategoryOptions(categories ?? []);
  formOptions.value = mapFormOptions(formPage.records ?? [], formDetail);
  formApi.setState({ schema: buildFormSchema() });
}

async function prepareFormState(record: WorkflowProcessModelInfo) {
  currentProcess.value = record;
  hasSyncedMountedValues.value = false;
  await loadOptions(record);
  initialValues.value = buildInitialValues(record);
  formApi.setState({ schema: buildFormSchema() });
  drawerApi.setState({ loading: false });
}

async function syncMountedFormValues() {
  if (!formApi.isMounted || hasSyncedMountedValues.value) {
    return;
  }
  await formApi.resetForm();
  await formApi.setValues(initialValues.value);
  hasSyncedMountedValues.value = true;
}

function buildPayload(values: Record<string, any>): WorkflowProcessTemplateCopyReq {
  return {
    categoryId: values.categoryId,
    formDefinitionId: values.formDefinitionId,
    processKey: values.processKey,
    processName: values.processName,
    remark: values.remark,
    sourceProcessModelId: currentProcess.value?.id,
  };
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid || !currentProcess.value?.id) {
    return;
  }

  try {
    drawerApi.lock();
    const values = await formApi.getValues();
    await copyWorkflowProcessTemplate(buildPayload({ ...initialValues.value, ...values }));
    message.success('复制审批模板成功');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(payload: DrawerPayload) {
  await prepareFormState(payload.record);

  if (formApi.isMounted) {
    await syncMountedFormValues();
  }

  drawerApi.setData(payload).open();

  await nextTick();
  if (formApi.isMounted) {
    void syncMountedFormValues();
  }
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <Form />
  </Drawer>
</template>
