<script setup lang="ts">
import type Modeler from 'bpmn-js/lib/Modeler';
import type {
  WorkflowProcessModelInfo,
  WorkflowProcessNodeConfigInfo,
} from '#/api/workflow';

import { computed, markRaw, nextTick, onBeforeUnmount, ref } from 'vue';

import BpmnModeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/diagram-js.css';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Alert,
  Button,
  Checkbox,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  RadioGroup,
  Select,
  Space,
  Tabs,
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
import WorkflowCcConfigEditor from './WorkflowCcConfigEditor.vue';

interface DrawerPayload {
  record: WorkflowProcessModelInfo;
}

interface SaveXmlResult {
  xml?: string;
}

interface BpmnBusinessObject {
  $type?: string;
  conditionExpression?: unknown;
  default?: BpmnBusinessObject;
  id?: string;
  name?: string;
  outgoing?: BpmnBusinessObject[];
  targetRef?: {
    id?: string;
  };
}

interface BpmnElement {
  businessObject?: BpmnBusinessObject;
  id?: string;
  parent?: BpmnElement;
  outgoing?: BpmnElement[];
  type?: string;
  width?: number;
  x?: number;
  y?: number;
}

interface BpmnNode {
  id: string;
  name: string;
  type: 'approver' | 'gateway';
}

