package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.document.dto.BusinessDocumentAccessContext;
import com.lawoffice.document.service.IBusinessDocumentProvider;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
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

    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessModelMapper processModelMapper;
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
        return instances.stream()
                .filter(instance -> StringUtils.hasText(instance.getProcessModelId()))
                .collect(Collectors.toMap(
                        ProcessInstance::getId,
                        ProcessInstance::getProcessModelId,
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
        return processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .select("id", "process_name")
                        .in("id", normalizedGroupIds)
                        .eq("tenant_id", context.tenantId())
                        .eq("delete_flag", 0))
                .stream()
                .collect(Collectors.toMap(
                        ProcessModel::getId,
                        model -> StringUtils.hasText(model.getProcessName())
                                ? model.getProcessName()
                                : "未命名流程",
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
        return normalizedBizIds.stream()
                .filter(bizId -> canAccess(bizId, context))
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
                .select("id", "process_model_id", "instance_no", "instance_title", "start_time")
                .in("id", normalizedBizIds)
                .eq("tenant_id", context.tenantId())
                .eq("delete_flag", 0));
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

    private RequestContext toRequestContext(BusinessDocumentAccessContext context) {
        return RequestContext.builder()
                .tenantId(context.tenantId())
                .userId(context.userId())
                .username(context.username())
                .build();
    }
}
