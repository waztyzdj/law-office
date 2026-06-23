package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.service.IRuntimeViewAssemblerService;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.CcRecordVO;
import com.lawoffice.workflow.vo.FormInstanceVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.ProcessInstanceVO;
import com.lawoffice.workflow.vo.RuntimeFieldPermissionVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskActionPermissionVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import com.lawoffice.workflow.vo.TaskReturnNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeViewAssemblerServiceImpl implements IRuntimeViewAssemblerService {

    private final ProcessModelMapper processModelMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final ReminderRecordMapper reminderRecordMapper;
    private final IAssigneeResolveService assigneeResolveService;
    private final IProcessNodeConfigService processNodeConfigService;

    public RuntimeViewAssemblerServiceImpl(ProcessModelMapper processModelMapper,
            FormDefinitionMapper formDefinitionMapper,
            FormInstanceMapper formInstanceMapper,
            OperationRecordMapper operationRecordMapper,
            ProcessInstanceMapper processInstanceMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            ReminderRecordMapper reminderRecordMapper,
            IAssigneeResolveService assigneeResolveService,
            IProcessNodeConfigService processNodeConfigService) {
        this.processModelMapper = processModelMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.formInstanceMapper = formInstanceMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.reminderRecordMapper = reminderRecordMapper;
        this.assigneeResolveService = assigneeResolveService;
        this.processNodeConfigService = processNodeConfigService;
    }

    @Override
    public List<AvailableProcessVO> buildAvailableProcessRecords(List<ProcessModel> processModels, String tenantId) {
        if (processModels == null || processModels.isEmpty()) {
            return List.of();
        }
        Set<String> formIds = new HashSet<>();
        processModels.stream()
                .map(ProcessModel::getFormDefinitionId)
                .filter(StringUtils::hasText)
                .forEach(formIds::add);
        Map<String, FormDefinition> formMap = new HashMap<>();
        if (!formIds.isEmpty()) {
            formDefinitionMapper.selectList(new QueryWrapper<FormDefinition>()
                            .in("id", formIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(form -> formMap.put(form.getId(), form));
        }
        return processModels.stream()
                .map(model -> buildAvailableProcessVO(model, formMap.get(model.getFormDefinitionId())))
                .toList();
    }

    @Override
    public List<StartedInstanceVO> buildStartedInstanceRecords(List<ProcessInstance> instances, String tenantId) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        Set<String> processModelIds = new HashSet<>();
        Set<String> formInstanceIds = new HashSet<>();
        instances.stream()
                .map(ProcessInstance::getProcessModelId)
                .filter(StringUtils::hasText)
                .forEach(processModelIds::add);
        instances.stream()
                .map(ProcessInstance::getFormInstanceId)
                .filter(StringUtils::hasText)
                .forEach(formInstanceIds::add);
        Map<String, ProcessModel> processModelMap = new HashMap<>();
        if (!processModelIds.isEmpty()) {
            processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                            .in("id", processModelIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(model -> processModelMap.put(model.getId(), model));
        }
        Map<String, FormInstance> formInstanceMap = new HashMap<>();
        if (!formInstanceIds.isEmpty()) {
            formInstanceMapper.selectList(new QueryWrapper<FormInstance>()
                            .in("id", formInstanceIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(formInstance -> formInstanceMap.put(formInstance.getId(), formInstance));
        }
        Set<String> withdrawableInstanceIds = resolveWithdrawableInstanceIds(instances, tenantId);
        Set<String> urgeableInstanceIds = resolveUrgeableInstanceIds(instances, tenantId);
        return instances.stream()
                .map(instance -> buildStartedInstanceVO(
                        instance,
                        processModelMap.get(instance.getProcessModelId()),
                        formInstanceMap.get(instance.getFormInstanceId()),
                        withdrawableInstanceIds.contains(instance.getId()),
                        urgeableInstanceIds.contains(instance.getId())))
                .toList();
    }

    @Override
    public List<RuntimeTaskVO> buildRuntimeTaskRecords(List<Task> tasks, String tenantId) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Set<String> processInstanceIds = new HashSet<>();
        tasks.stream()
                .map(Task::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .forEach(processInstanceIds::add);
        Map<String, ProcessInstance> instanceMap = new HashMap<>();
        if (!processInstanceIds.isEmpty()) {
            processInstanceMapper.selectList(new QueryWrapper<ProcessInstance>()
                            .in("id", processInstanceIds)
                            .eq("tenant_id", tenantId)
                            .eq("delete_flag", 0))
                    .forEach(instance -> instanceMap.put(instance.getId(), instance));
        }
        return tasks.stream()
                .map(task -> buildRuntimeTaskVO(task, instanceMap.get(task.getProcessInstanceId())))
                .toList();
    }

    @Override
    public InstanceDetailVO buildInstanceDetail(ProcessInstance processInstance, FormInstance formInstance,
            List<Task> currentTasks, List<OperationRecord> records, List<CcRecord> ccRecords,
            RequestContext context) {
        InstanceDetailVO vo = new InstanceDetailVO();
        vo.setProcessInstance(buildProcessInstanceVO(processInstance, canWithdraw(processInstance, records),
                canUrge(processInstance, currentTasks, context)));
        vo.setFormInstance(buildFormInstanceVO(formInstance));
        Map<String, String> candidateNamesByTaskId = buildCandidateNamesByTaskId(currentTasks, processInstance.getTenantId());
        vo.setCurrentTasks(currentTasks.stream()
                .map(task -> buildRuntimeTaskVO(task, processInstance, candidateNamesByTaskId.get(task.getId())))
                .toList());
        vo.setRecords(records.stream()
                .map(this::buildOperationRecordVO)
                .toList());
        vo.setCcRecords(ccRecords.stream()
                .map(this::buildCcRecordVO)
                .toList());
        return vo;
    }

    private CcRecordVO buildCcRecordVO(CcRecord record) {
        CcRecordVO vo = new CcRecordVO();
        vo.setId(record.getId());
        vo.setCreateTime(record.getCreateTime());
        vo.setCreateBy(record.getCreateBy());
        vo.setUpdateTime(record.getUpdateTime());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setTenantId(record.getTenantId());
        vo.setProcessInstanceId(record.getProcessInstanceId());
        vo.setProcessModelId(record.getProcessModelId());
        vo.setTaskId(record.getTaskId());
        vo.setNodeId(record.getNodeId());
        vo.setNodeName(record.getNodeName());
        vo.setTriggerAction(record.getTriggerAction());
        vo.setSourceType(record.getSourceType());
        vo.setSourceId(record.getSourceId());
        vo.setReceiverUserId(record.getReceiverUserId());
        vo.setReceiverUsername(record.getReceiverUsername());
        vo.setReceiverRealname(record.getReceiverRealname());
        vo.setStatus(record.getStatus());
        vo.setReadTime(record.getReadTime());
        vo.setMessageId(record.getMessageId());
        vo.setRemark(record.getRemark());
        return vo;
    }

    @Override
    public OperationRecordVO buildOperationRecordVO(OperationRecord record) {
        OperationRecordVO vo = new OperationRecordVO();
        vo.setId(record.getId());
        vo.setCreateTime(record.getCreateTime());
        vo.setCreateBy(record.getCreateBy());
        vo.setUpdateTime(record.getUpdateTime());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setTenantId(record.getTenantId());
        vo.setProcessInstanceId(record.getProcessInstanceId());
        vo.setTaskId(record.getTaskId());
        vo.setFlowableTaskId(record.getFlowableTaskId());
        vo.setNodeId(record.getNodeId());
        vo.setNodeName(record.getNodeName());
        vo.setAction(record.getAction());
        vo.setOperatorUserId(record.getOperatorUserId());
        vo.setOperatorUsername(record.getOperatorUsername());
        vo.setOperatorRealname(record.getOperatorRealname());
        vo.setTargetUserId(record.getTargetUserId());
        vo.setTargetUsername(record.getTargetUsername());
        vo.setTargetRealname(record.getTargetRealname());
        vo.setTargetNodeId(record.getTargetNodeId());
        vo.setTargetNodeName(record.getTargetNodeName());
        vo.setComment(record.getComment());
        vo.setFormDataSnapshotJson(record.getFormDataSnapshotJson());
        vo.setOperateTime(record.getOperateTime());
        return vo;
    }

    @Override
    public TaskFormVO buildTaskForm(Task task, ProcessInstance processInstance, FormInstance formInstance,
            List<FieldPermission> permissions, ProcessNodeConfig nodeConfig) {
        boolean startDraftTask = WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType());
        List<ProcessNodeConfig> returnableNodes = startDraftTask
                ? List.of()
                : processNodeConfigService.listReturnableNodeConfigs(processInstance, nodeConfig, task.getTenantId());
        TaskFormVO vo = new TaskFormVO();
        vo.setTaskId(task.getId());
        vo.setProcessInstanceId(processInstance.getId());
        vo.setInstanceNo(processInstance.getInstanceNo());
        vo.setInstanceTitle(processInstance.getInstanceTitle());
        vo.setNodeId(task.getNodeId());
        vo.setTaskName(task.getTaskName());
        vo.setTaskType(task.getTaskType());
        vo.setApprovalMode(task.getApprovalMode());
        vo.setTaskGroupId(task.getTaskGroupId());
        vo.setGroupTotal(task.getGroupTotal());
        vo.setGroupCompleted(task.getGroupCompleted());
        vo.setParentTaskId(task.getParentTaskId());
        vo.setFormInstanceId(formInstance.getId());
        vo.setFormDefinitionId(formInstance.getFormDefinitionId());
        vo.setFormKey(formInstance.getFormKey());
        vo.setFormName(formInstance.getFormName());
        vo.setFormVersion(formInstance.getFormVersion());
        vo.setSchemaJson(formInstance.getFormSchemaSnapshotJson());
        vo.setOptionJson(formInstance.getFormOptionSnapshotJson());
        vo.setFormDataJson(formInstance.getFormDataJson());
        vo.setActionPermissions(buildTaskActionPermissions(task, nodeConfig));
        List<TaskReturnNodeVO> returnNodes = new ArrayList<>();
        if (!startDraftTask && isEnabled(nodeConfig.getAllowReturn())) {
            returnNodes.add(buildStartDraftReturnNode());
        }
        returnNodes.addAll(returnableNodes.stream().map(this::buildTaskReturnNode).toList());
        vo.setReturnNodes(returnNodes);
        vo.setFieldPermissions(permissions.stream().map(this::buildRuntimeFieldPermission).toList());
        vo.setAssigneeSelectNodes(shouldSelectNextAssigneeOnApprove(task, task.getTenantId())
                ? assigneeResolveService.buildRequiredAssigneeSelectNodes(
                        processInstance.getProcessModelId(), processInstance, task.getTenantId(), task.getNodeId())
                : List.of());
        return vo;
    }

    @Override
    public StartFormVO buildStartForm(ProcessModel model, FormDefinition form,
            List<FieldPermission> permissions, RequestContext context) {
        StartFormVO vo = new StartFormVO();
        vo.setProcessModelId(model.getId());
        vo.setProcessName(model.getProcessName());
        vo.setFormDefinitionId(form.getId());
        vo.setFormKey(form.getFormKey());
        vo.setFormName(form.getFormName());
        vo.setFormVersion(form.getVersion());
        vo.setSchemaJson(form.getSchemaJson());
        vo.setOptionJson(form.getOptionJson());
        vo.setFieldPermissions(permissions.stream()
                .map(this::buildRuntimeFieldPermission)
                .toList());
        vo.setAssigneeSelectNodes(assigneeResolveService.buildRequiredAssigneeSelectNodes(
                model.getId(), buildStarterContextProcessInstance(model, context), model.getTenantId(),
                WorkflowConstants.VirtualNode.START_DRAFT));
        return vo;
    }

    private AvailableProcessVO buildAvailableProcessVO(ProcessModel model, FormDefinition form) {
        AvailableProcessVO vo = new AvailableProcessVO();
        vo.setId(model.getId());
        vo.setCreateTime(model.getCreateTime());
        vo.setCreateBy(model.getCreateBy());
        vo.setUpdateTime(model.getUpdateTime());
        vo.setUpdateBy(model.getUpdateBy());
        vo.setCategoryId(model.getCategoryId());
        vo.setProcessKey(model.getProcessKey());
        vo.setProcessName(model.getProcessName());
        vo.setProcessVersion(model.getVersion());
        vo.setDesignerType(model.getDesignerType());
        vo.setFormDefinitionId(model.getFormDefinitionId());
        vo.setStartScopeType(model.getStartScopeType());
        vo.setRemark(model.getRemark());
        vo.setPublishedTime(model.getPublishedTime());
        if (form != null) {
            vo.setFormKey(form.getFormKey());
            vo.setFormName(form.getFormName());
            vo.setFormVersion(form.getVersion());
        }
        return vo;
    }

    private StartedInstanceVO buildStartedInstanceVO(ProcessInstance instance, ProcessModel processModel,
            FormInstance formInstance, boolean canWithdraw, boolean canUrge) {
        StartedInstanceVO vo = new StartedInstanceVO();
        vo.setId(instance.getId());
        vo.setCreateTime(instance.getCreateTime());
        vo.setCreateBy(instance.getCreateBy());
        vo.setUpdateTime(instance.getUpdateTime());
        vo.setUpdateBy(instance.getUpdateBy());
        vo.setProcessModelId(instance.getProcessModelId());
        vo.setFormInstanceId(instance.getFormInstanceId());
        vo.setFormDefinitionId(instance.getFormDefinitionId());
        vo.setInstanceNo(instance.getInstanceNo());
        vo.setInstanceTitle(instance.getInstanceTitle());
        vo.setBusinessKey(instance.getBusinessKey());
        vo.setStatus(instance.getStatus());
        vo.setStartTime(instance.getStartTime());
        vo.setEndTime(instance.getEndTime());
        vo.setCurrentTaskNames(instance.getCurrentTaskNames());
        vo.setCurrentAssigneeNames(instance.getCurrentAssigneeNames());
        vo.setCanWithdraw(canWithdraw);
        vo.setCanUrge(canUrge);
        if (processModel != null) {
            vo.setProcessName(processModel.getProcessName());
        }
        if (formInstance != null) {
            vo.setFormName(formInstance.getFormName());
        }
        return vo;
    }

    private ProcessInstanceVO buildProcessInstanceVO(ProcessInstance processInstance, boolean canWithdraw,
            boolean canUrge) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setId(processInstance.getId());
        vo.setCreateTime(processInstance.getCreateTime());
        vo.setCreateBy(processInstance.getCreateBy());
        vo.setUpdateTime(processInstance.getUpdateTime());
        vo.setUpdateBy(processInstance.getUpdateBy());
        vo.setTenantId(processInstance.getTenantId());
        vo.setProcessModelId(processInstance.getProcessModelId());
        vo.setFormInstanceId(processInstance.getFormInstanceId());
        vo.setFlowableProcessInstanceId(processInstance.getFlowableProcessInstanceId());
        vo.setFlowableProcessDefinitionId(processInstance.getFlowableProcessDefinitionId());
        vo.setFormDefinitionId(processInstance.getFormDefinitionId());
        vo.setInstanceNo(processInstance.getInstanceNo());
        vo.setInstanceTitle(processInstance.getInstanceTitle());
        vo.setBusinessKey(processInstance.getBusinessKey());
        vo.setStarterUserId(processInstance.getStarterUserId());
        vo.setStarterUsername(processInstance.getStarterUsername());
        vo.setStarterRealname(processInstance.getStarterRealname());
        vo.setStatus(processInstance.getStatus());
        vo.setStartTime(processInstance.getStartTime());
        vo.setEndTime(processInstance.getEndTime());
        vo.setCurrentTaskNames(processInstance.getCurrentTaskNames());
        vo.setCurrentAssigneeNames(processInstance.getCurrentAssigneeNames());
        vo.setCanWithdraw(canWithdraw);
        vo.setCanUrge(canUrge);
        return vo;
    }

    /**
     * 撤回按钮必须由后端业务规则决定：运行中且尚未出现审批办理类记录时才可撤回。
     */
    private Set<String> resolveWithdrawableInstanceIds(List<ProcessInstance> instances, String tenantId) {
        Set<String> runningInstanceIds = instances.stream()
                .filter(instance -> WorkflowConstants.Status.RUNNING.equals(instance.getStatus()))
                .map(ProcessInstance::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (runningInstanceIds.isEmpty()) {
            return Set.of();
        }
        Set<String> handledInstanceIds = operationRecordMapper.selectList(new QueryWrapper<OperationRecord>()
                        .select("process_instance_id")
                        .eq("tenant_id", tenantId)
                        .in("process_instance_id", runningInstanceIds)
                        .in("action", withdrawBlockingActions())
                        .eq("delete_flag", 0))
                .stream()
                .map(OperationRecord::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        runningInstanceIds.removeAll(handledInstanceIds);
        return runningInstanceIds;
    }

    private boolean canWithdraw(ProcessInstance processInstance, List<OperationRecord> records) {
        if (!WorkflowConstants.Status.RUNNING.equals(processInstance.getStatus())) {
            return false;
        }
        return records.stream()
                .map(OperationRecord::getAction)
                .noneMatch(withdrawBlockingActions()::contains);
    }

    private Set<String> resolveUrgeableInstanceIds(List<ProcessInstance> instances, String tenantId) {
        Set<String> runningInstanceIds = instances.stream()
                .filter(instance -> WorkflowConstants.Status.RUNNING.equals(instance.getStatus()))
                .map(ProcessInstance::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (runningInstanceIds.isEmpty()) {
            return Set.of();
        }
        List<Task> todoTasks = taskMapper.selectList(new QueryWrapper<Task>()
                .select("id", "process_instance_id")
                .eq("tenant_id", tenantId)
                .in("process_instance_id", runningInstanceIds)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0));
        if (todoTasks.isEmpty()) {
            return Set.of();
        }
        Set<String> recentlyUrgedTaskIds = resolveRecentlyUrgedTaskIds(todoTasks, tenantId);
        Set<String> recentlyUrgedInstanceIds = todoTasks.stream()
                .filter(task -> recentlyUrgedTaskIds.contains(task.getId()))
                .map(Task::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        return todoTasks.stream()
                .map(Task::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .filter(instanceId -> !recentlyUrgedInstanceIds.contains(instanceId))
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean canUrge(ProcessInstance processInstance, List<Task> currentTasks, RequestContext context) {
        if (!WorkflowConstants.Status.RUNNING.equals(processInstance.getStatus())) {
            return false;
        }
        if (context == null || !StringUtils.hasText(processInstance.getStarterUserId())
                || !processInstance.getStarterUserId().equals(context.getUserId())) {
            return false;
        }
        List<Task> todoTasks = currentTasks.stream()
                .filter(task -> WorkflowConstants.Status.TODO.equals(task.getStatus()))
                .toList();
        if (todoTasks.isEmpty()) {
            return false;
        }
        return resolveRecentlyUrgedTaskIds(todoTasks, processInstance.getTenantId()).isEmpty();
    }

    private Set<String> resolveRecentlyUrgedTaskIds(List<Task> tasks, String tenantId) {
        Set<String> taskIds = tasks.stream()
                .map(Task::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Set.of();
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(WorkflowConstants.Reminder.URGE_INTERVAL_MINUTES);
        return reminderRecordMapper.selectList(new QueryWrapper<ReminderRecord>()
                        .select("task_id")
                        .eq("tenant_id", tenantId)
                        .in("task_id", taskIds)
                        .eq("remind_type", WorkflowConstants.RemindType.URGE)
                        .ge("operate_time", since)
                        .eq("delete_flag", 0))
                .stream()
                .map(ReminderRecord::getTaskId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<String> withdrawBlockingActions() {
        return Set.of(
                WorkflowConstants.Action.APPROVE,
                WorkflowConstants.Action.REJECT,
                WorkflowConstants.Action.RETURN,
                WorkflowConstants.Action.TRANSFER,
                WorkflowConstants.Action.ADD_SIGN
        );
    }

    private FormInstanceVO buildFormInstanceVO(FormInstance formInstance) {
        FormInstanceVO vo = new FormInstanceVO();
        vo.setId(formInstance.getId());
        vo.setCreateTime(formInstance.getCreateTime());
        vo.setCreateBy(formInstance.getCreateBy());
        vo.setUpdateTime(formInstance.getUpdateTime());
        vo.setUpdateBy(formInstance.getUpdateBy());
        vo.setTenantId(formInstance.getTenantId());
        vo.setProcessInstanceId(formInstance.getProcessInstanceId());
        vo.setFormDefinitionId(formInstance.getFormDefinitionId());
        vo.setFormKey(formInstance.getFormKey());
        vo.setFormName(formInstance.getFormName());
        vo.setFormVersion(formInstance.getFormVersion());
        vo.setFormDataJson(formInstance.getFormDataJson());
        vo.setFormSchemaSnapshotJson(formInstance.getFormSchemaSnapshotJson());
        vo.setFormOptionSnapshotJson(formInstance.getFormOptionSnapshotJson());
        vo.setStatus(formInstance.getStatus());
        vo.setSubmittedTime(formInstance.getSubmittedTime());
        return vo;
    }

    private RuntimeTaskVO buildRuntimeTaskVO(Task task, ProcessInstance processInstance) {
        return buildRuntimeTaskVO(task, processInstance, null);
    }

    private RuntimeTaskVO buildRuntimeTaskVO(Task task, ProcessInstance processInstance, String candidateAssigneeNames) {
        RuntimeTaskVO vo = new RuntimeTaskVO();
        vo.setId(task.getId());
        vo.setCreateTime(task.getCreateTime());
        vo.setCreateBy(task.getCreateBy());
        vo.setUpdateTime(task.getUpdateTime());
        vo.setUpdateBy(task.getUpdateBy());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setFlowableTaskId(task.getFlowableTaskId());
        vo.setNodeId(task.getNodeId());
        vo.setTaskName(task.getTaskName());
        vo.setTaskType(task.getTaskType());
        vo.setApprovalMode(task.getApprovalMode());
        vo.setTaskGroupId(task.getTaskGroupId());
        vo.setGroupTotal(task.getGroupTotal());
        vo.setGroupCompleted(task.getGroupCompleted());
        vo.setAssigneeUserId(task.getAssigneeUserId());
        vo.setAssigneeUsername(task.getAssigneeUsername());
        vo.setAssigneeRealname(task.getAssigneeRealname());
        vo.setCandidateAssigneeNames(candidateAssigneeNames);
        vo.setStatus(task.getStatus());
        vo.setClaimTime(task.getClaimTime());
        vo.setCompleteTime(task.getCompleteTime());
        if (processInstance != null) {
            vo.setInstanceNo(processInstance.getInstanceNo());
            vo.setInstanceTitle(processInstance.getInstanceTitle());
            vo.setStarterUserId(processInstance.getStarterUserId());
            vo.setStarterUsername(processInstance.getStarterUsername());
            vo.setStarterRealname(processInstance.getStarterRealname());
            vo.setStartTime(processInstance.getStartTime());
        }
        return vo;
    }

    private Map<String, String> buildCandidateNamesByTaskId(List<Task> tasks, String tenantId) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        Set<String> taskIds = tasks.stream()
                .map(Task::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> candidateNames = new HashMap<>();
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .in("task_id", taskIds)
                        .eq("tenant_id", tenantId)
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .forEach(candidate -> candidateNames
                        .computeIfAbsent(candidate.getTaskId(), key -> new ArrayList<>())
                        .add(assigneeResolveService.resolveDisplayName(
                                candidate.getCandidateRealname(),
                                candidate.getCandidateUsername(),
                                candidate.getCandidateUserId())));
        return candidateNames.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue().stream()
                                .filter(StringUtils::hasText)
                                .distinct()
                                .toList())));
    }

    private TaskActionPermissionVO buildTaskActionPermissions(Task task, ProcessNodeConfig nodeConfig) {
        TaskActionPermissionVO vo = new TaskActionPermissionVO();
        if (WorkflowConstants.TaskType.START_DRAFT.equals(task.getTaskType())) {
            vo.setAllowApprove(true);
            vo.setAllowReject(false);
            vo.setAllowTransfer(false);
            vo.setAllowAddSign(false);
            vo.setAllowReturn(false);
            return vo;
        }
        boolean addSignTask = WorkflowConstants.TaskType.ADD_SIGN.equals(task.getTaskType());
        vo.setAllowApprove(true);
        vo.setAllowReject(!addSignTask);
        vo.setAllowTransfer(!addSignTask && isEnabled(nodeConfig.getAllowTransfer()));
        vo.setAllowAddSign(!addSignTask && isEnabled(nodeConfig.getAllowAddSign()));
        vo.setAllowReturn(!addSignTask && isEnabled(nodeConfig.getAllowReturn()));
        return vo;
    }

    /**
     * 会签只有最后一个完成的人会真正推进流程，因此只有最后一个人的任务表单才需要返回下一节点执行人选择项。
     */
    private boolean shouldSelectNextAssigneeOnApprove(Task task, String tenantId) {
        if (!WorkflowConstants.ApprovalMode.COUNTERSIGN.equals(task.getApprovalMode())
                && !WorkflowConstants.TaskType.COUNTERSIGN.equals(task.getTaskType())) {
            return true;
        }
        return countDoneGroupTasks(task, tenantId) + 1 >= resolveGroupTotal(task);
    }

    private int countDoneGroupTasks(Task task, String tenantId) {
        if (!StringUtils.hasText(task.getTaskGroupId())) {
            return WorkflowConstants.Status.DONE.equals(task.getStatus()) ? 1 : 0;
        }
        Long count = taskMapper.selectCount(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("task_group_id", task.getTaskGroupId())
                .eq("status", WorkflowConstants.Status.DONE)
                .eq("delete_flag", 0));
        return count == null ? 0 : count.intValue();
    }

    private int resolveGroupTotal(Task task) {
        return task.getGroupTotal() == null || task.getGroupTotal() <= 0 ? 1 : task.getGroupTotal();
    }

    private TaskReturnNodeVO buildTaskReturnNode(ProcessNodeConfig nodeConfig) {
        TaskReturnNodeVO vo = new TaskReturnNodeVO();
        vo.setNodeId(nodeConfig.getNodeId());
        vo.setNodeName(nodeConfig.getNodeName());
        vo.setNodeType(nodeConfig.getNodeType());
        vo.setSortOrder(nodeConfig.getSortOrder());
        return vo;
    }

    private TaskReturnNodeVO buildStartDraftReturnNode() {
        TaskReturnNodeVO vo = new TaskReturnNodeVO();
        vo.setNodeId(WorkflowConstants.VirtualNode.START_DRAFT);
        vo.setNodeName(WorkflowConstants.VirtualNodeName.START_DRAFT);
        vo.setNodeType(WorkflowConstants.NodeType.START);
        vo.setSortOrder(0);
        return vo;
    }

    private RuntimeFieldPermissionVO buildRuntimeFieldPermission(FieldPermission permission) {
        RuntimeFieldPermissionVO vo = new RuntimeFieldPermissionVO();
        vo.setFieldKey(permission.getFieldKey());
        vo.setPermission(permission.getPermission());
        vo.setRequiredFlag(permission.getRequiredFlag());
        return vo;
    }

    private ProcessInstance buildStarterContextProcessInstance(ProcessModel model, RequestContext context) {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setTenantId(model.getTenantId());
        processInstance.setProcessModelId(model.getId());
        processInstance.setStarterUserId(context.getUserId());
        processInstance.setStarterUsername(context.getUsername());
        processInstance.setStarterRealname(assigneeResolveService.resolveCurrentUserRealname(context));
        return processInstance;
    }

    private boolean isEnabled(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }

}
