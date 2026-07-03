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

export interface WorkflowFormTemplateCopyReq {
  categoryId?: string;
  formKey?: string;
  formName?: string;
  remark?: string;
  sourceFormDefinitionId?: string;
}

export interface WorkflowProcessModelInfo {
  id?: string;
  bpmnXml?: string;
  categoryId?: string;
  designerType?: string;
  flowableDeploymentId?: string;
  flowableProcessDefinitionId?: string;
  formDefinitionId?: string;
  formKey?: string;
  formName?: string;
  formVersion?: number;
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

export interface WorkflowProcessTemplateCopyReq {
  categoryId?: string;
  formDefinitionId?: string;
  processKey?: string;
  processName?: string;
  remark?: string;
  sourceProcessModelId?: string;
}

export interface WorkflowProcessNodeConfigInfo {
  id?: string;
  allowAddSign?: number;
  allowReturn?: number;
  allowTransfer?: number;
  approvalMode?: string;
  assigneeResolveMode?: string;
  assigneeJson?: string;
  assigneeType?: string;
  attachmentJson?: string;
  branchJson?: string;
  ccJson?: string;
  nodeId?: string;
  nodeName?: string;
  nodeType?: string;
  processModelId?: string;
  rejectPolicy?: string;
  sortOrder?: number;
  tenantId?: string;
  timeoutJson?: null | string;
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
  approvalMode?: string;
  assigneeRealname?: string;
  assigneeUserId?: string;
  assigneeUsername?: string;
  candidateAssigneeNames?: string;
  claimTime?: string;
  completeTime?: string;
  dueTime?: string;
  flowableTaskId?: string;
  groupCompleted?: number;
  groupTotal?: number;
  instanceNo?: string;
  instanceTitle?: string;
  lastRemindTime?: string;
  nodeId?: string;
  processInstanceId?: string;
  remindCount?: number;
  startTime?: string;
  starterRealname?: string;
  starterUserId?: string;
  starterUsername?: string;
  status?: string;
  taskGroupId?: string;
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
  canUrge?: boolean;
  canWithdraw?: boolean;
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

export interface AdminMonitorInstanceInfo {
  archived?: boolean;
  canArchive?: boolean;
  canMaintain?: boolean;
  categoryId?: string;
  currentAssigneeNames?: string;
  currentTaskNames?: string;
  endTime?: string;
  formDefinitionId?: string;
  formInstanceId?: string;
  id?: string;
  instanceNo?: string;
  instanceTitle?: string;
  processModelId?: string;
  processKey?: string;
  processName?: string;
  processVersion?: number;
  starterRealname?: string;
  starterUserId?: string;
  starterUsername?: string;
  status?: string;
  startTime?: string;
  todoTaskCount?: number;
  updateTime?: string;
}

export interface AdminMonitorPageReq extends BasePageReq {
  archiveReason?: string;
  categoryId?: string;
  currentAssigneeNames?: string;
  currentTaskNames?: string;
  instanceNo?: string;
  instanceTitle?: string;
  processKey?: string;
  processName?: string;
  processVersion?: number;
  starterRealname?: string;
  startTimeGe?: string;
  startTimeLe?: string;
  status?: string;
  updateTimeGe?: string;
  updateTimeLe?: string;
}

export interface AdminMonitorActionReq {
  operationReason?: string;
  processInstanceId?: string;
  targetUserId?: string;
  taskId?: string;
}

export interface AdminOperationRecordInfo {
  afterSnapshotJson?: string;
  beforeSnapshotJson?: string;
  createTime?: string;
  errorMessage?: string;
  id?: string;
  operateTime?: string;
  operationReason?: string;
  operationType?: string;
  operatorRealname?: string;
  operatorUserId?: string;
  operatorUsername?: string;
  processInstanceId?: string;
  status?: string;
  taskId?: string;
}

export interface AdminMonitorDetailInfo {
  adminOperationRecords?: AdminOperationRecordInfo[];
  detail?: InstanceDetailInfo;
}

export type ArchiveTreeNodeType = 'all' | 'category' | 'process';

export interface ArchiveTreeNodeInfo {
  categoryId?: string;
  children?: ArchiveTreeNodeInfo[];
  key?: string;
  processKey?: string;
  processModelId?: string;
  processName?: string;
  title?: string;
  type?: ArchiveTreeNodeType;
}

export interface ArchiveRecordInfo {
  archiveReason?: string;
  archiveSource?: string;
  archiveTime?: string;
  archiverRealname?: string;
  archiverUserId?: string;
  archiverUsername?: string;
  categoryId?: string;
  categoryName?: string;
  formDefinitionId?: string;
  formInstanceId?: string;
  id?: string;
  instanceNo?: string;
  instanceStatus?: string;
  instanceTitle?: string;
  processEndTime?: string;
  processInstanceId?: string;
  processKey?: string;
  processModelId?: string;
  processName?: string;
  processStartTime?: string;
  processVersion?: number;
  starterRealname?: string;
  starterUserId?: string;
  starterUsername?: string;
}

export interface ArchivePageReq extends BasePageReq {
  archiveReason?: string;
  archiveSource?: string;
  archiveTimeGe?: string;
  archiveTimeLe?: string;
  categoryId?: string;
  instanceNo?: string;
  instanceStatus?: string;
  instanceTitle?: string;
  processEndTimeGe?: string;
  processEndTimeLe?: string;
  processKey?: string;
  processName?: string;
  processVersion?: number;
  processStartTimeGe?: string;
  processStartTimeLe?: string;
  starterRealname?: string;
}

export interface ArchiveActionReq {
  archiveReason?: string;
  id?: string;
  processInstanceId?: string;
  processInstanceIds?: string[];
}

export interface WorkflowCcRecordInfo {
  id?: string;
  createTime?: string;
  instanceNo?: string;
  instanceTitle?: string;
  messageId?: string;
  nodeId?: string;
  nodeName?: string;
  processInstanceId?: string;
  processModelId?: string;
  processName?: string;
  processStatus?: string;
  readTime?: string;
  receiverRealname?: string;
  receiverUserId?: string;
  receiverUsername?: string;
  remark?: string;
  sourceId?: string;
  sourceType?: string;
  starterRealname?: string;
  starterUserId?: string;
  starterUsername?: string;
  status?: string;
  triggerAction?: string;
}

export interface WorkflowCcPageReq extends BasePageReq {
  createTimeGe?: string;
  createTimeLe?: string;
  instanceTitle?: string;
  processInstanceId?: string;
  status?: string;
}

export interface WorkflowCcSendReq {
  processInstanceId?: string;
  receiverUserIds?: string[];
}

export interface BranchRecordInfo {
  branchId?: string;
  branchName?: string;
  conditionSnapshotJson?: string;
  formDataSnapshotJson?: string;
  id?: string;
  matchedTime?: string;
  processInstanceId?: string;
  processModelId?: string;
  sourceNodeId?: string;
  sourceNodeName?: string;
  targetNodeId?: string;
  targetNodeName?: string;
}

export type WorkflowAttachmentSource = 'comment' | 'start' | 'task';

export interface WorkflowAttachmentInfo {
  attachmentSource?: WorkflowAttachmentSource | string;
  createTime?: string;
  fileId?: string;
  fileName?: string;
  fileRelationId?: string;
  fileSize?: number;
  fileType?: string;
  id?: string;
  nodeId?: string;
  nodeName?: string;
  processInstanceId?: string;
  remark?: string;
  sortOrder?: number;
  status?: string;
  taskId?: string;
  uploaderRealname?: string;
  uploaderUserId?: string;
  uploaderUsername?: string;
}

export interface WorkflowAttachmentBindReq {
  attachmentSource?: WorkflowAttachmentSource;
  fileId?: string;
  nodeId?: string;
  nodeName?: string;
  processInstanceId?: string;
  remark?: string;
  taskId?: string;
}

export interface StartFormInfo {
  assigneeSelectNodes?: AssigneeSelectNodeInfo[];
  fieldPermissions?: RuntimeFieldPermissionInfo[];
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
  selectedAssignees?: SelectedAssigneeReq[];
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
  selectedAssignees?: SelectedAssigneeReq[];
  targetNodeId?: string;
  targetUserId?: string;
  taskId?: string;
}

export interface SelectedAssigneeReq {
  nodeId?: string;
  userIds?: string[];
}

export interface AssigneePreviewReq {
  formDataJson?: string;
  processModelId?: string;
  taskId?: string;
}

export interface AssigneeOptionInfo {
  displayName?: string;
  realname?: string;
  sourceId?: string;
  sourceType?: string;
  userId?: string;
  username?: string;
}

export interface AssigneeSelectNodeInfo {
  assigneeType?: string;
  fallback?: boolean;
  nodeId?: string;
  nodeName?: string;
  options?: AssigneeOptionInfo[];
  required?: boolean;
  selectType?: string;
  warningMessage?: string;
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
  approvalMode?: string;
  assigneeSelectNodes?: AssigneeSelectNodeInfo[];
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
  taskGroupId?: string;
  groupCompleted?: number;
  groupTotal?: number;
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
  canUrge?: boolean;
  canWithdraw?: boolean;
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
  ccRecords?: WorkflowCcRecordInfo[];
  currentTasks?: RuntimeTaskInfo[];
  formInstance?: FormInstanceInfo;
  processInstance?: ProcessInstanceInfo;
  records?: OperationRecordInfo[];
}

export interface InstanceDiagramInfo {
  bpmnXml?: string;
  branchRecords?: BranchRecordInfo[];
  operationRecords?: OperationRecordInfo[];
  processInstanceId?: string;
  processModelId?: string;
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
export const copyWorkflowFormTemplate = (data: WorkflowFormTemplateCopyReq) =>
  requestClient.post<WorkflowFormDefinitionInfo>(
    '/workflow/admin/form/copy-template',
    data,
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
export const copyWorkflowProcessTemplate = (
  data: WorkflowProcessTemplateCopyReq,
) =>
  requestClient.post<WorkflowProcessModelInfo>(
    '/workflow/admin/process/copy-template',
    data,
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
export const pageAdminMonitorInstances = (params: AdminMonitorPageReq) =>
  requestClient.post<WorkflowPageResult<AdminMonitorInstanceInfo>>(
    '/workflow/admin/monitor/page',
    normalizeAdminMonitorPageReq(params),
  );
export const getAdminMonitorDetail = (processInstanceId: string) =>
  requestClient.post<AdminMonitorDetailInfo>('/workflow/admin/monitor/detail', {
    processInstanceId,
  });
export const reassignAdminMonitorTask = (data: AdminMonitorActionReq) =>
  requestClient.post<AdminOperationRecordInfo>(
    '/workflow/admin/monitor/reassign',
    data,
  );
export const terminateAdminMonitorInstance = (data: AdminMonitorActionReq) =>
  requestClient.post<AdminOperationRecordInfo>(
    '/workflow/admin/monitor/terminate',
    data,
  );
export const resendAdminMonitorNotice = (data: AdminMonitorActionReq) =>
  requestClient.post<AdminOperationRecordInfo>(
    '/workflow/admin/monitor/resend-notice',
    data,
  );
export const archiveAdminMonitorInstance = (data: ArchiveActionReq) =>
  requestClient.post<ArchiveRecordInfo>('/workflow/admin/monitor/archive', data);
export const batchArchiveAdminMonitorInstances = (data: ArchiveActionReq) =>
  requestClient.post<ArchiveRecordInfo[]>(
    '/workflow/admin/monitor/batch-archive',
    data,
  );
export const batchArchiveAdminMonitorByQuery = (params: AdminMonitorPageReq) =>
  requestClient.post<ArchiveRecordInfo[]>(
    '/workflow/admin/monitor/batch-archive-by-query',
    normalizeAdminMonitorPageReq(params),
  );
export const getWorkflowArchiveTree = () =>
  requestClient.post<ArchiveTreeNodeInfo[]>('/workflow/admin/archive/tree');
export const pageWorkflowArchivedRecords = (params: ArchivePageReq) =>
  requestClient.post<WorkflowPageResult<ArchiveRecordInfo>>(
    '/workflow/admin/archive/archived-page',
    normalizeArchivePageReq(params),
  );
export const pageWorkflowUnarchivedRecords = (params: ArchivePageReq) =>
  requestClient.post<WorkflowPageResult<ArchiveRecordInfo>>(
    '/workflow/admin/archive/unarchived-page',
    normalizeArchivePageReq(params),
  );
export const getWorkflowArchiveDetail = (processInstanceId: string) =>
  requestClient.post<AdminMonitorDetailInfo>('/workflow/admin/archive/detail', {
    processInstanceId,
  });
export const getWorkflowArchiveDiagram = (processInstanceId: string) =>
  requestClient.post<InstanceDiagramInfo>('/workflow/admin/archive/diagram', {
    processInstanceId,
  });
export const listWorkflowArchiveAttachments = (processInstanceId: string) =>
  requestClient.post<WorkflowAttachmentInfo[]>(
    '/workflow/admin/archive/attachment/list',
    { processInstanceId },
  );
export const downloadWorkflowArchiveAttachment = (id: string) =>
  requestClient.download<Blob>('/workflow/admin/archive/attachment/download', {
    data: { id },
    method: 'POST',
  });
export const downloadWorkflowArchiveAttachmentPackage = (processInstanceId: string) =>
  requestClient.download<Blob>(
    '/workflow/admin/archive/attachment/download-all',
    {
      data: { processInstanceId },
      method: 'POST',
    },
  );
export const archiveWorkflowInstance = (data: ArchiveActionReq) =>
  requestClient.post<ArchiveRecordInfo>('/workflow/admin/archive/archive', data);
export const batchArchiveWorkflowInstances = (data: ArchiveActionReq) =>
  requestClient.post<ArchiveRecordInfo[]>(
    '/workflow/admin/archive/batch-archive',
    data,
  );
export const batchArchiveWorkflowByQuery = (params: ArchivePageReq) =>
  requestClient.post<ArchiveRecordInfo[]>(
    '/workflow/admin/archive/batch-archive-by-query',
    normalizeArchivePageReq(params),
  );
export const downloadWorkflowArchivePackage = (processInstanceId: string) =>
  requestClient.download<Blob>('/workflow/admin/archive/download', {
    data: { processInstanceId },
    method: 'POST',
  });
export const pageWorkflowCcRecords = (params: WorkflowCcPageReq) =>
  requestClient.post<WorkflowPageResult<WorkflowCcRecordInfo>>(
    '/workflow/cc/page',
    normalizeWorkflowCcPageReq(params),
  );
export const markWorkflowCcRead = (id: string) =>
  requestClient.post<WorkflowCcRecordInfo>('/workflow/cc/read', { id });
export const sendWorkflowCc = (params: WorkflowCcSendReq) =>
  requestClient.post<WorkflowCcRecordInfo[]>('/workflow/cc/send', params);
export const listWorkflowAttachments = (processInstanceId: string) =>
  requestClient.post<WorkflowAttachmentInfo[]>('/workflow/attachment/list', {
    id: processInstanceId,
  });
export const bindWorkflowAttachment = (data: WorkflowAttachmentBindReq) =>
  requestClient.post<WorkflowAttachmentInfo>('/workflow/attachment/bind', data);
export const deleteWorkflowAttachment = (id: string) =>
  requestClient.post<void>('/workflow/attachment/delete', { id });
export const downloadWorkflowAttachment = (id: string) =>
  requestClient.download<Blob>(`/workflow/attachment/download/${id}`);
export const downloadWorkflowAttachmentPackage = (id: string) =>
  requestClient.download<Blob>('/workflow/attachment/download-all', {
    data: { id },
    method: 'POST',
  });
export const downloadWorkflowInstancePackage = (id: string) =>
  requestClient.download<Blob>('/workflow/instance/download/package', {
    data: { id },
    method: 'POST',
  });
export const getWorkflowInstanceDetail = (id: string) =>
  requestClient.post<InstanceDetailInfo>('/workflow/instance/detail', { id });
export const getWorkflowInstanceDiagram = (id: string) =>
  requestClient.post<InstanceDiagramInfo>('/workflow/instance/diagram', { id });
export const listWorkflowInstanceRecords = (id: string) =>
  requestClient.post<OperationRecordInfo[]>('/workflow/instance/records', {
    id,
  });
export const getWorkflowTaskForm = (taskId: string) =>
  requestClient.post<TaskFormInfo>('/workflow/task/form', { taskId });
export const previewNextAssigneeSelectNodes = (data: AssigneePreviewReq) =>
  requestClient.post<AssigneeSelectNodeInfo[]>('/workflow/assignee/preview', data);
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
export const withdrawWorkflowInstance = (id: string) =>
  requestClient.post<TaskActionResult>('/workflow/task/withdraw', { id });
export const urgeWorkflowInstance = (processInstanceId: string, remark?: string) =>
  requestClient.post('/workflow/task/urge', { processInstanceId, remark });

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

function normalizeAdminMonitorPageReq(
  params: AdminMonitorPageReq,
): AdminMonitorPageReq {
  const queryParams = params.queryParams ?? {};
  return {
    ...params,
    currentAssigneeNames:
      params.currentAssigneeNames ?? queryParams.currentAssigneeNames_like,
    currentTaskNames:
      params.currentTaskNames ?? queryParams.currentTaskNames_like,
    categoryId: params.categoryId ?? queryParams.categoryId_eq,
    instanceNo: params.instanceNo ?? queryParams.instanceNo_like,
    instanceTitle: params.instanceTitle ?? queryParams.instanceTitle_like,
    processKey: params.processKey ?? queryParams.processKey_eq,
    processName: params.processName ?? queryParams.processName_like,
    processVersion: normalizeVersionFilter(
      params.processVersion ??
      queryParams.processVersion_eq ??
      queryParams.processVersion_like,
    ),
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
    status: params.status ?? queryParams.status_eq,
    updateTimeGe:
      params.updateTimeGe ??
      queryParams.updateTime_ge ??
      queryParams.updateTime_gt ??
      queryParams.updateTime_eq,
    updateTimeLe:
      params.updateTimeLe ??
      queryParams.updateTime_le ??
      queryParams.updateTime_lt ??
      queryParams.updateTime_eq,
  };
}

function normalizeArchivePageReq(params: ArchivePageReq): ArchivePageReq {
  const queryParams = params.queryParams ?? {};
  return {
    ...params,
    archiveSource: params.archiveSource ?? queryParams.archiveSource_eq,
    archiveTimeGe:
      params.archiveTimeGe ??
      queryParams.archiveTime_ge ??
      queryParams.archiveTime_gt ??
      queryParams.archiveTime_eq,
    archiveTimeLe:
      params.archiveTimeLe ??
      queryParams.archiveTime_le ??
      queryParams.archiveTime_lt ??
      queryParams.archiveTime_eq,
    categoryId: params.categoryId ?? queryParams.categoryId_eq,
    instanceNo: params.instanceNo ?? queryParams.instanceNo_like,
    instanceStatus: params.instanceStatus ?? queryParams.instanceStatus_eq,
    instanceTitle: params.instanceTitle ?? queryParams.instanceTitle_like,
    processEndTimeGe:
      params.processEndTimeGe ??
      queryParams.processEndTime_ge ??
      queryParams.processEndTime_gt ??
      queryParams.processEndTime_eq,
    processEndTimeLe:
      params.processEndTimeLe ??
      queryParams.processEndTime_le ??
      queryParams.processEndTime_lt ??
      queryParams.processEndTime_eq,
    processKey: params.processKey ?? queryParams.processKey_eq,
    processName: params.processName ?? queryParams.processName_like,
    processVersion: normalizeVersionFilter(
      params.processVersion ??
      queryParams.processVersion_eq ??
      queryParams.processVersion_like,
    ),
    processStartTimeGe:
      params.processStartTimeGe ??
      queryParams.processStartTime_ge ??
      queryParams.processStartTime_gt ??
      queryParams.processStartTime_eq,
    processStartTimeLe:
      params.processStartTimeLe ??
      queryParams.processStartTime_le ??
      queryParams.processStartTime_lt ??
      queryParams.processStartTime_eq,
    starterRealname: params.starterRealname ?? queryParams.starterRealname_like,
  };
}

function normalizeVersionFilter(value: unknown): number | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined;
  }
  const text = String(value).trim().replace(/^v/i, '');
  if (!/^\d+$/.test(text)) {
    return undefined;
  }
  return Number(text);
}

function normalizeWorkflowCcPageReq(params: WorkflowCcPageReq): WorkflowCcPageReq {
  const queryParams = params.queryParams ?? {};
  return {
    ...params,
    createTimeGe: params.createTimeGe ?? queryParams.createTime_ge,
    createTimeLe: params.createTimeLe ?? queryParams.createTime_le,
    instanceTitle: params.instanceTitle ?? queryParams.instanceTitle_like,
    status: params.status ?? queryParams.status_eq,
  };
}
