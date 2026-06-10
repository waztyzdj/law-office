<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type {
  WorkflowCategoryInfo,
  WorkflowFormDefinitionInfo,
} from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import {
  getWorkflowFormById,
  listWorkflowCategories,
  saveWorkflowForm,
} from '#/api/workflow';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: WorkflowFormDefinitionInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);
const categoryOptions = ref<{ label: string; value: string }[]>([]);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新建表单' : '编辑表单'));

const emptyFormValues = {
  categoryId: '',
  formKey: '',
  formName: '',
  optionJson: '{}',
  remark: '',
  schemaJson: '[]',
  status: 'draft',
  version: 1,
};

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
      disabled: !isCreate.value,
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
      .max(100, '表单编码不能超过100个字符'),
    componentProps: {
      disabled: !isCreate.value,
      maxlength: 100,
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
      disabled: !isCreate.value,
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
  confirmText: '保存',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return { ...emptyFormValues };
  }

  return {
    ...emptyFormValues,
    ...payload.record,
    optionJson: payload.record?.optionJson || '{}',
    schemaJson: payload.record?.schemaJson || '[]',
  };
}

function mapCategoryOptions(categories: WorkflowCategoryInfo[]) {
  return categories
    .filter((item) => item.id)
    .map((item) => ({
      label: item.categoryName ?? item.categoryCode ?? item.id!,
      value: item.id!,
    }));
}

async function loadOptions() {
  const categories = await listWorkflowCategories({
    queryParams: { status: 'enabled' },
  });
  categoryOptions.value = mapCategoryOptions(categories ?? []);
  formApi.setState({ schema: buildFormSchema() });
}

async function prepareFormState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentId.value = payload.record?.id;
  initialValues.value = buildInitialValues(payload);
  hasSyncedMountedValues.value = false;
  await loadOptions();
  formApi.setState({ schema: buildFormSchema() });
  drawerApi.setState({ loading: false, title: drawerTitle.value });
}

async function syncMountedFormValues() {
  if (!formApi.isMounted || hasSyncedMountedValues.value) {
    return;
  }

  await formApi.resetForm();
  await formApi.setValues(initialValues.value);
  hasSyncedMountedValues.value = true;
}

async function refreshDetailSilently() {
  if (!currentId.value) {
    return;
  }

  try {
    const detail = await getWorkflowFormById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // handled by request layer
  }
}

function cleanPayload(values: Record<string, any>): WorkflowFormDefinitionInfo {
  const payload = cleanFormPayload<WorkflowFormDefinitionInfo>(values, {
    id: currentId.value,
  });
  payload.optionJson = initialValues.value.optionJson || '{}';
  payload.schemaJson = initialValues.value.schemaJson || '[]';
  payload.status = initialValues.value.status || 'draft';
  if (!isCreate.value) {
    payload.categoryId = initialValues.value.categoryId;
    payload.version = initialValues.value.version;
  }
  return payload;
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  try {
    drawerApi.lock();
    const values = await formApi.getValues();
    await saveWorkflowForm(cleanPayload(values));
    message.success(isCreate.value ? '新建表单成功' : '保存表单成功');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(payload: DrawerPayload) {
  await prepareFormState(payload);

  if (formApi.isMounted) {
    await syncMountedFormValues();
  }

  drawerApi.setData(payload).open();

  await nextTick();
  if (formApi.isMounted) {
    void syncMountedFormValues();
  }
  void refreshDetailSilently();
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
