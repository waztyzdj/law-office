<script setup lang="ts">
import type {
  BranchRecordInfo,
  InstanceDetailInfo,
  InstanceDiagramInfo,
  OperationRecordInfo,
  RuntimeTaskInfo,
} from '#/api/workflow';

import { computed } from 'vue';

import { Empty, Tag } from 'ant-design-vue';

import {
  formatApprovalProgress,
  getStatusMeta,
  getWorkflowActionMeta,
} from '../../components/status';

type DiagramNodeType =
  | 'endEvent'
  | 'exclusiveGateway'
  | 'parallelGateway'
  | 'startEvent'
  | 'userTask';
type DiagramNodeStatus = 'canceled' | 'current' | 'done' | 'end' | 'pending';

interface ParsedNode {
  id: string;
  name: string;
  type: DiagramNodeType;
}

interface ParsedEdge {
  id: string;
  sourceRef: string;
  targetRef: string;
}

interface ViewEdge extends ParsedEdge {
  active: boolean;
  branchName?: string;
  muted: boolean;
  targetName: string;
}

interface ViewNode extends ParsedNode {
  action?: string;
  actor?: string;
  edges: ViewEdge[];
  groupProgress?: string;
  status: DiagramNodeStatus;
  time?: string;
}

const props = defineProps<{
  detail?: InstanceDetailInfo;
  diagram?: InstanceDiagramInfo;
}>();

const nodeTypeLabelMap: Record<DiagramNodeType, string> = {
  endEvent: '结束',
  exclusiveGateway: '条件',
  parallelGateway: '并行',
  startEvent: '开始',
  userTask: '审批',
};

const hiddenRecordActions = new Set(['branch_match', 'save_draft', 'urge']);
const finishedStatuses = new Set(['approved', 'rejected', 'terminated', 'withdrawn']);

const parsedDiagram = computed(() => parseBpmnXml(props.diagram?.bpmnXml));
const viewNodes = computed(() => buildViewNodes());

function buildViewNodes() {
  const parsed = parsedDiagram.value;
  if (parsed.nodes.length === 0) {
    return [];
  }

  const nodeMap = new Map(parsed.nodes.map((node) => [node.id, node]));
  const branchRecords = props.diagram?.branchRecords ?? [];
  const operationRecords = props.diagram?.operationRecords ?? props.detail?.records ?? [];
  const currentTasks = props.detail?.currentTasks ?? [];
  const readableNameByNodeId = buildReadableNameMap(
    branchRecords,
    operationRecords,
    currentTasks,
  );
  const currentNodeIds = new Set(currentTasks.map((task) => task.nodeId).filter(Boolean));
  const doneRecordMap = buildDoneRecordMap(operationRecords);
  const canceledNodeIds = new Set(
    operationRecords
      .filter((record) => record.action === 'task_cancel')
      .map((record) => record.nodeId)
      .filter(Boolean),
  );
  const matchedGatewayIds = new Set(branchRecords.map((record) => record.sourceNodeId).filter(Boolean));
  const matchedEdgeKeys = new Set(branchRecords.map(toEdgeKey).filter(Boolean));
  const branchNameByEdgeKey = new Map(
    branchRecords
      .map((record) => [toEdgeKey(record), sanitizeBusinessText(record.branchName)] as const)
      .filter(([key, value]) => Boolean(key && value)),
  );
  const outgoingEdges = groupEdgesBySource(parsed.edges);

  return orderNodes(parsed.nodes, parsed.edges).map((node) => {
    const currentTasksOnNode = currentTasks.filter((task) => task.nodeId === node.id);
    const doneRecord = doneRecordMap.get(node.id);
    const status = resolveNodeStatus(node, {
      canceledNodeIds,
      currentNodeIds,
      doneRecord,
      matchedGatewayIds,
    });
    return {
      ...node,
      action: doneRecord?.action,
      actor: currentTasksOnNode.length > 0
        ? formatTaskActors(currentTasksOnNode)
        : formatRecordActor(doneRecord),
      edges: (outgoingEdges.get(node.id) ?? []).map((edge) =>
        buildViewEdge(
          edge,
          nodeMap,
          matchedGatewayIds,
          matchedEdgeKeys,
          branchNameByEdgeKey,
          readableNameByNodeId,
        ),
      ),
      groupProgress: currentTasksOnNode.map(formatApprovalProgress).filter(Boolean).join('，'),
      name: resolveNodeDisplayName(node, readableNameByNodeId),
      status,
      time: resolveNodeTime(node, currentTasksOnNode, doneRecord),
    } satisfies ViewNode;
  });
}

