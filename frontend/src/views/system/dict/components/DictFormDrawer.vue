<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { SysDictInfo } from '#/api/system/dict';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import { getDictById, saveDict } from '#/api/system/dict';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: SysDictInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增字典' : '编辑字典'));

const emptyDictValues = {
  dictCode: '',
  dictName: '',
  description: '',
};

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'dictCode',
    component: 'Input',
    label: '字典编码',
    defaultValue: initialValues.value.dictCode ?? '',
    rules: z
      .string({ required_error: '请输入字典编码' })
      .min(1, '请输入字典编码')
      .max(100, '字典编码不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入字典编码',
    },
  },
  {
    fieldName: 'dictName',
    component: 'Input',
    label: '字典名称',
    defaultValue: initialValues.value.dictName ?? '',
    rules: z
      .string({ required_error: '请输入字典名称' })
      .min(1, '请输入字典名称')
      .max(100, '字典名称不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入字典名称',
    },
  },
  {
    fieldName: 'description',
    component: 'Input',
    label: '描述',
    defaultValue: initialValues.value.description ?? '',
    rules: z.string().max(200, '描述不能超过200个字符').optional(),
    componentProps: {
      maxlength: 200,
      placeholder: '请输入描述',
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
  class: 'w-full sm:w-1/2! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return { ...emptyDictValues };
  }

  return {
    ...emptyDictValues,
    ...payload.record,
  };
}

function prepareFormState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentId.value = payload.record?.id;
  initialValues.value = buildInitialValues(payload);
  hasSyncedMountedValues.value = false;
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
    const detail = await getDictById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // handled by the request layer
  }
}

function cleanPayload(values: Record<string, any>): SysDictInfo {
  return cleanFormPayload<SysDictInfo>(values, {
    id: currentId.value,
  });
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  try {
    drawerApi.lock();
    const values = await formApi.getValues();
    await saveDict(cleanPayload(values));
    message.success(isCreate.value ? '新增字典成功' : '修改字典成功');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(payload: DrawerPayload) {
  prepareFormState(payload);

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
