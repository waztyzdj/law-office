package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
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
import com.lawoffice.workflow.service.ICcRuntimeService;
import com.lawoffice.workflow.service.IConditionBranchRuntimeService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.IWorkflowFormDataService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import com.lawoffice.workflow.vo.StartProcessVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessStartServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "starter-1";
    private static final String USERNAME = "starter";
    private static final String REALNAME = "发起人";
    private static final String DISPLAY_NAME = "发起人";
    private static final String PROCESS_MODEL_ID = "model-1";
    private static final String FORM_DEFINITION_ID = "form-1";
    private static final String FORM_INSTANCE_ID = "form-instance-1";
    private static final String FLOWABLE_PROCESS_INSTANCE_ID = "flowable-instance-1";
    private static final String FLOWABLE_PROCESS_DEFINITION_ID = "flowable-definition-1";
    private static final String FORM_DATA_JSON = "{\"reason\":\"test\",\"amount\":100}";

    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private IConditionBranchRuntimeService conditionBranchRuntimeService;
    @Mock
    private ICcRuntimeService ccRuntimeService;
    @Mock
    private IFlowableService flowableService;
    @Mock
    private IAssigneeResolveService assigneeResolveService;
    @Mock
    private IInstanceStateService instanceStateService;
    @Mock
    private IWorkflowFormDataService workflowFormDataService;
    @Mock
    private IWorkflowRuntimeLookupService workflowRuntimeLookupService;

    private ProcessStartServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ProcessStartServiceImpl(
                formInstanceMapper,
                processInstanceMapper,
                taskMapper,
                conditionBranchRuntimeService,
                ccRuntimeService,
                flowableService,
                assigneeResolveService,
                instanceStateService,
                workflowFormDataService,
                workflowRuntimeLookupService,
                new NoOpTransactionManager()
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .username(USERNAME)
                .build();
        lenient().when(conditionBranchRuntimeService.matchNextBranch(
                any(ProcessModel.class), any(ProcessInstance.class), any(FormInstance.class),
                anyString(), any(), anyString(), any(RequestContext.class)))
                .thenReturn(java.util.Optional.empty());
        lenient().when(conditionBranchRuntimeService.buildFlowableVariables(any()))
                .thenReturn(Map.of());
    }

    @Test
    void shouldStartProcessAndCreateSubmittedStartTask() {
        StartProcessReq req = startReq("测试发起", "biz-1");
        ProcessModel model = processModel();
        FormDefinition form = formDefinition();
        FieldPermission permission = fieldPermission();
        mockCommonLookup(model, form, List.of(permission));
        when(flowableService.startProcessInstance(eq(model), anyString(), anyMap()))
                .thenReturn(new FlowableStartResult(FLOWABLE_PROCESS_INSTANCE_ID, FLOWABLE_PROCESS_DEFINITION_ID));

        BaseResult<StartProcessVO> result = service.start(req, context);

        assertEquals(200, result.getCode(), result.getMessage());
        StartProcessVO vo = result.getData();
        assertEquals(WorkflowConstants.Status.RUNNING, vo.getStatus());
        assertEquals(FORM_INSTANCE_ID, vo.getFormInstanceId());
        assertEquals(FLOWABLE_PROCESS_INSTANCE_ID, vo.getFlowableProcessInstanceId());
        assertTrue(vo.getInstanceNo().startsWith("WF"));

        ArgumentCaptor<FormInstance> formCaptor = ArgumentCaptor.forClass(FormInstance.class);
        verify(formInstanceMapper).insert(formCaptor.capture());
        FormInstance formInstance = formCaptor.getValue();
        assertEquals(WorkflowConstants.Status.ACTIVE, formInstance.getStatus());
        assertEquals(FORM_DATA_JSON, formInstance.getFormDataJson());
        assertNotNull(formInstance.getSubmittedTime());
        verify(workflowFormDataService).saveStartFormData(
                eq(FORM_DATA_JSON), same(formInstance), eq(List.of(permission)), same(context), eq(true));

        ArgumentCaptor<ProcessInstance> processCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceMapper).insert(processCaptor.capture());
        ProcessInstance processInstance = processCaptor.getValue();
        assertEquals(WorkflowConstants.Status.RUNNING, processInstance.getStatus());
        assertEquals("测试发起", processInstance.getInstanceTitle());
        assertEquals("biz-1", processInstance.getBusinessKey());
        assertEquals(USER_ID, processInstance.getStarterUserId());
        assertEquals(REALNAME, processInstance.getStarterRealname());
        verify(assigneeResolveService).saveFirstAssigneeSnapshot(
                same(processInstance), eq(req.getSelectedAssignees()), eq(TENANT_ID), same(context));
        verify(assigneeResolveService).syncCurrentTasks(same(processInstance), eq(TENANT_ID), same(context));
        verify(instanceStateService).createStartRecord(same(processInstance), same(formInstance), eq(TENANT_ID), same(context));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());
        Task startTask = taskCaptor.getValue();
        assertEquals(WorkflowConstants.VirtualNode.START_DRAFT, startTask.getNodeId());
        assertEquals(WorkflowConstants.TaskType.START_DRAFT, startTask.getTaskType());
        assertEquals(WorkflowConstants.Status.DONE, startTask.getStatus());
        assertEquals(USER_ID, startTask.getAssigneeUserId());
        assertEquals(DISPLAY_NAME, startTask.getAssigneeRealname());
        assertNotNull(startTask.getCompleteTime());

        verify(processInstanceMapper).updateById(processInstance);
        assertEquals(FLOWABLE_PROCESS_INSTANCE_ID, processInstance.getFlowableProcessInstanceId());
        assertEquals(FLOWABLE_PROCESS_DEFINITION_ID, processInstance.getFlowableProcessDefinitionId());
    }

    @Test
    void shouldSaveStartDraftAndCreateStarterTodoTask() {
        StartProcessReq req = startReq("测试草稿", "biz-draft");
        ProcessModel model = processModel();
        FormDefinition form = formDefinition();
        FieldPermission permission = fieldPermission();
        mockCommonLookup(model, form, List.of(permission));

        BaseResult<StartProcessVO> result = service.saveStartDraft(req, context);

        assertEquals(200, result.getCode(), result.getMessage());
        StartProcessVO vo = result.getData();
        assertEquals(WorkflowConstants.Status.DRAFT, vo.getStatus());
        assertEquals(FORM_INSTANCE_ID, vo.getFormInstanceId());
        assertNull(vo.getFlowableProcessInstanceId());

        ArgumentCaptor<FormInstance> formCaptor = ArgumentCaptor.forClass(FormInstance.class);
        verify(formInstanceMapper).insert(formCaptor.capture());
        FormInstance formInstance = formCaptor.getValue();
        assertEquals(WorkflowConstants.Status.DRAFT, formInstance.getStatus());
        assertNull(formInstance.getSubmittedTime());
        verify(workflowFormDataService).saveStartFormData(
                eq(FORM_DATA_JSON), same(formInstance), eq(List.of(permission)), same(context), eq(false));

        ArgumentCaptor<ProcessInstance> processCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(processInstanceMapper).insert(processCaptor.capture());
        ProcessInstance processInstance = processCaptor.getValue();
        assertEquals(WorkflowConstants.Status.DRAFT, processInstance.getStatus());
        assertEquals("测试草稿", processInstance.getInstanceTitle());
        assertEquals(WorkflowConstants.VirtualNodeName.START_DRAFT, processInstance.getCurrentTaskNames());
        assertEquals(DISPLAY_NAME, processInstance.getCurrentAssigneeNames());
        verify(instanceStateService).createDraftRecord(same(processInstance), same(formInstance), eq(TENANT_ID), same(context));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());
        Task draftTask = taskCaptor.getValue();
        assertEquals(WorkflowConstants.VirtualNode.START_DRAFT, draftTask.getNodeId());
        assertEquals(WorkflowConstants.TaskType.START_DRAFT, draftTask.getTaskType());
        assertEquals(WorkflowConstants.Status.TODO, draftTask.getStatus());
        assertEquals(USER_ID, draftTask.getAssigneeUserId());
        assertEquals(DISPLAY_NAME, draftTask.getAssigneeRealname());
        assertNull(draftTask.getCompleteTime());

        verify(flowableService, never()).startProcessInstance(any(ProcessModel.class), anyString(), any(Map.class));
        verify(assigneeResolveService, never()).saveFirstAssigneeSnapshot(any(), any(), anyString(), any());
        verify(assigneeResolveService, never()).syncCurrentTasks(any(), anyString(), any());
        verify(instanceStateService, never()).createStartRecord(any(), any(), anyString(), any());
    }

    private void mockCommonLookup(ProcessModel model, FormDefinition form, List<FieldPermission> permissions) {
        when(workflowRuntimeLookupService.requireTenantId(same(context))).thenReturn(TENANT_ID);
        when(workflowRuntimeLookupService.requireUserId(same(context))).thenReturn(USER_ID);
        when(workflowRuntimeLookupService.requirePublishedModel(PROCESS_MODEL_ID, TENANT_ID)).thenReturn(model);
        when(workflowRuntimeLookupService.requirePublishedForm(FORM_DEFINITION_ID, TENANT_ID)).thenReturn(form);
        when(workflowRuntimeLookupService.listFieldPermissions(PROCESS_MODEL_ID, WorkflowConstants.VirtualNode.START, TENANT_ID))
                .thenReturn(permissions);
        when(assigneeResolveService.resolveCurrentUserRealname(same(context))).thenReturn(REALNAME);
        when(assigneeResolveService.resolveDisplayName(REALNAME, USERNAME, USER_ID)).thenReturn(DISPLAY_NAME);
        doAnswer(invocation -> {
            FormInstance formInstance = invocation.getArgument(0);
            formInstance.setId(FORM_INSTANCE_ID);
            return 1;
        }).when(formInstanceMapper).insert(any(FormInstance.class));
        doAnswer(invocation -> {
            String formDataJson = invocation.getArgument(0);
            FormInstance formInstance = invocation.getArgument(1);
            formInstance.setFormDataJson(formDataJson);
            return null;
        }).when(workflowFormDataService).saveStartFormData(
                anyString(), any(FormInstance.class), any(), same(context), anyBoolean());
    }

    private StartProcessReq startReq(String title, String businessKey) {
        StartProcessReq req = new StartProcessReq();
        req.setProcessModelId(PROCESS_MODEL_ID);
        req.setInstanceTitle(title);
        req.setBusinessKey(businessKey);
        req.setFormDataJson(FORM_DATA_JSON);
        return req;
    }

    private ProcessModel processModel() {
        ProcessModel model = new ProcessModel();
        model.setId(PROCESS_MODEL_ID);
        model.setTenantId(TENANT_ID);
        model.setFormDefinitionId(FORM_DEFINITION_ID);
        model.setFlowableProcessDefinitionId("published-definition-1");
        model.setProcessName("测试流程");
        return model;
    }

    private FormDefinition formDefinition() {
        FormDefinition form = new FormDefinition();
        form.setId(FORM_DEFINITION_ID);
        form.setTenantId(TENANT_ID);
        form.setFormKey("test_form");
        form.setFormName("测试表单");
        form.setVersion(1);
        form.setSchemaJson("{}");
        form.setOptionJson("{}");
        return form;
    }

    private FieldPermission fieldPermission() {
        FieldPermission permission = new FieldPermission();
        permission.setFieldKey("reason");
        permission.setPermission(WorkflowConstants.FieldPermission.EDITABLE);
        return permission;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
