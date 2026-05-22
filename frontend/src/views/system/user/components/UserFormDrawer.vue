<script setup lang="ts">
import type { UserInfo } from '#/api/system/user';
import type { VbenFormSchema } from '#/adapter/form';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import {
  cleanFormPayload,
  clearAutofillValues,
  confirmPasswordRule,
  newPasswordInputProps,
  noAutofillInputProps,
  optionalIdCardRule,
  optionalMobilePhoneRule,
  optionalString,
  optionalTelephoneRule,
  passwordComplexityRule,
  useVbenForm,
  z,
} from '#/adapter/form';
import { getUserById, saveUser } from '#/api/system/user';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: UserInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增用户' : '编辑用户'));

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const emptyUserFormValues = {
  username: '',
  realname: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: '',
  sex: 0,
  status: 1,
  workNo: '',
  post: '',
  telephone: '',
  idCard: '',
};

function clearCreateAutofillValues() {
  if (!isCreate.value || !formApi.isMounted) {
    return;
  }

  clearAutofillValues(formApi, emptyUserFormValues, {
    enabled: () => isCreate.value,
  });
}

const buildPasswordSchemas = (): VbenFormSchema[] => [
  {
    fieldName: 'password',
    component: 'VbenInputPassword',
    label: '登录密码',
    defaultValue: undefined,
    rules: passwordComplexityRule(),
    componentProps: newPasswordInputProps('newUserPassword', {
      maxlength: 20,
      passwordStrength: true,
      placeholder: '请输入登录密码',
    }),
  },
  {
    fieldName: 'confirmPassword',
    component: 'VbenInputPassword',
    label: '确认密码',
    defaultValue: undefined,
    componentProps: newPasswordInputProps('newUserConfirmPassword', {
      maxlength: 20,
      passwordStrength: true,
      placeholder: '请再次输入登录密码',
    }),
    dependencies: {
      rules(values) {
        return confirmPasswordRule(values);
      },
      triggerFields: ['password'],
    },
  },
];

const buildFormSchema = (create: boolean): VbenFormSchema[] => {
  const schema: VbenFormSchema[] = [
    {
      fieldName: 'username',
      component: 'Input',
      label: '用户名',
      defaultValue: getDefaultValue('username', ''),
      rules: z
        .string({ required_error: '请输入用户名' })
        .min(1, { message: '请输入用户名' })
        .max(50, { message: '用户名不能超过50个字符' }),
      componentProps: noAutofillInputProps(
        create ? 'newUserAccount' : 'editUserAccount',
        {
          disabled: !create,
          maxlength: 50,
          placeholder: '请输入用户名',
        },
      ),
    },
    {
      fieldName: 'realname',
      component: 'Input',
      label: '真实姓名',
      defaultValue: getDefaultValue('realname', ''),
      rules: z
        .string({ required_error: '请输入真实姓名' })
        .min(1, { message: '请输入真实姓名' })
        .max(50, { message: '真实姓名不能超过50个字符' }),
      componentProps: noAutofillInputProps('userRealname', {
        maxlength: 50,
        placeholder: '请输入真实姓名',
      }),
    },
  ];

  if (create) {
    schema.push(...buildPasswordSchemas());
  }

  schema.push(
    {
      fieldName: 'phone',
      component: 'Input',
      label: '手机号码',
      defaultValue: getDefaultValue('phone', ''),
      rules: optionalMobilePhoneRule(),
      componentProps: noAutofillInputProps('userMobilePhone', {
        maxlength: 11,
        placeholder: '请输入手机号码',
      }),
    },
    {
      fieldName: 'email',
      component: 'Input',
      label: '邮箱',
      defaultValue: getDefaultValue('email', ''),
      rules: optionalString(z.string().email('请输入正确的邮箱地址')),
      componentProps: noAutofillInputProps('userContactEmail', {
        maxlength: 100,
        placeholder: '请输入邮箱',
      }),
    },
    {
      fieldName: 'sex',
      component: 'Select',
      label: '性别',
      defaultValue: getDefaultValue('sex', 0),
      rules: 'selectRequired',
      componentProps: {
        options: [
          { label: '未知', value: 0 },
          { label: '男', value: 1 },
          { label: '女', value: 2 },
        ],
      },
    },
    {
      fieldName: 'status',
      component: 'Select',
      label: '状态',
      defaultValue: getDefaultValue('status', 1),
      rules: 'selectRequired',
      componentProps: {
        options: [
          { label: '正常', value: 1 },
          { label: '冻结', value: 2 },
        ],
      },
    },
    {
      fieldName: 'workNo',
      component: 'Input',
      label: '工号',
      defaultValue: getDefaultValue('workNo', ''),
      rules: optionalString(z.string().max(50, '工号不能超过50个字符')),
      componentProps: noAutofillInputProps('userWorkNo', {
        maxlength: 50,
        placeholder: '请输入工号',
      }),
    },
    {
      fieldName: 'post',
      component: 'Input',
      label: '职务',
      defaultValue: getDefaultValue('post', ''),
      rules: optionalString(z.string().max(50, '职务不能超过50个字符')),
      componentProps: noAutofillInputProps('userPost', {
        maxlength: 50,
        placeholder: '请输入职务',
      }),
    },
    {
      fieldName: 'telephone',
      component: 'Input',
      label: '座机号',
      defaultValue: getDefaultValue('telephone', ''),
      rules: optionalTelephoneRule(),
      componentProps: noAutofillInputProps('userTelephone', {
        maxlength: 20,
        placeholder: '请输入座机号',
      }),
    },
    {
      fieldName: 'idCard',
      component: 'Input',
      label: '身份证号',
      defaultValue: getDefaultValue('idCard', ''),
      rules: optionalIdCardRule(),
      componentProps: noAutofillInputProps('userIdCard', {
        maxlength: 18,
        placeholder: '请输入身份证号',
      }),
    },
  );

  return schema;
};

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return { ...emptyUserFormValues };
  }

  return {
    ...emptyUserFormValues,
    ...payload.record,
    password: undefined,
    confirmPassword: undefined,
  };
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 92,
  },
  layout: 'horizontal',
  schema: buildFormSchema(true),
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

function prepareFormState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentId.value = payload.record?.id;
  initialValues.value = buildInitialValues(payload);
  hasSyncedMountedValues.value = false;
  formApi.setState({ schema: buildFormSchema(isCreate.value) });
  drawerApi.setState({ loading: false, title: drawerTitle.value });
}

async function syncMountedFormValues() {
  if (!formApi.isMounted || hasSyncedMountedValues.value) {
    return;
  }

  await formApi.resetForm();
  await formApi.setValues(initialValues.value);
  if (isCreate.value) {
    clearCreateAutofillValues();
  }
  hasSyncedMountedValues.value = true;
}

async function refreshDetailSilently() {
  if (!currentId.value) {
    return;
  }
  try {
    const detail = await getUserById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues({
      ...detail,
      password: undefined,
    });
  } catch {
    // 请求层会统一提示错误，这里保持抽屉中已有行数据不被打断。
  }
}

function cleanPayload(values: Record<string, any>): UserInfo {
  return cleanFormPayload<UserInfo>(values, {
    id: currentId.value,
    omit: ['confirmPassword'],
    removeFalsyKeys: ['password'],
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
    await saveUser(cleanPayload(values));
    message.success(isCreate.value ? '新增用户成功' : '修改用户成功');
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