interface NodeConfigDraft {
  allowAddSign: boolean;
  allowReturn: boolean;
  allowTransfer: boolean;
  approvalMode: ApprovalMode;
  assigneeResolveMode: AssigneeResolveMode;
  assigneeJson: Record<string, unknown>;
  assigneeType: string;
  branchConfig?: BranchConfig;
  ccConfig?: CcConfig;
  id?: string;
  rejectPolicy: string;
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

interface CcConfig {
  events?: string[];
  targets?: Array<{
    targetIds?: string[];
    targetType: string;
  }>;
}

interface BpmnElementRegistry {
  get: (id: string) => BpmnElement | undefined;
  getAll: () => BpmnElement[];
}

interface BpmnElementFactory {
  createShape: (attrs: Record<string, unknown>) => BpmnElement;
}

interface BpmnAutoPlace {
  append: (source: BpmnElement, shape: BpmnElement) => BpmnElement;
}

interface BpmnCreate {
  start: (
    event: Event,
    shape: BpmnElement,
    context?: Record<string, unknown>,
  ) => void;
}

interface BpmnPalette {
  registerProvider: (
    priority: number,
    provider: { getPaletteEntries: () => BpmnToolEntries },
  ) => void;
}

interface BpmnContextPad {
  registerProvider: (
    priority: number,
    provider: { getContextPadEntries: (element: BpmnElement) => BpmnToolEntries },
  ) => void;
}

interface BpmnModeling {
  updateProperties: (
    element: BpmnElement,
    properties: Record<string, unknown>,
  ) => void;
}

interface BpmnModdle {
  create: (type: string, properties: Record<string, unknown>) => unknown;
}

type BpmnToolTarget = BpmnElement | BpmnElement[] | undefined;
type BpmnToolAction = (
  event: Event,
  target?: BpmnToolTarget,
  autoActivate?: boolean,
) => void;

interface BpmnToolEntry {
  action: BpmnToolAction | Record<string, BpmnToolAction>;
  className?: string;
  group?: string;
  title?: string;
}

type BpmnToolEntries = Record<string, BpmnToolEntry>;
type ApprovalMode = 'single' | 'countersign' | 'orsign';
type AssigneeResolveMode = 'all' | 'select';

const approvalModeOptions = [
  { label: '单人审批', value: 'single' },
  { label: '会签', value: 'countersign' },
  { label: '或签', value: 'orsign' },
];

const bpmnTranslateMap: Record<string, string> = {
  'Activate the create/remove space tool': '启用创建/删除空间工具',
  'Activate the global connect tool': '启用全局连线工具',
  'Activate the hand tool': '启用抓手工具',
  'Activate the lasso tool': '启用套索工具',
  'Ad-hoc': '临时子流程',
  'Add Lane above': '在上方添加泳道',
  'Add Lane below': '在下方添加泳道',
  'Append {type}': '追加{type}',
  'Append compensation activity': '追加补偿活动',
  'Append EndEvent': '追加结束事件',
  'Append Gateway': '追加网关',
  'Append Intermediate/Boundary Event': '追加中间/边界事件',
  'Append Task': '追加任务',
  'Business Rule Task': '业务规则任务',
  'Call Activity': '调用活动',
  'Cancel Boundary Event': '取消边界事件',
  'Cancel End Event': '取消结束事件',
  'Change type': '更改类型',
  'Collapsed Pool': '折叠池',
  'Compensation Boundary Event': '补偿边界事件',
  'Compensation End Event': '补偿结束事件',
  'Compensation Intermediate Throw Event': '补偿中间抛出事件',
  'Complex Gateway': '复杂网关',
  'Conditional Boundary Event': '条件边界事件',
  'Conditional Boundary Event (non-interrupting)': '条件边界事件（非中断）',
  'Conditional Intermediate Catch Event': '条件中间捕获事件',
  'Connect using Association': '使用关联线连接',
  'Connect using DataInputAssociation': '使用数据输入关联连接',
  'Connect using Sequence/MessageFlow or Association': '使用顺序流/消息流/关联线连接',
  'Create DataObjectReference': '创建数据对象',
  'Create DataStoreReference': '创建数据存储',
  'Create EndEvent': '创建结束事件',
  'Create expanded SubProcess': '创建展开子流程',
  'Create Gateway': '创建网关',
  'Create Group': '创建分组',
  'Create Intermediate/Boundary Event': '创建中间/边界事件',
  'Create Pool/Participant': '创建池/参与者',
  'Create StartEvent': '创建开始事件',
  'Create Task': '创建任务',
  'Data Object Reference': '数据对象',
  'Data Store Reference': '数据存储',
  'Delete Lane': '删除泳道',
  'Divide into three Lanes': '拆分为三个泳道',
  'Divide into two Lanes': '拆分为两个泳道',
  'Empty Pool': '空池',
  'End Event': '结束事件',
  'Error Boundary Event': '错误边界事件',
  'Error End Event': '错误结束事件',
  'Escalation Boundary Event': '升级边界事件',
  'Escalation Boundary Event (non-interrupting)': '升级边界事件（非中断）',
  'Escalation End Event': '升级结束事件',
  'Escalation Intermediate Throw Event': '升级中间抛出事件',
  'Event Sub Process': '事件子流程',
  'Exclusive Gateway': '排他网关',
  'Expanded Pool': '展开池',
  'Inclusive Gateway': '包容网关',
  'Intermediate Throw Event': '中间抛出事件',
  'Link Intermediate Catch Event': '链接中间捕获事件',
  'Link Intermediate Throw Event': '链接中间抛出事件',
  'Loop': '循环',
  'Manual Task': '人工任务',
  'Message Boundary Event': '消息边界事件',
  'Message Boundary Event (non-interrupting)': '消息边界事件（非中断）',
  'Message End Event': '消息结束事件',
  'Message Intermediate Catch Event': '消息中间捕获事件',
  'Message Intermediate Throw Event': '消息中间抛出事件',
  'Message Start Event': '消息开始事件',
  'Message Start Event (non-interrupting)': '消息开始事件（非中断）',
  'Parallel Gateway': '并行网关',
  'Parallel Multi Instance': '并行多实例',
  'Participant Multiplicity': '多参与者',
  'Receive Task': '接收任务',
  'Remove': '删除',
  'Script Task': '脚本任务',
  'Send Task': '发送任务',
  'Sequential Multi Instance': '串行多实例',
  'Service Task': '服务任务',
  'Signal Boundary Event': '信号边界事件',
  'Signal Boundary Event (non-interrupting)': '信号边界事件（非中断）',
  'Signal End Event': '信号结束事件',
  'Signal Intermediate Catch Event': '信号中间捕获事件',
  'Signal Intermediate Throw Event': '信号中间抛出事件',
  'Signal Start Event': '信号开始事件',
  'Signal Start Event (non-interrupting)': '信号开始事件（非中断）',
  'Start Event': '开始事件',
  'Sub Process': '子流程',
  'Sub Process (collapsed)': '子流程（折叠）',
  'Task': '任务',
  'Terminate End Event': '终止结束事件',
  'Timer Boundary Event': '定时边界事件',
  'Timer Boundary Event (non-interrupting)': '定时边界事件（非中断）',
  'Timer Intermediate Catch Event': '定时中间捕获事件',
  'Timer Start Event': '定时开始事件',
  'Timer Start Event (non-interrupting)': '定时开始事件（非中断）',
  'Transaction': '事务',
  'User Task': '用户任务',
};

const bpmnTranslateModule = {
  translate: [
    'value',
    (template: string, replacements?: Record<string, string>) => {
      const translated = bpmnTranslateMap[template] || template;
      return translated.replaceAll(/\{([^}]+)\}/g, (_, key: string) =>
        replacements?.[key] ?? `{${key}}`,
      );
    },
  ],
};

