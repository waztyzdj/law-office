<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { TenantInfo } from '#/api/system/tenant';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import { getTenantById, saveTenant } from '#/api/system/tenant';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: TenantInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增租户' : '编辑租户'));

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const emptyTenantValues = {
  id: '',
  name: '',
  beginDate: '',
  endDate: '',
  status: 1,
  trade: '',
  companySize: '',
  companyAddress: '',
  companyLogo: '',
  houseNumber: '',
  workPlace: '',
  secondaryDomain: '',
  loginBkgdImg: '',
  position: '',
  department: '',
  applyStatus: 1,
};

const buildFormSchema = (create: boolean): VbenFormSchema[] => [
  {
    fieldName: 'id',
    component: 'Input',
    label: '租户编码',
    defaultValue: getDefaultValue('id', ''),
    rules: create
      ? z
          .string({ required_error: '请输入租户编码' })
          .min(1, '请输入租户编码')
          .max(64, '租户编码不能超过64个字符')
      : z.string().max(64, '租户编码不能超过64个字符').optional(),
    componentProps: {
      disabled: !create,
      maxlength: 64,
      placeholder: '请输入租户编码',
    },
  },
  {
    fieldName: 'name',
    component: 'Input',
    label: '租户名称',
    defaultValue: getDefaultValue('name', ''),
    rules: z
      .string({ required_error: '请输入租户名称' })
      .min(1, '请输入租户名称')
      .max(64, '租户名称不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入租户名称',
    },
  },
  {
    fieldName: 'beginDate',
    component: 'DatePicker',
    label: '开始时间',
    defaultValue: getDefaultValue('beginDate', ''),
    componentProps: {
      format: 'YYYY-MM-DD HH:mm:ss',
      placeholder: '请选择开始时间',
      showTime: true,
      style: 'width: 100%',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
    },
  },
  {
    fieldName: 'endDate',
    component: 'DatePicker',
    label: '结束时间',
    defaultValue: getDefaultValue('endDate', ''),
    componentProps: {
      format: 'YYYY-MM-DD HH:mm:ss',
      placeholder: '请选择结束时间',
      showTime: true,
      style: 'width: 100%',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
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
        { label: '冻结', value: 0 },
      ],
    },
  },
  {
    fieldName: 'trade',
    component: 'Input',
    label: '所属行业',
    defaultValue: getDefaultValue('trade', ''),
    componentProps: {
      maxlength: 16,
      placeholder: '请输入所属行业',
    },
  },
  {
    fieldName: 'companySize',
    component: 'Input',
    label: '公司规模',
    defaultValue: getDefaultValue('companySize', ''),
    componentProps: {
      maxlength: 16,
      placeholder: '请输入公司规模',
    },
  },
  {
    fieldName: 'companyAddress',
    component: 'Textarea',
    label: '公司地址',
    defaultValue: getDefaultValue('companyAddress', ''),
    componentProps: {
      autoSize: { minRows: 2, maxRows: 4 },
      maxlength: 64,
      placeholder: '请输入公司地址',
    },
  },
  {
    fieldName: 'companyLogo',
    component: 'Input',
    label: '公司Logo',
    defaultValue: getDefaultValue('companyLogo', ''),
    componentProps: {
      maxlength: 1024,
      placeholder: '请输入公司Logo地址',
    },
  },
  {
    fieldName: 'houseNumber',
    component: 'Input',
    label: '门牌号',
    defaultValue: getDefaultValue('houseNumber', ''),
    componentProps: {
      maxlength: 16,
      placeholder: '请输入门牌号',
    },
  },
  {
    fieldName: 'workPlace',
    component: 'Input',
    label: '工作地点',
    defaultValue: getDefaultValue('workPlace', ''),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入工作地点',
    },
  },
  {
    fieldName: 'secondaryDomain',
    component: 'Input',
    label: '二级域名',
    defaultValue: getDefaultValue('secondaryDomain', ''),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入二级域名',
    },
  },
  {
    fieldName: 'loginBkgdImg',
    component: 'Input',
    label: '登录背景图',
    defaultValue: getDefaultValue('loginBkgdImg', ''),
    componentProps: {
      maxlength: 1024,
      placeholder: '请输入登录背景图地址',
    },
  },
  {
    fieldName: 'position',
    component: 'Input',
    label: '职级',
    defaultValue: getDefaultValue('position', ''),
    componentProps: {
      maxlength: 16,
      placeholder: '请输入职级',
    },
  },
  {
    fieldName: 'department',
    component: 'Input',
    label: '部门',
    defaultValue: getDefaultValue('department', ''),
    componentProps: {
      maxlength: 16,
      placeholder: '请输入部门',
    },
  },
  {
    fieldName: 'applyStatus',
    component: 'Select',
    label: '申请管理者',
    defaultValue: getDefaultValue('applyStatus', 1),
    rules: 'selectRequired',
    componentProps: {
      options: [
        { label: '允许', value: 1 },
        { label: '不允许', value: 0 },
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
  schema: buildFormSchema(true),
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
    return { ...emptyTenantValues };
  }

  return {
    ...emptyTenantValues,
    ...payload.record,
  };
}

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
  hasSyncedMountedValues.value = true;
}

async function refreshDetailSilently() {
  if (!currentId.value) {
    return;
  }

  try {
    const detail = await getTenantById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层会统一处理错误，这里保留当前表单值。
  }
}

function cleanPayload(values: Record<string, any>): TenantInfo {
  return cleanFormPayload<TenantInfo>(values, {
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
    await saveTenant(cleanPayload(values));
    message.success(isCreate.value ? '新增租户成功' : '修改租户成功');
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
