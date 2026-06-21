package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeAccessServiceImpl implements IRuntimeAccessService {

    private final OperationRecordMapper operationRecordMapper;
    private final CcRecordMapper ccRecordMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;

    public RuntimeAccessServiceImpl(OperationRecordMapper operationRecordMapper,
            CcRecordMapper ccRecordMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            IWorkflowRuntimeLookupService workflowRuntimeLookupService) {
        this.operationRecordMapper = operationRecordMapper;
        this.ccRecordMapper = ccRecordMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.workflowRuntimeLookupService = workflowRuntimeLookupService;
    }

    /**
     * 审批详情属于运行时业务数据，只允许发起人、处理人、候选人或审批记录操作人查看。
     */
    @Override
    public void ensureInstanceAccess(ProcessInstance processInstance, RequestContext context) {
        String userId = workflowRuntimeLookupService.requireUserId(context);
        if (userId.equals(processInstance.getStarterUserId())) {
            return;
        }
        if (hasTaskAccess(processInstance.getId(), processInstance.getTenantId(), context)) {
            return;
        }
        if (hasRecordAccess(processInstance.getId(), processInstance.getTenantId(), userId)) {
            return;
        }
        if (hasCcAccess(processInstance.getId(), processInstance.getTenantId(), userId)) {
            return;
        }
        throw new IllegalArgumentException("当前用户无权查看该审批实例");
    }

    /**
     * 处理人或候选人可以查看实例，保证待办、已办和多人候选任务都能进入详情。
     */
    private boolean hasTaskAccess(String processInstanceId, String tenantId, RequestContext context) {
        QueryWrapper<Task> taskWrapper = new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .eq("assignee_user_id", context.getUserId());
        if (taskMapper.selectCount(taskWrapper) > 0) {
            return true;
        }
        List<String> taskIds = taskMapper.selectList(new QueryWrapper<Task>()
                        .select("id")
                        .eq("tenant_id", tenantId)
                        .eq("process_instance_id", processInstanceId)
                        .eq("delete_flag", 0))
                .stream()
                .map(Task::getId)
                .toList();
        if (taskIds.isEmpty()) {
            return false;
        }
        return taskCandidateMapper.selectCount(new QueryWrapper<TaskCandidate>()
                .eq("tenant_id", tenantId)
                .in("task_id", taskIds)
                .eq("candidate_user_id", context.getUserId())
                .eq("delete_flag", 0)) > 0;
    }

    /**
     * 已经产生操作记录的用户可以查看实例，保证历史经办人能回看审批记录。
     */
    private boolean hasRecordAccess(String processInstanceId, String tenantId, String userId) {
        return operationRecordMapper.selectCount(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("operator_user_id", userId)
                .eq("delete_flag", 0)) > 0;
    }

    /**
     * 抄送记录是二期新增的详情查看权来源，但只授予查看权，不授予办理权。
     */
    private boolean hasCcAccess(String processInstanceId, String tenantId, String userId) {
        return ccRecordMapper.selectCount(new QueryWrapper<CcRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("receiver_user_id", userId)
                .eq("delete_flag", 0)) > 0;
    }
}