function parseBpmnXml(xml?: string): { edges: ParsedEdge[]; nodes: ParsedNode[] } {
  if (!xml) {
    return { edges: [], nodes: [] };
  }
  try {
    const document = new DOMParser().parseFromString(xml, 'application/xml');
    if (document.getElementsByTagName('parsererror').length > 0) {
      return { edges: [], nodes: [] };
    }
    const elements = Array.from(document.getElementsByTagName('*'));
    const nodes = elements
      .filter((element) => isSupportedNodeType(element.localName))
      .map((element) => ({
        id: element.getAttribute('id') || '',
        name: element.getAttribute('name') || element.getAttribute('id') || '',
        type: element.localName as DiagramNodeType,
      }))
      .filter((node) => node.id);
    const edges = elements
      .filter((element) => element.localName === 'sequenceFlow')
      .map((element) => ({
        id: element.getAttribute('id') || '',
        sourceRef: element.getAttribute('sourceRef') || '',
        targetRef: element.getAttribute('targetRef') || '',
      }))
      .filter((edge) => edge.id && edge.sourceRef && edge.targetRef);
    return { edges, nodes };
  } catch {
    return { edges: [], nodes: [] };
  }
}

function isSupportedNodeType(type: string): type is DiagramNodeType {
  return ['endEvent', 'exclusiveGateway', 'parallelGateway', 'startEvent', 'userTask'].includes(type);
}

function buildDoneRecordMap(records: OperationRecordInfo[]) {
  const map = new Map<string, OperationRecordInfo>();
  records
    .filter((record) => record.nodeId && !hiddenRecordActions.has(record.action ?? ''))
    .forEach((record) => map.set(record.nodeId as string, record));
  return map;
}

function groupEdgesBySource(edges: ParsedEdge[]) {
  const map = new Map<string, ParsedEdge[]>();
  edges.forEach((edge) => {
    map.set(edge.sourceRef, [...(map.get(edge.sourceRef) ?? []), edge]);
  });
  return map;
}

function orderNodes(nodes: ParsedNode[], edges: ParsedEdge[]) {
  const nodeMap = new Map(nodes.map((node) => [node.id, node]));
  const outgoingEdges = groupEdgesBySource(edges);
  const startNode = nodes.find((node) => node.type === 'startEvent') ?? nodes[0];
  const ordered: ParsedNode[] = [];
  const visited = new Set<string>();

  function visit(nodeId?: string) {
    if (!nodeId || visited.has(nodeId)) {
      return;
    }
    const node = nodeMap.get(nodeId);
    if (!node) {
      return;
    }
    visited.add(nodeId);
    ordered.push(node);
    (outgoingEdges.get(nodeId) ?? []).forEach((edge) => visit(edge.targetRef));
  }

  visit(startNode?.id);
  nodes.forEach((node) => visit(node.id));
  return ordered;
}

function resolveNodeStatus(
  node: ParsedNode,
  context: {
    canceledNodeIds: Set<string | undefined>;
    currentNodeIds: Set<string | undefined>;
    doneRecord?: OperationRecordInfo;
    matchedGatewayIds: Set<string | undefined>;
  },
): DiagramNodeStatus {
  const processStatus = props.detail?.processInstance?.status;
  if (node.type === 'endEvent') {
    return finishedStatuses.has(processStatus ?? '') ? 'end' : 'pending';
  }
  if (node.type === 'startEvent') {
    return props.detail?.processInstance?.startTime || context.doneRecord ? 'done' : 'pending';
  }
  if (context.currentNodeIds.has(node.id)) {
    return 'current';
  }
  if (context.canceledNodeIds.has(node.id)) {
    return 'canceled';
  }
  if (node.type === 'exclusiveGateway' && context.matchedGatewayIds.has(node.id)) {
    return 'done';
  }
  return context.doneRecord ? 'done' : 'pending';
}

