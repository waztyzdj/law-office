import { requestClient } from '#/framework/api/request';
import {
  BaseApi,
  type BasePageReq,
  type BaseQueryReq,
} from '#/framework/api/base.api';

export interface WorkflowPageResult<T> {
  pageNum: number;
  pageSize: number;
  records: T[];
  total: number;
}

export interface WorkflowCategoryInfo {
  id?: string;
  parentId?: string;
  categoryCode?: string;
  categoryName?: string;
  sortOrder?: number;
  status?: string;
  remark?: string;
}

export interface WorkflowFormDefinitionInfo {
  id?: string;
  categoryId?: string;
  formKey?: string;
  formName?: string;
  optionJson?: string;
  publishedTime?: string;
  remark?: string;
  schemaJson?: string;
  status?: string;
  version?: number;
}

export interface WorkflowProcessModelInfo {
  id?: string;
  bpmnXml?: string;
  categoryId?: string;
  designerType?: string;
  flowableDeploymentId?: string;
  flowableProcessDefinitionId?: string;
  formDefinitionId?: string;
  nodeJson?: string;
  processKey?: string;
  processName?: string;
  publishedTime?: string;
  remark?: string;
  startScopeType?: string;
  status?: string;
  version?: number;
}

export interface WorkflowProcessPageReq extends BasePageReq {
  categoryId?: string;
  designerType?: string;
  processKey?: string;
  processName?: string;
  status?: string;
}

export interface WorkflowProcessNodeConfigInfo {
  id?: string;
  allowAddSign?: number;
  allowReturn?: number;
  allowTransfer?: number;
  assigneeJson?: string;
  assigneeType?: string;
  nodeId?: string;
  nodeName?: string;
  nodeType?: string;
  processModelId?: string;
  sortOrder?: number;
  tenantId?: string;
}

export interface WorkflowFieldPermissionInfo {
  id?: string;
  fieldKey?: string;
  nodeId?: string;
  permission?: string;
  processModelId?: string;
  requiredFlag?: number;
  tenantId?: string;
}

export interface AvailableProcessInfo {
  id?: string;
  categoryId?: string;
  designerType?: string;
  formDefinitionId?: string;
  formKey?: string;
  formName?: string;
  formVersion?: number;
  processKey?: string;
  processName?: string;
  processVersion?: number;
  publishedTime?: string;
  remark?: string;
  startScopeType?: string;
}

export interface AvailableProcessPageReq extends BasePageReq {
  categoryId?: string;
  designerType?: string;
  formName?: string;
  formVersion?: number;
  processKey?: string;
  processName?: string;
  processVersion?: number;
  publishedTimeGe?: string;
  publishedTimeLe?: string;
  startScopeType?: string;
}

export interface RuntimeTaskInfo {
  id?: string;
  assigneeRealname?: string;
  assigneeUserId?: string;
  assigneeUsername?: string;
  candidateAssigneeNames?: string;
  claimTime?: string;
  completeTime?: string;
  flowableTaskId?: string;
  instanceNo?: string;
  instanceTitle?: string;
  nodeId?: string;
  processInstanceId?: string;
  startTime?: string;
  starterRealname?: string;
  starterUserId?: string;
  starterUsername?: string;
  status?: string;
  taskName?: string;
  taskType?: string;
}

export interface RuntimeTaskPageReq extends BasePageReq {
  assigneeRealname?: string;
  completeTimeGe?: string;
  completeTimeLe?: string;
  instanceNo?: string;
  instanceTitle?: string;
  processInstanceId?: string;
  starterRealname?: string;
  startTimeGe?: string;
  startTimeLe?: string;
  status?: string;
  taskName?: string;
  taskType?: string;
}

export interface StartedInstanceInfo {
  id?: string;
  businessKey?: string;
  currentAssigneeNames?: string;
  currentTaskNames?: string;
  endTime?: string;
  formDefinitionId?: string;
  formInstanceId?: string;
  formName?: string;
  instanceNo?: string;
  instanceTitle?: string;
  processModelId?: string;
  processName?: string;
  startTime?: string;
  status?: string;
}

export interface StartedInstancePageReq extends BasePageReq {
  currentAssigneeNames?: string;
  currentTaskNames?: string;
  endTimeGe?: string;
  endTimeLe?: string;
  instanceNo?: string;
  instanceTitle?: string;
  processName?: string;
  startTimeGe?: string;
  startTimeLe?: string;
  status?: string;
}

export interface StartFormInfo {
  formDefinitionId?: string;
  formKey?: string;
  formName?: string;
  formVersion?: number;
  optionJson?: string;
  processModelId?: string;
  processName?: string;
  schemaJson?: string;
}

