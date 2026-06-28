<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type {
  WorkflowCategoryInfo,
  WorkflowFormDefinitionInfo,
  WorkflowFormTemplateCopyReq,
} from '#/api/workflow';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm, z } from '#/adapter/form';
import {
  copyWorkflowFormTemplate,
  listWorkflowCategories,
} from '#/api/workflow';

interface DrawerPayload {
  record: WorkflowFormDefinitionInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const currentForm = ref<WorkflowFormDefinitionInfo>();
const categoryOptions = ref<{ label: string; value: string }[]>([]);
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
      optionFilterProp: 'label',
      options: categoryOptions.value,
      placeholder: '请选择流程分类',
      showSearch: true,
    },
  },
  {
    fieldName: 'formKey',
    component: 'Input',
    label: '表单编码',
    defaultValue: getDefaultValue('formKey', ''),
    rules: z
      .string({ required_error: '请输入表单编码' })
      .min(1, '请输入表单编码')
      .max(64, '表单编码不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入表单编码',
    },
  },
  {
    fieldName: 'formName',
    component: 'Input',
    label: '表单名称',
    defaultValue: getDefaultValue('formName', ''),
    rules: z
      .string({ required_error: '请输入表单名称' })
      .min(1, '请输入表单名称')
      .max(100, '表单名称不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入表单名称',
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
  class: 'w-full sm:w-[640px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '复制',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: '复制表单模板',
});

function mapCategoryOptions(categories: WorkflowCategoryInfo[]) {
  return categories
    .filter((item) => item.id)
    .map((item) => ({
      label: item.categoryName ?? item.categoryCode ?? item.id!,
      value: item.id!,
    }));
}

function buildCopyKey(sourceKey?: string) {
  const baseKey = sourceKey?.trim() || 'form';
  const suffix = `_copy_${Date.now().toString(36).slice(-6)}`;
  return `${baseKey}${suffix}`.slice(0, 64);
}

function buildInitialValues(record: WorkflowFormDefinitionInfo) {
  return {
    categoryId: record.categoryId ?? '',
    formKey: buildCopyKey(record.formKey),
    formName: `${record.formName ?? '审批表单'}-副本`,
    remark: '',
    status: 'draft',
    version: 1,
  };
}

async function loadOptions() {
  const categories = await listWorkflowCategories({
    queryParams: { status: 'enabled' },
  });
  categoryOptions.value = mapCategoryOptions(categories ?? []);
  formApi.setState({ schema: buildFormSchema() });
}

async function prepareFormState(record: WorkflowFormDefinitionInfo) {
  currentForm.value = record;
  initialValues.value = buildInitialValues(record);
  hasSyncedMountedValues.value = false;
  await loadOptions();
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

function buildPayload(values: Record<string, any>): WorkflowFormTemplateCopyReq {
  return {
    categoryId: values.categoryId,
    formKey: values.formKey,
    formName: values.formName,
    remark: values.remark,
    sourceFormDefinitionId: currentForm.value?.id,
  };
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid || !currentForm.value?.id) {
    return;
  }

  try {
    drawerApi.lock();
    const values = await formApi.getValues();
    await copyWorkflowFormTemplate(buildPayload(values));
    message.success('复制表单模板成功');
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
