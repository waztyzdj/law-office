<script setup lang="ts">
import type {
  WorkflowProcessModelInfo,
  WorkflowProcessNodeConfigInfo,
} from '#/api/workflow';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Button,
  Checkbox,
  Divider,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  RadioGroup,
  Select,
  Space,
  Tag,
  message,
} from 'ant-design-vue';

import {
  deleteWorkflowProcessNode,
  getWorkflowProcessById,
  listWorkflowProcessNodes,
  saveWorkflowProcess,
  saveWorkflowProcessNode,
} from '#/api/workflow';

import WorkflowAssigneeSelector from './WorkflowAssigneeSelector.vue';

interface DrawerPayload {
  record: WorkflowProcessModelInfo;
}

interface SimpleNode {
  allowAddSign?: boolean;
  allowReturn?: boolean;
  allowTransfer?: boolean;
  approvalMode?: ApprovalMode;
  assigneeResolveMode?: AssigneeResolveMode;
  assigneeJson?: Record<string, unknown>;
  assigneeType?: string;
  branchConfig?: BranchConfig;
  id: string;
  name: string;
  rejectPolicy?: string;
  type: 'approver' | 'gateway';
}

interface SimpleFlowJson {
  nodes: Array<Record<string, unknown>>;
}

interface BranchCondition {
  fieldKey?: string;
  operator: string;
  sourceType: string;
  value?: string;
  valueType: string;
}

interface BranchItem {
  branchId: string;
  branchName: string;
  conditions: BranchCondition[];
  defaultBranch: boolean;
  logic: 'and' | 'or';
  priority: number;
  targetNodeId: string;
}

interface BranchConfig {
  branches: BranchItem[];
}

type ApprovalMode = 'single' | 'countersign' | 'orsign';
type AssigneeResolveMode = 'all' | 'select';

const approvalModeOptions = [
  { label: '单人审批', value: 'single' },
  { label: '会签', value: 'countersign' },
  { label: '或签', value: 'orsign' },
];

const emit = defineEmits<{
  success: [];
}>();

const currentProcess = ref<WorkflowProcessModelInfo>();
const nodes = ref<SimpleNode[]>([]);
const existingNodeConfigs = ref<WorkflowProcessNodeConfigInfo[]>([]);
const hasSyncedMountedValues = ref(false);

const drawerTitle = computed(() =>
  currentProcess.value?.processName
    ? `设计流程：${currentProcess.value.processName}`
    : '设计流程',
);
const isPublished = computed(() => currentProcess.value?.status !== 'draft');
const isEmpty = computed(() => nodes.value.length === 0);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[92vw]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存设计',
  contentClass: 'px-5 py-4 sm:px-6',
  onConfirm: handleSubmit,
  onOpened: syncMountedDesigner,
  title: drawerTitle.value,
  zIndex: 1001,
});

function parseJsonValue<T>(json: string | undefined, fallback: T): T {
  if (!json) {
    return fallback;
  }
  try {
    return JSON.parse(json) as T;
  } catch {
    return fallback;
  }
}

function normalizeNode(raw: Record<string, any>, index: number): SimpleNode {
  if (raw.type === 'gateway' || raw.nodeType === 'gateway') {
    return {
      branchConfig: raw.branchConfig || raw.branchJson || { branches: [] },
      id: raw.id || raw.nodeId || `gateway_${index + 1}`,
      name: raw.name || raw.nodeName || `条件分支${index + 1}`,
      type: 'gateway',
    };
  }
  const assigneeType = raw.assigneeType || 'starter';
  return {
    allowAddSign: raw.allowAddSign ?? true,
    allowReturn: raw.allowReturn ?? true,
    allowTransfer: raw.allowTransfer ?? true,
    approvalMode: normalizeApprovalMode(raw.approvalMode),
    assigneeResolveMode: normalizeAssigneeResolveMode(
      raw.assigneeResolveMode,
      raw.approvalMode,
    ),
    assigneeJson: raw.assigneeJson || {},
    assigneeType,
    id: raw.id || raw.nodeId || `approve_${index + 1}`,
    name: raw.name || raw.nodeName || `审批节点${index + 1}`,
    rejectPolicy: raw.rejectPolicy || 'terminate',
    type: 'approver',
  };
}

