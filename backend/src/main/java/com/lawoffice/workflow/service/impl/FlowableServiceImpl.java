package com.lawoffice.workflow.service.impl;

import com.lawoffice.workflow.dto.FlowableDeploymentResult;
import com.lawoffice.workflow.dto.FlowableStartResult;
import com.lawoffice.workflow.dto.FlowableTaskInfo;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.service.IFlowableService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Flowable 引擎访问实现，业务层不直接依赖 Flowable API。
 */
@Service
public class FlowableServiceImpl implements IFlowableService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    @Autowired
    public FlowableServiceImpl(RepositoryService repositoryService,
            RuntimeService runtimeService,
            TaskService taskService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Override
    public FlowableDeploymentResult deployProcessModel(ProcessModel processModel) {
        validateProcessModel(processModel);

        String resourceName = buildResourceName(processModel);
        Deployment deployment = repositoryService.createDeployment()
                .name(buildDeploymentName(processModel))
                .tenantId(processModel.getTenantId())
                .addBytes(resourceName, processModel.getBpmnXml().getBytes(StandardCharsets.UTF_8))
                .deploy();

        ProcessDefinition processDefinition = resolveProcessDefinition(deployment.getId(), processModel.getProcessKey());
        return new FlowableDeploymentResult(
                deployment.getId(),
                processDefinition.getId(),
                processDefinition.getKey(),
                processDefinition.getVersion());
    }

    @Override
    public FlowableStartResult startProcessInstance(ProcessModel processModel, String businessKey, Map<String, Object> variables) {
        if (processModel == null || !StringUtils.hasText(processModel.getFlowableProcessDefinitionId())) {
            throw new IllegalArgumentException("流程未部署到Flowable，不能发起");
        }
        ProcessInstance instance = runtimeService.startProcessInstanceById(
                processModel.getFlowableProcessDefinitionId(),
                businessKey,
                variables);
        return new FlowableStartResult(instance.getId(), instance.getProcessDefinitionId());
    }

    @Override
    public List<FlowableTaskInfo> listActiveTasks(String flowableProcessInstanceId) {
        if (!StringUtils.hasText(flowableProcessInstanceId)) {
            return List.of();
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(flowableProcessInstanceId)
                .active()
                .list();
        return tasks.stream()
                .map(task -> new FlowableTaskInfo(
                        task.getId(),
                        task.getTaskDefinitionKey(),
                        task.getName(),
                        task.getAssignee(),
                        task.getOwner()))
                .toList();
    }

    @Override
    public void claimTask(String flowableTaskId, String assigneeUserId) {
        if (!StringUtils.hasText(flowableTaskId) || !StringUtils.hasText(assigneeUserId)) {
            throw new IllegalArgumentException("Flowable任务ID和处理人不能为空");
        }
        Task task = taskService.createTaskQuery().taskId(flowableTaskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Flowable任务不存在或已处理");
        }
        if (!StringUtils.hasText(task.getAssignee())) {
            taskService.claim(flowableTaskId, assigneeUserId);
            return;
        }
        if (!assigneeUserId.equals(task.getAssignee())) {
            throw new IllegalArgumentException("Flowable任务已被其他人认领");
        }
    }

    @Override
    public void setTaskAssignee(String flowableTaskId, String assigneeUserId) {
        if (!StringUtils.hasText(flowableTaskId) || !StringUtils.hasText(assigneeUserId)) {
            throw new IllegalArgumentException("Flowable任务ID和处理人不能为空");
        }
        taskService.setAssignee(flowableTaskId, assigneeUserId);
    }

    @Override
    public void addCandidateUsers(String flowableTaskId, List<String> candidateUserIds) {
        if (!StringUtils.hasText(flowableTaskId) || candidateUserIds == null || candidateUserIds.isEmpty()) {
            return;
        }
        candidateUserIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(userId -> taskService.addCandidateUser(flowableTaskId, userId));
    }

    @Override
    public void completeTask(String flowableTaskId, Map<String, Object> variables) {
        if (!StringUtils.hasText(flowableTaskId)) {
            throw new IllegalArgumentException("Flowable任务ID不能为空");
        }
        taskService.complete(flowableTaskId, variables);
    }

    @Override
    public void terminateProcessInstance(String flowableProcessInstanceId, String reason) {
        if (!StringUtils.hasText(flowableProcessInstanceId)) {
            throw new IllegalArgumentException("Flowable流程实例ID不能为空");
        }
        if (isProcessInstanceActive(flowableProcessInstanceId)) {
            runtimeService.deleteProcessInstance(flowableProcessInstanceId, reason);
        }
    }

    @Override
    public void moveActivityTo(String flowableProcessInstanceId, String currentActivityId, String targetActivityId) {
        if (!StringUtils.hasText(flowableProcessInstanceId)
                || !StringUtils.hasText(currentActivityId)
                || !StringUtils.hasText(targetActivityId)) {
            throw new IllegalArgumentException("Flowable流程实例ID、当前节点和目标节点不能为空");
        }
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(flowableProcessInstanceId)
                .moveActivityIdTo(currentActivityId, targetActivityId)
                .changeState();
    }

    @Override
    public boolean isProcessInstanceActive(String flowableProcessInstanceId) {
        if (!StringUtils.hasText(flowableProcessInstanceId)) {
            return false;
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableProcessInstanceId)
                .active()
                .singleResult() != null;
    }

    private void validateProcessModel(ProcessModel processModel) {
        if (processModel == null) {
            throw new IllegalArgumentException("流程模型不能为空");
        }
        if (!StringUtils.hasText(processModel.getTenantId())) {
            throw new IllegalArgumentException("流程模型租户ID不能为空");
        }
        if (!StringUtils.hasText(processModel.getProcessKey())) {
            throw new IllegalArgumentException("流程标识不能为空");
        }
        if (!StringUtils.hasText(processModel.getBpmnXml())) {
            throw new IllegalArgumentException("BPMN XML不能为空");
        }
    }

    private ProcessDefinition resolveProcessDefinition(String deploymentId, String expectedProcessKey) {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .list();
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("Flowable部署未生成流程定义，请检查BPMN XML");
        }
        return definitions.stream()
                .filter(definition -> expectedProcessKey.equals(definition.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("BPMN XML中的流程ID必须与流程标识一致"));
    }

    private String buildDeploymentName(ProcessModel processModel) {
        String version = processModel.getVersion() == null ? "draft" : "v" + processModel.getVersion();
        return processModel.getProcessName() + "-" + version;
    }

    private String buildResourceName(ProcessModel processModel) {
        String version = processModel.getVersion() == null ? "draft" : "v" + processModel.getVersion();
        return processModel.getProcessKey() + "-" + version + ".bpmn20.xml";
    }
}
