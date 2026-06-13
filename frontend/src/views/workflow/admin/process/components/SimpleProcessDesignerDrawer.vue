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
  allowAddSign: boolean;
  allowReturn: boolean;
  allowTransfer: boolean;
  assigneeJson: Record<string, unknown>;
  assigneeType: string;
  id: string;
  name: string;
  type: 'approver';
}

interface SimpleFlowJson {
  nodes: Array<Record<string, unknown>>;
}

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
  const assigneeType = raw.assigneeType || 'starter';
  return {
    allowAddSign: raw.allowAddSign ?? true,
    allowReturn: raw.allowReturn ?? true,
    allowTransfer: raw.allowTransfer ?? true,
    assigneeJson: raw.assigneeJson || {},
    assigneeType,
    id: raw.id || raw.nodeId || `approve_${index + 1}`,
    name: raw.name || raw.nodeName || `审批节点${index + 1}`,
    type: 'approver',
  };
}

function buildNodesFromProcess(process: WorkflowProcessModelInfo, configs: WorkflowProcessNodeConfigInfo[]) {
  const parsed = parseJsonValue<SimpleFlowJson>(process.nodeJson, { nodes: [] });
  const jsonNodes = (parsed.nodes || [])
    .filter((item) => item.type === 'approver')
    .map((item, index) => normalizeNode(item, index));

  if (jsonNodes.length > 0) {
    return jsonNodes;
  }

  return configs
    .filter((item) => item.nodeType === 'approver')
    .sort((a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0))
    .map((item, index) =>
      normalizeNode(
        {
          allowAddSign: item.allowAddSign === 1,
          allowReturn: item.allowReturn === 1,
          allowTransfer: item.allowTransfer === 1,
          assigneeJson: parseJsonValue<Record<string, unknown>>(item.assigneeJson, {}),
          assigneeType: item.assigneeType,
          id: item.nodeId,
          name: item.nodeName,
        },
        index,
      ),
    );
}

function buildNodeJson() {
  return JSON.stringify({
    nodes: [
      { id: 'start', name: '开始', type: 'start' },
      ...nodes.value.map((node) => ({
        allowAddSign: node.allowAddSign,
        allowReturn: node.allowReturn,
        allowTransfer: node.allowTransfer,
        assigneeJson: node.assigneeJson || {},
        assigneeType: node.assigneeType,
        id: node.id,
        name: node.name,
        type: 'approver',
      })),
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
  const userTasks = nodes.value
    .map(
      (node) =>
        `    <userTask id="${escapeXml(node.id)}" name="${escapeXml(node.name)}" />`,
    )
    .join('\n');
  const sequenceIds = ['start', ...nodes.value.map((node) => node.id), 'end'];
  const flows = sequenceIds
    .slice(0, -1)
    .map((source, index) => {
      const target = sequenceIds[index + 1];
      return `    <sequenceFlow id="flow_${index + 1}" sourceRef="${escapeXml(source)}" targetRef="${escapeXml(target)}" />`;
    })
    .join('\n');

  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://lawoffice.com/workflow">
  <process id="${processKey}" name="${processName}" isExecutable="true">
    <startEvent id="start" name="开始" />
${userTasks}
    <endEvent id="end" name="结束" />
${flows}
  </process>
</definitions>`;
}

function handleAddNode() {
  const nextIndex = nodes.value.length + 1;
  nodes.value.push({
    allowAddSign: true,
    allowReturn: true,
    allowTransfer: true,
    assigneeJson: {},
    assigneeType: 'starter',
    id: `approve_${Date.now()}`,
    name: `审批节点${nextIndex}`,
    type: 'approver',
  });
}

function handleRemoveNode(index: number) {
  nodes.value.splice(index, 1);
}

function validateNodes() {
  if (!currentProcess.value?.id) {
    message.warning('请先保存流程基础信息');
    return false;
  }
  if (nodes.value.length === 0) {
    message.warning('至少需要一个审批节点');
    return false;
  }

  const nodeIds = new Set<string>();
  for (const node of nodes.value) {
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
    if (needsAssigneeConfig(node) && !hasAssigneeConfig(node.assigneeJson)) {
      message.warning(`请选择“${node.name}”的审批人配置`);
      return false;
    }
  }
  return true;
}

function needsAssigneeConfig(node: SimpleNode) {
  return !['starter', 'depart_leader', 'starter_select'].includes(node.assigneeType);
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
      config.nodeType === 'approver' &&
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
    await saveWorkflowProcessNode({
      id: current?.id,
      allowAddSign: node.allowAddSign ? 1 : 0,
      allowReturn: node.allowReturn ? 1 : 0,
      allowTransfer: node.allowTransfer ? 1 : 0,
      assigneeJson: JSON.stringify(node.assigneeJson || {}),
      assigneeType: node.assigneeType,
      nodeId: node.id,
      nodeName: node.name,
      nodeType: 'approver',
      processModelId: processId,
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
    handleAddNode();
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
        <Button
          :disabled="isPublished"
          type="primary"
          @click="handleAddNode"
        >
          添加审批节点
        </Button>
      </div>

      <div
        v-if="isPublished"
        class="readonly-tip"
      >
        非草稿流程不可直接修改，请新建版本后调整。
      </div>

      <div class="flow-canvas">
        <div class="terminal-node">开始</div>
        <div class="flow-line"></div>

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
              <Tag color="blue">审批</Tag>
              <Input
                v-model:value="node.name"
                :disabled="isPublished"
                :maxlength="100"
                class="node-name-input"
                placeholder="节点名称"
              />
              <Button
                :disabled="isPublished || nodes.length <= 1"
                danger
                size="small"
                type="link"
                @click="handleRemoveNode(index)"
              >
                删除
              </Button>
            </div>

            <Form
              :model="node"
              layout="vertical"
            >
              <WorkflowAssigneeSelector
                v-model="node.assigneeJson"
                v-model:type="node.assigneeType"
                :disabled="isPublished"
              />
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
            </Form>
          </div>
          <div class="flow-line"></div>
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
}

.node-name-input {
  flex: 1;
}
</style>
