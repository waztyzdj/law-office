package com.lawoffice.workflow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessCategory;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IFormDefinitionService;
import com.lawoffice.workflow.service.IProcessModelService;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.vo.FormDefinitionVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.ProcessModelVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartProcessVO;
import com.lawoffice.workflow.vo.TaskActionVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkflowSmokeIT {

    private static final String PREFIX = "WF_SMOKE_";
    private static final String TENANT_ID = "WF_SMOKE_TENANT";
    private static final String STARTER_ID = "WF_SMOKE_USER_STARTER";
    private static final String APPROVER1_ID = "WF_SMOKE_USER_APPROVER1";
    private static final String APPROVER2_ID = "WF_SMOKE_USER_APPROVER2";
    private static final String ADD_SIGN_ID = "WF_SMOKE_USER_ADD_SIGN";
    private static final String PROCESS_KEY = "WF_SMOKE_PROCESS";
    private static final String FORM_KEY = "WF_SMOKE_FORM";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private IRuntimeService runtimeService;
    @Autowired
    private IFormDefinitionService formDefinitionService;
    @Autowired
    private IProcessModelService processModelService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserTenantMapper userTenantMapper;
    @Autowired
    private ProcessCategoryMapper processCategoryMapper;
    @Autowired
    private FormDefinitionMapper formDefinitionMapper;
    @Autowired
    private ProcessModelMapper processModelMapper;
    @Autowired
    private ProcessNodeConfigMapper processNodeConfigMapper;
    @Autowired
    private FieldPermissionMapper fieldPermissionMapper;
    @Autowired
    private ProcessStartPermissionMapper processStartPermissionMapper;
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;
    @Autowired
    private FormInstanceMapper formInstanceMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskCandidateMapper taskCandidateMapper;
    @Autowired
    private OperationRecordMapper operationRecordMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RequestContext starterContext = context(STARTER_ID, "wf_smoke_starter");
    private final RequestContext approver1Context = context(APPROVER1_ID, "wf_smoke_approver1");
    private final RequestContext approver2Context = context(APPROVER2_ID, "wf_smoke_approver2");
    private final RequestContext addSignContext = context(ADD_SIGN_ID, "wf_smoke_add_sign");

    @BeforeEach
    void setUp() {
        TenantContextHolder.setCurrentTenantId(TENANT_ID);
        ensureCurrentSchema();
        cleanup();
        seedUsers();
    }

    @AfterEach
    void tearDown() {
        try {
            cleanup();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void shouldRunWorkflowPhaseOneRuntimeSmoke() throws Exception {
        String categoryId = seedCategory();
        String formId = seedAndPublishForm(categoryId);
        String processModelId = seedAndPublishProcess(categoryId, formId);

        StartProcessReq startReq = new StartProcessReq();
        startReq.setProcessModelId(processModelId);
        startReq.setInstanceTitle(PREFIX + "审批联调");
        startReq.setBusinessKey(PREFIX + UUID.randomUUID().toString().replace("-", ""));
        startReq.setFormDataJson("{\"reason\":\"initial\",\"amount\":100}");
        StartProcessVO start = success(runtimeService.start(startReq, starterContext));
        assertEquals(WorkflowConstants.Status.RUNNING, start.getStatus());

        RuntimeTaskVO firstTask = firstTodo(approver1Context);
        assertEquals("approve_1", firstTask.getNodeId());

        TaskFormVO firstForm = success(runtimeService.getTaskForm(firstTask.getId(), approver1Context));
        assertTrue(firstForm.getActionPermissions().getAllowTransfer());
        assertTrue(firstForm.getActionPermissions().getAllowAddSign());

        TaskActionReq transferReq = action(firstTask.getId(), "转办给二级审批人");
        transferReq.setTargetUserId(APPROVER2_ID);
        success(runtimeService.transfer(firstTask.getId(), transferReq, approver1Context));

        RuntimeTaskVO transferredTask = firstTodo(approver2Context);
        assertEquals(firstTask.getId(), transferredTask.getId());
        assertEquals(WorkflowConstants.TaskType.TRANSFER, transferredTask.getTaskType());

        TaskActionReq addSignReq = action(transferredTask.getId(), "加签确认");
        addSignReq.setTargetUserId(ADD_SIGN_ID);
        success(runtimeService.addSign(transferredTask.getId(), addSignReq, approver2Context));

        RuntimeTaskVO addSignTask = firstTodo(addSignContext);
        assertEquals(WorkflowConstants.TaskType.ADD_SIGN, addSignTask.getTaskType());
        TaskFormVO addSignForm = success(runtimeService.getTaskForm(addSignTask.getId(), addSignContext));
        assertTrue(addSignForm.getActionPermissions().getAllowApprove());
        assertFalse(addSignForm.getActionPermissions().getAllowReject());
        assertFalse(addSignForm.getActionPermissions().getAllowTransfer());
        assertFalse(addSignForm.getActionPermissions().getAllowAddSign());
        assertFalse(addSignForm.getActionPermissions().getAllowReturn());

        success(runtimeService.approve(addSignTask.getId(), action(addSignTask.getId(), "加签通过"), addSignContext));

        RuntimeTaskVO resumedTask = firstTodo(approver2Context);
        assertEquals(transferredTask.getId(), resumedTask.getId());
        TaskActionReq approveNode1Req = action(resumedTask.getId(), "一级审批通过");
        approveNode1Req.setFormDataJson("{\"reason\":\"updated-by-approver\",\"amount\":999}");
        success(runtimeService.approve(resumedTask.getId(), approveNode1Req, approver2Context));

        RuntimeTaskVO secondTask = firstTodo(approver2Context);
        assertEquals("approve_2", secondTask.getNodeId());
        TaskFormVO secondForm = success(runtimeService.getTaskForm(secondTask.getId(), approver2Context));
        assertTrue(secondForm.getActionPermissions().getAllowReturn());
        assertEquals("approve_1", secondForm.getReturnNodes().get(0).getNodeId());

        TaskActionReq returnReq = action(secondTask.getId(), "退回一级审批");
        returnReq.setTargetNodeId("approve_1");
        success(runtimeService.returnTask(secondTask.getId(), returnReq, approver2Context));

        RuntimeTaskVO returnedTask = firstTodo(approver1Context);
        assertEquals("approve_1", returnedTask.getNodeId());
        success(runtimeService.approve(returnedTask.getId(), action(returnedTask.getId(), "退回后重新通过"), approver1Context));

        RuntimeTaskVO finalTask = firstTodo(approver2Context);
        assertEquals("approve_2", finalTask.getNodeId());
        TaskActionVO finalResult = success(runtimeService.approve(finalTask.getId(), action(finalTask.getId(), "最终通过"), approver2Context));
        assertEquals(WorkflowConstants.Status.APPROVED, finalResult.getProcessStatus());

        InstanceDetailVO detail = success(runtimeService.getInstanceDetail(start.getProcessInstanceId(), starterContext));
        assertEquals(WorkflowConstants.Status.APPROVED, detail.getProcessInstance().getStatus());
        JsonNode finalFormData = OBJECT_MAPPER.readTree(detail.getFormInstance().getFormDataJson());
        assertEquals("updated-by-approver", finalFormData.path("reason").asText());
        assertEquals(100, finalFormData.path("amount").asInt());

        List<String> actions = detail.getRecords().stream().map(OperationRecordVO::getAction).toList();
        assertTrue(actions.contains(WorkflowConstants.Action.START));
        assertTrue(actions.contains(WorkflowConstants.Action.TRANSFER));
        assertTrue(actions.contains(WorkflowConstants.Action.ADD_SIGN));
        assertTrue(actions.contains(WorkflowConstants.Action.RETURN));
        assertTrue(actions.stream().filter(WorkflowConstants.Action.APPROVE::equals).count() >= 4);
    }

    @Test
    void shouldReturnStartDraftTaskToStarter() {
        String categoryId = seedCategory();
        String formId = seedAndPublishForm(categoryId);
        String processModelId = seedAndPublishProcess(categoryId, formId);

        StartProcessReq startReq = new StartProcessReq();
        startReq.setProcessModelId(processModelId);
        startReq.setInstanceTitle(PREFIX + "RETURN_START_DRAFT");
        startReq.setBusinessKey(PREFIX + UUID.randomUUID().toString().replace("-", ""));
        startReq.setFormDataJson("{\"reason\":\"return-to-starter\",\"amount\":100}");
        success(runtimeService.start(startReq, starterContext));

        RuntimeTaskVO firstTask = firstTodo(approver1Context);
        success(runtimeService.approve(firstTask.getId(), action(firstTask.getId(), "approve first"), approver1Context));

        RuntimeTaskVO secondTask = firstTodo(approver2Context);
        TaskActionReq returnReq = action(secondTask.getId(), "return to submitter");
        returnReq.setTargetNodeId("start_draft");
        success(runtimeService.returnTask(secondTask.getId(), returnReq, approver2Context));

        RuntimeTaskVO returnedTask = firstTodo(starterContext);
        assertEquals("start_draft", returnedTask.getNodeId());
        assertEquals(STARTER_ID, returnedTask.getAssigneeUserId());
        assertEquals(0, todoPage(approver2Context).getTotal());
    }

    private RuntimeTaskVO firstTodo(RequestContext context) {
        PageVO<RuntimeTaskVO> page = todoPage(context);
        assertTrue(page.getTotal() > 0, "expected todo task for " + context.getUsername());
        return page.getRecords().get(0);
    }

    private PageVO<RuntimeTaskVO> todoPage(RequestContext context) {
        TaskPageReq req = new TaskPageReq();
        req.setPageNum(1);
        req.setPageSize(10);
        return success(runtimeService.pageTodo(req, context));
    }

    private TaskActionReq action(String taskId, String comment) {
        TaskActionReq req = new TaskActionReq();
        req.setTaskId(taskId);
        req.setComment(comment);
        return req;
    }

    private String seedCategory() {
        ProcessCategory category = new ProcessCategory();
        category.setId(PREFIX + "CATEGORY");
        category.setTenantId(TENANT_ID);
        category.setCategoryCode(PREFIX + "CATEGORY");
        category.setCategoryName(PREFIX + "审批分类");
        category.setSortOrder(1);
        category.setStatus(WorkflowConstants.Status.ENABLED);
        fillCreate(category);
        processCategoryMapper.insert(category);
        return category.getId();
    }

    private String seedAndPublishForm(String categoryId) {
        FormDefinition form = new FormDefinition();
        form.setId(PREFIX + "FORM");
        form.setTenantId(TENANT_ID);
        form.setCategoryId(categoryId);
        form.setFormKey(FORM_KEY);
        form.setFormName(PREFIX + "测试表单");
        form.setVersion(1);
        form.setSchemaJson("[{\"type\":\"input\",\"field\":\"reason\",\"title\":\"事由\"},{\"type\":\"inputNumber\",\"field\":\"amount\",\"title\":\"金额\"}]");
        form.setOptionJson("{}");
        form.setStatus(WorkflowConstants.Status.DRAFT);
        fillCreate(form);
        formDefinitionMapper.insert(form);
        FormDefinitionVO published = success(formDefinitionService.publish(form.getId(), starterContext));
        assertEquals(WorkflowConstants.Status.PUBLISHED, published.getStatus());
        return form.getId();
    }

    private String seedAndPublishProcess(String categoryId, String formId) {
        ProcessModel model = new ProcessModel();
        model.setId(PREFIX + "PROCESS_MODEL");
        model.setTenantId(TENANT_ID);
        model.setCategoryId(categoryId);
        model.setFormDefinitionId(formId);
        model.setProcessKey(PROCESS_KEY);
        model.setProcessName(PREFIX + "测试流程");
        model.setVersion(1);
        model.setDesignerType(WorkflowConstants.DesignerType.SIMPLE);
        model.setNodeJson("{\"nodes\":[{\"id\":\"approve_1\"},{\"id\":\"approve_2\"}]}");
        model.setBpmnXml(buildBpmnXml());
        model.setStatus(WorkflowConstants.Status.DRAFT);
        model.setStartScopeType(WorkflowConstants.StartScopeType.ALL);
        fillCreate(model);
        processModelMapper.insert(model);

        seedNode(model.getId(), "approve_1", "一级审批", APPROVER1_ID, 10, true);
        seedNode(model.getId(), "approve_2", "二级审批", APPROVER2_ID, 20, true);
        seedFieldPermission(model.getId(), "approve_1", "reason", WorkflowConstants.FieldPermission.EDITABLE);
        seedFieldPermission(model.getId(), "approve_1", "amount", WorkflowConstants.FieldPermission.READONLY);
        seedFieldPermission(model.getId(), "approve_2", "reason", WorkflowConstants.FieldPermission.EDITABLE);
        seedFieldPermission(model.getId(), "approve_2", "amount", WorkflowConstants.FieldPermission.READONLY);

        ProcessModelVO published = success(processModelService.publish(model.getId(), starterContext));
        assertEquals(WorkflowConstants.Status.PUBLISHED, published.getStatus());
        assertNotNull(published.getFlowableProcessDefinitionId());
        return model.getId();
    }

    private void seedNode(String processModelId, String nodeId, String nodeName, String userId,
            int sortOrder, boolean allowReturn) {
        ProcessNodeConfig node = new ProcessNodeConfig();
        node.setId(PREFIX + "NODE_" + nodeId);
        node.setTenantId(TENANT_ID);
        node.setProcessModelId(processModelId);
        node.setNodeId(nodeId);
        node.setNodeName(nodeName);
        node.setNodeType(WorkflowConstants.NodeType.APPROVER);
        node.setAssigneeType(WorkflowConstants.AssigneeType.USER);
        node.setAssigneeJson("{\"userIds\":[\"" + userId + "\"]}");
        node.setAllowTransfer(1);
        node.setAllowAddSign(1);
        node.setAllowReturn(allowReturn ? 1 : 0);
        node.setSortOrder(sortOrder);
        fillCreate(node);
        processNodeConfigMapper.insert(node);
    }

    private void seedFieldPermission(String processModelId, String nodeId, String fieldKey, String permission) {
        FieldPermission fieldPermission = new FieldPermission();
        fieldPermission.setId(PREFIX + "FIELD_" + nodeId + "_" + fieldKey);
        fieldPermission.setTenantId(TENANT_ID);
        fieldPermission.setProcessModelId(processModelId);
        fieldPermission.setNodeId(nodeId);
        fieldPermission.setFieldKey(fieldKey);
        fieldPermission.setPermission(permission);
        fieldPermission.setRequiredFlag(0);
        fillCreate(fieldPermission);
        fieldPermissionMapper.insert(fieldPermission);
    }

    private void seedUsers() {
        seedUser(STARTER_ID, "wf_smoke_starter", "联调发起人");
        seedUser(APPROVER1_ID, "wf_smoke_approver1", "联调审批人一");
        seedUser(APPROVER2_ID, "wf_smoke_approver2", "联调审批人二");
        seedUser(ADD_SIGN_ID, "wf_smoke_add_sign", "联调加签人");
    }

    private void seedUser(String userId, String username, String realname) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setRealname(realname);
        user.setPassword("WF_SMOKE_PASSWORD");
        user.setStatus(1);
        user.setWorkNo(userId);
        user.setUserIdentity(1);
        user.setLoginTenantId(TENANT_ID);
        fillCreate(user);
        userMapper.insert(user);

        UserTenant userTenant = new UserTenant();
        userTenant.setId(PREFIX + "USER_TENANT_" + userId);
        userTenant.setUserId(userId);
        userTenant.setTenantId(TENANT_ID);
        userTenant.setStatus("1");
        fillCreate(userTenant);
        userTenantMapper.insert(userTenant);
    }

    private String buildBpmnXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://lawoffice.com/workflow/smoke">
                  <process id="WF_SMOKE_PROCESS" name="WF_SMOKE 测试流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <sequenceFlow id="flow_start_approve_1" sourceRef="start" targetRef="approve_1"/>
                    <userTask id="approve_1" name="一级审批"/>
                    <sequenceFlow id="flow_approve_1_approve_2" sourceRef="approve_1" targetRef="approve_2"/>
                    <userTask id="approve_2" name="二级审批"/>
                    <sequenceFlow id="flow_approve_2_end" sourceRef="approve_2" targetRef="end"/>
                    <endEvent id="end" name="结束"/>
                  </process>
                </definitions>
                """;
    }

    private void cleanup() {
        TenantContextHolder.setCurrentTenantId(TENANT_ID);
        deleteFlowableDeployments();

        List<String> processInstanceIds = processInstanceMapper.selectList(new QueryWrapper<ProcessInstance>()
                        .eq("tenant_id", TENANT_ID)
                        .likeRight("business_key", PREFIX)
                        .eq("delete_flag", 0))
                .stream()
                .map(ProcessInstance::getId)
                .toList();
        List<String> formInstanceIds = formInstanceMapper.selectList(new QueryWrapper<FormInstance>()
                        .eq("tenant_id", TENANT_ID)
                        .likeRight("form_key", PREFIX)
                        .eq("delete_flag", 0))
                .stream()
                .map(FormInstance::getId)
                .toList();
        List<String> processModelIds = processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .eq("tenant_id", TENANT_ID)
                        .likeRight("process_key", PREFIX)
                        .eq("delete_flag", 0))
                .stream()
                .map(ProcessModel::getId)
                .toList();

        if (!processInstanceIds.isEmpty()) {
            List<String> taskIds = taskMapper.selectList(new QueryWrapper<Task>()
                            .eq("tenant_id", TENANT_ID)
                            .in("process_instance_id", processInstanceIds)
                            .eq("delete_flag", 0))
                    .stream()
                    .map(Task::getId)
                    .toList();
            if (!taskIds.isEmpty()) {
                taskCandidateMapper.delete(new QueryWrapper<TaskCandidate>().eq("tenant_id", TENANT_ID).in("task_id", taskIds));
            }
            operationRecordMapper.delete(new QueryWrapper<OperationRecord>().eq("tenant_id", TENANT_ID).in("process_instance_id", processInstanceIds));
            taskMapper.delete(new QueryWrapper<Task>().eq("tenant_id", TENANT_ID).in("process_instance_id", processInstanceIds));
            processInstanceMapper.delete(new QueryWrapper<ProcessInstance>().eq("tenant_id", TENANT_ID).in("id", processInstanceIds));
        }
        if (!formInstanceIds.isEmpty()) {
            formInstanceMapper.delete(new QueryWrapper<FormInstance>().eq("tenant_id", TENANT_ID).in("id", formInstanceIds));
        }
        if (!processModelIds.isEmpty()) {
            processStartPermissionMapper.delete(new QueryWrapper<ProcessStartPermission>().eq("tenant_id", TENANT_ID).in("process_model_id", processModelIds));
            fieldPermissionMapper.delete(new QueryWrapper<FieldPermission>().eq("tenant_id", TENANT_ID).in("process_model_id", processModelIds));
            processNodeConfigMapper.delete(new QueryWrapper<ProcessNodeConfig>().eq("tenant_id", TENANT_ID).in("process_model_id", processModelIds));
            processModelMapper.delete(new QueryWrapper<ProcessModel>().eq("tenant_id", TENANT_ID).in("id", processModelIds));
        }
        formDefinitionMapper.delete(new QueryWrapper<FormDefinition>().eq("tenant_id", TENANT_ID).likeRight("form_key", PREFIX));
        processCategoryMapper.delete(new QueryWrapper<ProcessCategory>().eq("tenant_id", TENANT_ID).likeRight("category_code", PREFIX));
        userTenantMapper.delete(new QueryWrapper<UserTenant>().eq("tenant_id", TENANT_ID).likeRight("user_id", PREFIX));
        userMapper.delete(new QueryWrapper<User>().likeRight("id", PREFIX));
    }

    private void ensureCurrentSchema() {
        Integer notNullable = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'wf_process_instance'
                  AND COLUMN_NAME = 'flowable_process_instance_id'
                  AND IS_NULLABLE = 'NO'
                """, Integer.class);
        if (notNullable != null && notNullable > 0) {
            jdbcTemplate.execute("""
                    ALTER TABLE wf_process_instance
                    MODIFY COLUMN flowable_process_instance_id varchar(128) DEFAULT NULL
                    COMMENT 'Flowable流程实例ID，启动Flowable成功后回填'
                    """);
        }
    }

    private void deleteFlowableDeployments() {
        List<Deployment> deployments = repositoryService.createDeploymentQuery()
                .deploymentNameLike("%" + PREFIX + "%")
                .list();
        for (Deployment deployment : deployments) {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }

    private RequestContext context(String userId, String username) {
        return RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(userId)
                .username(username)
                .ipAddress("127.0.0.1")
                .userAgent("WorkflowSmokeIT")
                .build();
    }

    private <T> T success(BaseResult<T> result) {
        assertNotNull(result);
        assertEquals(200, result.getCode(), result.getMessage());
        return result.getData();
    }

    private void fillCreate(com.lawoffice.framework.entity.BaseEntity entity) {
        entity.setCreateBy("workflow-smoke");
        entity.setCreateTime(LocalDateTime.now());
        entity.setDeleteFlag(0);
    }
}
