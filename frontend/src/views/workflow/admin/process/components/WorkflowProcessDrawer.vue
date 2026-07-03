<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';
import type {
  WorkflowCategoryInfo,
  WorkflowFormDefinitionInfo,
  WorkflowProcessModelInfo,
} from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { cleanFormPayload, useVbenForm, z } from '#/adapter/form';
import {
  getWorkflowProcessById,
  listWorkflowCategories,
  listWorkflowForms,
  saveWorkflowProcess,
} from '#/api/workflow';

type DrawerMode = 'create' | 'edit';

interface DrawerPayload {
  designerType?: 'bpmn' | 'simple';
  mode: DrawerMode;
  record?: WorkflowProcessModelInfo;
}

const emit = defineEmits<{
  success: [];
}>();

const mode = ref<DrawerMode>('create');
const currentId = ref<string>();
const initialValues = ref<Record<string, any>>({});
const hasSyncedMountedValues = ref(false);
const formOptions = ref<{ label: string; value: string }[]>([]);
const categoryOptions = ref<{ label: string; value: string }[]>([]);

const isCreate = computed(() => mode.value === 'create');
const drawerTitle = computed(() => (isCreate.value ? '新建流程' : '编辑流程'));

const emptyProcessValues = {
  bpmnXml: '',
  categoryId: '',
  designerType: 'simple',
  formDefinitionId: '',
  nodeJson: '',
  processKey: '',
  processName: '',
  remark: '',
  startScopeType: 'all',
  status: 'draft',
  version: 1,
};

const getDefaultValue = (fieldName: string, fallback?: any) =>
  initialValues.value[fieldName] ?? fallback;

const buildFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'categoryId',
    component: 'Select',
    label: '流程分类',
    defaultValue: getDefaultValue('categoryId', ''),
    rules: 'selectRequired',
    componentProps: {
      disabled: !isCreate.value,
      options: categoryOptions.value,
      placeholder: '请选择流程分类',
      showSearch: true,
      optionFilterProp: 'label',
    },
  },
  {
    fieldName: 'formDefinitionId',
    component: 'Select',
    label: '绑定表单',
    defaultValue: getDefaultValue('formDefinitionId', ''),
    rules: 'selectRequired',
    componentProps: {
      options: formOptions.value,
      placeholder: '请选择已发布表单版本',
      showSearch: true,
      optionFilterProp: 'label',
    },
  },
  {
    fieldName: 'processKey',
    component: 'Input',
    label: '流程编码',
    defaultValue: getDefaultValue('processKey', ''),
    rules: z
      .string({ required_error: '请输入流程编码' })
      .min(1, '请输入流程编码')
      .max(64, '流程编码不能超过64个字符'),
    componentProps: {
      disabled: !isCreate.value,
      maxlength: 64,
      placeholder: '请输入流程编码',
    },
  },
  {
    fieldName: 'processName',
    component: 'Input',
    label: '流程名称',
    defaultValue: getDefaultValue('processName', ''),
    rules: z
      .string({ required_error: '请输入流程名称' })
      .min(1, '请输入流程名称')
      .max(100, '流程名称不能超过100个字符'),
    componentProps: {
      maxlength: 100,
      placeholder: '请输入流程名称',
    },
  },
  {
    fieldName: 'designerType',
    component: 'Select',
    label: '设计器',
    defaultValue: getDefaultValue('designerType', 'simple'),
    rules: 'selectRequired',
    componentProps: {
      disabled: !isCreate.value,
      options: [
        { label: '简易设计器', value: 'simple' },
        { label: 'BPMN设计器', value: 'bpmn' },
      ],
    },
  },
  {
    fieldName: 'version',
    component: 'InputNumber',
    label: '版本号',
    defaultValue: getDefaultValue('version', 1),
    rules: z.number().min(1, '版本号不能小于1'),
    componentProps: {
      disabled: !isCreate.value,
      min: 1,
      precision: 0,
      style: 'width: 100%',
    },
  },
  {
    fieldName: 'startScopeType',
    component: 'Select',
    label: '发起范围',
    defaultValue: getDefaultValue('startScopeType', 'all'),
    rules: 'selectRequired',
    componentProps: {
      options: [
        { label: '全部人员', value: 'all' },
        { label: '指定范围', value: 'specified' },
      ],
    },
  },
  {
    fieldName: 'status',
    component: 'Select',
    label: '状态',
    defaultValue: getDefaultValue('status', 'draft'),
    rules: 'selectRequired',
    componentProps: {
      disabled: true,
      options: [
        { label: '草稿', value: 'draft' },
        { label: '已发布', value: 'published' },
        { label: '已停用', value: 'disabled' },
      ],
    },
  },
  {
    fieldName: 'remark',
    component: 'Textarea',
    label: '备注',
    defaultValue: getDefaultValue('remark', ''),
    rules: z.string().max(500, '备注不能超过500个字符').optional(),
    componentProps: {
      autoSize: { minRows: 3, maxRows: 5 },
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
    labelWidth: 92,
  },
  layout: 'horizontal',
  schema: buildFormSchema(),
  scrollToFirstError: true,
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[680px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存',
  contentClass: 'px-5 py-4 sm:px-6',
  onOpened: syncMountedFormValues,
  onConfirm: handleSubmit,
  title: drawerTitle.value,
});