const bpmnApprovalNodeModule = {
  __init__: ['approvalNodePaletteProvider', 'approvalNodeContextPadProvider'],
  approvalNodeContextPadProvider: [
    'type',
    function ApprovalNodeContextPadProvider(
      contextPad: BpmnContextPad,
      elementFactory: BpmnElementFactory,
      autoPlace: BpmnAutoPlace,
      create: BpmnCreate,
    ) {
      contextPad.registerProvider(1200, {
        getContextPadEntries(element: BpmnElement) {
          if (!isBpmnUserTaskElement(element)) {
            return {} as BpmnToolEntries;
          }

          function appendApprovalNode(event: Event) {
            const shape = elementFactory.createShape({ type: 'bpmn:UserTask' });
            if (autoPlace) {
              autoPlace.append(element, shape);
              return;
            }
            create.start(event, shape, { source: element });
          }

          return {
            'append-approval-node': {
              action: {
                click: appendApprovalNode,
                dragstart: appendApprovalNode,
              },
              className: 'bpmn-icon-user-task',
              group: 'model',
              title: '追加审批节点',
            },
          };
        },
      });
    },
  ],
  approvalNodePaletteProvider: [
    'type',
    function ApprovalNodePaletteProvider(
      palette: BpmnPalette,
      elementFactory: BpmnElementFactory,
      create: BpmnCreate,
    ) {
      palette.registerProvider(1200, {
        getPaletteEntries() {
          function createApprovalNode(event: Event) {
            const shape = elementFactory.createShape({ type: 'bpmn:UserTask' });
            create.start(event, shape);
          }

          return {
            'create-approval-node': {
              action: {
                click: createApprovalNode,
                dragstart: createApprovalNode,
              },
              className: 'bpmn-icon-user-task',
              group: 'activity',
              title: '创建审批节点',
            },
          };
        },
      });
    },
  ],
};

const emit = defineEmits<{
  success: [];
}>();

const canvasRef = ref<HTMLDivElement>();
const modeler = ref<Modeler>();
const currentProcess = ref<WorkflowProcessModelInfo>();
const loading = ref(false);
const importError = ref('');
const bpmnNodes = ref<BpmnNode[]>([]);
const activeNodeId = ref('');
const existingNodeConfigs = ref<WorkflowProcessNodeConfigInfo[]>([]);
const nodeConfigs = ref<Record<string, NodeConfigDraft>>({});

const drawerTitle = computed(() =>
  currentProcess.value?.processName
    ? `BPMN 设计：${currentProcess.value.processName}`
    : 'BPMN 设计',
);
const isPublished = computed(() => currentProcess.value?.status !== 'draft');
const activeNode = computed(() =>
  bpmnNodes.value.find((item) => item.id === activeNodeId.value),
);
const activeNodeConfig = computed(() =>
  activeNodeId.value ? nodeConfigs.value[activeNodeId.value] : undefined,
);
const approverNodeOptions = computed(() =>
  bpmnNodes.value
    .filter((node) => node.type === 'approver')
    .map((node) => ({ label: node.name, value: node.id })),
);

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-full sm:w-[92vw]! sm:max-w-none!',
  closeOnClickModal: true,
  confirmText: '保存设计',
  contentClass: 'workflow-bpmn-designer-drawer px-4 py-4 sm:px-5',
  onClosed: destroyModeler,
  onConfirm: handleSubmit,
  onOpened: syncModeler,
  title: drawerTitle.value,
  zIndex: 1001,
});

function createDefaultBpmnXml(process: WorkflowProcessModelInfo) {
  const processId = escapeXml(process.processKey || 'workflow_process');
  const processName = escapeXml(process.processName || '审批流程');
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:flowable="http://flowable.org/bpmn"
                  id="Definitions_${processId}"
                  targetNamespace="http://lawoffice.com/workflow">
  <bpmn:process id="${processId}" name="${processName}" isExecutable="true">
    <bpmn:startEvent id="start" name="开始">
      <bpmn:outgoing>flow_start_approve</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="approve_1" name="审批节点">
      <bpmn:incoming>flow_start_approve</bpmn:incoming>
      <bpmn:outgoing>flow_approve_end</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="end" name="结束">
      <bpmn:incoming>flow_approve_end</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="flow_start_approve" sourceRef="start" targetRef="approve_1" />
    <bpmn:sequenceFlow id="flow_approve_end" sourceRef="approve_1" targetRef="end" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${processId}">
    <bpmndi:BPMNPlane id="BPMNPlane_${processId}" bpmnElement="${processId}">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="160" y="120" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="approve_1_di" bpmnElement="approve_1">
        <dc:Bounds x="260" y="98" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="430" y="120" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_start_approve_di" bpmnElement="flow_start_approve">
        <di:waypoint x="196" y="138" />
        <di:waypoint x="260" y="138" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_approve_end_di" bpmnElement="flow_approve_end">
        <di:waypoint x="360" y="138" />
        <di:waypoint x="430" y="138" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;
}

function escapeXml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

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

function isBpmnUserTaskElement(element: BpmnElement | undefined) {
  return (
    !!element
    && (element.type === 'bpmn:UserTask'
      || element.businessObject?.$type === 'bpmn:UserTask')
  );
}