function normalizeApprovalMode(value: unknown): ApprovalMode {
  return value === 'countersign' || value === 'orsign' ? value : 'single';
}

function defaultAssigneeResolveMode(approvalMode: unknown): AssigneeResolveMode {
  return normalizeApprovalMode(approvalMode) === 'orsign' ? 'all' : 'select';
}

function resolveAssigneeResolveMode(
  value: unknown,
  approvalMode: unknown,
): AssigneeResolveMode {
  return normalizeApprovalMode(approvalMode) === 'single'
    ? 'select'
    : normalizeAssigneeResolveMode(value, approvalMode);
}

function normalizeAssigneeResolveMode(
  value: unknown,
  approvalMode: unknown,
): AssigneeResolveMode {
  return value === 'all' || value === 'select'
    ? value
    : defaultAssigneeResolveMode(approvalMode);
}

const assigneeResolveModeOptions = [
  { label: '发送全部', value: 'all' },
  { label: '上一步选择', value: 'select' },
];
const branchSourceOptions = [
  { label: '表单字段', value: 'form_field' },
  { label: '发起人', value: 'starter' },
  { label: '发起人部门', value: 'starter_depart' },
  { label: '发起人角色', value: 'starter_role' },
  { label: '流程实例', value: 'instance' },
];
const branchValueTypeOptions = [
  { label: '文本', value: 'text' },
  { label: '数值', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '单选', value: 'single_select' },
  { label: '多选', value: 'multi_select' },
  { label: '布尔', value: 'boolean' },
];
const branchOperatorOptions = [
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'ne' },
  { label: '包含', value: 'contains' },
  { label: '不包含', value: 'not_contains' },
  { label: '为空', value: 'empty' },
  { label: '不为空', value: 'not_empty' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'ge' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'le' },
  { label: '区间', value: 'between' },
  { label: '属于', value: 'in' },
  { label: '不属于', value: 'not_in' },
  { label: '包含任一', value: 'contains_any' },
  { label: '包含全部', value: 'contains_all' },
  { label: '为真', value: 'is_true' },
  { label: '为假', value: 'is_false' },
];
const branchLogicOptions = [
  { label: '全部满足', value: 'and' },
  { label: '任一满足', value: 'or' },
];
const branchIdPattern = /^[\w-]+$/;
function buildNodesFromProcess(process: WorkflowProcessModelInfo, configs: WorkflowProcessNodeConfigInfo[]) {
  const parsed = parseJsonValue<SimpleFlowJson>(process.nodeJson, { nodes: [] });
  const jsonNodes = (parsed.nodes || [])
    .filter((item) => item.type === 'approver' || item.type === 'gateway')
    .map((item, index) => normalizeNode(item, index));

  if (jsonNodes.length > 0) {
    return jsonNodes;
  }

  return configs
    .filter((item) => item.nodeType === 'approver' || item.nodeType === 'gateway')
    .sort((a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0))
    .map((item, index) =>
      normalizeNode(
        {
          allowAddSign: item.allowAddSign === 1,
          allowReturn: item.allowReturn === 1,
          allowTransfer: item.allowTransfer === 1,
          approvalMode: item.approvalMode,
          assigneeResolveMode: item.assigneeResolveMode,
          assigneeJson: parseJsonValue<Record<string, unknown>>(item.assigneeJson, {}),
          assigneeType: item.assigneeType,
          branchConfig: parseJsonValue<BranchConfig | undefined>(item.branchJson, undefined),
          id: item.nodeId,
          name: item.nodeName,
          type: item.nodeType,
          rejectPolicy: item.rejectPolicy,
        },
        index,
      ),
    );
}

