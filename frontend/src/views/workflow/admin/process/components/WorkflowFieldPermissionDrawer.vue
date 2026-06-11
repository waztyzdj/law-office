<script setup lang="ts">
import type { FormRule } from '@form-create/ant-design-vue';

import type {
  WorkflowFieldPermissionInfo,
  WorkflowFormDefinitionInfo,
  WorkflowProcessModelInfo,
  WorkflowProcessNodeConfigInfo,
} from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Alert,
  Empty,
  RadioGroup,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import {
  deleteWorkflowFieldPermission,
  getWorkflowFormById,
  getWorkflowProcessById,
  listWorkflowFieldPermissions,
  listWorkflowProcessNodes,
  saveWorkflowFieldPermission,
} from '#/api/workflow';

interface DrawerPayload {
  record: WorkflowProcessModelInfo;
}

interface FormFieldItem {
  fieldKey: string;
  fieldTitle: string;
  fieldType: string;
}

interface FieldPermissionRow extends FormFieldItem {
  id?: string;
  permission: 'editable' | 'hidden' | 'readonly';
  requiredFlag: boolean;
}

const emit = defineEmits<{
  success: [];
}>();

const currentProcess = ref<WorkflowProcessModelInfo>();
const currentForm = ref<WorkflowFormDefinitionInfo>();
const nodes = ref<WorkflowProcessNodeConfigInfo[]>([]);
const activeNodeId = ref<string>();
const rows = ref<FieldPermissionRow[]>([]);
const existingPermissions = ref<WorkflowFieldPermissionInfo[]>([]);
const loading = ref(false);
const parseError = ref('');

const drawerTitle = computed(() =>
  currentProcess.value?.processName
    ? `字段权限：${currentProcess.value.processName}`
    : '字段权限',
);
const isPublished = computed(() => currentProcess.value?.status !== 'draft');
const activeNode = computed(() =>
  nodes.value.find((node) => node.nodeId === activeNodeId.value),
);
const nodeOptions = computed(() =>
  nodes.value.map((node) => ({
    label: node.nodeName ?? node.nodeId ?? '',
    value: node.nodeId ?? '',
  })),
);

const permissionOptions = [
  { label: '可编辑', value: 'editable' },
  { label: '只读', value: 'readonly' },
  { label: '隐藏', value: 'hidden' },
];

const columns = [
  { dataIndex: 'fieldTitle', title: '字段名称', width: 220 },
  { dataIndex: 'fieldType', title: '组件', width: 140 },
  { dataIndex: 'permission', title: '权限', width: 260 },
  { dataIndex: 'requiredFlag', title: '必填', width: 100 },
];

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[940px]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存权限',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  onOpened: syncMountedValues,
  title: drawerTitle.value,
});

function parseJson<T>(json: string | undefined, fallback: T): T {
  if (!json) {
    return fallback;
  }

  try {
    return JSON.parse(json) as T;
  } catch {
    parseError.value = '表单设计内容解析失败，请先检查绑定表单';
    return fallback;
  }
}

function resolveRuleType(rule: FormRule) {
  if (typeof rule.type === 'string') {
    return rule.type;
  }
  return '-';
}

function collectFields(rules: FormRule[], target: FormFieldItem[] = []) {
  for (const rule of rules) {
    if (!rule || typeof rule !== 'object') {
      continue;
    }

    if (rule.field) {
      target.push({
        fieldKey: String(rule.field),
        fieldTitle: String(rule.title ?? rule.field),
        fieldType: resolveRuleType(rule),
      });
    }

    if (Array.isArray(rule.children)) {
      collectFields(rule.children as FormRule[], target);
    }
  }
  return target;
}

function buildPermissionMap() {
  const map = new Map<string, WorkflowFieldPermissionInfo>();
  for (const item of existingPermissions.value) {
    if (item.nodeId === activeNodeId.value && item.fieldKey) {
      map.set(item.fieldKey, item);
    }
  }
  return map;
}

function buildRows() {
  const rules = parseJson<FormRule[]>(currentForm.value?.schemaJson, []);
  const fields = collectFields(Array.isArray(rules) ? rules : []);
  const permissionMap = buildPermissionMap();
  rows.value = fields.map((field) => {
    const current = permissionMap.get(field.fieldKey);
    return {
      ...field,
      id: current?.id,
      permission:
        current?.permission === 'hidden' || current?.permission === 'readonly'
          ? current.permission
          : 'editable',
      requiredFlag: Number(current?.requiredFlag ?? 0) === 1,
    };
  });
}

async function loadData(record: WorkflowProcessModelInfo) {
  if (!record.id) {
    message.warning('请先保存流程基础信息');
    return;
  }

  parseError.value = '';
  const process = await getWorkflowProcessById(record.id);
  currentProcess.value = process;
  currentForm.value = process.formDefinitionId
    ? await getWorkflowFormById(process.formDefinitionId)
    : undefined;
  nodes.value = (
    await listWorkflowProcessNodes({
      queryParams: {
        nodeType: 'approver',
        processModelId: process.id,
      },
      sortField: 'sortOrder',
      sortOrder: 'asc',
    })
  ).filter((node) => node.nodeId);
  existingPermissions.value = await listWorkflowFieldPermissions({
    queryParams: { processModelId: process.id },
    sortField: 'fieldKey',
    sortOrder: 'asc',
  });
  activeNodeId.value = nodes.value[0]?.nodeId;
  buildRows();
}

