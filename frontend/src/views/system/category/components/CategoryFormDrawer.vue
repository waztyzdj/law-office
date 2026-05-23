<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { CategoryInfo } from '#/api/system/category';
import type { StringTreeSelectOption } from '#/composables/Tree/useTree';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import { getCategoryById, saveCategory } from '#/api/system/category';
import { filterTreeSelectOptions } from '#/composables/Tree/useTree';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  parentId?: string;
  record?: CategoryInfo;
}

const props = defineProps<{
  treeOptions: StringTreeSelectOption[];
}>();

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增通用类型' : '编辑通用类型'));
const availableTreeOptions = computed(() =>
  filterTreeSelectOptions(props.treeOptions, currentId.value),
);

const emptyCategoryValues = {
  pid: undefined,
  code: '',
  name: '',
};

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'pid',
    component: 'TreeSelect',
    label: '父级类型',
    defaultValue: initialValues.value.pid,
    componentProps: {
      allowClear: true,
      placeholder: '请选择父级类型',
      treeData: availableTreeOptions.value,
      treeDefaultExpandAll: true,
    },
  },
  {
    fieldName: 'code',
    component: 'Input',
    label: '类型编码',
    defaultValue: initialValues.value.code ?? '',
    rules: z
      .string({ required_error: '请输入类型编码' })
      .min(1, '请输入类型编码')
      .max(64, '类型编码不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入类型编码',
    },
  },
  {
    fieldName: 'name',
    component: 'Input',
    label: '类型名称',
    defaultValue: initialValues.value.name ?? '',
    rules: z
      .string({ required_error: '请输入类型名称' })
      .min(1, '请输入类型名称')
      .max(64, '类型名称不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入类型名称',
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
  class: 'w-full sm:w-[760px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return {
      ...emptyCategoryValues,
      pid: payload.parentId,
    };
  }

  return {
    ...emptyCategoryValues,
    ...payload.record,
    pid: payload.record?.pid ?? payload.parentId,
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
    const detail = await getCategoryById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层统一处理错误。
  }
}

function cleanPayload(values: Record<string, any>): CategoryInfo {
  const payload = cleanFormPayload<CategoryInfo>(values, {
    id: currentId.value,
  });

  if (!payload.pid) {
    delete payload.pid;
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
    await saveCategory(cleanPayload(values));
    message.success(isCreate.value ? '新增类型成功' : '修改类型成功');
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
