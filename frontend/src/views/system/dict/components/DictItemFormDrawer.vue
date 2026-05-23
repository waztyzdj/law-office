<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { SysDictItemInfo } from '#/api/system/dict';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import { getDictItemById, saveDictItem } from '#/api/system/dict';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  dictId?: string;
  dictName?: string;
  mode: DrawerMode;
  record?: SysDictItemInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const currentDictId = ref<string>();
const currentDictName = ref('');
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() =>
  currentDictName.value
    ? `${isCreate.value ? '新增' : '编辑'}字典项 - ${currentDictName.value}`
    : isCreate.value
      ? '新增字典项'
      : '编辑字典项',
);

const emptyDictItemValues = {
  dictId: '',
  itemText: '',
  itemValue: '',
  description: '',
  sortOrder: 0,
  status: 1,
};

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'dictId',
    component: 'Input',
    label: '字典ID',
    defaultValue: initialValues.value.dictId ?? '',
    componentProps: {
      disabled: true,
      placeholder: '字典ID',
    },
  },
  {
    fieldName: 'itemText',
    component: 'Input',
    label: '字典项文本',
    defaultValue: initialValues.value.itemText ?? '',
    rules: z
      .string({ required_error: '请输入字典项文本' })
      .min(1, '请输入字典项文本')
      .max(100, '字典项文本不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入字典项文本',
    },
  },
  {
    fieldName: 'itemValue',
    component: 'Input',
    label: '字典项值',
    defaultValue: initialValues.value.itemValue ?? '',
    rules: z
      .string({ required_error: '请输入字典项值' })
      .min(1, '请输入字典项值')
      .max(100, '字典项值不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入字典项值',
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
  {
    fieldName: 'sortOrder',
    component: 'InputNumber',
    label: '排序',
    defaultValue: initialValues.value.sortOrder ?? 0,
    componentProps: {
      min: 0,
      precision: 0,
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'status',
    component: 'Select',
    label: '状态',
    defaultValue: initialValues.value.status ?? 1,
    rules: 'selectRequired',
    componentProps: {
      options: [
        { label: '正常', value: 1 },
        { label: '冻结', value: 0 },
      ],
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
    return {
      ...emptyDictItemValues,
      dictId: payload.dictId,
    };
  }

  return {
    ...emptyDictItemValues,
    ...payload.record,
    dictId: payload.dictId ?? payload.record?.dictId ?? '',
  };
}

function prepareFormState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentId.value = payload.record?.id;
  currentDictId.value = payload.dictId ?? payload.record?.dictId ?? '';
  currentDictName.value = payload.dictName ?? '';
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
    const detail = await getDictItemById(currentId.value);
    initialValues.value = buildInitialValues({
      dictId: currentDictId.value,
      mode: mode.value,
      record: detail,
    });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层统一处理。
  }
}

function cleanPayload(values: Record<string, any>): SysDictItemInfo {
  const payload = cleanFormPayload<SysDictItemInfo>(values, {
    id: currentId.value,
  });

  payload.dictId = currentDictId.value;
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
    await saveDictItem(cleanPayload(values));
    message.success(isCreate.value ? '新增字典项成功' : '修改字典项成功');
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
