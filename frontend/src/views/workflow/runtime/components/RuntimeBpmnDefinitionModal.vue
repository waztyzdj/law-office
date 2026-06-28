<script setup lang="ts">
import type Viewer from 'bpmn-js/lib/Viewer';
import type {
  BranchRecordInfo,
  InstanceDetailInfo,
  InstanceDiagramInfo,
  OperationRecordInfo,
} from '#/api/workflow';

import { computed, markRaw, nextTick, onBeforeUnmount, ref, watch } from 'vue';

import BpmnViewer from 'bpmn-js/lib/Viewer';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/diagram-js.css';

import { Empty, Modal, Tag } from 'ant-design-vue';

type BpmnRuntimeStatus = 'canceled' | 'current' | 'done' | 'end' | 'pending';

interface ParsedElement {
  id: string;
  sourceRef?: string;
  targetRef?: string;
  type: string;
}

interface BpmnCanvas {
  addMarker: (elementId: string, marker: string) => void;
  getGraphics: (elementId: string) => SVGElement;
  removeMarker: (elementId: string, marker: string) => void;
  zoom: (mode: string) => void;
}

interface BpmnElementRegistry {
  getAll: () => Array<{ id: string }>;
}

const props = defineProps<{
  detail?: InstanceDetailInfo;
  diagram?: InstanceDiagramInfo;
  open: boolean;
  title?: string;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

const canvasRef = ref<HTMLDivElement>();
const viewer = ref<Viewer>();
const renderError = ref('');
const markerIdPrefix = `runtime-bpmn-${Math.random().toString(36).slice(2)}`;
const flowMarkerColorMap = new Map<string, string>();

const bpmnXml = computed(() => props.diagram?.bpmnXml?.trim() ?? '');
const hasBpmnXml = computed(() => Boolean(bpmnXml.value));

const statusMarkerClasses = [
  'runtime-bpmn-node-canceled',
  'runtime-bpmn-node-current',
  'runtime-bpmn-node-done',
  'runtime-bpmn-node-end',
  'runtime-bpmn-node-pending',
  'runtime-bpmn-flow-active',
  'runtime-bpmn-flow-done',
  'runtime-bpmn-flow-muted',
];

watch(
  () => props.open,
  async (open) => {
    if (open) {
      await nextTick();
      await renderBpmn();
    }
  },
);

watch(
  () => [bpmnXml.value, props.detail?.currentTasks?.length, props.detail?.records?.length],
  async () => {
    if (props.open) {
      await nextTick();
      await renderBpmn();
    }
  },
);

onBeforeUnmount(() => {
  destroyViewer();
});

function handleCancel() {
  emit('update:open', false);
}

async function handleAfterOpenChange(open: boolean) {
  if (!open) {
    destroyViewer();
    return;
  }
  await nextTick();
  await renderBpmn();
}

async function renderBpmn() {
  if (!canvasRef.value || !hasBpmnXml.value) {
    return;
  }
  createViewer();
  if (!viewer.value) {
    return;
  }

  renderError.value = '';
  try {
    await viewer.value.importXML(bpmnXml.value);
    applyRuntimeMarkers();
    fitViewport();
  } catch (error) {
    renderError.value = error instanceof Error ? error.message : 'BPMN XML 解析失败';
  }
}

function createViewer() {
  if (!canvasRef.value || viewer.value) {
    return;
  }
  viewer.value = markRaw(
    new BpmnViewer({
      container: canvasRef.value,
    }),
  );
}

function destroyViewer() {
  viewer.value?.destroy();
  viewer.value = undefined;
}

function fitViewport() {
  const canvas = viewer.value?.get('canvas') as BpmnCanvas | undefined;
  requestAnimationFrame(() => {
    canvas?.zoom('fit-viewport');
  });
}

function applyRuntimeMarkers() {
  const canvas = viewer.value?.get('canvas') as BpmnCanvas | undefined;
  const elementRegistry = viewer.value?.get('elementRegistry') as
    | BpmnElementRegistry
    | undefined;
  if (!canvas || !elementRegistry) {
    return;
  }

  elementRegistry.getAll().forEach((element) => {
    statusMarkerClasses.forEach((marker) => canvas.removeMarker(element.id, marker));
  });

  const parsed = parseBpmnXml(bpmnXml.value);
  const nodeStatusMap = buildNodeStatusMap(parsed.nodes);
  parsed.nodes.forEach((node) => {
    canvas.addMarker(node.id, `runtime-bpmn-node-${nodeStatusMap.get(node.id) ?? 'pending'}`);
  });
  flowMarkerColorMap.clear();
  applyFlowMarkers(canvas, parsed.flows, nodeStatusMap);
  applyFlowArrowMarkers(canvas);
}

function applyFlowMarkers(
  canvas: BpmnCanvas,
  flows: ParsedElement[],
  nodeStatusMap: Map<string, BpmnRuntimeStatus>,
) {
  const branchRecords = props.diagram?.branchRecords ?? [];
  const matchedGatewayIds = new Set(
    branchRecords.map((record) => record.sourceNodeId).filter(Boolean),
  );
  const matchedFlowKeys = new Set(branchRecords.map(toFlowKey).filter(Boolean));

  flows.forEach((flow) => {
    const flowKey = `${flow.sourceRef}->${flow.targetRef}`;
    if (matchedGatewayIds.has(flow.sourceRef) && !matchedFlowKeys.has(flowKey)) {
      canvas.addMarker(flow.id, 'runtime-bpmn-flow-muted');
      flowMarkerColorMap.set(flow.id, '#bfbfbf');
      return;
    }
    if (matchedFlowKeys.has(flowKey)) {
      canvas.addMarker(flow.id, 'runtime-bpmn-flow-active');
      flowMarkerColorMap.set(flow.id, '#73d13d');
      return;
    }

    const sourceStatus = nodeStatusMap.get(flow.sourceRef ?? '');
    const targetStatus = nodeStatusMap.get(flow.targetRef ?? '');
    if (
      (sourceStatus === 'done' || sourceStatus === 'end') &&
      (targetStatus === 'done' || targetStatus === 'current' || targetStatus === 'end')
    ) {
      canvas.addMarker(flow.id, 'runtime-bpmn-flow-done');
      flowMarkerColorMap.set(flow.id, '#73d13d');
    }
  });
}

function applyFlowArrowMarkers(canvas: BpmnCanvas) {
  const svg = canvasRef.value?.querySelector('svg');
  if (!svg) {
    return;
  }

  const doneMarkerId = ensureArrowMarker(svg, 'done', '#73d13d');
  const mutedMarkerId = ensureArrowMarker(svg, 'muted', '#bfbfbf');
  flowMarkerColorMap.forEach((color, flowId) => {
    const markerId = color === '#73d13d' ? doneMarkerId : mutedMarkerId;
    const gfx = canvas.getGraphics(flowId);
    const path = gfx.querySelector<SVGPathElement>('.djs-visual > path');
    if (!path) {
      return;
    }
    path.setAttribute('marker-end', `url(#${markerId})`);
    path.setAttribute('markerEnd', `url(#${markerId})`);
    path.style.markerEnd = `url(#${markerId})`;
    path.style.setProperty('marker-end', `url(#${markerId})`, 'important');
    recolorExistingMarker(svg, path, color);
  });
}

function ensureArrowMarker(svg: SVGSVGElement, key: string, color: string) {
  const markerId = `${markerIdPrefix}-${key}`;
  if (svg.querySelector(`#${markerId}`)) {
    return markerId;
  }

  const namespace = 'http://www.w3.org/2000/svg';
  let defs = svg.querySelector(':scope > defs');
  if (!defs) {
    defs = document.createElementNS(namespace, 'defs');
    svg.append(defs);
  }

  const marker = document.createElementNS(namespace, 'marker');
  marker.setAttribute('id', markerId);
  marker.setAttribute('viewBox', '0 0 20 20');
  marker.setAttribute('refX', '11');
  marker.setAttribute('refY', '10');
  marker.setAttribute('markerWidth', '10');
  marker.setAttribute('markerHeight', '10');
  marker.setAttribute('orient', 'auto');

  const path = document.createElementNS(namespace, 'path');
  path.setAttribute('d', 'M 1 5 L 11 10 L 1 15 Z');
  path.setAttribute('fill', color);
  path.setAttribute('stroke', color);
  path.setAttribute('stroke-width', '1');

  marker.append(path);
  defs.append(marker);
  return markerId;
}

function recolorExistingMarker(
  svg: SVGSVGElement,
  path: SVGPathElement,
  color: string,
) {
  const markerRef = path.getAttribute('marker-end') || path.style.markerEnd;
  const markerId = markerRef.match(/#([^)"]+)/)?.[1];
  if (!markerId) {
    return;
  }
  const markerPath = svg.querySelector<SVGPathElement>(`#${markerId} path`);
  markerPath?.setAttribute('fill', color);
  markerPath?.setAttribute('stroke', color);
}

function parseBpmnXml(xml: string): {
  flows: ParsedElement[];
  nodes: ParsedElement[];
} {
  try {
    const document = new DOMParser().parseFromString(xml, 'application/xml');
    if (document.getElementsByTagName('parsererror').length > 0) {
      return { flows: [], nodes: [] };
    }
    const elements = Array.from(document.getElementsByTagName('*'));
    const nodes = elements
      .filter((element) => isRuntimeNodeType(element.localName))
      .map((element) => ({
        id: element.getAttribute('id') || '',
        type: element.localName,
      }))
      .filter((node) => node.id);
    const flows = elements
      .filter((element) => element.localName === 'sequenceFlow')
      .map((element) => ({
        id: element.getAttribute('id') || '',
        sourceRef: element.getAttribute('sourceRef') || '',
        targetRef: element.getAttribute('targetRef') || '',
        type: element.localName,
      }))
      .filter((flow) => flow.id && flow.sourceRef && flow.targetRef);
    return { flows, nodes };
  } catch {
    return { flows: [], nodes: [] };
  }
}

function isRuntimeNodeType(type: string) {
  return [
    'endEvent',
    'exclusiveGateway',
    'parallelGateway',
    'startEvent',
    'userTask',
  ].includes(type);
}

function buildNodeStatusMap(nodes: ParsedElement[]) {
  const operationRecords = props.diagram?.operationRecords ?? props.detail?.records ?? [];
  const currentNodeIds = new Set(
    (props.detail?.currentTasks ?? []).map((task) => task.nodeId).filter(Boolean),
  );
  const doneRecordMap = buildDoneRecordMap(operationRecords);
  const canceledNodeIds = new Set(
    operationRecords
      .filter((record) => record.action === 'task_cancel')
      .map((record) => record.nodeId)
      .filter(Boolean),
  );
  const matchedGatewayIds = new Set(
    (props.diagram?.branchRecords ?? [])
      .map((record) => record.sourceNodeId)
      .filter(Boolean),
  );
  const finishedStatuses = new Set(['approved', 'rejected', 'terminated', 'withdrawn']);
  const processStatus = props.detail?.processInstance?.status;
  const statusMap = new Map<string, BpmnRuntimeStatus>();

  nodes.forEach((node) => {
    if (node.type === 'endEvent') {
      statusMap.set(node.id, finishedStatuses.has(processStatus ?? '') ? 'end' : 'pending');
      return;
    }
    if (node.type === 'startEvent') {
      statusMap.set(
        node.id,
        props.detail?.processInstance?.startTime || doneRecordMap.has(node.id)
          ? 'done'
          : 'pending',
      );
      return;
    }
    if (currentNodeIds.has(node.id)) {
      statusMap.set(node.id, 'current');
      return;
    }
    if (canceledNodeIds.has(node.id)) {
      statusMap.set(node.id, 'canceled');
      return;
    }
    if (node.type === 'exclusiveGateway' && matchedGatewayIds.has(node.id)) {
      statusMap.set(node.id, 'done');
      return;
    }
    statusMap.set(node.id, doneRecordMap.has(node.id) ? 'done' : 'pending');
  });

  return statusMap;
}

function buildDoneRecordMap(records: OperationRecordInfo[]) {
  const hiddenActions = new Set(['branch_match', 'save_draft', 'urge']);
  const map = new Map<string, OperationRecordInfo>();
  records
    .filter((record) => record.nodeId && !hiddenActions.has(record.action ?? ''))
    .forEach((record) => map.set(record.nodeId as string, record));
  return map;
}

function toFlowKey(record: BranchRecordInfo) {
  if (!record.sourceNodeId || !record.targetNodeId) {
    return '';
  }
  return `${record.sourceNodeId}->${record.targetNodeId}`;
}
</script>

<template>
  <Modal
    :footer="null"
    :open="open"
    :title="title || '流程定义'"
    centered
    destroy-on-close
    width="78vw"
    wrap-class-name="runtime-bpmn-definition-modal"
    @after-open-change="handleAfterOpenChange"
    @cancel="handleCancel"
  >
    <div class="runtime-bpmn-viewer-shell">
      <div class="runtime-bpmn-viewer-legend">
        <Tag color="success">已办理</Tag>
        <Tag color="processing">办理中</Tag>
        <Tag>未开始</Tag>
      </div>
      <div
        v-if="hasBpmnXml"
        ref="canvasRef"
        class="runtime-bpmn-viewer"
      />
      <div
        v-else
        class="runtime-bpmn-empty"
      >
        <Empty description="暂无流程定义" />
      </div>
      <div
        v-if="renderError"
        class="runtime-bpmn-error"
      >
        {{ renderError }}
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.runtime-bpmn-viewer-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.runtime-bpmn-viewer-legend {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.runtime-bpmn-viewer {
  border: 1px solid #e5e7eb;
  height: min(68vh, 720px);
  min-height: 420px;
  overflow: hidden;
  width: 100%;
}

.runtime-bpmn-empty {
  align-items: center;
  border: 1px dashed #d9d9d9;
  display: flex;
  height: 420px;
  justify-content: center;
}

.runtime-bpmn-error {
  color: #cf1322;
  font-size: 13px;
  margin-top: 8px;
}

:global(.runtime-bpmn-definition-modal .ant-modal-body) {
  padding-top: 12px;
}

:global(.runtime-bpmn-definition-modal .bjs-powered-by) {
  display: none;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-done .djs-visual > rect),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-done .djs-visual > circle),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-done .djs-visual > polygon),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-end .djs-visual > rect),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-end .djs-visual > circle),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-end .djs-visual > polygon) {
  fill: #f6ffed !important;
  stroke: #73d13d !important;
  stroke-width: 2px !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-current .djs-visual > rect),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-current .djs-visual > circle),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-current .djs-visual > polygon) {
  fill: #eff6ff !important;
  stroke: #4096ff !important;
  stroke-width: 2px !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-pending .djs-visual > rect),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-pending .djs-visual > circle),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-pending .djs-visual > polygon) {
  fill: #fafafa !important;
  stroke: #d9d9d9 !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-canceled) {
  opacity: 0.45;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-canceled .djs-visual > rect),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-canceled .djs-visual > circle),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-canceled .djs-visual > polygon) {
  fill: #f5f5f5 !important;
  stroke: #bfbfbf !important;
  stroke-dasharray: 5 3;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-done .djs-visual > path),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-end .djs-visual > path) {
  fill: #73d13d !important;
  stroke: #73d13d !important;
  stroke-width: 1px !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-current .djs-visual > path) {
  fill: #4096ff !important;
  stroke: #4096ff !important;
  stroke-width: 1px !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-pending .djs-visual > path),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-node-canceled .djs-visual > path) {
  fill: #bfbfbf !important;
  stroke: #bfbfbf !important;
  stroke-width: 1px !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-flow-done .djs-visual > path),
:global(.runtime-bpmn-definition-modal .runtime-bpmn-flow-active .djs-visual > path) {
  stroke: #73d13d !important;
  stroke-width: 2px !important;
}

:global(.runtime-bpmn-definition-modal .runtime-bpmn-flow-muted) {
  opacity: 0.28;
}
</style>
