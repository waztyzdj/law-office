package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.document.dto.BusinessDocumentAccessContext;
import com.lawoffice.document.service.IBusinessDocumentProvider;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkflowBusinessDocumentProvider implements IBusinessDocumentProvider {

    private static final DateTimeFormatter INSTANCE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CcRecordMapper ccRecordMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessModelMapper processModelMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final IRuntimeAccessService runtimeAccessService;

    @Override
    public String bizType() {
        return WorkflowConstants.BusinessDocument.APPROVAL_BIZ_TYPE;
    }

    @Override
    public String moduleName() {
        return "审批中心";
    }

    @Override
    public Map<String, String> resolveRecordGroupIds(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        List<ProcessInstance> instances = listActiveTenantInstances(bizIds, context);
        Map<String, ProcessModel> modelMap = listProcessModelsByIds(
                instances.stream()
                        .map(ProcessInstance::getProcessModelId)
                        .filter(StringUtils::hasText)
                        .toList(),
                context.tenantId());
        return instances.stream()
                .filter(instance -> {
                    ProcessModel model = modelMap.get(instance.getProcessModelId());
                    return model != null && StringUtils.hasText(model.getProcessKey());
                })
                .collect(Collectors.toMap(
                        ProcessInstance::getId,
                        instance -> encodeGroupId(modelMap.get(instance.getProcessModelId()).getProcessKey()),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    public Map<String, String> resolveGroupNames(
            Collection<String> groupIds,
            BusinessDocumentAccessContext context) {
        List<String> normalizedGroupIds = normalizeIds(groupIds);
        if (normalizedGroupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> processKeyByGroupId = normalizedGroupIds.stream()
                .collect(Collectors.toMap(
                        groupId -> groupId,
                        this::decodeGroupId,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<String> processKeys = processKeyByGroupId.values().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (processKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> nameByProcessKey = processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .select("process_key", "process_name", "version")
                        .in("process_key", processKeys)
                        .eq("tenant_id", context.tenantId())
                        .eq("status", WorkflowConstants.Status.PUBLISHED)
                        .eq("delete_flag", 0)
                        .orderByDesc("version")
                        .orderByDesc("create_time"))
                .stream()
                .filter(model -> StringUtils.hasText(model.getProcessKey()))
                .collect(Collectors.toMap(
                        ProcessModel::getProcessKey,
                        model -> StringUtils.hasText(model.getProcessName())
                                ? model.getProcessName()
                                : "未命名流程",
                        (left, right) -> left,
                        LinkedHashMap::new));
        return processKeyByGroupId.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> nameByProcessKey.getOrDefault(entry.getValue(), entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    public Map<String, String> resolveRecordNames(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        List<ProcessInstance> instances = listActiveTenantInstances(bizIds, context);
        return instances.stream()
                .collect(Collectors.toMap(
                        ProcessInstance::getId,
                        this::buildInstanceFolderName,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    public boolean canAccess(String bizId, BusinessDocumentAccessContext context) {
        if (!StringUtils.hasText(bizId)) {
            return false;
        }
        ProcessInstance instance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("id", bizId)
                .eq("tenant_id", context.tenantId())
                .eq("delete_flag", 0)
                .last("LIMIT 1"));
        if (instance == null) {
            return false;
        }
        try {
            runtimeAccessService.ensureInstanceAccess(instance, toRequestContext(context));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public Set<String> filterAccessibleBizIds(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        List<String> normalizedBizIds = normalizeIds(bizIds);
        if (normalizedBizIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<ProcessInstance> instances = listActiveTenantInstances(normalizedBizIds, context);
        if (instances.isEmpty() || !StringUtils.hasText(context.userId())) {
            return Collections.emptySet();
        }
        Set<String> activeInstanceIds = instances.stream()
                .map(ProcessInstance::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> accessibleIds = instances.stream()
                .filter(instance -> context.userId().equals(instance.getStarterUserId()))
                .map(ProcessInstance::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        accessibleIds.addAll(listTaskAccessibleInstanceIds(activeInstanceIds, context));
        accessibleIds.addAll(listRecordAccessibleInstanceIds(activeInstanceIds, context));
        accessibleIds.addAll(listCcAccessibleInstanceIds(activeInstanceIds, context));
        return normalizedBizIds.stream()
                .filter(accessibleIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<ProcessInstance> listActiveTenantInstances(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        List<String> normalizedBizIds = normalizeIds(bizIds);
        if (normalizedBizIds.isEmpty()) {
            return Collections.emptyList();
        }
        return processInstanceMapper.selectList(new QueryWrapper<ProcessInstance>()
                .select("id", "process_model_id", "instance_no", "instance_title", "starter_user_id", "start_time")
                .in("id", normalizedBizIds)
                .eq("tenant_id", context.tenantId())
                .eq("delete_flag", 0));
    }

    private Map<String, ProcessModel> listProcessModelsByIds(Collection<String> processModelIds, String tenantId) {
        List<String> normalizedModelIds = normalizeIds(processModelIds);
        if (normalizedModelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .select("id", "process_key", "process_name", "version")
                        .in("id", normalizedModelIds)
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .stream()
                .collect(Collectors.toMap(
                        ProcessModel::getId,
                        model -> model,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Set<String> listTaskAccessibleInstanceIds(
            Set<String> processInstanceIds,
            BusinessDocumentAccessContext context) {
        if (processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> accessibleIds = taskMapper.selectList(new QueryWrapper<Task>()
                        .select("process_instance_id")
                        .in("process_instance_id", processInstanceIds)
                        .eq("tenant_id", context.tenantId())
                        .eq("assignee_user_id", context.userId())
                        .eq("delete_flag", 0))
                .stream()
                .map(Task::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> candidateTaskIds = taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .select("task_id")
                        .eq("tenant_id", context.tenantId())
                        .eq("candidate_user_id", context.userId())
                        .eq("delete_flag", 0))
                .stream()
                .map(TaskCandidate::getTaskId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!candidateTaskIds.isEmpty()) {
            accessibleIds.addAll(taskMapper.selectList(new QueryWrapper<Task>()
                            .select("process_instance_id")
                            .in("id", candidateTaskIds)
                            .in("process_instance_id", processInstanceIds)
                            .eq("tenant_id", context.tenantId())
                            .eq("delete_flag", 0))
                    .stream()
                    .map(Task::getProcessInstanceId)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return accessibleIds;
    }

    private Set<String> listRecordAccessibleInstanceIds(
            Set<String> processInstanceIds,
            BusinessDocumentAccessContext context) {
        if (processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        return operationRecordMapper.selectList(new QueryWrapper<OperationRecord>()
                        .select("process_instance_id")
                        .in("process_instance_id", processInstanceIds)
                        .eq("tenant_id", context.tenantId())
                        .eq("operator_user_id", context.userId())
                        .eq("delete_flag", 0))
                .stream()
                .map(OperationRecord::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> listCcAccessibleInstanceIds(
            Set<String> processInstanceIds,
            BusinessDocumentAccessContext context) {
        if (processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        return ccRecordMapper.selectList(new QueryWrapper<CcRecord>()
                        .select("process_instance_id")
                        .in("process_instance_id", processInstanceIds)
                        .eq("tenant_id", context.tenantId())
                        .eq("receiver_user_id", context.userId())
                        .eq("delete_flag", 0))
                .stream()
                .map(CcRecord::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> normalizeIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String buildInstanceFolderName(ProcessInstance instance) {
        String title = StringUtils.hasText(instance.getInstanceTitle())
                ? instance.getInstanceTitle()
                : "未命名审批";
        if (StringUtils.hasText(instance.getInstanceNo())) {
            return title + "-" + instance.getInstanceNo();
        }
        if (instance.getStartTime() != null) {
            return title + "-" + INSTANCE_TIME_FORMATTER.format(instance.getStartTime());
        }
        return title;
    }

    private String encodeGroupId(String processKey) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(processKey.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeGroupId(String groupId) {
        try {
            return new String(Base64.getUrlDecoder().decode(groupId), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return groupId;
        }
    }

    private RequestContext toRequestContext(BusinessDocumentAccessContext context) {
        return RequestContext.builder()
                .tenantId(context.tenantId())
                .userId(context.userId())
                .username(context.username())
                .build();
    }
}
