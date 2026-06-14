package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableStartResult;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.vo.StartProcessVO;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class WorkflowRuntimeSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter INSTANCE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String START_DRAFT_NODE_ID = "start_draft";
    private static final String START_DRAFT_TASK_NAME = "提交申请";

    private final FormInstanceMapper formInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskMapper taskMapper;
    private final IFlowableService flowableService;
    private final IAssigneeResolveService assigneeResolveService;
    private final IInstanceStateService instanceStateService;
    private final IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    private final TransactionTemplate transactionTemplate;

    public WorkflowRuntimeSupport(FormInstanceMapper formInstanceMapper,
            ProcessInstanceMapper processInstanceMapper,
            TaskMapper taskMapper,
            IFlowableService flowableService,
            IAssigneeResolveService assigneeResolveService,
            IInstanceStateService instanceStateService,
            IWorkflowRuntimeLookupService workflowRuntimeLookupService,
            PlatformTransactionManager transactionManager) {
        this.formInstanceMapper = formInstanceMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.taskMapper = taskMapper;
        this.flowableService = flowableService;
        this.assigneeResolveService = assigneeResolveService;
        this.instanceStateService = instanceStateService;
        this.workflowRuntimeLookupService = workflowRuntimeLookupService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context) {
        return executeInTransaction(() -> {
            validateStartReq(req);
            validateJson(req.getFormDataJson(), "表单数据JSON");

            String tenantId = requireTenantId(context);
            requireUserId(context);
            ProcessModel model = requirePublishedModel(req.getProcessModelId(), tenantId);
            checkStartPermission(model, context);
            FormDefinition form = requirePublishedForm(model.getFormDefinitionId(), tenantId);

            FormInstance formInstance = createFormInstance(req, form, tenantId, context, WorkflowConstants.Status.ACTIVE);
            saveStartFormData(req.getFormDataJson(), formInstance,
                    listFieldPermissions(model.getId(), WorkflowConstants.VirtualNode.START, tenantId), context, true);
            ProcessInstance processInstance = createProcessInstance(req, model, form, formInstance, tenantId, context,
                    WorkflowConstants.Status.RUNNING);
            formInstance.setProcessInstanceId(processInstance.getId());
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
            assigneeResolveService.saveFirstAssigneeSnapshot(processInstance, req.getSelectedAssignees(), tenantId, context);

            FlowableStartResult flowableStartResult = flowableService.startProcessInstance(
                    model,
                    processInstance.getId(),
                    buildFlowableVariables(processInstance, formInstance, context));
            processInstance.setFlowableProcessInstanceId(flowableStartResult.getProcessInstanceId());
            processInstance.setFlowableProcessDefinitionId(flowableStartResult.getProcessDefinitionId());
            createSubmittedStartTask(processInstance, tenantId, context);
            assigneeResolveService.syncCurrentTasks(processInstance, tenantId, context);
            EntityFillUtils.fillAuditFields(processInstance, context, false);
            processInstanceMapper.updateById(processInstance);
            instanceStateService.createStartRecord(processInstance, formInstance, tenantId, context);

            return BaseResult.success(buildStartResult(processInstance, formInstance));
        }, "发起申请失败");
    }

    public BaseResult<StartProcessVO> saveStartDraft(StartProcessReq req, RequestContext context) {
        return executeInTransaction(() -> {
            validateStartReq(req);
            validateJson(req.getFormDataJson(), "表单数据JSON");

            String tenantId = requireTenantId(context);
            requireUserId(context);
            ProcessModel model = requirePublishedModel(req.getProcessModelId(), tenantId);
            checkStartPermission(model, context);
            FormDefinition form = requirePublishedForm(model.getFormDefinitionId(), tenantId);

            FormInstance formInstance = createFormInstance(req, form, tenantId, context, WorkflowConstants.Status.DRAFT);
            saveStartFormData(req.getFormDataJson(), formInstance,
                    listFieldPermissions(model.getId(), WorkflowConstants.VirtualNode.START, tenantId), context, false);
            ProcessInstance processInstance = createProcessInstance(req, model, form, formInstance, tenantId, context,
                    WorkflowConstants.Status.DRAFT);
            formInstance.setProcessInstanceId(processInstance.getId());
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
            createStartDraftTask(processInstance, tenantId, context);
            instanceStateService.createDraftRecord(processInstance, formInstance, tenantId, context);

            return BaseResult.success(buildStartResult(processInstance, formInstance));
        }, "保存申请草稿失败");
    }

    private void saveStartFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired) {
        if (!StringUtils.hasText(formDataJson)) {
            return;
        }
        saveRuntimeFormData(formDataJson, formInstance, permissions, context, validateRequired);
    }

    private void saveRuntimeFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired) {
        try {
            JsonNode submitted = OBJECT_MAPPER.readTree(formDataJson);
            JsonNode current = StringUtils.hasText(formInstance.getFormDataJson())
                    ? OBJECT_MAPPER.readTree(formInstance.getFormDataJson()) : OBJECT_MAPPER.createObjectNode();
            if (!submitted.isObject() || !current.isObject()) {
                throw new IllegalArgumentException("表单数据必须是JSON对象");
            }
            if (permissions == null || permissions.isEmpty()) {
                if (validateRequired) {
                    validateRequiredFields(List.of(), (ObjectNode) submitted);
                }
                formInstance.setFormDataJson(OBJECT_MAPPER.writeValueAsString(submitted));
                EntityFillUtils.fillAuditFields(formInstance, context, false);
                formInstanceMapper.updateById(formInstance);
                return;
            }
            ObjectNode merged = ((ObjectNode) current).deepCopy();
            Set<String> editableFields = new HashSet<>();
            for (FieldPermission permission : permissions) {
                if (WorkflowConstants.FieldPermission.EDITABLE.equals(permission.getPermission())) {
                    editableFields.add(permission.getFieldKey());
                }
            }
            for (String fieldKey : editableFields) {
                if (submitted.has(fieldKey)) {
                    merged.set(fieldKey, submitted.get(fieldKey));
                }
            }
            if (validateRequired) {
                validateRequiredFields(permissions, merged);
            }
            formInstance.setFormDataJson(OBJECT_MAPPER.writeValueAsString(merged));
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("表单数据JSON处理失败");
        }
    }

    private void validateRequiredFields(List<FieldPermission> permissions, ObjectNode formData) {
        for (FieldPermission permission : permissions) {
            if (Integer.valueOf(1).equals(permission.getRequiredFlag())
                    && WorkflowConstants.FieldPermission.EDITABLE.equals(permission.getPermission())
                    && isEmptyJsonValue(formData.get(permission.getFieldKey()))) {
                throw new IllegalArgumentException("必填字段不能为空: " + permission.getFieldKey());
            }
        }
    }

    private boolean isEmptyJsonValue(JsonNode node) {
        return node == null || node.isNull()
                || (node.isTextual() && !StringUtils.hasText(node.asText()))
                || (node.isArray() && node.isEmpty());
    }

    private List<FieldPermission> listFieldPermissions(String processModelId, String nodeId, String tenantId) {
        return workflowRuntimeLookupService.listFieldPermissions(processModelId, nodeId, tenantId);
    }

    private void validateStartReq(StartProcessReq req) {
        if (req == null) {
            throw new IllegalArgumentException("发起申请请求不能为空");
        }
        if (!StringUtils.hasText(req.getProcessModelId())) {
            throw new IllegalArgumentException("流程模型ID不能为空");
        }
        if (!StringUtils.hasText(req.getFormDataJson())) {
            throw new IllegalArgumentException("表单数据不能为空");
        }
    }

    private void validateJson(String json, String fieldName) {
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "不是合法JSON");
        }
    }

    private ProcessModel requirePublishedModel(String processModelId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedModel(processModelId, tenantId);
    }

    private FormDefinition requirePublishedForm(String formDefinitionId, String tenantId) {
        return workflowRuntimeLookupService.requirePublishedForm(formDefinitionId, tenantId);
    }

    private void checkStartPermission(ProcessModel model, RequestContext context) {
        workflowRuntimeLookupService.checkStartPermission(model, context);
    }

    private FormInstance createFormInstance(StartProcessReq req, FormDefinition form, String tenantId, RequestContext context,
            String status) {
        FormInstance formInstance = new FormInstance();
        formInstance.setTenantId(tenantId);
        formInstance.setFormDefinitionId(form.getId());
        formInstance.setFormKey(form.getFormKey());
        formInstance.setFormName(form.getFormName());
        formInstance.setFormVersion(form.getVersion());
        formInstance.setFormDataJson(req.getFormDataJson());
        formInstance.setFormSchemaSnapshotJson(form.getSchemaJson());
        formInstance.setFormOptionSnapshotJson(form.getOptionJson());
        formInstance.setStatus(status);
        if (WorkflowConstants.Status.ACTIVE.equals(status)) {
            formInstance.setSubmittedTime(LocalDateTime.now());
        }
        EntityFillUtils.fillAuditFields(formInstance, context, true);
        formInstanceMapper.insert(formInstance);
        return formInstance;
    }

    private ProcessInstance createProcessInstance(StartProcessReq req, ProcessModel model, FormDefinition form,
            FormInstance formInstance, String tenantId, RequestContext context, String status) {
        String currentUserRealname = assigneeResolveService.resolveCurrentUserRealname(context);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(newId());
        processInstance.setTenantId(tenantId);
        processInstance.setProcessModelId(model.getId());
        processInstance.setFormInstanceId(formInstance.getId());
        processInstance.setFormDefinitionId(form.getId());
        processInstance.setFlowableProcessDefinitionId(model.getFlowableProcessDefinitionId());
        processInstance.setInstanceNo(generateInstanceNo());
        processInstance.setInstanceTitle(resolveInstanceTitle(req, model));
        processInstance.setBusinessKey(StringUtils.hasText(req.getBusinessKey()) ? req.getBusinessKey() : processInstance.getId());
        processInstance.setStarterUserId(context.getUserId());
        processInstance.setStarterUsername(context.getUsername());
        processInstance.setStarterRealname(currentUserRealname);
        processInstance.setStatus(status);
        processInstance.setStartTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(processInstance, context, true);
        processInstanceMapper.insert(processInstance);
        return processInstance;
    }

    private Map<String, Object> buildFlowableVariables(ProcessInstance processInstance, FormInstance formInstance, RequestContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", processInstance.getTenantId());
        variables.put("processInstanceId", processInstance.getId());
        variables.put("formInstanceId", formInstance.getId());
        variables.put("starterUserId", context.getUserId());
        variables.put("starterUsername", context.getUsername());
        return variables;
    }

    private void createStartDraftTask(ProcessInstance processInstance, String tenantId, RequestContext context) {
        String starterDisplayName = assigneeResolveService.resolveDisplayName(
                processInstance.getStarterRealname(),
                processInstance.getStarterUsername(),
                processInstance.getStarterUserId());
        Task task = new Task();
        task.setId(newId());
        task.setTenantId(tenantId);
        task.setProcessInstanceId(processInstance.getId());
        task.setFlowableTaskId("draft:" + task.getId());
        task.setNodeId(START_DRAFT_NODE_ID);
        task.setTaskName(START_DRAFT_TASK_NAME);
        task.setTaskType(WorkflowConstants.TaskType.START_DRAFT);
        task.setAssigneeUserId(processInstance.getStarterUserId());
        task.setAssigneeUsername(processInstance.getStarterUsername());
        task.setAssigneeRealname(starterDisplayName);
        task.setStatus(WorkflowConstants.Status.TODO);
        EntityFillUtils.fillAuditFields(task, context, true);
        taskMapper.insert(task);
        processInstance.setCurrentTaskNames(START_DRAFT_TASK_NAME);
        processInstance.setCurrentAssigneeNames(starterDisplayName);
        EntityFillUtils.fillAuditFields(processInstance, context, false);
        processInstanceMapper.updateById(processInstance);
    }

    /**
     * 直接发起没有本地草稿任务，也要补齐一条已办任务，保证“直接发起”和“草稿提交”的已办口径一致。
     */
    private void createSubmittedStartTask(ProcessInstance processInstance, String tenantId, RequestContext context) {
        String starterDisplayName = assigneeResolveService.resolveDisplayName(
                processInstance.getStarterRealname(),
                processInstance.getStarterUsername(),
                processInstance.getStarterUserId());
        Task task = new Task();
        task.setId(newId());
        task.setTenantId(tenantId);
        task.setProcessInstanceId(processInstance.getId());
        task.setFlowableTaskId("start:" + task.getId());
        task.setNodeId(START_DRAFT_NODE_ID);
        task.setTaskName(START_DRAFT_TASK_NAME);
        task.setTaskType(WorkflowConstants.TaskType.START_DRAFT);
        task.setAssigneeUserId(processInstance.getStarterUserId());
        task.setAssigneeUsername(processInstance.getStarterUsername());
        task.setAssigneeRealname(starterDisplayName);
        task.setStatus(WorkflowConstants.Status.DONE);
        task.setCompleteTime(LocalDateTime.now());
        EntityFillUtils.fillAuditFields(task, context, true);
        taskMapper.insert(task);
    }

    private StartProcessVO buildStartResult(ProcessInstance processInstance, FormInstance formInstance) {
        StartProcessVO vo = new StartProcessVO();
        vo.setProcessInstanceId(processInstance.getId());
        vo.setFormInstanceId(formInstance.getId());
        vo.setFlowableProcessInstanceId(processInstance.getFlowableProcessInstanceId());
        vo.setInstanceNo(processInstance.getInstanceNo());
        vo.setStatus(processInstance.getStatus());
        return vo;
    }

    private String resolveInstanceTitle(StartProcessReq req, ProcessModel model) {
        if (StringUtils.hasText(req.getInstanceTitle())) {
            return req.getInstanceTitle();
        }
        return model.getProcessName() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }

    private String generateInstanceNo() {
        return "WF" + LocalDateTime.now().format(INSTANCE_NO_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String requireTenantId(RequestContext context) {
        return workflowRuntimeLookupService.requireTenantId(context);
    }

    private String requireUserId(RequestContext context) {
        return workflowRuntimeLookupService.requireUserId(context);
    }

    /**
     * 运行时写操作需要在捕获异常后返回 BaseResult，同时不能让 Spring 在方法返回后再提交已回滚事务。
     */
    private <T> BaseResult<T> executeInTransaction(Supplier<BaseResult<T>> action, String errorMessage) {
        return transactionTemplate.execute(status -> {
            try {
                return action.get();
            } catch (IllegalArgumentException e) {
                status.setRollbackOnly();
                return BaseResult.error(400, e.getMessage());
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error(errorMessage, e);
                return BaseResult.error(errorMessage + ": " + e.getMessage());
            }
        });
    }
}