export interface StartProcessReq {
  businessKey?: string;
  formDataJson?: string;
  instanceTitle?: string;
  processModelId?: string;
}

export interface StartProcessResult {
  formInstanceId?: string;
  instanceNo?: string;
  processInstanceId?: string;
  status?: string;
}

export interface TaskActionReq {
  comment?: string;
  formDataJson?: string;
  targetNodeId?: string;
  targetUserId?: string;
  taskId?: string;
}

export interface TaskActionResult {
  action?: string;
  processInstanceId?: string;
  processStatus?: string;
  taskId?: string;
}

export interface TaskActionPermissionInfo {
  allowAddSign?: boolean;
  allowApprove?: boolean;
  allowReject?: boolean;
  allowReturn?: boolean;
  allowTransfer?: boolean;
}

export interface TaskReturnNodeInfo {
  nodeId?: string;
  nodeName?: string;
}

export interface RuntimeFieldPermissionInfo {
  fieldKey?: string;
  permission?: string;
  requiredFlag?: boolean;
}

export interface TaskFormInfo {
  actionPermissions?: TaskActionPermissionInfo;
  fieldPermissions?: RuntimeFieldPermissionInfo[];
  formDataJson?: string;
  formDefinitionId?: string;
  formInstanceId?: string;
  formKey?: string;
  formName?: string;
  formVersion?: number;
  instanceNo?: string;
  instanceTitle?: string;
  nodeId?: string;
  optionJson?: string;
  parentTaskId?: string;
  processInstanceId?: string;
  returnNodes?: TaskReturnNodeInfo[];
  schemaJson?: string;
  taskId?: string;
  taskName?: string;
  taskType?: string;
}

export interface FormInstanceInfo {
  formDataJson?: string;
  formDefinitionId?: string;
  formKey?: string;
  formName?: string;
  formOptionSnapshotJson?: string;
  formSchemaSnapshotJson?: string;
  formVersion?: number;
  id?: string;
  processInstanceId?: string;
  status?: string;
  submittedTime?: string;
}

export interface ProcessInstanceInfo {
  currentAssigneeNames?: string;
  currentTaskNames?: string;
  endTime?: string;
  formDefinitionId?: string;
  formInstanceId?: string;
  id?: string;
  instanceNo?: string;
  instanceTitle?: string;
  processModelId?: string;
  starterRealname?: string;
  starterUserId?: string;
  starterUsername?: string;
  status?: string;
  startTime?: string;
}

export interface OperationRecordInfo {
  action?: string;
  comment?: string;
  formDataSnapshotJson?: string;
  id?: string;
  nodeId?: string;
  nodeName?: string;
  operateTime?: string;
  operatorRealname?: string;
  operatorUserId?: string;
  operatorUsername?: string;
  processInstanceId?: string;
  targetNodeId?: string;
  targetNodeName?: string;
  targetRealname?: string;
  targetUserId?: string;
  targetUsername?: string;
  taskId?: string;
}

export interface InstanceDetailInfo {
  currentTasks?: RuntimeTaskInfo[];
  formInstance?: FormInstanceInfo;
  processInstance?: ProcessInstanceInfo;
  records?: OperationRecordInfo[];
}

const categoryApi = new BaseApi('/workflow/admin/category');
const fieldPermissionApi = new BaseApi('/workflow/admin/field-permission');
const formApi = new BaseApi('/workflow/admin/form');
const nodeApi = new BaseApi('/workflow/admin/node');
const processApi = new BaseApi('/workflow/admin/process');

export const pageWorkflowCategories = (params: BasePageReq) =>
  categoryApi.page<WorkflowCategoryInfo>(params);
export const listWorkflowCategories = (params?: BaseQueryReq) =>
  categoryApi.list<WorkflowCategoryInfo>(params);
export const getWorkflowCategoryById = (id: string) =>
  categoryApi.getById<WorkflowCategoryInfo>({ id });
export const saveWorkflowCategory = (data: WorkflowCategoryInfo) =>
  categoryApi.save<WorkflowCategoryInfo>(data);
export const deleteWorkflowCategory = (id: string) =>
  categoryApi.delete({ id });

export const pageWorkflowForms = (params: BasePageReq) =>
  formApi.page<WorkflowFormDefinitionInfo>(params);
export const pageLatestWorkflowForms = (params: BasePageReq) =>
  requestClient.post<WorkflowPageResult<WorkflowFormDefinitionInfo>>(
    '/workflow/admin/form/latest-page',
    params,
  );
export const listWorkflowForms = (params?: BaseQueryReq) =>
  formApi.list<WorkflowFormDefinitionInfo>(params);