function resolveNodeTime(
  node: ParsedNode,
  currentTasks: RuntimeTaskInfo[],
  doneRecord?: OperationRecordInfo,
) {
  if (node.type === 'startEvent') {
    return props.detail?.processInstance?.startTime || doneRecord?.operateTime;
  }
  if (node.type === 'endEvent') {
    return props.detail?.processInstance?.endTime;
  }
  return currentTasks[0]?.startTime || doneRecord?.operateTime;
}

function buildViewEdge(
  edge: ParsedEdge,
  nodeMap: Map<string, ParsedNode>,
  matchedGatewayIds: Set<string | undefined>,
  matchedEdgeKeys: Set<string>,
  branchNameByEdgeKey: Map<string, string | undefined>,
  readableNameByNodeId: Map<string, string>,
): ViewEdge {
  const edgeKey = `${edge.sourceRef}->${edge.targetRef}`;
  const hasGatewayMatch = matchedGatewayIds.has(edge.sourceRef);
  const active = hasGatewayMatch ? matchedEdgeKeys.has(edgeKey) : false;
  const targetNode = nodeMap.get(edge.targetRef);
  return {
    ...edge,
    active,
    branchName: branchNameByEdgeKey.get(edgeKey),
    muted: hasGatewayMatch && !active,
    targetName: targetNode
      ? resolveNodeDisplayName(targetNode, readableNameByNodeId)
      : '后续环节',
  };
}

function toEdgeKey(record: BranchRecordInfo) {
  if (!record.sourceNodeId || !record.targetNodeId) {
    return '';
  }
  return `${record.sourceNodeId}->${record.targetNodeId}`;
}

function formatTaskActors(tasks: RuntimeTaskInfo[]) {
  return tasks
    .map((task) =>
      task.assigneeRealname ||
      task.assigneeUsername ||
      task.candidateAssigneeNames ||
      '',
    )
    .filter(Boolean)
    .join('，');
}

function buildReadableNameMap(
  branchRecords: BranchRecordInfo[],
  operationRecords: OperationRecordInfo[],
  currentTasks: RuntimeTaskInfo[],
) {
  const map = new Map<string, string>();
  branchRecords.forEach((record) => {
    putReadableName(map, record.sourceNodeId, record.sourceNodeName);
    putReadableName(map, record.targetNodeId, record.targetNodeName);
  });
  operationRecords.forEach((record) => {
    putReadableName(map, record.nodeId, record.nodeName);
    putReadableName(map, record.targetNodeId, record.targetNodeName);
  });
  currentTasks.forEach((task) => {
    putReadableName(map, task.nodeId, task.taskName);
  });
  return map;
}

function putReadableName(
  map: Map<string, string>,
  nodeId?: string,
  nodeName?: string,
) {
  if (!nodeId || map.has(nodeId)) {
    return;
  }
  const readableName = sanitizeBusinessText(nodeName);
  if (readableName) {
    map.set(nodeId, readableName);
  }
}

function resolveNodeDisplayName(
  node: ParsedNode,
  readableNameByNodeId: Map<string, string>,
) {
  const explicitName = sanitizeBusinessText(node.name);
  if (explicitName && !isTechnicalNodeName(explicitName, node.id)) {
    return explicitName;
  }
  const runtimeName = readableNameByNodeId.get(node.id);
  if (runtimeName) {
    return runtimeName;
  }
  return nodeTypeLabelMap[node.type];
}

function sanitizeBusinessText(value?: string) {
  const text = value?.trim();
  return text && !isTechnicalText(text) ? text : '';
}

function isTechnicalNodeName(value: string, nodeId: string) {
  return value === nodeId || isTechnicalText(value);
}

function isTechnicalText(value: string) {
  return /^(approve|flow|gateway|userTask|sequenceFlow)[_-]/i.test(value);
}

function formatRecordActor(record?: OperationRecordInfo) {
  if (!record) {
    return '';
  }
  return record.operatorRealname || record.operatorUsername || '';
}

function getNodeStatusMeta(node: ViewNode) {
  if (node.status === 'current') {
    return { color: 'processing', label: '当前' };
  }
  if (node.status === 'done') {
    return { color: 'success', label: '已完成' };
  }
  if (node.status === 'canceled') {
    return { color: 'default', label: '已取消' };
  }
  if (node.status === 'end') {
    const meta = getStatusMeta(props.detail?.processInstance?.status);
    return { color: meta.color, label: meta.label };
  }
  return { color: 'default', label: '未到达' };
}

