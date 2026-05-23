<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { DepartInfo } from '#/api/system/depart';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import { getDepartById, saveDepart } from '#/api/system/depart';
import {
  filterTreeSelectOptions,
  type StringTreeSelectOption,
} from '#/composables/Tree/useTree';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  parentId?: string | null;
  record?: DepartInfo;
}

const props = defineProps<{
  orgTypeOptions: Array<{
    label: string;
    value: string;
  }>;
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
const drawerTitle = computed(() => (isCreate.value ? '新增部门' : '编辑部门'));
const availableTreeOptions = computed(() =>
  filterTreeSelectOptions(props.treeOptions, currentId.value),
);

const emptyDepartValues = {
  parentId: null,
  orgCode: '',
  departName: '',
  departNameEn: '',
  departNameAbbr: '',
  departOrder: 0,
  description: '',
  orgType: '',
  mobile: '',
  fax: '',
  address: '',
  memo: '',
  status: '1',
};

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'parentId',
    component: 'TreeSelect',
    label: '父机构',
    defaultValue: initialValues.value.parentId,
    componentProps: {
      allowClear: true,
      placeholder: '请选择父机构',
      treeData: availableTreeOptions.value,
      treeDefaultExpandAll: true,
    },
  },
  {
    fieldName: 'orgCode',
    component: 'Input',
    label: '机构编码',
    defaultValue: initialValues.value.orgCode ?? '',
    rules: z
      .string({ required_error: '请输入机构编码' })
      .min(1, '请输入机构编码')
      .max(64, '机构编码不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入机构编码',
    },
  },
  {
    fieldName: 'departName',
    component: 'Input',
    label: '机构名称',
    defaultValue: initialValues.value.departName ?? '',
    rules: z
      .string({ required_error: '请输入机构名称' })
      .min(1, '请输入机构名称')
      .max(64, '机构名称不能超过64个字符'),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入机构名称',
    },
  },
  {
    fieldName: 'orgType',
    component: 'Select',
    label: '机构类型',
    defaultValue: initialValues.value.orgType ?? '',
    rules: z
      .string({ required_error: '请选择机构类型' })
      .min(1, '请选择机构类型'),
    componentProps: {
      allowClear: false,
      options: props.orgTypeOptions,
      placeholder: '请选择机构类型',
    },
  },
  {
    fieldName: 'departNameEn',
    component: 'Input',
    label: '英文名',
    defaultValue: initialValues.value.departNameEn ?? '',
    rules: z.string().max(1024, '英文名不能超过1024个字符').optional(),
    componentProps: {
      maxlength: 1024,
      placeholder: '请输入英文名',
    },
  },
  {
    fieldName: 'departNameAbbr',
    component: 'Input',
    label: '缩写',
    defaultValue: initialValues.value.departNameAbbr ?? '',
    rules: z.string().max(1024, '缩写不能超过1024个字符').optional(),
    componentProps: {
      maxlength: 1024,
      placeholder: '请输入缩写',
    },
  },
  {
    fieldName: 'departOrder',
    component: 'InputNumber',
    label: '排序',
    defaultValue: initialValues.value.departOrder ?? 0,
    componentProps: {
      min: 0,
      precision: 0,
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'mobile',
    component: 'Input',
    label: '手机号',
    defaultValue: initialValues.value.mobile ?? '',
    rules: z.string().max(64, '手机号不能超过64个字符').optional(),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入手机号',
    },
  },
  {
    fieldName: 'fax',
    component: 'Input',
    label: '传真',
    defaultValue: initialValues.value.fax ?? '',
    rules: z.string().max(64, '传真不能超过64个字符').optional(),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入传真',
    },
  },
  {
    fieldName: 'address',
    component: 'Input',
    label: '地址',
    defaultValue: initialValues.value.address ?? '',
    rules: z.string().max(64, '地址不能超过64个字符').optional(),
    componentProps: {
      maxlength: 64,
      placeholder: '请输入地址',
    },
  },
  {
    fieldName: 'memo',
    component: 'Input',
    label: '备注',
    defaultValue: initialValues.value.memo ?? '',
    rules: z.string().max(1024, '备注不能超过1024个字符').optional(),
    componentProps: {
      maxlength: 1024,
      placeholder: '请输入备注',
    },
  },
  {
    fieldName: 'description',
    component: 'Input',
    label: '描述',
    defaultValue: initialValues.value.description ?? '',
    rules: z.string().max(1024, '描述不能超过1024个字符').optional(),
    componentProps: {
      maxlength: 1024,
      placeholder: '请输入描述',
    },
  },
  {
    fieldName: 'status',
    component: 'Select',
    label: '状态',
    defaultValue: initialValues.value.status ?? '1',
    rules: 'selectRequired',
    componentProps: {
      options: [
        { label: '正常', value: '1' },
        { label: '停用', value: '0' },
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
      ...emptyDepartValues,
      parentId: normalizeParentId(payload.parentId),
    };
  }

  return {
    ...emptyDepartValues,
    ...payload.record,
    parentId: normalizeParentId(payload.record?.parentId),
  };
}

function normalizeParentId(parentId?: string | null) {
  if (!parentId || parentId === '0') {
    return null;
  }

  return parentId;
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
    const detail = await getDepartById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层统一处理错误，这里保留当前表单值。
  }
}

function cleanPayload(values: Record<string, any>): DepartInfo {
  const payload = cleanFormPayload<DepartInfo>(values, {
    id: currentId.value,
  });

  payload.parentId = normalizeParentId(payload.parentId);

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
    await saveDepart(cleanPayload(values));
    message.success(isCreate.value ? '新增部门成功' : '修改部门成功');
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