export const getWorkflowFormById = (id: string) =>
  formApi.getById<WorkflowFormDefinitionInfo>({ id });
export const saveWorkflowForm = (data: WorkflowFormDefinitionInfo) =>
  formApi.save<WorkflowFormDefinitionInfo>(data);
export const deleteWorkflowForm = (id: string) =>
  formApi.delete({ id });
export const publishWorkflowForm = (id: string) =>
  requestClient.post<WorkflowFormDefinitionInfo>('/workflow/admin/form/publish', {
    id,
  });
export const copyWorkflowFormAsDraft = (id: string) =>
  requestClient.post<WorkflowFormDefinitionInfo>(
    '/workflow/admin/form/copy-as-draft',
    { id },
  );
export const listWorkflowFormHistory = (id: string) =>
  requestClient.post<WorkflowFormDefinitionInfo[]>(
    '/workflow/admin/form/history',
    { id },
  );

export const pageWorkflowProcesses = (params: WorkflowProcessPageReq) =>
  processApi.page<WorkflowProcessModelInfo>(params);
export const pageLatestWorkflowProcesses = (params: WorkflowProcessPageReq) =>
  requestClient.post<WorkflowPageResult<WorkflowProcessModelInfo>>(
    '/workflow/admin/process/latest-page',
    params,
  );
export const listWorkflowProcesses = (params?: BaseQueryReq) =>
  processApi.list<WorkflowProcessModelInfo>(params);
export const getWorkflowProcessById = (id: string) =>
  processApi.getById<WorkflowProcessModelInfo>({ id });
export const saveWorkflowProcess = (data: WorkflowProcessModelInfo) =>
  processApi.save<WorkflowProcessModelInfo>(data);
export const deleteWorkflowProcess = (id: string) =>
  processApi.delete({ id });
export const publishWorkflowProcess = (id: string) =>
  requestClient.post<WorkflowProcessModelInfo>(
    '/workflow/admin/process/publish',
    { id },
  );
export const copyWorkflowProcessAsDraft = (id: string) =>
  requestClient.post<WorkflowProcessModelInfo>(
    '/workflow/admin/process/copy-as-draft',
    { id },
  );
export const listWorkflowProcessHistory = (id: string) =>
  requestClient.post<WorkflowProcessModelInfo[]>(
    '/workflow/admin/process/history',
    { id },
  );
export const listWorkflowProcessNodes = (params?: BaseQueryReq) =>
  nodeApi.list<WorkflowProcessNodeConfigInfo>(params);
export const saveWorkflowProcessNode = (data: WorkflowProcessNodeConfigInfo) =>
  nodeApi.save<WorkflowProcessNodeConfigInfo>(data);
export const deleteWorkflowProcessNode = (id: string) =>
  nodeApi.delete({ id });
export const listWorkflowFieldPermissions = (params?: BaseQueryReq) =>
  fieldPermissionApi.list<WorkflowFieldPermissionInfo>(params);
export const saveWorkflowFieldPermission = (data: WorkflowFieldPermissionInfo) =>
  fieldPermissionApi.save<WorkflowFieldPermissionInfo>(data);
export const deleteWorkflowFieldPermission = (id: string) =>
  fieldPermissionApi.delete({ id });

export const pageAvailableProcesses = (params: AvailableProcessPageReq) =>
  requestClient.post<WorkflowPageResult<AvailableProcessInfo>>(
    '/workflow/available/page',
    normalizeAvailableProcessPageReq(params),
  );
export const getStartForm = (processModelId: string) =>
  requestClient.post<StartFormInfo>('/workflow/start/form', { processModelId });
export const startWorkflowProcess = (data: StartProcessReq) =>
  requestClient.post<StartProcessResult>('/workflow/start', data);
export const saveWorkflowStartDraft = (data: StartProcessReq) =>
  requestClient.post<StartProcessResult>('/workflow/start/draft', data);
export const pageTodoTasks = (params: RuntimeTaskPageReq) =>
  requestClient.post<WorkflowPageResult<RuntimeTaskInfo>>(
    '/workflow/todo/page',
    normalizeRuntimeTaskPageReq(params),
  );
export const pageDoneTasks = (params: RuntimeTaskPageReq) =>
  requestClient.post<WorkflowPageResult<RuntimeTaskInfo>>(
    '/workflow/done/page',
    normalizeRuntimeTaskPageReq(params),
  );
export const pageStartedInstances = (params: StartedInstancePageReq) =>
  requestClient.post<WorkflowPageResult<StartedInstanceInfo>>(
    '/workflow/started/page',
    normalizeStartedInstancePageReq(params),
  );
export const getWorkflowInstanceDetail = (id: string) =>
  requestClient.post<InstanceDetailInfo>('/workflow/instance/detail', { id });