function getNodeTypeColor(type: DiagramNodeType) {
  if (type === 'exclusiveGateway' || type === 'parallelGateway') {
    return 'purple';
  }
  if (type === 'startEvent' || type === 'endEvent') {
    return 'default';
  }
  return 'blue';
}
</script>

<template>
  <div class="runtime-process-diagram">
    <div
      v-if="viewNodes.length"
      class="diagram-list"
    >
      <template
        v-for="(node, index) in viewNodes"
        :key="node.id"
      >
        <div
          class="diagram-node"
          :class="`diagram-node--${node.status}`"
        >
          <div class="diagram-node__head">
            <Tag :color="getNodeTypeColor(node.type)">
              {{ nodeTypeLabelMap[node.type] }}
            </Tag>
            <span class="diagram-node__name">{{ node.name || node.id }}</span>
            <Tag :color="getNodeStatusMeta(node).color">
              {{ getNodeStatusMeta(node).label }}
            </Tag>
          </div>
          <div class="diagram-node__meta">
            <span v-if="node.actor">处理人：{{ node.actor }}</span>
            <span v-if="node.groupProgress">进度：{{ node.groupProgress }}</span>
            <span v-if="node.action">
              动作：
              <Tag :color="getWorkflowActionMeta(node.action).color">
                {{ getWorkflowActionMeta(node.action).label }}
              </Tag>
            </span>
            <span v-if="node.time">时间：{{ node.time }}</span>
          </div>
          <div
            v-if="node.status === 'current' && node.edges.length === 0"
            class="diagram-node__hint"
          >
            当前节点办理完成后进入后续环节。
          </div>
          <div
            v-if="node.edges.length"
            class="diagram-branches"
          >
            <span
              v-for="edge in node.edges"
              :key="edge.id"
              class="diagram-branch"
              :class="{
                'diagram-branch--active': edge.active,
                'diagram-branch--muted': edge.muted,
              }"
            >
              <span v-if="edge.branchName">{{ edge.branchName }}</span>
              <span>{{ edge.targetName }}</span>
            </span>
          </div>
        </div>
        <div
          v-if="index < viewNodes.length - 1"
          class="diagram-connector"
        />
      </template>
    </div>
    <div
      v-else
      class="runtime-empty-panel"
    >
      <Empty description="暂无流程图数据" />
    </div>
  </div>
</template>

<style scoped>
.runtime-process-diagram {
  min-height: 0;
  padding: 8px 4px 0;
}

.diagram-list {
  align-items: stretch;
  display: flex;
  flex-direction: column;
}

.diagram-node {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left-width: 4px;
  border-radius: 6px;
  padding: 10px 12px;
}

.diagram-node--current {
  background: #eff6ff;
  border-color: #91caff;
  border-left-color: #1677ff;
}

.diagram-node--done,
.diagram-node--end {
  border-left-color: #52c41a;
}

.diagram-node--canceled {
  background: #fafafa;
  border-left-color: #bfbfbf;
}

.diagram-node--pending {
  background: #fafafa;
  border-left-color: #d9d9d9;
}

.diagram-node__head {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 0;
}

.diagram-node__name {
  color: #111827;
  flex: 1;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diagram-node__meta {
  color: #6b7280;
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  gap: 6px 14px;
  line-height: 1.7;
  margin-top: 8px;
}

.diagram-node__hint {
  color: #6b7280;
  font-size: 13px;
  margin-top: 8px;
}

.diagram-branches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.diagram-branch {
  align-items: center;
  background: #f5f5f5;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  color: #6b7280;
  display: inline-flex;
  font-size: 12px;
  gap: 4px;
  line-height: 20px;
  max-width: 100%;
  padding: 0 8px;
}

.diagram-branch::before {
  content: "→";
}

.diagram-branch--active {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #237804;
}

.diagram-branch--muted {
  opacity: 0.48;
}

.diagram-connector {
  align-self: center;
  background: #d9d9d9;
  height: 18px;
  width: 2px;
}

.runtime-empty-panel {
  align-items: center;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  justify-content: center;
  min-height: 180px;
}
</style>