function handleNodeChange() {
  buildRows();
}

function toPermissionRow(record: Record<string, any>): FieldPermissionRow {
  return record as FieldPermissionRow;
}

function normalizeRequired(row: FieldPermissionRow) {
  if (row.permission !== 'editable') {
    row.requiredFlag = false;
  }
}

async function deleteRemovedPermissions() {
  const activeFieldKeys = new Set(rows.value.map((row) => row.fieldKey));
  const removed = existingPermissions.value.filter(
    (item) =>
      item.id &&
      item.nodeId === activeNodeId.value &&
      item.fieldKey &&
      !activeFieldKeys.has(item.fieldKey),
  );
  for (const item of removed) {
    await deleteWorkflowFieldPermission(item.id!);
  }
}

async function handleSubmit() {
  if (isPublished.value) {
    message.warning('非草稿流程不可直接修改，请新建版本后调整');
    return;
  }
  if (!currentProcess.value?.id || !activeNodeId.value) {
    message.warning('请先保存流程节点配置');
    return;
  }
  if (rows.value.length === 0) {
    message.warning('当前绑定表单没有可配置字段');
    return;
  }

  try {
    drawerApi.lock();
    await deleteRemovedPermissions();
    for (const row of rows.value) {
      await saveWorkflowFieldPermission({
        id: row.id,
        fieldKey: row.fieldKey,
        nodeId: activeNodeId.value,
        permission: row.permission,
        processModelId: currentProcess.value.id,
        requiredFlag: row.permission === 'editable' && row.requiredFlag ? 1 : 0,
      });
    }
    message.success('字段权限已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function syncMountedValues() {
  if (!currentProcess.value) {
    return;
  }
  drawerApi.setState({ title: drawerTitle.value });
}

async function open(payload: DrawerPayload) {
  loading.value = true;
  rows.value = [];
  nodes.value = [];
  existingPermissions.value = [];
  currentProcess.value = payload.record;
  currentForm.value = undefined;
  drawerApi.setState({ loading: true, title: drawerTitle.value });
  drawerApi.setData(payload).open();

  try {
    await loadData(payload.record);
  } finally {
    loading.value = false;
    drawerApi.setState({ loading: false, title: drawerTitle.value });
  }

  await nextTick();
  void syncMountedValues();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="field-permission-drawer">
      <Alert
        v-if="isPublished"
        show-icon
        type="warning"
        message="非草稿流程不可直接修改字段权限，请新建版本后调整。"
      />
      <Alert
        v-if="parseError"
        show-icon
        type="error"
        :message="parseError"
      />

      <div class="permission-toolbar">
        <Space wrap>
          <Tag color="processing">
            {{ currentForm?.formName ?? currentProcess?.formDefinitionId ?? '未绑定表单' }}
          </Tag>
          <span class="permission-subtitle">
            {{ currentProcess?.processKey }} · v{{ currentProcess?.version ?? 1 }}
          </span>
        </Space>
        <Select
          v-model:value="activeNodeId"
          :disabled="isPublished || nodes.length === 0"
          :options="nodeOptions"
          class="node-select"
          placeholder="请选择审批节点"
          @change="handleNodeChange"
        />
      </div>

      <Empty
        v-if="nodes.length === 0"
        description="请先在简易节点设计器中保存审批节点"
      />
      <Empty
        v-else-if="rows.length === 0 && !loading"
        description="当前绑定表单没有可配置字段"
      />
      <Table
        v-else
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 720, y: 520 }"
        row-key="fieldKey"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'fieldTitle'">
            {{ record.fieldTitle }}
          </span>
          <Tag v-else-if="column.dataIndex === 'fieldType'">
            {{ record.fieldType }}
          </Tag>
          <RadioGroup
            v-else-if="column.dataIndex === 'permission'"
            v-model:value="record.permission"
            :disabled="isPublished"
            :options="permissionOptions"
            option-type="button"
            size="small"
            @change="normalizeRequired(toPermissionRow(record))"
          />
          <Switch
            v-else-if="column.dataIndex === 'requiredFlag'"
            v-model:checked="record.requiredFlag"
            :disabled="isPublished || record.permission !== 'editable'"
            checked-children="是"
            un-checked-children="否"
          />
        </template>
      </Table>

      <div
        v-if="activeNode"
        class="permission-footer"
      >
        当前节点：{{ activeNode.nodeName ?? activeNode.nodeId }}
      </div>
    </div>
  </Drawer>
</template>

<style scoped>
.field-permission-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.permission-toolbar {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.permission-subtitle,
.permission-footer {
  color: #6b7280;
  font-size: 13px;
}

.node-select {
  width: 260px;
}
</style>
