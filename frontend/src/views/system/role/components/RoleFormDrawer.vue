<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { RoleInfo } from '#/api/system/role';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  cleanFormPayload,
  noAutofillInputProps,
  optionalString,
  useVbenForm,
  z,
} from '#/adapter/form';
import { getRoleById, saveRole } from '#/api/system/role';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: RoleInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增角色' : '编辑角色'));

const emptyRoleValues = {
  roleCode: '',
  roleName: '',
  description: '',
};

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'roleCode',
    component: 'Input',
    label: '角色编码',
    defaultValue: getDefaultValue('roleCode', ''),
    rules: z
      .string({ required_error: '请输入角色编码' })
      .min(1, '请输入角色编码')
      .max(64, '角色编码不能超过64个字符')
      .regex(/^[A-Za-z][A-Za-z0-9_:.-]*$/, '角色编码需以字母开头')
      .refine((value) => !value.startsWith('ADMIN_'), {
        message: '自定义角色编码不能以 ADMIN_ 开头',
      }),
    componentProps: noAutofillInputProps('roleCode', {
      disabled: !isCreate.value,
      maxlength: 64,
      placeholder: '请输入角色编码',
    }),
  },
  {
    fieldName: 'roleName',
    component: 'Input',
    label: '角色名称',
    defaultValue: getDefaultValue('roleName', ''),
    rules: z
      .string({ required_error: '请输入角色名称' })
      .min(1, '请输入角色名称')
      .max(64, '角色名称不能超过64个字符'),
    componentProps: noAutofillInputProps('roleName', {
      maxlength: 64,
      placeholder: '请输入角色名称',
    }),
  },
  {
    fieldName: 'description',
    component: 'Textarea',
    label: '描述',
    defaultValue: getDefaultValue('description', ''),
    rules: optionalString(z.string().max(500, '描述不能超过500个字符')),
    componentProps: noAutofillInputProps('roleDescription', {
      maxlength: 500,
      placeholder: '请输入描述',
      rows: 4,
    }),
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
  class: 'w-full sm:w-2/5! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return { ...emptyRoleValues };
  }
  return {
    ...emptyRoleValues,
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
    const detail = await getRoleById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(detail);
  } catch {
    // 请求层会统一提示错误，这里保留已有行数据。
  }
}

function cleanPayload(values: Record<string, any>): RoleInfo {
  return cleanFormPayload<RoleInfo>(values, {
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
    await saveRole(cleanPayload(values));
    message.success(isCreate.value ? '新增角色成功' : '修改角色成功');
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
