package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.message.mapper.SysMessageActionMapper;
import com.lawoffice.system.entity.User;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IArchiveService;
import com.lawoffice.workflow.service.ICcRuntimeService;
import com.lawoffice.workflow.service.IConditionBranchRuntimeService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.service.IProcessResultNotificationService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import com.lawoffice.workflow.service.IWorkflowFormDataService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import com.lawoffice.workflow.vo.TaskActionVO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskActionServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";
    private static final String PROCESS_MODEL_ID = "model-1";
    private static final String FORM_INSTANCE_ID = "form-instance-1";
    private static final String FLOWABLE_INSTANCE_ID = "flowable-instance-1";
    private static final String FLOWABLE_TASK_ID = "flowable-task-1";
    private static final String GROUP_ID = "group-1";
    private static final String USER_ID = "user-1";

    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private ReminderRecordMapper reminderRecordMapper;
    @Mock
    private SysMessageActionMapper sysMessageActionMapper;
    @Mock
    private IArchiveService archiveService;
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
    private IProcessNodeConfigService processNodeConfigService;
    @Mock
    private IProcessResultNotificationService processResultNotificationService;
    @Mock
    private ITaskNotificationService taskNotificationService;
    @Mock
    private IWorkflowFormDataService workflowFormDataService;
    @Mock
    private IWorkflowRuntimeLookupService workflowRuntimeLookupService;

    private TaskActionServiceImpl service;
    private RequestContext context;
    private ProcessInstance processInstance;
    private FormInstance formInstance;

    @BeforeEach
    void setUp() {
        service = new TaskActionServiceImpl(
                formInstanceMapper,
                processInstanceMapper,
                taskMapper,
                taskCandidateMapper,
                reminderRecordMapper,
                sysMessageActionMapper,
                archiveService,
                conditionBranchRuntimeService,
                ccRuntimeService,
                flowableService,
                assigneeResolveService,
                instanceStateService,
                processNodeConfigService,
                processResultNotificationService,
                taskNotificationService,
                workflowFormDataService,
                workflowRuntimeLookupService,
                new NoOpTransactionManager()
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .username("u1")
                .build();
        processInstance = processInstance();
        formInstance = formInstance();
        lenient().when(conditionBranchRuntimeService.matchNextBranch(
                any(), any(ProcessInstance.class), any(FormInstance.class),
                anyString(), any(), anyString(), any(RequestContext.class)))
                .thenReturn(java.util.Optional.empty());
        lenient().when(conditionBranchRuntimeService.buildFlowableVariables(any()))
                .thenReturn(Map.of());
        lenient().when(archiveService.archiveAutomatically(anyString(), any(RequestContext.class)))
                .thenReturn(BaseResult.success(null));
    }

    @Test
    void shouldNotCompleteFlowableWhenCountersignGroupIsNotFinished() {
        Task task = groupTask("task-1", WorkflowConstants.ApprovalMode.COUNTERSIGN, USER_ID);
        mockCommonLookup(task);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 0L, 0L, 1L);

        BaseResult<TaskActionVO> result = service.approve(task.getId(), req(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        verify(instanceStateService).markTaskDone(same(task), same(context));
        verify(flowableService, never()).completeTask(anyString(), anyMap());
        verify(assigneeResolveService, never()).saveNextAssigneeSnapshot(any(), anyString(), any(), anyString(), any());
        verify(instanceStateService).refreshCurrentTaskSummary(same(processInstance), eq(TENANT_ID));
    }

    @Test
    void shouldCompleteFlowableAndCancelSiblingsWhenOrsignApproved() {
        Task task = groupTask("task-2", WorkflowConstants.ApprovalMode.ORSIGN, USER_ID);
        Task anchor = groupTask("task-1", WorkflowConstants.ApprovalMode.ORSIGN, "user-0");
        anchor.setFlowableTaskId(FLOWABLE_TASK_ID);
        mockCommonLookup(task);
        Task transferredSibling = siblingTask("task-4", "user-4");
        transferredSibling.setStatus(WorkflowConstants.Status.TRANSFERRED);
        when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(siblingTask(), transferredSibling), List.of(addSignTask("task-5", "task-4")));
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 1L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(anchor);
        when(reminderRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(reminderRecord("message-1")));
        when(flowableService.isProcessInstanceActive(FLOWABLE_INSTANCE_ID)).thenReturn(true);

        BaseResult<TaskActionVO> result = service.approve(task.getId(), req(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        verify(instanceStateService).markTaskDone(same(task), same(context));
        verify(flowableService).completeTask(eq(FLOWABLE_TASK_ID), anyMap());
        ArgumentCaptor<Wrapper> selectWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper, times(2)).selectList(selectWrapperCaptor.capture());
        assertTrue(selectWrapperCaptor.getAllValues().get(0).getSqlSegment().contains("status IN"));
        ArgumentCaptor<UpdateWrapper> updateWrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskMapper, times(3)).update(eq(null), updateWrapperCaptor.capture());
        assertTrue(updateWrapperCaptor.getAllValues().stream()
                .map(UpdateWrapper::getSqlSegment)
                .anyMatch(segment -> segment.contains("parent_task_id")));
        ArgumentCaptor<UpdateWrapper> messageActionUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sysMessageActionMapper).update(eq(null), messageActionUpdateCaptor.capture());
        assertTrue(messageActionUpdateCaptor.getValue().getSqlSegment().contains("biz_type"));
        verify(assigneeResolveService).syncCurrentTasks(same(processInstance), eq(TENANT_ID), same(context));
    }

    @Test
    void shouldReuseExistingTodoWhenOrsignTransferTargetAlreadyHasGroupTask() {
        Task task = groupTask("task-2", WorkflowConstants.ApprovalMode.ORSIGN, USER_ID);
        Task existingTargetTask = groupTask("task-4", WorkflowConstants.ApprovalMode.ORSIGN, "user-4");
        mockTransferLookup(task, targetUser("user-4"));
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(existingTargetTask);

        BaseResult<TaskActionVO> result = service.transfer(task.getId(), targetReq("user-4"), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertEquals(existingTargetTask.getId(), result.getData().getTaskId());
        verify(taskMapper).updateById(same(task));
        verify(taskMapper, never()).insert(any(Task.class));
        verify(flowableService, never()).addCandidateUsers(anyString(), any());
        verify(instanceStateService).cancelActiveCandidates(same(task), same(context));
    }

    @Test
    void shouldShrinkCountersignTotalWhenTransferredToExistingGroupAssignee() {
        Task task = groupTask("task-1", WorkflowConstants.ApprovalMode.COUNTERSIGN, USER_ID);
        task.setGroupTotal(3);
        Task existingTargetTask = groupTask("task-3", WorkflowConstants.ApprovalMode.COUNTERSIGN, "user-3");
        existingTargetTask.setGroupTotal(3);
        mockTransferLookup(task, targetUser("user-3"));
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(existingTargetTask);

        BaseResult<TaskActionVO> result = service.transfer(task.getId(), targetReq("user-3"), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertEquals(existingTargetTask.getId(), result.getData().getTaskId());
        assertEquals(2, task.getGroupTotal());
        verify(taskMapper).update(eq(null), any(UpdateWrapper.class));
        verify(taskMapper, never()).insert(any(Task.class));
        verify(flowableService, never()).addCandidateUsers(anyString(), any());
    }

    @Test
    void shouldAdvanceCountersignAfterMergedTransferWhenRemainingAssigneesDone() {
        Task task = groupTask("task-3", WorkflowConstants.ApprovalMode.COUNTERSIGN, USER_ID);
        task.setGroupTotal(2);
        Task anchor = groupTask("task-1", WorkflowConstants.ApprovalMode.COUNTERSIGN, USER_ID);
        anchor.setFlowableTaskId(FLOWABLE_TASK_ID);
        mockCommonLookup(task);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L, 1L, 2L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(anchor);
        when(flowableService.isProcessInstanceActive(FLOWABLE_INSTANCE_ID)).thenReturn(true);

        BaseResult<TaskActionVO> result = service.approve(task.getId(), req(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        verify(flowableService).completeTask(eq(FLOWABLE_TASK_ID), anyMap());
        verify(assigneeResolveService).syncCurrentTasks(same(processInstance), eq(TENANT_ID), same(context));
    }

    @Test
    void shouldCreateGroupTodoAndUseAnchorWhenOrsignTransferredToNewUser() {
        Task task = groupTask("task-2", WorkflowConstants.ApprovalMode.ORSIGN, USER_ID);
        Task anchor = groupTask("task-1", WorkflowConstants.ApprovalMode.ORSIGN, "user-0");
        anchor.setFlowableTaskId(FLOWABLE_TASK_ID);
        mockTransferLookup(task, targetUser("user-4"));
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null, anchor);

        BaseResult<TaskActionVO> result = service.transfer(task.getId(), targetReq("user-4"), context);

        assertEquals(200, result.getCode(), result.getMessage());
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());
        Task insertedTask = taskCaptor.getValue();
        assertNotNull(insertedTask.getId());
        assertEquals("user-4", insertedTask.getAssigneeUserId());
        assertEquals(WorkflowConstants.Status.TODO, insertedTask.getStatus());
        assertEquals(WorkflowConstants.TaskType.TRANSFER, insertedTask.getTaskType());
        assertEquals(WorkflowConstants.ApprovalMode.ORSIGN, insertedTask.getApprovalMode());
        assertEquals(GROUP_ID, insertedTask.getTaskGroupId());
        assertEquals(task.getId(), insertedTask.getParentTaskId());
        assertEquals("group:" + insertedTask.getId(), insertedTask.getFlowableTaskId());
        assertEquals(insertedTask.getId(), result.getData().getTaskId());
        verify(flowableService).addCandidateUsers(eq(FLOWABLE_TASK_ID), eq(List.of("user-4")));
        verify(flowableService, never()).addCandidateUsers(eq(task.getFlowableTaskId()), any());
    }

    @Test
    void shouldNotCreateAddSignTaskWhenTargetAlreadyHasCurrentStepTodo() {
        Task task = groupTask("task-2", WorkflowConstants.ApprovalMode.ORSIGN, USER_ID);
        Task existingTargetTask = groupTask("task-4", WorkflowConstants.ApprovalMode.ORSIGN, "user-4");
        mockAddSignLookup(task, targetUser("user-4"));
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(existingTargetTask);

        BaseResult<TaskActionVO> result = service.addSign(task.getId(), targetReq("user-4"), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertEquals(existingTargetTask.getId(), result.getData().getTaskId());
        verify(taskMapper, never()).insert(any(Task.class));
        verify(taskMapper, never()).updateById(same(task));
        verify(instanceStateService, never()).cancelActiveCandidates(same(task), same(context));
    }

    @Test
    void shouldRejectAddSignCompletionWhenParentTaskAlreadyCanceled() {
        Task addSignTask = addSignTask("add-sign-1", "task-2");
        Task parentTask = groupTask("task-2", WorkflowConstants.ApprovalMode.ORSIGN, "user-2");
        parentTask.setStatus(WorkflowConstants.Status.CANCELED);
        mockCommonLookup(addSignTask);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(parentTask);

        BaseResult<TaskActionVO> result = service.approve(addSignTask.getId(), req(), context);

        assertEquals(400, result.getCode());
        verify(instanceStateService, never()).markTaskDone(same(addSignTask), same(context));
        verify(taskMapper, never()).updateById(same(parentTask));
        verify(assigneeResolveService, never()).syncCurrentTasks(any(), anyString(), any());
    }

    private void mockCommonLookup(Task task) {
        when(workflowRuntimeLookupService.requireTenantId(same(context))).thenReturn(TENANT_ID);
        when(workflowRuntimeLookupService.requireTodoTask(task.getId(), TENANT_ID)).thenReturn(task);
        when(workflowRuntimeLookupService.requireProcessInstance(PROCESS_INSTANCE_ID, TENANT_ID)).thenReturn(processInstance);
        when(workflowRuntimeLookupService.requireFormInstance(FORM_INSTANCE_ID, TENANT_ID)).thenReturn(formInstance);
        when(workflowRuntimeLookupService.listFieldPermissions(PROCESS_MODEL_ID, "approve_1", TENANT_ID))
                .thenReturn(List.of(new FieldPermission()));
        lenient().when(workflowRuntimeLookupService.requireRuntimeModel(PROCESS_MODEL_ID, TENANT_ID)).thenReturn(runtimeModel());
    }

    private void mockTransferLookup(Task task, User targetUser) {
        mockActionLookup(task, targetUser, nodeConfig(1, 0));
    }

    private void mockAddSignLookup(Task task, User targetUser) {
        mockActionLookup(task, targetUser, nodeConfig(0, 1));
    }

    private void mockActionLookup(Task task, User targetUser, ProcessNodeConfig nodeConfig) {
        when(workflowRuntimeLookupService.requireTenantId(same(context))).thenReturn(TENANT_ID);
        when(workflowRuntimeLookupService.requireTodoTask(task.getId(), TENANT_ID)).thenReturn(task);
        when(workflowRuntimeLookupService.requireProcessInstance(PROCESS_INSTANCE_ID, TENANT_ID)).thenReturn(processInstance);
        when(processNodeConfigService.requireRuntimeNodeConfig(PROCESS_MODEL_ID, task.getNodeId(), TENANT_ID))
                .thenReturn(nodeConfig);
        when(assigneeResolveService.loadTenantActiveUsers(List.of(targetUser.getId()), TENANT_ID))
                .thenReturn(Map.of(targetUser.getId(), targetUser));
        when(workflowRuntimeLookupService.requireFormInstance(FORM_INSTANCE_ID, TENANT_ID)).thenReturn(formInstance);
    }

    private TaskActionReq req() {
        TaskActionReq req = new TaskActionReq();
        req.setComment("同意");
        return req;
    }

    private TaskActionReq targetReq(String targetUserId) {
        TaskActionReq req = req();
        req.setTargetUserId(targetUserId);
        return req;
    }

    private Task groupTask(String id, String approvalMode, String assigneeUserId) {
        Task task = new Task();
        task.setId(id);
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId(PROCESS_INSTANCE_ID);
        task.setFlowableTaskId("group:" + id);
        task.setNodeId("approve_1");
        task.setTaskName("审批");
        task.setTaskType(approvalMode);
        task.setApprovalMode(approvalMode);
        task.setTaskGroupId(GROUP_ID);
        task.setGroupTotal(2);
        task.setGroupCompleted(0);
        task.setAssigneeUserId(assigneeUserId);
        task.setAssigneeUsername("u1");
        task.setAssigneeRealname("审批人");
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private Task siblingTask() {
        return siblingTask("task-3", "user-3");
    }

    private Task siblingTask(String id, String assigneeUserId) {
        Task task = groupTask(id, WorkflowConstants.ApprovalMode.ORSIGN, assigneeUserId);
        task.setFlowableTaskId("group:" + id);
        return task;
    }

    private Task addSignTask(String id, String parentTaskId) {
        Task task = groupTask(id, WorkflowConstants.ApprovalMode.ORSIGN, USER_ID);
        task.setParentTaskId(parentTaskId);
        task.setFlowableTaskId("ADD_SIGN_" + id);
        task.setTaskType(WorkflowConstants.TaskType.ADD_SIGN);
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private ReminderRecord reminderRecord(String messageId) {
        ReminderRecord record = new ReminderRecord();
        record.setMessageId(messageId);
        record.setRemindType(WorkflowConstants.RemindType.URGE);
        return record;
    }

    private ProcessNodeConfig nodeConfig(Integer allowTransfer, Integer allowAddSign) {
        ProcessNodeConfig nodeConfig = new ProcessNodeConfig();
        nodeConfig.setProcessModelId(PROCESS_MODEL_ID);
        nodeConfig.setNodeId("approve_1");
        nodeConfig.setAllowTransfer(allowTransfer);
        nodeConfig.setAllowAddSign(allowAddSign);
        nodeConfig.setAllowReturn(1);
        return nodeConfig;
    }

    private User targetUser(String userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("target_" + userId);
        user.setRealname("鐩爣浜?");
        return user;
    }

    private ProcessInstance processInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(PROCESS_INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setProcessModelId(PROCESS_MODEL_ID);
        instance.setFormInstanceId(FORM_INSTANCE_ID);
        instance.setFlowableProcessInstanceId(FLOWABLE_INSTANCE_ID);
        instance.setStatus(WorkflowConstants.Status.RUNNING);
        return instance;
    }

    private FormInstance formInstance() {
        FormInstance instance = new FormInstance();
        instance.setId(FORM_INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setFormDataJson("{}");
        return instance;
    }

    private ProcessModel runtimeModel() {
        ProcessModel model = new ProcessModel();
        model.setId(PROCESS_MODEL_ID);
        model.setTenantId(TENANT_ID);
        model.setProcessKey("process-key");
        model.setVersion(1);
        model.setStatus(WorkflowConstants.Status.DISABLED);
        return model;
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
