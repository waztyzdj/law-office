package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InstanceStateServiceImpl implements IInstanceStateService {

    private final FormInstanceMapper formInstanceMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final ITaskNotificationService taskNotificationService;
    private final UserMapper userMapper;

    public InstanceStateServiceImpl(FormInstanceMapper formInstanceMapper,
            OperationRecordMapper operationRecordMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            ITaskNotificationService taskNotificationService,
            UserMapper userMapper) {
        this.formInstanceMapper = formInstanceMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.taskNotificationService = taskNotificationService;
        this.userMapper = userMapper;
    }

    @Override
    public void markTaskDone(Task task, RequestContext context) {
        task.setStatus(WorkflowConstants.Status.DONE);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, false);
        taskMapper.updateById(task);
        taskNotificationService.expireTodoMessageActions(List.of(task.getId()), task.getTenantId(), context);
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_id", task.getId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    @Override
    public void cancelActiveCandidates(Task task, RequestContext context) {
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", task.getTenantId())
                .eq("task_id", task.getId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    @Override
    public void cancelTodoTasks(String processInstanceId, String completedTaskId, String tenantId, RequestContext context) {
        QueryWrapper<Task> wrapper = new QueryWrapper<Task>()
                .select("id")
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0);
        if (StringUtils.hasText(completedTaskId)) {
            wrapper.ne("id", completedTaskId);
        }
        List<String> canceledTaskIds = taskMapper.selectList(wrapper)
                .stream()
                .map(Task::getId)
                .toList();
        if (canceledTaskIds.isEmpty()) {
            return;
        }
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .in("id", canceledTaskIds)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
        taskNotificationService.expireTodoMessageActions(canceledTaskIds, tenantId, context);
        taskCandidateMapper.update(null, new UpdateWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", canceledTaskIds)
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .set("status", WorkflowConstants.Status.CANCELED)
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
    }

    @Override
    public void archiveFormInstance(FormInstance formInstance, RequestContext context) {
        formInstance.setStatus(WorkflowConstants.Status.ARCHIVED);
        EntityFillUtils.fillAuditFields(formInstance, context, false);
        formInstanceMapper.updateById(formInstance);
    }

    @Override
    public void createTaskRecord(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String action, String tenantId, RequestContext context) {
        createTaskRecord(task, processInstance, formInstance, req, action, tenantId, context, null, null);
    }

    @Override
    public void createTaskRecord(Task task, ProcessInstance processInstance, FormInstance formInstance,
            TaskActionReq req, String action, String tenantId, RequestContext context,
            ProcessNodeConfig targetNodeConfig, User targetUser) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setTaskId(task.getId());
        record.setFlowableTaskId(task.getFlowableTaskId());
        record.setNodeId(task.getNodeId());
        record.setNodeName(task.getTaskName());
        record.setAction(action);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(resolveCurrentUserRealname(context));
        if (targetUser != null) {
            record.setTargetUserId(targetUser.getId());
            record.setTargetUsername(targetUser.getUsername());
            record.setTargetRealname(targetUser.getRealname());
        }
        if (targetNodeConfig != null) {
            record.setTargetNodeId(targetNodeConfig.getNodeId());
            record.setTargetNodeName(targetNodeConfig.getNodeName());
        }
        record.setComment(resolveActionComment(req, resolveDefaultActionComment(action)));
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    @Override
    public void refreshCurrentTaskSummary(ProcessInstance processInstance, String tenantId) {
        List<Task> todoTasks = taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstance.getId())
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
        if (todoTasks.isEmpty()) {
            processInstance.setCurrentTaskNames(null);
            processInstance.setCurrentAssigneeNames(null);
            return;
        }
        processInstance.setCurrentTaskNames(String.join(",", todoTasks.stream()
                .map(Task::getTaskName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList()));
        Set<String> taskIds = todoTasks.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        Map<String, List<String>> candidateNamesByTaskId = new HashMap<>();
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .in("task_id", taskIds)
                        .eq("tenant_id", tenantId)
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .forEach(candidate -> candidateNamesByTaskId
                        .computeIfAbsent(candidate.getTaskId(), key -> new ArrayList<>())
                        .add(resolveDisplayName(candidate.getCandidateRealname(), candidate.getCandidateUsername(), candidate.getCandidateUserId())));
        List<String> assigneeNames = new ArrayList<>();
        for (Task task : todoTasks) {
            if (StringUtils.hasText(task.getAssigneeUserId())) {
                assigneeNames.add(resolveDisplayName(task.getAssigneeRealname(), task.getAssigneeUsername(), task.getAssigneeUserId()));
            } else {
                assigneeNames.addAll(candidateNamesByTaskId.getOrDefault(task.getId(), List.of()));
            }
        }
        processInstance.setCurrentAssigneeNames(String.join(",", assigneeNames.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList()));
    }

    @Override
    public void createStartRecord(ProcessInstance processInstance, FormInstance formInstance, String tenantId, RequestContext context) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setAction(WorkflowConstants.Action.START);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(resolveCurrentUserRealname(context));
        record.setComment("发起申请");
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    @Override
    public void createDraftRecord(ProcessInstance processInstance, FormInstance formInstance, String tenantId, RequestContext context) {
        OperationRecord record = new OperationRecord();
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setAction(WorkflowConstants.Action.SAVE_DRAFT);
        record.setOperatorUserId(context.getUserId());
        record.setOperatorUsername(context.getUsername());
        record.setOperatorRealname(resolveCurrentUserRealname(context));
        record.setComment("保存申请草稿");
        record.setFormDataSnapshotJson(formInstance.getFormDataJson());
        record.setOperateTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(record, context, true);
        operationRecordMapper.insert(record);
    }

    private String resolveDisplayName(String realname, String username, String userId) {
        if (StringUtils.hasText(realname)) {
            return realname;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return userId;
    }

    private String resolveCurrentUserRealname(RequestContext context) {
        String userId = context == null ? null : context.getUserId();
        String username = context == null ? null : context.getUsername();
        User user = null;
        if (StringUtils.hasText(userId)) {
            user = userMapper.selectOne(new QueryWrapper<User>()
                    .select("id", "username", "realname")
                    .eq("id", userId)
                    .eq("delete_flag", 0)
                    .last("limit 1"));
        }
        if (user != null) {
            return resolveDisplayName(user.getRealname(), user.getUsername(), user.getId());
        }
        return resolveDisplayName(null, username, userId);
    }

    private String resolveActionComment(TaskActionReq req, String defaultComment) {
        if (req != null && StringUtils.hasText(req.getComment())) {
            return req.getComment();
        }
        return defaultComment;
    }

    private String resolveDefaultActionComment(String action) {
        return switch (action) {
            case WorkflowConstants.Action.SAVE_DRAFT -> "保存草稿";
            case WorkflowConstants.Action.APPROVE -> "审批通过";
            case WorkflowConstants.Action.REJECT -> "审批不通过";
            case WorkflowConstants.Action.TRANSFER -> "转办";
            case WorkflowConstants.Action.RETURN -> "退回";
            case WorkflowConstants.Action.ADD_SIGN -> "加签";
            default -> action;
        };
    }
}