function buildInitialNodeJson() {
  return JSON.stringify({
    nodes: [
      { id: 'start', name: '开始', type: 'start' },
      {
        allowAddSign: true,
        allowReturn: true,
        allowTransfer: true,
        assigneeJson: {},
        assigneeType: 'starter',
        id: 'approve_1',
        name: '审批节点',
        type: 'approver',
      },
      { id: 'end', name: '结束', type: 'end' },
    ],
  });
}

function buildInitialValues(payload: DrawerPayload) {
  if (payload.mode === 'create') {
    return {
      ...emptyProcessValues,
      designerType: payload.designerType ?? 'simple',
      nodeJson: buildInitialNodeJson(),
    };
  }

  return {
    ...emptyProcessValues,
    ...payload.record,
    bpmnXml: payload.record?.bpmnXml || '',
    nodeJson: payload.record?.nodeJson || buildInitialNodeJson(),
  };
}

function mapFormOptions(forms: WorkflowFormDefinitionInfo[]) {
  return latestPublishedForms(forms)
    .filter((item) => item.id)
    .map((item) => ({
      label: `${item.formName ?? item.formKey ?? item.id} v${item.version ?? 1}`,
      value: item.id!,
    }));
}

function latestPublishedForms(forms: WorkflowFormDefinitionInfo[]) {
  const latestMap = new Map<string, WorkflowFormDefinitionInfo>();
  for (const form of forms) {
    if (!form.id) {
      continue;
    }
    const key = form.formKey ?? form.id;
    const current = latestMap.get(key);
    if (!current || (form.version ?? 0) > (current.version ?? 0)) {
      latestMap.set(key, form);
    }
  }
  return [...latestMap.values()].sort((left, right) =>
    (left.formKey ?? left.id ?? '').localeCompare(right.formKey ?? right.id ?? ''),
  );
}

function mapCategoryOptions(categories: WorkflowCategoryInfo[]) {
  return categories
    .filter((item) => item.id)
    .map((item) => ({
      label: item.categoryName ?? item.categoryCode ?? item.id!,
      value: item.id!,
    }));
}

async function loadOptions() {
  const [forms, categories] = await Promise.all([
    listWorkflowForms({
      queryParams: { status: 'published' },
      sortField: 'form_key',
      sortOrder: 'asc',
    }),
    listWorkflowCategories({ queryParams: { status: 'enabled' } }),
  ]);
  formOptions.value = mapFormOptions(forms ?? []);
  categoryOptions.value = mapCategoryOptions(categories ?? []);
  formApi.setState({ schema: buildFormSchema() });
}

async function prepareFormState(payload: DrawerPayload) {
  mode.value = payload.mode;
  currentId.value = payload.record?.id;
  initialValues.value = buildInitialValues(payload);
  hasSyncedMountedValues.value = false;
  await loadOptions();
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
    const detail = await getWorkflowProcessById(currentId.value);
    initialValues.value = buildInitialValues({ mode: mode.value, record: detail });
    await formApi.setValues(initialValues.value);
  } catch {
    // handled by request layer
  }
}

function cleanPayload(values: Record<string, any>): WorkflowProcessModelInfo {
  const payload = cleanFormPayload<WorkflowProcessModelInfo>(values, {
    id: currentId.value,
  });
  payload.bpmnXml = initialValues.value.bpmnXml || '';
  payload.nodeJson = initialValues.value.nodeJson || buildInitialNodeJson();
  payload.status = initialValues.value.status || 'draft';
  if (!isCreate.value) {
    payload.categoryId = initialValues.value.categoryId;
    payload.version = initialValues.value.version;
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
    await saveWorkflowProcess(cleanPayload(values));
    message.success(isCreate.value ? '新建流程成功' : '保存流程成功');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function open(payload: DrawerPayload) {
  await prepareFormState(payload);

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
