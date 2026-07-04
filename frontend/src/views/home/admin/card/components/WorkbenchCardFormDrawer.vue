<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { WorkbenchCardInfo } from '#/api/home/workbench';

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
import {
  getWorkbenchCardDetail,
  saveWorkbenchCard,
} from '#/api/home/workbench';

import {
  workbenchCardComponentOptions,
  workbenchCardPermissionOptions,
  workbenchCardSizeOptions,
  workbenchStatusOptions,
} from '../../constants';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: WorkbenchCardInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新增工作台卡片' : '编辑工作台卡片'));

const emptyCardValues: WorkbenchCardInfo = {
  cardCode: '',
  cardName: '',
  componentKey: '',
  configJson: '',
  defaultRefreshInterval: undefined,
  defaultSize: 'medium',
  defaultSort: 0,
  defaultVisible: 1,
  permissionCode: '',
  remark: '',
  status: 'enabled',
};

const jsonTextRule = optionalString(
  z.string().refine((value) => {
    try {
      JSON.parse(value);
      return true;
    } catch {
      return false;
    }
  }, '请输入合法 JSON'),
);

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'cardCode',
    component: 'Input',
    label: '卡片编码',
    defaultValue: getDefaultValue('cardCode', ''),
    rules: z
      .string({ required_error: '请输入卡片编码' })
      .min(1, '请输入卡片编码')
      .max(64, '卡片编码不能超过64个字符'),
    componentProps: noAutofillInputProps('workbenchCardCode', {
      maxlength: 64,
      placeholder: '例如 todo',
    }),
  },
  {
    fieldName: 'cardName',
    component: 'Input',
    label: '卡片名称',
    defaultValue: getDefaultValue('cardName', ''),
    rules: z
      .string({ required_error: '请输入卡片名称' })
      .min(1, '请输入卡片名称')
      .max(100, '卡片名称不能超过100个字符'),
    componentProps: noAutofillInputProps('workbenchCardName', {
      maxlength: 100,
      placeholder: '请输入卡片名称',
    }),
  },
  {
    fieldName: 'componentKey',
    component: 'Select',
    label: '组件 Key',
    defaultValue: getDefaultValue('componentKey', ''),
    rules: z
      .string({ required_error: '请选择卡片组件' })
      .min(1, '请选择卡片组件')
      .max(100, '组件 Key 不能超过100个字符'),
    componentProps: {
      allowClear: true,
      options: workbenchCardComponentOptions,
      placeholder: '请选择卡片组件',
      showSearch: true,
    },
  },
  {
    fieldName: 'permissionCode',
    component: 'Select',
    label: '权限码',
    defaultValue: getDefaultValue('permissionCode', ''),
    rules: optionalString(z.string().max(128, '权限码不能超过128个字符')),
    componentProps: {
      allowClear: true,
      options: workbenchCardPermissionOptions,
      placeholder: '为空时拥有工作台访问权的用户默认可见',
      showSearch: true,
    },
  },
  {
    fieldName: 'defaultSize',
    component: 'Select',
    label: '默认尺寸',
    defaultValue: getDefaultValue('defaultSize', 'medium'),
    rules: 'selectRequired',
    componentProps: {
      options: workbenchCardSizeOptions,
    },
  },
  {
    fieldName: 'defaultVisible',
    component: 'Select',
    label: '默认显示',
    defaultValue: getDefaultValue('defaultVisible', 1),
    rules: 'selectRequired',
    componentProps: {
      options: [
        { label: '显示', value: 1 },
        { label: '隐藏', value: 0 },
      ],
    },
  },
  {
    fieldName: 'defaultSort',
    component: 'InputNumber',
    label: '默认排序',
    defaultValue: getDefaultValue('defaultSort', 0),
    componentProps: {
      min: 0,
      precision: 0,
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'defaultRefreshInterval',
    component: 'InputNumber',
    label: '刷新间隔',
    defaultValue: getDefaultValue('defaultRefreshInterval', undefined),
    componentProps: {
      min: 30,
      precision: 0,
      placeholder: '单位秒，可为空',
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'status',
    component: 'Select',
    label: '状态',
    defaultValue: getDefaultValue('status', 'enabled'),
    rules: 'selectRequired',
    componentProps: {
      options: workbenchStatusOptions,
    },
  },
  {
    fieldName: 'configJson',
    component: 'Textarea',
    label: '扩展配置',
    defaultValue: getDefaultValue('configJson', ''),
    rules: jsonTextRule,
    componentProps: {
      autoSize: { minRows: 3, maxRows: 8 },
      placeholder: '例如 {"limit":8}；快捷菜单可设 {"limit":99}，设 0 表示不限',
    },
  },
  {
    fieldName: 'remark',
    component: 'Textarea',
    label: '备注',
    defaultValue: getDefaultValue('remark', ''),
    rules: optionalString(z.string().max(500, '备注不能超过500个字符')),
    componentProps: {
      autoSize: { minRows: 2, maxRows: 5 },
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
    labelWidth: 100,
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
  return {
    ...emptyCardValues,
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
    const detail = await getWorkbenchCardDetail(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层统一提示错误，这里保留列表行数据。
  }
}

function cleanPayload(values: Record<string, any>): WorkbenchCardInfo {
  return cleanFormPayload<WorkbenchCardInfo>(values, {
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
    await saveWorkbenchCard(cleanPayload(values));
    message.success(isCreate.value ? '新增卡片成功' : '修改卡片成功');
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
