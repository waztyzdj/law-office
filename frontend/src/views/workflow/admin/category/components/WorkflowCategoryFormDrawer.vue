<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type { WorkflowCategoryInfo } from '#/api/workflow';
import type { StringTreeSelectOption } from '#/composables/Tree/useTree';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import {
  getWorkflowCategoryById,
  saveWorkflowCategory,
} from '#/api/workflow';
import { filterTreeSelectOptions } from '#/composables/Tree/useTree';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  mode: DrawerMode;
  parentId?: string;
  record?: WorkflowCategoryInfo;
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
const drawerTitle = computed(() =>
  isCreate.value ? '新增流程分类' : '编辑流程分类',
);
const availableTreeOptions = computed(() =>
  filterTreeSelectOptions(props.treeOptions, currentId.value),
);

const statusOptions = [
  { label: '启用', value: 'enabled' },
  { label: '停用', value: 'disabled' },
];

const emptyCategoryValues = {
  categoryCode: '',
  categoryName: '',
  parentId: undefined,
  remark: '',
  sortOrder: 0,
  status: 'enabled',
};

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const buildFormSchema = (): VbenFormSchema[] => [
  {
    component: 'TreeSelect',
    componentProps: {
      allowClear: true,
      placeholder: '请选择上级分类',
      treeData: availableTreeOptions.value,
      treeDefaultExpandAll: true,
    },
    defaultValue: getDefaultValue('parentId'),
    fieldName: 'parentId',
    label: '上级分类',
  },
  {
    component: 'Input',
    componentProps: {
      maxlength: 64,
      placeholder: '请输入分类编码',
    },
    defaultValue: getDefaultValue('categoryCode', ''),
    fieldName: 'categoryCode',
    label: '分类编码',
    rules: z
      .string({ required_error: '请输入分类编码' })
      .min(1, '请输入分类编码')
      .max(64, '分类编码不能超过64个字符'),
  },
  {
    component: 'Input',
    componentProps: {
      maxlength: 100,
      placeholder: '请输入分类名称',
    },
    defaultValue: getDefaultValue('categoryName', ''),
    fieldName: 'categoryName',
    label: '分类名称',
    rules: z
      .string({ required_error: '请输入分类名称' })
      .min(1, '请输入分类名称')
      .max(100, '分类名称不能超过100个字符'),
  },
  {
    component: 'InputNumber',
    componentProps: {
      min: 0,
      precision: 0,
      style: 'width: 100%',
    },
    defaultValue: getDefaultValue('sortOrder', 0),
    fieldName: 'sortOrder',
    label: '排序',
    rules: z.number().min(0, '排序不能小于0').optional(),
  },
  {
    component: 'Select',
    componentProps: {
      options: statusOptions,
    },
    defaultValue: getDefaultValue('status', 'enabled'),
    fieldName: 'status',
    label: '状态',
    rules: 'selectRequired',
  },
  {
    component: 'Textarea',
    componentProps: {
      autoSize: { minRows: 3, maxRows: 5 },
      maxlength: 500,
      placeholder: '请输入备注',
      showCount: true,
    },
    defaultValue: getDefaultValue('remark', ''),
    fieldName: 'remark',
    label: '备注',
    rules: z.string().max(500, '备注不能超过500个字符').optional(),
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
      parentId: payload.parentId,
    };
  }

  return {
    ...emptyCategoryValues,
    ...payload.record,
    parentId: payload.record?.parentId ?? payload.parentId,
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
    const detail = await getWorkflowCategoryById(currentId.value);
    initialValues.value = buildInitialValues({
      mode: mode.value,
      record: detail,
    });
    await formApi.setValues(initialValues.value);
  } catch {
    // 请求层统一提示错误，这里保留当前表单值。
  }
}

function cleanPayload(values: Record<string, any>): WorkflowCategoryInfo {
  const payload = cleanFormPayload<WorkflowCategoryInfo>(values, {
    id: currentId.value,
  });

  if (!payload.parentId) {
    delete payload.parentId;
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
    await saveWorkflowCategory(cleanPayload(values));
    message.success(isCreate.value ? '新增分类成功' : '修改分类成功');
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