export const listWorkflowInstanceRecords = (id: string) =>
  requestClient.post<OperationRecordInfo[]>('/workflow/instance/records', {
    id,
  });
export const getWorkflowTaskForm = (taskId: string) =>
  requestClient.post<TaskFormInfo>('/workflow/task/form', { taskId });
export const approveWorkflowTask = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/approve', data);
export const saveWorkflowStartDraftTask = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/start-draft/save', data);
export const submitWorkflowStartDraft = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/start-draft/submit', data);
export const rejectWorkflowTask = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/reject', data);
export const transferWorkflowTask = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/transfer', data);
export const returnWorkflowTask = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/return', data);
export const addSignWorkflowTask = (data: TaskActionReq) =>
  requestClient.post<TaskActionResult>('/workflow/task/add-sign', data);

function normalizeAvailableProcessPageReq(
  params: AvailableProcessPageReq,
): AvailableProcessPageReq {
  const queryParams = params.queryParams ?? {};
  return {
    ...params,
    categoryId: params.categoryId ?? queryParams.categoryId_eq,
    designerType: params.designerType ?? queryParams.designerType_eq,
    formName: params.formName ?? queryParams.formName_like,
    formVersion: params.formVersion ?? queryParams.formVersion_eq,
    processKey: params.processKey ?? queryParams.processKey_like,
    processName: params.processName ?? queryParams.processName_like,
    processVersion: params.processVersion ?? queryParams.processVersion_eq,
    publishedTimeGe:
      params.publishedTimeGe ??
      queryParams.publishedTime_ge ??
      queryParams.publishedTime_gt ??
      queryParams.publishedTime_eq,
    publishedTimeLe:
      params.publishedTimeLe ??
      queryParams.publishedTime_le ??
      queryParams.publishedTime_lt ??
      queryParams.publishedTime_eq,
    startScopeType: params.startScopeType ?? queryParams.startScopeType_eq,
  };
}

function normalizeRuntimeTaskPageReq(
  params: RuntimeTaskPageReq,
): RuntimeTaskPageReq {
  const queryParams = params.queryParams ?? {};
  return {
    ...params,
    assigneeRealname:
      params.assigneeRealname ?? queryParams.assigneeRealname_like,
    completeTimeGe:
      params.completeTimeGe ??
      queryParams.completeTime_ge ??
      queryParams.completeTime_gt ??
      queryParams.completeTime_eq,
    completeTimeLe:
      params.completeTimeLe ??
      queryParams.completeTime_le ??
      queryParams.completeTime_lt ??
      queryParams.completeTime_eq,
    instanceNo: params.instanceNo ?? queryParams.instanceNo_like,
    instanceTitle: params.instanceTitle ?? queryParams.instanceTitle_like,
    starterRealname: params.starterRealname ?? queryParams.starterRealname_like,
    startTimeGe:
      params.startTimeGe ??
      queryParams.startTime_ge ??
      queryParams.startTime_gt ??
      queryParams.startTime_eq,
    startTimeLe:
      params.startTimeLe ??
      queryParams.startTime_le ??
      queryParams.startTime_lt ??
      queryParams.startTime_eq,
    taskName: params.taskName ?? queryParams.taskName_like,
    status: params.status ?? queryParams.status_eq,
    taskType: params.taskType ?? queryParams.taskType_eq,
  };
}

function normalizeStartedInstancePageReq(
  params: StartedInstancePageReq,
): StartedInstancePageReq {
  const queryParams = params.queryParams ?? {};
  return {
    ...params,
    currentAssigneeNames:
      params.currentAssigneeNames ?? queryParams.currentAssigneeNames_like,
    currentTaskNames:
      params.currentTaskNames ?? queryParams.currentTaskNames_like,
    endTimeGe:
      params.endTimeGe ??
      queryParams.endTime_ge ??
      queryParams.endTime_gt ??
      queryParams.endTime_eq,
    endTimeLe:
      params.endTimeLe ??
      queryParams.endTime_le ??
      queryParams.endTime_lt ??
      queryParams.endTime_eq,
    instanceNo: params.instanceNo ?? queryParams.instanceNo_like,
    instanceTitle: params.instanceTitle ?? queryParams.instanceTitle_like,
    processName: params.processName ?? queryParams.processName_like,
    startTimeGe:
      params.startTimeGe ??
      queryParams.startTime_ge ??
      queryParams.startTime_gt ??
      queryParams.startTime_eq,
    startTimeLe:
      params.startTimeLe ??
      queryParams.startTime_le ??
      queryParams.startTime_lt ??
      queryParams.startTime_eq,
    status: params.status ?? queryParams.status_eq,
  };
}
