<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { WorkbenchQuickEntryInfo } from '#/api/home/workbench';

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
import { saveWorkbenchQuickEntry } from '#/api/home/workbench';
import { menuIconOptions } from '#/constants/menu-icons';

import {
  workbenchCardPermissionOptions,
  workbenchQuickEntryTypeOptions,
  workbenchStatusOptions,
} from '../../constants';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  record?: WorkbenchQuickEntryInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() =>
  isCreate.value ? '新增系统快捷菜单' : '编辑系统快捷菜单',
);

const emptyQuickEntryValues: WorkbenchQuickEntryInfo = {
  configJson: '',
  entryCode: '',
  entryName: '',
  entryType: 'menu',
  icon: '',
  menuId: '',
  path: '',
  permissionCode: '',
  sortNo: 0,
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
    fieldName: 'entryCode',
    component: 'Input',
    label: '菜单编码',
    defaultValue: getDefaultValue('entryCode', ''),
    rules: z
      .string({ required_error: '请输入菜单编码' })
      .min(1, '请输入菜单编码')
      .max(64, '菜单编码不能超过64个字符'),
    componentProps: noAutofillInputProps('workbenchEntryCode', {
      maxlength: 64,
      placeholder: '例如 workflow-start',
    }),
  },
  {
    fieldName: 'entryName',
    component: 'Input',
    label: '菜单名称',
    defaultValue: getDefaultValue('entryName', ''),
    rules: z
      .string({ required_error: '请输入菜单名称' })
      .min(1, '请输入菜单名称')
      .max(100, '菜单名称不能超过100个字符'),
    componentProps: noAutofillInputProps('workbenchEntryName', {
      maxlength: 100,
      placeholder: '请输入菜单名称',
    }),
  },
  {
    fieldName: 'entryType',
    component: 'Select',
    label: '菜单类型',
    defaultValue: getDefaultValue('entryType', 'menu'),
    rules: 'selectRequired',
    componentProps: {
      options: workbenchQuickEntryTypeOptions,
    },
  },
  {
    fieldName: 'path',
    component: 'Input',
    label: '路径',
    defaultValue: getDefaultValue('path', ''),
    rules: optionalString(z.string().max(512, '路径不能超过512个字符')),
    componentProps: noAutofillInputProps('workbenchEntryPath', {
      maxlength: 512,
      placeholder: '例如 /workflow/start',
    }),
  },
  {
    fieldName: 'menuId',
    component: 'Input',
    label: '菜单 ID',
    defaultValue: getDefaultValue('menuId', ''),
    rules: optionalString(z.string().max(64, '菜单 ID 不能超过64个字符')),
    componentProps: noAutofillInputProps('workbenchEntryMenuId', {
      maxlength: 64,
      placeholder: '可绑定 sys_permission.id',
    }),
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
      placeholder: '为空时只按菜单 ID 校验',
      showSearch: true,
    },
  },
  {
    fieldName: 'icon',
    component: 'Select',
    label: '图标',
    defaultValue: getDefaultValue('icon', ''),
    rules: optionalString(z.string().max(128, '图标不能超过128个字符')),
    componentProps: {
      allowClear: true,
      options: menuIconOptions.map((icon) => ({ label: icon, value: icon })),
      placeholder: '请选择图标',
      showSearch: true,
    },
  },
  {
    fieldName: 'sortNo',
    component: 'InputNumber',
    label: '排序',
    defaultValue: getDefaultValue('sortNo', 0),
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
      placeholder: '例如 {}',
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
    ...emptyQuickEntryValues,
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

function cleanPayload(values: Record<string, any>): WorkbenchQuickEntryInfo {
  return cleanFormPayload<WorkbenchQuickEntryInfo>(values, {
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
    await saveWorkbenchQuickEntry(cleanPayload(values));
    message.success(isCreate.value ? '新增快捷菜单成功' : '修改快捷菜单成功');
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