function isBpmnExclusiveGatewayElement(element: BpmnElement | undefined) {
  return (
    !!element
    && (element.type === 'bpmn:ExclusiveGateway'
      || element.businessObject?.$type === 'bpmn:ExclusiveGateway')
  );
}

function isBpmnConfigurableNodeElement(element: BpmnElement | undefined) {
  return isBpmnUserTaskElement(element) || isBpmnExclusiveGatewayElement(element);
}

function resolveBpmnElementId(element: BpmnElement | undefined) {
  return element?.businessObject?.id || element?.id || '';
}

function buildNodeConfigDraft(
  config?: WorkflowProcessNodeConfigInfo,
): NodeConfigDraft {
  const assigneeType = config?.assigneeType || 'starter';
  return {
    allowAddSign: config?.allowAddSign === undefined ? true : config.allowAddSign === 1,
    allowReturn: config?.allowReturn === undefined ? true : config.allowReturn === 1,
    allowTransfer:
      config?.allowTransfer === undefined ? true : config.allowTransfer === 1,
    approvalMode: normalizeApprovalMode(config?.approvalMode),
    assigneeResolveMode: normalizeAssigneeResolveMode(
      config?.assigneeResolveMode,
      config?.approvalMode,
    ),
    branchConfig: parseJsonValue<BranchConfig | undefined>(config?.branchJson, undefined),
    ccConfig: parseJsonValue<CcConfig | undefined>(config?.ccJson, undefined),
    assigneeJson: parseJsonValue<Record<string, unknown>>(config?.assigneeJson, {}),
    assigneeType,
    id: config?.id,
    rejectPolicy: config?.rejectPolicy || 'terminate',
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

function handleApprovalModeChange(config: NodeConfigDraft) {
  config.assigneeResolveMode = defaultAssigneeResolveMode(config.approvalMode);
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

function ensureBranchConfig(config: NodeConfigDraft) {
  const firstTarget = bpmnNodes.value.find((node) => node.type === 'approver')?.id || '';
  config.branchConfig ||= {
    branches: [
      createBranch(false, firstTarget),
      createBranch(true, firstTarget),
    ],
  };
  return config.branchConfig;
}

function handleAddBranch(config: NodeConfigDraft) {
  const branchConfig = ensureBranchConfig(config);
  const firstTarget = bpmnNodes.value.find((node) => node.type === 'approver')?.id || '';
  branchConfig.branches.splice(
    Math.max(0, branchConfig.branches.length - 1),
    0,
    createBranch(false, firstTarget),
  );
}

function handleRemoveBranch(config: NodeConfigDraft, index: number) {
  config.branchConfig?.branches.splice(index, 1);
}

function handleAddCondition(branch: BranchItem) {
  branch.conditions.push(createCondition());
}

function handleRemoveCondition(branch: BranchItem, index: number) {
  branch.conditions.splice(index, 1);
}

function requiresConditionValue(operator: string) {
  return !['empty', 'not_empty', 'is_true', 'is_false'].includes(operator);
}

function listGatewayOutgoingTargets(gatewayId: string) {
  const elementRegistry = modeler.value?.get('elementRegistry') as
    | BpmnElementRegistry
    | undefined;
  const gateway = elementRegistry?.get(gatewayId);
  return (gateway?.outgoing || [])
    .map((flow) => flow.businessObject?.targetRef?.id)
    .filter(Boolean) as string[];
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

function createModeler() {
  if (!canvasRef.value || modeler.value) {
    return;
  }

  modeler.value = markRaw(
    new BpmnModeler({
      additionalModules: [bpmnTranslateModule, bpmnApprovalNodeModule],
      container: canvasRef.value,
      keyboard: {
        bindTo: document,
      },
    }),
  );
  bindModelerEvents();
}

async function importBpmnXml() {
  if (!modeler.value || !currentProcess.value) {
    return;
  }

  importError.value = '';
  const xml =
    currentProcess.value.bpmnXml || createDefaultBpmnXml(currentProcess.value);
  try {
    await modeler.value.importXML(xml);
    syncBpmnUserTasks();
    fitViewport();
  } catch (error) {
    importError.value =
      error instanceof Error ? error.message : 'BPMN XML 解析失败';
  }
}

function fitViewport() {
  const canvas = modeler.value?.get('canvas') as
    | { zoom: (mode: string) => void }
    | undefined;
  canvas?.zoom('fit-viewport');
}

function bindModelerEvents() {
  const eventBus = modeler.value?.get('eventBus') as
    | {
        on: (
          eventName: string,
          callback: (event: Record<string, unknown>) => void,
        ) => void;
      }
    | undefined;
  eventBus?.on('selection.changed', (event) => {
    const selected = (
      Array.isArray(event.newSelection) ? event.newSelection : []
    ) as BpmnElement[];
    const node = selected.find(isBpmnConfigurableNodeElement);
    setActiveNode(resolveBpmnElementId(node));
  });
  eventBus?.on('elements.changed', () => {
    syncBpmnUserTasks();
  });
}

function setActiveNode(nodeId: string) {
  if (!nodeId) {
    return;
  }
  syncBpmnUserTasks();
  if (!bpmnNodes.value.some((node) => node.id === nodeId)) {
    return;
  }
  activeNodeId.value = nodeId;
}

function extractBpmnUserTasks() {
  const elementRegistry = modeler.value?.get('elementRegistry') as
    | BpmnElementRegistry
    | undefined;
  const elements = elementRegistry?.getAll() || [];
  const visited = new Set<string>();
  return elements
    .filter(
      (element) =>
        element.type === 'bpmn:UserTask'
        || element.businessObject?.$type === 'bpmn:UserTask',
    )
    .sort((a, b) => (a.y ?? 0) - (b.y ?? 0) || (a.x ?? 0) - (b.x ?? 0))
    .map((element) => {
      const id = element.businessObject?.id || element.id || '';
      return {
        id,
        name: element.businessObject?.name || id,
        type: 'approver' as const,
      };
    })
    .filter((node) => {
      if (!node.id || visited.has(node.id)) {
        return false;
      }
      visited.add(node.id);
      return true;
    });
}

function extractBpmnExclusiveGateways() {
  const elementRegistry = modeler.value?.get('elementRegistry') as
    | BpmnElementRegistry
    | undefined;
  const elements = elementRegistry?.getAll() || [];
  const visited = new Set<string>();
  return elements
    .filter(
      (element) =>
        element.type === 'bpmn:ExclusiveGateway'
        || element.businessObject?.$type === 'bpmn:ExclusiveGateway',
    )
    .sort((a, b) => (a.y ?? 0) - (b.y ?? 0) || (a.x ?? 0) - (b.x ?? 0))
    .map((element) => {
      const id = element.businessObject?.id || element.id || '';
      return {
        id,
        name: element.businessObject?.name || id,
        type: 'gateway' as const,
      };
    })
    .filter((node) => {
      if (!node.id || visited.has(node.id)) {
        return false;
      }
      visited.add(node.id);
      return true;
    });
}

function syncBpmnUserTasks() {
  const nodes = [...extractBpmnUserTasks(), ...extractBpmnExclusiveGateways()];
  bpmnNodes.value = nodes;

  const activeIds = new Set(nodes.map((node) => node.id));
  const nextConfigs: Record<string, NodeConfigDraft> = {};
  for (const node of nodes) {
    nextConfigs[node.id] =
      nodeConfigs.value[node.id]
      || buildNodeConfigDraft(
        existingNodeConfigs.value.find((item) => item.nodeId === node.id),
      );
  }
  nodeConfigs.value = nextConfigs;

  if (!activeNodeId.value || !activeIds.has(activeNodeId.value)) {
    activeNodeId.value = nodes[0]?.id || '';
  }
}

async function syncModeler() {
  await nextTick();
  createModeler();
  await importBpmnXml();
}

function validateNodeConfigs() {
  if (!currentProcess.value?.id) {
    message.warning('请先保存流程基础信息');
    return false;
  }
  syncBpmnUserTasks();
  const approverNodes = bpmnNodes.value.filter((node) => node.type === 'approver');
  if (approverNodes.length === 0) {
    message.warning('BPMN 流程至少需要一个用户任务节点');
    return false;
  }

  const nodeIds = new Set<string>();
  const approverIds = new Set(approverNodes.map((node) => node.id));
  for (const node of bpmnNodes.value) {
    if (!node.name.trim()) {
      message.warning(`用户任务 ${node.id} 缺少节点名称`);
      return false;
    }
    if (nodeIds.has(node.id)) {
      message.warning(`用户任务节点ID重复：${node.id}`);
      return false;
    }
    nodeIds.add(node.id);
    const config = nodeConfigs.value[node.id];
    if (!config) {
      message.warning(`请配置节点：${node.name}`);
      return false;
    }
    if (node.type === 'gateway') {
      if (!validateGatewayConfig(node, config, approverIds)) {
        return false;
      }
      continue;
    }
    if (needsAssigneeConfig(config) && !hasAssigneeConfig(config.assigneeJson)) {
      message.warning(`请选择“${node.name}”的审批人配置`);
      return false;
    }
  }
  return true;
}

function validateGatewayConfig(node: BpmnNode, config: NodeConfigDraft, approverIds: Set<string>) {
  const branchConfig = ensureBranchConfig(config);
  const branches = branchConfig.branches;
  if (branches.length < 2) {
    message.warning(`请为“${node.name}”至少配置一个条件分支和一个默认分支`);
    return false;
  }
  if (branches.filter((branch) => branch.defaultBranch).length !== 1) {
    message.warning(`请为“${node.name}”配置默认分支`);
    return false;
  }
  const branchIds = new Set<string>();
  const outgoingTargets = new Set(listGatewayOutgoingTargets(node.id));
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
    if (!branch.targetNodeId || !approverIds.has(branch.targetNodeId)) {
      message.warning(`请选择“${branch.branchName || node.name}”的目标审批节点`);
      return false;
    }
    if (!outgoingTargets.has(branch.targetNodeId)) {
      message.warning(`“${branch.branchName}”的目标节点必须是网关已连接的出线节点`);
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

/**
 * BPMN 设计器以图上的出线作为流程拓扑，侧栏配置只同步每条出线的安全条件表达式。
 */
function syncGatewayConditionExpressions() {
  const elementRegistry = modeler.value?.get('elementRegistry') as
    | BpmnElementRegistry
    | undefined;
  const modeling = modeler.value?.get('modeling') as BpmnModeling | undefined;
  const moddle = modeler.value?.get('moddle') as BpmnModdle | undefined;
  if (!elementRegistry || !modeling || !moddle) {
    return;
  }

  for (const node of bpmnNodes.value.filter((item) => item.type === 'gateway')) {
    const gateway = elementRegistry.get(node.id);
    const config = nodeConfigs.value[node.id];
    if (!gateway || !config?.branchConfig) {
      continue;
    }
    const defaultBranch = config.branchConfig.branches.find((branch) => branch.defaultBranch);
    let defaultFlow: BpmnElement | undefined;

    for (const flow of gateway.outgoing || []) {
      const targetNodeId = flow.businessObject?.targetRef?.id;
      const branch = config.branchConfig.branches.find(
        (item) => item.targetNodeId === targetNodeId,
      );
      if (!branch) {
        continue;
      }
      if (branch.defaultBranch) {
        defaultFlow = flow;
        modeling.updateProperties(flow, { conditionExpression: undefined });
        continue;
      }
      const conditionExpression = moddle.create('bpmn:FormalExpression', {
        body: `\${branch == '${branch.branchId}'}`,
      });
      modeling.updateProperties(flow, { conditionExpression });
    }

    const fallbackDefaultFlow = (gateway.outgoing || []).find(
      (flow) => flow.businessObject?.targetRef?.id === defaultBranch?.targetNodeId,
    );
    modeling.updateProperties(gateway, {
      default: defaultFlow?.businessObject || fallbackDefaultFlow?.businessObject,
    });
  }
}

function needsAssigneeConfig(config: NodeConfigDraft) {
  return ![
    'starter',
    'depart_leader',
    'starter_select',
    'starter_supervisor',
  ].includes(config.assigneeType);
}

function hasAssigneeConfig(value: Record<string, unknown>) {
  return Object.values(value || {}).some(
    (item) => Array.isArray(item) && item.length > 0,
  );
}

async function deleteRemovedNodeConfigs() {
  const activeNodeIds = new Set(bpmnNodes.value.map((node) => node.id));
  const removedConfigs = existingNodeConfigs.value.filter(
    (config) =>
      config.id
      && (config.nodeType === 'approver' || config.nodeType === 'gateway')
      && config.nodeId
      && !activeNodeIds.has(config.nodeId),
  );

  for (const config of removedConfigs) {
    await deleteWorkflowProcessNode(config.id!);
  }
}

async function saveNodeConfigs(processId: string) {
  for (const [index, node] of bpmnNodes.value.entries()) {
    const config = nodeConfigs.value[node.id];
    const current = existingNodeConfigs.value.find((item) => item.nodeId === node.id);
    if (node.type === 'gateway') {
      const gatewayConfig = config || buildNodeConfigDraft(current);
      await saveWorkflowProcessNode({
        id: gatewayConfig.id || current?.id,
        branchJson: JSON.stringify(ensureBranchConfig(gatewayConfig)),
        nodeId: node.id,
        nodeName: node.name,
        nodeType: 'gateway',
        processModelId: processId,
        sortOrder: (index + 1) * 10,
      });
      continue;
    }
    await saveWorkflowProcessNode({
      id: config?.id || current?.id,
      allowAddSign: config?.allowAddSign ? 1 : 0,
      allowReturn: config?.allowReturn ? 1 : 0,
      allowTransfer: config?.allowTransfer ? 1 : 0,
      approvalMode: config?.approvalMode || 'single',
      assigneeResolveMode: resolveAssigneeResolveMode(
        config?.assigneeResolveMode,
        config?.approvalMode,
      ),
      assigneeJson: JSON.stringify(config?.assigneeJson || {}),
      assigneeType: config?.assigneeType || 'starter',
      ccJson: JSON.stringify(config?.ccConfig || { events: [], targets: [] }),
      nodeId: node.id,
      nodeName: node.name,
      nodeType: 'approver',
      processModelId: processId,
      rejectPolicy: config?.rejectPolicy || 'terminate',
      sortOrder: (index + 1) * 10,
    });
  }
}

async function handleSubmit() {
  if (isPublished.value) {
    message.warning('非草稿流程不可直接修改，请新建版本后调整');
    return;
  }
  if (!currentProcess.value?.id || !modeler.value) {
    message.warning('请先保存流程基础信息');
    return;
  }
  if (!validateNodeConfigs()) {
    return;
  }

  try {
    drawerApi.lock();
    syncGatewayConditionExpressions();
    const { xml } = (await modeler.value.saveXML({
      format: true,
    })) as SaveXmlResult;
    if (!xml?.trim()) {
      message.warning('BPMN XML 不能为空');
      return;
    }
    await saveWorkflowProcess({
      ...currentProcess.value,
      bpmnXml: xml,
      designerType: 'bpmn',
    });
    await deleteRemovedNodeConfigs();
    await saveNodeConfigs(currentProcess.value.id);
    message.success('BPMN 设计已保存');
    emit('success');
    drawerApi.close();
  } finally {
    drawerApi.unlock();
  }
}

async function handleResetDefault() {
  if (!currentProcess.value || !modeler.value) {
    return;
  }
  await modeler.value.importXML(createDefaultBpmnXml(currentProcess.value));
  syncBpmnUserTasks();
  fitViewport();
}

function destroyModeler() {
  modeler.value?.destroy();
  modeler.value = undefined;
  importError.value = '';
  bpmnNodes.value = [];
  activeNodeId.value = '';
  nodeConfigs.value = {};
}

function updateDrawerState(loadingValue = false) {
  drawerApi.setState({
    footer: !isPublished.value,
    loading: loadingValue,
    title: drawerTitle.value,
  });
}

async function open(payload: DrawerPayload) {
  loading.value = true;
  importError.value = '';
  existingNodeConfigs.value = [];
  currentProcess.value = payload.record;
  updateDrawerState(true);
  drawerApi.setData(payload).open();

  try {
    currentProcess.value = payload.record.id
      ? await getWorkflowProcessById(payload.record.id)
      : payload.record;
    existingNodeConfigs.value = currentProcess.value.id
      ? await listWorkflowProcessNodes({
          queryParams: { processModelId: currentProcess.value.id },
          sortField: 'sort_order',
          sortOrder: 'asc',
        })
      : [];
    updateDrawerState(false);
    await syncModeler();
  } finally {
    loading.value = false;
    updateDrawerState(false);
  }
}

onBeforeUnmount(destroyModeler);

defineExpose({
  open,
});
</script>

<template>
  <Drawer>
    <div class="bpmn-designer">
      <div class="designer-toolbar">
        <Space>
          <Tag color="processing">BPMN</Tag>
          <span class="designer-subtitle">
            {{ currentProcess?.processKey }} · v{{ currentProcess?.version ?? 1 }}
          </span>
        </Space>
        <Space>
          <Button
            :disabled="isPublished || loading"
            @click="handleResetDefault"
          >
            重置默认图
          </Button>
        </Space>
      </div>

      <Alert
        v-if="isPublished"
        show-icon
        type="warning"
        message="非草稿流程不可直接修改，请新建版本后调整。"
      />
      <Alert
        v-if="importError"
        show-icon
        type="error"
        :message="importError"
      />

      <div class="designer-shell">
        <div
          ref="canvasRef"
          class="bpmn-canvas"
        ></div>

        <div class="node-config-panel">
          <div class="node-config-header">
            <div>
              <div class="node-config-title">节点配置</div>
              <div class="node-config-subtitle">
                BPMN 用户任务配置审批人，条件分支配置流转规则
              </div>
            </div>
            <Tag>{{ bpmnNodes.length }} 个节点</Tag>
          </div>

          <Empty
            v-if="bpmnNodes.length === 0"
            description="暂无用户任务节点"
          />
          <template v-else>
            <Form
              v-if="activeNode && activeNodeConfig"
              :key="activeNode.id"
              :model="activeNodeConfig"
              layout="vertical"
            >
              <FormItem label="节点ID">
                <Input
                  :value="activeNode.id"
                  disabled
                />
              </FormItem>
              <FormItem label="节点名称">
                <Input
                  :value="activeNode.name"
                  disabled
                />
              </FormItem>
              <template v-if="activeNode.type === 'approver'">
                <Tabs
                  class="node-config-tabs"
                  size="small"
                >
                  <Tabs.TabPane
                    key="assignee"
                    tab="审批人配置"
                  >
                    <WorkflowAssigneeSelector
                      v-model="activeNodeConfig.assigneeJson"
                      v-model:type="activeNodeConfig.assigneeType"
                      :disabled="isPublished"
                    />
                    <FormItem label="办理策略">
                      <RadioGroup
                        v-model:value="activeNodeConfig.approvalMode"
                        :disabled="isPublished"
                        :options="approvalModeOptions"
                        button-style="solid"
                        option-type="button"
                        @change="handleApprovalModeChange(activeNodeConfig)"
                      />
                    </FormItem>
                    <FormItem
                      v-if="activeNodeConfig.approvalMode !== 'single'"
                      label="执行人确定方式"
                    >
                      <RadioGroup
                        v-model:value="activeNodeConfig.assigneeResolveMode"
                        :disabled="isPublished"
                        :options="assigneeResolveModeOptions"
                        button-style="solid"
                        option-type="button"
                      />
                    </FormItem>
                  </Tabs.TabPane>
                  <Tabs.TabPane
                    key="cc"
                    tab="抄送配置"
                  >
                    <WorkflowCcConfigEditor
                      v-model="activeNodeConfig.ccConfig"
                      :disabled="isPublished"
                    />
                  </Tabs.TabPane>
                  <Tabs.TabPane
                    key="actions"
                    tab="节点动作"
                  >
                    <FormItem label="节点动作">
                      <Space wrap>
                        <Checkbox
                          v-model:checked="activeNodeConfig.allowTransfer"
                          :disabled="isPublished"
                        >
                          转办
                        </Checkbox>
                        <Checkbox
                          v-model:checked="activeNodeConfig.allowReturn"
                          :disabled="isPublished"
                        >
                          退回
                        </Checkbox>
                        <Checkbox
                          v-model:checked="activeNodeConfig.allowAddSign"
                          :disabled="isPublished"
                        >
                          加签
                        </Checkbox>
                      </Space>
                    </FormItem>
                  </Tabs.TabPane>
                </Tabs>
              </template>
              <template v-else>
                <div class="branch-list">
                  <div
                    v-for="(branch, branchIndex) in ensureBranchConfig(activeNodeConfig).branches"
                    :key="`${activeNode.id}_${branchIndex}`"
                    class="branch-card"
                  >
                    <div class="branch-card-header">
                      <Tag :color="branch.defaultBranch ? 'default' : 'purple'">
                        {{ branch.defaultBranch ? '默认' : '条件' }}
                      </Tag>
                      <Input
                        v-model:value="branch.branchName"
                        :disabled="isPublished"
                        class="branch-name-input"
                        placeholder="分支名称"
                      />
                      <Button
                        v-if="!branch.defaultBranch"
                        :disabled="isPublished"
                        danger
                        size="small"
                        type="link"
                        @click="handleRemoveBranch(activeNodeConfig, branchIndex)"
                      >
                        删除
                      </Button>
                    </div>
                    <div class="branch-grid">
                      <FormItem label="分支编码">
                        <Input
                          v-model:value="branch.branchId"
                          :disabled="isPublished || branch.defaultBranch"
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
                          :options="approverNodeOptions"
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
                    @click="handleAddBranch(activeNodeConfig)"
                  >
                    添加分支
                  </Button>
                </div>
              </template>
            </Form>
          </template>
        </div>
      </div>
    </div>
  </Drawer>
</template>

<style scoped>
.bpmn-designer {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
}

:global(.workflow-bpmn-designer-drawer) {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 110px);
  overflow: hidden;
}

.designer-toolbar {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.designer-subtitle {
  color: #6b7280;
  font-size: 13px;
}

.designer-shell {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 70%) minmax(380px, 30%);
  min-height: 0;
  overflow: hidden;
}

.bpmn-canvas {
  background: #fff;
  height: 100%;
  width: 100%;
}

.node-config-panel {
  background: #f8fafc;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding: 14px;
}

.node-config-header {
  align-items: flex-start;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.node-config-title {
  color: #111827;
  font-size: 15px;
  font-weight: 600;
}

.node-config-subtitle {
  color: #6b7280;
  font-size: 12px;
  margin-top: 2px;
}

.node-config-tabs {
  min-width: 0;
}

.node-config-tabs :deep(.ant-tabs-content-holder) {
  padding-top: 2px;
}

.branch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.branch-card {
  background: #fff;
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
  grid-template-columns: 1fr;
}

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-control,
.w-full {
  width: 100%;
}

@media (max-width: 900px) {
  .designer-shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .bpmn-canvas {
    height: 520px;
  }

  .node-config-panel {
    border-left: 0;
    border-top: 1px solid #e5e7eb;
    max-height: none;
  }
}

:deep(.djs-palette) {
  border-color: #d1d5db;
}

:deep(.djs-palette .entry[title='创建审批节点']) {
  color: #1677ff;
}

:deep(.djs-context-pad),
:deep(.djs-popup) {
  z-index: 20;
}
</style>