function buildNodeJson() {
  return JSON.stringify({
    nodes: [
      { id: 'start', name: '开始', type: 'start' },
      ...nodes.value.map((node) =>
        node.type === 'gateway'
          ? {
              branchConfig: node.branchConfig || { branches: [] },
              id: node.id,
              name: node.name,
              type: 'gateway',
            }
          : {
              allowAddSign: node.allowAddSign,
              allowReturn: node.allowReturn,
              allowTransfer: node.allowTransfer,
              approvalMode: node.approvalMode,
              assigneeResolveMode: resolveAssigneeResolveMode(
                node.assigneeResolveMode,
                node.approvalMode,
              ),
              assigneeJson: node.assigneeJson || {},
              assigneeType: node.assigneeType,
              id: node.id,
              name: node.name,
              rejectPolicy: node.rejectPolicy,
              type: 'approver',
            },
      ),
      { id: 'end', name: '结束', type: 'end' },
    ],
  });
}

function escapeXml(value: string | undefined) {
  return (value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function buildBpmnXml(process: WorkflowProcessModelInfo) {
  const processKey = escapeXml(process.processKey);
  const processName = escapeXml(process.processName);
  const flowElements = nodes.value
    .map((node) =>
      node.type === 'gateway'
        ? `    <exclusiveGateway id="${escapeXml(node.id)}" name="${escapeXml(node.name)}" />`
        : `    <userTask id="${escapeXml(node.id)}" name="${escapeXml(node.name)}" />`,
    )
    .join('\n');
  const sequenceIds = ['start', ...nodes.value.map((node) => node.id), 'end'];
  const flowLines: string[] = [];
  sequenceIds.slice(0, -1).forEach((source, index) => {
    const sourceNode = nodes.value.find((node) => node.id === source);
    if (sourceNode?.type === 'gateway') {
      return;
    }
    const target = sequenceIds[index + 1];
    flowLines.push(`    <sequenceFlow id="flow_${index + 1}" sourceRef="${escapeXml(source)}" targetRef="${escapeXml(target)}" />`);
  });
  nodes.value
    .filter((node) => node.type === 'gateway')
    .forEach((node) => {
      (node.branchConfig?.branches || []).forEach((branch) => {
        const flowId = `flow_${node.id}_${branch.branchId}`;
        const condition = branch.defaultBranch
          ? ''
          : `\n      <conditionExpression xsi:type="tFormalExpression">\${branch == '${escapeXml(branch.branchId)}'}</conditionExpression>\n    `;
        flowLines.push(`    <sequenceFlow id="${escapeXml(flowId)}" sourceRef="${escapeXml(node.id)}" targetRef="${escapeXml(branch.targetNodeId)}">${condition}</sequenceFlow>`);
      });
    });
  const flows = flowLines.join('\n');

  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://lawoffice.com/workflow">
  <process id="${processKey}" name="${processName}" isExecutable="true">
    <startEvent id="start" name="开始" />
${flowElements}
    <endEvent id="end" name="结束" />
${flows}
  </process>
</definitions>`;
}

function createApproverNode(): SimpleNode {
  const nextIndex = nodes.value.filter((node) => node.type === 'approver').length + 1;
  return {
    allowAddSign: true,
    allowReturn: true,
    allowTransfer: true,
    approvalMode: 'single',
    assigneeResolveMode: 'select',
    assigneeJson: {},
    assigneeType: 'starter',
    id: `approve_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: `审批节点${nextIndex}`,
    rejectPolicy: 'terminate',
    type: 'approver',
  };
}

function createGatewayNode(insertIndex: number): SimpleNode {
  const nextIndex = nodes.value.filter((node) => node.type === 'gateway').length + 1;
  const firstTarget = getFirstApproverIdAfter(insertIndex) || '';
  return {
    branchConfig: {
      branches: [
        createBranch(false, firstTarget),
        createBranch(true, firstTarget),
      ],
    },
    id: `gateway_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: `条件分支${nextIndex}`,
    type: 'gateway',
  };
}

function handleInsertNode(index: number, type: SimpleNode['type']) {
  const node = type === 'gateway' ? createGatewayNode(index) : createApproverNode();
  nodes.value.splice(index, 0, node);
}

function handleMoveNode(index: number, direction: -1 | 1) {
  const targetIndex = index + direction;
  if (targetIndex < 0 || targetIndex >= nodes.value.length) {
    return;
  }
  const [node] = nodes.value.splice(index, 1);
  if (node) {
    nodes.value.splice(targetIndex, 0, node);
  }
}

function getFirstApproverIdAfter(index: number) {
  return nodes.value.slice(index).find((node) => node.type === 'approver')?.id;
}

function getBranchTargetOptions(gatewayIndex: number) {
  return nodes.value
    .slice(gatewayIndex + 1)
    .filter((node) => node.type === 'approver')
    .map((node) => ({ label: node.name, value: node.id }));
}

function canInsertGateway(insertIndex: number) {
  return !!getFirstApproverIdAfter(insertIndex);
}

function createBranch(defaultBranch = false, targetNodeId = ''): BranchItem {
  return {
    branchId: defaultBranch
      ? 'default'
      : `branch_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    branchName: defaultBranch ? '默认分支' : '条件分支',
    conditions: defaultBranch ? [] : [createCondition()],
    defaultBranch,
    logic: 'and',
    priority: defaultBranch ? 999 : 10,
    targetNodeId,
  };
}

function createCondition(): BranchCondition {
  return {
    fieldKey: '',
    operator: 'eq',
    sourceType: 'form_field',
    value: '',
    valueType: 'text',
  };
}

function handleApprovalModeChange(node: SimpleNode) {
  node.assigneeResolveMode = defaultAssigneeResolveMode(node.approvalMode);
}

function handleRemoveNode(index: number) {
  if (
    nodes.value[index]?.type === 'approver'
    && nodes.value.filter((node) => node.type === 'approver').length <= 1
  ) {
    return;
  }
  nodes.value.splice(index, 1);
}

function handleAddBranch(node: SimpleNode, nodeIndex: number) {
  const firstTarget = getFirstApproverIdAfter(nodeIndex + 1) || '';
  node.branchConfig ||= { branches: [] };
  node.branchConfig.branches.splice(
    Math.max(0, node.branchConfig.branches.length - 1),
    0,
    createBranch(false, firstTarget),
  );
}

function handleRemoveBranch(node: SimpleNode, index: number) {
  node.branchConfig?.branches.splice(index, 1);
}

function handleAddCondition(branch: BranchItem) {
  branch.conditions.push(createCondition());
}

function handleRemoveCondition(branch: BranchItem, index: number) {
  branch.conditions.splice(index, 1);
}

function validateNodes() {
  if (!currentProcess.value?.id) {
    message.warning('请先保存流程基础信息');
    return false;
  }
  if (nodes.value.filter((node) => node.type === 'approver').length === 0) {
    message.warning('至少需要一个审批节点');
    return false;
  }

  const nodeIds = new Set<string>();
  for (const [index, node] of nodes.value.entries()) {
    node.name = node.name.trim();
    if (!node.name) {
      message.warning('请输入节点名称');
      return false;
    }
    if (nodeIds.has(node.id)) {
      message.warning('节点ID重复，请删除后重新添加节点');
      return false;
    }
    nodeIds.add(node.id);
    if (node.type === 'gateway') {
      if (!validateGatewayNode(node, index)) {
        return false;
      }
      continue;
    }
    if (needsAssigneeConfig(node) && !hasAssigneeConfig(node.assigneeJson || {})) {
      message.warning(`请选择“${node.name}”的审批人配置`);
      return false;
    }
  }
  return true;
}

function validateGatewayNode(node: SimpleNode, nodeIndex: number) {
  const branches = node.branchConfig?.branches || [];
  const availableTargetIds = new Set(
    getBranchTargetOptions(nodeIndex).map((option) => option.value),
  );
  if (branches.length < 2) {
    message.warning(`请为“${node.name}”至少配置一个条件分支和一个默认分支`);
    return false;
  }
  if (branches.filter((branch) => branch.defaultBranch).length !== 1) {
    message.warning(`请为“${node.name}”配置默认分支`);
    return false;
  }
  const branchIds = new Set<string>();
  for (const branch of branches) {
    branch.branchId = branch.branchId.trim();
    branch.branchName = branch.branchName.trim();
    if (!branch.branchId || !branch.branchName) {
      message.warning(`请完善“${node.name}”的分支名称和编码`);
      return false;
    }
    if (!branchIdPattern.test(branch.branchId)) {
      message.warning(`“${branch.branchName}”的分支编码只能包含字母、数字、下划线和短横线`);
      return false;
    }
    if (branchIds.has(branch.branchId)) {
      message.warning(`“${node.name}”的分支编码不能重复`);
      return false;
    }
    branchIds.add(branch.branchId);
    if (!branch.targetNodeId || !availableTargetIds.has(branch.targetNodeId)) {
      message.warning(`请选择“${branch.branchName || node.name}”后续的目标审批节点`);
      return false;
    }
    if (!branch.defaultBranch && branch.conditions.length === 0) {
      message.warning(`请为“${branch.branchName}”配置条件`);
      return false;
    }
    for (const condition of branch.conditions) {
      if (condition.sourceType === 'form_field' && !condition.fieldKey?.trim()) {
        message.warning(`请填写“${branch.branchName}”的表单字段`);
        return false;
      }
      if (requiresConditionValue(condition.operator) && !String(condition.value ?? '').trim()) {
        message.warning(`请填写“${branch.branchName}”的比较值`);
        return false;
      }
    }
  }
  return true;
}

function requiresConditionValue(operator: string) {
  return !['empty', 'not_empty', 'is_true', 'is_false'].includes(operator);
}

function needsAssigneeConfig(node: SimpleNode) {
  if (node.type !== 'approver') {
    return false;
  }
  return ![
    'starter',
    'depart_leader',
    'starter_select',
    'starter_supervisor',
  ].includes(node.assigneeType || '');
}

function hasAssigneeConfig(value: Record<string, unknown>) {
  return Object.values(value || {}).some(
    (item) => Array.isArray(item) && item.length > 0,
  );
}

async function deleteRemovedNodeConfigs() {
  const activeNodeIds = new Set(nodes.value.map((node) => node.id));
  const removedConfigs = existingNodeConfigs.value.filter(
    (config) =>
      config.id &&
      (config.nodeType === 'approver' || config.nodeType === 'gateway') &&
      config.nodeId &&
      !activeNodeIds.has(config.nodeId),
  );

  for (const config of removedConfigs) {
    await deleteWorkflowProcessNode(config.id!);
  }
}

async function saveNodeConfigs(processId: string) {
  for (const [index, node] of nodes.value.entries()) {
    const current = existingNodeConfigs.value.find((item) => item.nodeId === node.id);
    if (node.type === 'gateway') {
      await saveWorkflowProcessNode({
        id: current?.id,
        branchJson: JSON.stringify(node.branchConfig || { branches: [] }),
        nodeId: node.id,
        nodeName: node.name,
        nodeType: 'gateway',
        processModelId: processId,
        sortOrder: (index + 1) * 10,
      });
      continue;
    }
    await saveWorkflowProcessNode({
      id: current?.id,
      allowAddSign: node.allowAddSign ? 1 : 0,
      allowReturn: node.allowReturn ? 1 : 0,
      allowTransfer: node.allowTransfer ? 1 : 0,
      approvalMode: node.approvalMode || 'single',
      assigneeResolveMode: resolveAssigneeResolveMode(
        node.assigneeResolveMode,
        node.approvalMode,
      ),
      assigneeJson: JSON.stringify(node.assigneeJson || {}),
      assigneeType: node.assigneeType,
      nodeId: node.id,
      nodeName: node.name,
      nodeType: 'approver',
      processModelId: processId,
      rejectPolicy: node.rejectPolicy || 'terminate',
      sortOrder: (index + 1) * 10,
    });
  }
}

async function handleSubmit() {
  if (isPublished.value) {
    message.warning('非草稿流程不可直接修改，请新建版本后调整');
    return;
  }
  if (!validateNodes()) {
    return;
  }

  try {
    drawerApi.lock();
    const process = currentProcess.value!;
    const payload: WorkflowProcessModelInfo = {
      ...process,
      bpmnXml: buildBpmnXml(process),
      nodeJson: buildNodeJson(),
    };
    await saveWorkflowProcess(payload);
    await deleteRemovedNodeConfigs();
    await saveNodeConfigs(process.id!);
    message.success('流程设计已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function syncMountedDesigner() {
  if (hasSyncedMountedValues.value) {
    return;
  }
  hasSyncedMountedValues.value = true;
}

async function loadProcess(record: WorkflowProcessModelInfo) {
  const detail = record.id ? await getWorkflowProcessById(record.id) : record;
  const configs = detail.id
    ? await listWorkflowProcessNodes({
        queryParams: { processModelId: detail.id },
        sortField: 'sort_order',
        sortOrder: 'asc',
      })
    : [];
  currentProcess.value = detail;
  existingNodeConfigs.value = configs ?? [];
  nodes.value = buildNodesFromProcess(detail, existingNodeConfigs.value);
  if (nodes.value.length === 0) {
    handleInsertNode(0, 'approver');
  }
}

function updateDrawerState(loading = false) {
  drawerApi.setState({
    footer: !isPublished.value,
    loading,
    title: drawerTitle.value,
  });
}

async function open(payload: DrawerPayload) {
  hasSyncedMountedValues.value = false;
  currentProcess.value = payload.record;
  updateDrawerState(true);
  drawerApi.setData(payload).open();

  try {
    await loadProcess(payload.record);
    updateDrawerState(false);
  } catch {
    updateDrawerState(false);
  }

  await nextTick();
  void syncMountedDesigner();
}

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="simple-designer">
      <div class="designer-toolbar">
        <Space>
          <Tag color="processing">简易顺序审批</Tag>
          <span class="designer-subtitle">
            {{ currentProcess?.processKey }} · v{{ currentProcess?.version ?? 1 }}
          </span>
        </Space>
      </div>

      <div
        v-if="isPublished"
        class="readonly-tip"
      >
        非草稿流程不可直接修改，请新建版本后调整。
      </div>

      <div class="flow-canvas">
        <div class="terminal-node">开始</div>

        <div class="insert-slot">
          <div class="flow-line"></div>
          <Space>
            <Button
              :disabled="isPublished"
              size="small"
              type="primary"
              @click="handleInsertNode(0, 'approver')"
            >
              插入审批
            </Button>
            <Button
              :disabled="isPublished || !canInsertGateway(0)"
              size="small"
              @click="handleInsertNode(0, 'gateway')"
            >
              插入条件
            </Button>
          </Space>
          <div class="flow-line"></div>
        </div>

        <Empty
          v-if="isEmpty"
          description="暂无审批节点"
        />

        <template
          v-for="(node, index) in nodes"
          :key="node.id"
        >
          <div class="node-card">
            <div class="node-card-header">
              <Tag :color="node.type === 'gateway' ? 'purple' : 'blue'">
                {{ node.type === 'gateway' ? '条件' : '审批' }}
              </Tag>
              <Input
                v-model:value="node.name"
                :disabled="isPublished"
                :maxlength="100"
                class="node-name-input"
                placeholder="节点名称"
              />
              <Button
                :disabled="isPublished || (node.type === 'approver' && nodes.filter((item) => item.type === 'approver').length <= 1)"
                danger
                size="small"
                type="link"
                @click="handleRemoveNode(index)"
              >
                删除
              </Button>
              <Button
                :disabled="isPublished || index === 0"
                size="small"
                type="link"
                @click="handleMoveNode(index, -1)"
              >
                上移
              </Button>
              <Button
                :disabled="isPublished || index === nodes.length - 1"
                size="small"
                type="link"
                @click="handleMoveNode(index, 1)"
              >
                下移
              </Button>
            </div>

            <Form
              :model="node"
              layout="vertical"
            >
              <template v-if="node.type === 'approver'">
                <WorkflowAssigneeSelector
                  v-model="node.assigneeJson"
                  v-model:type="node.assigneeType"
                  :disabled="isPublished"
                />
                <FormItem label="办理策略">
                  <RadioGroup
                    v-model:value="node.approvalMode"
                    :disabled="isPublished"
                    :options="approvalModeOptions"
                    button-style="solid"
                    option-type="button"
                    @change="handleApprovalModeChange(node)"
                  />
                </FormItem>
                <FormItem
                  v-if="node.approvalMode !== 'single'"
                  label="执行人确定方式"
                >
                  <RadioGroup
                    v-model:value="node.assigneeResolveMode"
                    :disabled="isPublished"
                    :options="assigneeResolveModeOptions"
                    button-style="solid"
                    option-type="button"
                  />
                </FormItem>
                <FormItem label="节点动作">
                  <Space wrap>
                    <Checkbox
                      v-model:checked="node.allowTransfer"
                      :disabled="isPublished"
                    >
                      转办
                    </Checkbox>
                    <Checkbox
                      v-model:checked="node.allowReturn"
                      :disabled="isPublished"
                    >
                      退回
                    </Checkbox>
                    <Checkbox
                      v-model:checked="node.allowAddSign"
                      :disabled="isPublished"
                    >
                      加签
                    </Checkbox>
                  </Space>
                </FormItem>
              </template>
              <template v-else>
                <div class="branch-list">
                  <div
                    v-for="(branch, branchIndex) in node.branchConfig?.branches"
                    :key="`${node.id}_${branchIndex}`"
                    class="branch-card"
                  >
                    <div class="branch-card-header">
                      <Tag :color="branch.defaultBranch ? 'default' : 'purple'">
                        {{ branch.defaultBranch ? '默认' : '条件' }}
                      </Tag>
                      <Input
                        v-model:value="branch.branchName"
                        :disabled="isPublished"
                        :maxlength="100"
                        class="branch-name-input"
                        placeholder="分支名称"
                      />
                      <Button
                        v-if="!branch.defaultBranch"
                        :disabled="isPublished"
                        danger
                        size="small"
                        type="link"
                        @click="handleRemoveBranch(node, branchIndex)"
                      >
                        删除
                      </Button>
                    </div>
                    <div class="branch-grid">
                      <FormItem label="分支编码">
                        <Input
                          v-model:value="branch.branchId"
                          :disabled="isPublished || branch.defaultBranch"
                          :maxlength="100"
                        />
                      </FormItem>
                      <FormItem label="优先级">
                        <InputNumber
                          v-model:value="branch.priority"
                          :disabled="isPublished"
                          class="w-full"
                          :min="1"
                        />
                      </FormItem>
                      <FormItem label="目标审批节点">
                        <Select
                          v-model:value="branch.targetNodeId"
                          :disabled="isPublished"
                          :options="getBranchTargetOptions(index)"
                          placeholder="请选择"
                        />
                      </FormItem>
                      <FormItem
                        v-if="!branch.defaultBranch"
                        label="条件关系"
                      >
                        <RadioGroup
                          v-model:value="branch.logic"
                          :disabled="isPublished"
                          :options="branchLogicOptions"
                          option-type="button"
                          button-style="solid"
                        />
                      </FormItem>
                    </div>
                    <div
                      v-if="!branch.defaultBranch"
                      class="condition-list"
                    >
                      <div
                        v-for="(condition, conditionIndex) in branch.conditions"
                        :key="conditionIndex"
                        class="condition-row"
                      >
                        <Select
                          v-model:value="condition.sourceType"
                          :disabled="isPublished"
                          :options="branchSourceOptions"
                          class="condition-control"
                        />
                        <Input
                          v-if="condition.sourceType === 'form_field'"
                          v-model:value="condition.fieldKey"
                          :disabled="isPublished"
                          class="condition-control"
                          placeholder="字段 key"
                        />
                        <Select
                          v-model:value="condition.valueType"
                          :disabled="isPublished"
                          :options="branchValueTypeOptions"
                          class="condition-control"
                        />
                        <Select
                          v-model:value="condition.operator"
                          :disabled="isPublished"
                          :options="branchOperatorOptions"
                          class="condition-control"
                        />
                        <Input
                          v-if="requiresConditionValue(condition.operator)"
                          v-model:value="condition.value"
                          :disabled="isPublished"
                          class="condition-control"
                          placeholder="比较值"
                        />
                        <Button
                          :disabled="isPublished || branch.conditions.length <= 1"
                          class="condition-remove"
                          danger
                          size="small"
                          type="link"
                          @click="handleRemoveCondition(branch, conditionIndex)"
                        >
                          删除
                        </Button>
                      </div>
                      <Button
                        :disabled="isPublished"
                        size="small"
                        @click="handleAddCondition(branch)"
                      >
                        添加条件
                      </Button>
                    </div>
                  </div>
                  <Button
                    :disabled="isPublished"
                    @click="handleAddBranch(node, index)"
                  >
                    添加分支
                  </Button>
                </div>
              </template>
            </Form>
          </div>
          <div class="insert-slot">
            <div class="flow-line"></div>
            <Space>
              <Button
                :disabled="isPublished"
                size="small"
                type="primary"
                @click="handleInsertNode(index + 1, 'approver')"
              >
                插入审批
              </Button>
              <Button
                :disabled="isPublished || !canInsertGateway(index + 1)"
                size="small"
                @click="handleInsertNode(index + 1, 'gateway')"
              >
                插入条件
              </Button>
            </Space>
            <div class="flow-line"></div>
          </div>
        </template>

        <div class="terminal-node">结束</div>
      </div>

      <Divider />
      <div class="designer-footer-tip">
        保存后会同步简单节点 JSON、Flowable BPMN XML 和节点运行配置。
      </div>
    </div>
  </Drawer>
</template>

<style scoped>
.simple-designer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.designer-toolbar {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.designer-subtitle,
.designer-footer-tip {
  color: #6b7280;
  font-size: 13px;
}

.readonly-tip {
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 6px;
  color: #ad6800;
  padding: 8px 12px;
}

.flow-canvas {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-height: 420px;
  padding: 20px;
}

.terminal-node {
  background: #111827;
  border-radius: 999px;
  color: #fff;
  font-weight: 500;
  line-height: 36px;
  min-width: 96px;
  text-align: center;
}

.flow-line {
  background: #cbd5e1;
  height: 28px;
  width: 2px;
}

.insert-slot {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-card {
  background: #fff;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
  padding: 14px;
  width: min(100%, 520px);
}

.node-card-header {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.node-name-input {
  flex: 1;
  min-width: 180px;
}

.branch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.branch-card {
  background: #fafafa;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
}

.branch-card-header {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.branch-name-input {
  flex: 1;
}

.branch-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-row {
  align-items: start;
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.condition-control {
  min-width: 0;
  width: 100%;
}

.condition-remove {
  justify-self: start;
}

.w-full {
  width: 100%;
}

@media (max-width: 900px) {
  .branch-grid {
    grid-template-columns: 1fr;
  }

  .condition-row {
    align-items: stretch;
    flex-direction: column;
  }

  .condition-control {
    width: 100%;
  }
}
</style>
