package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.AdminOperationRecord;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.AdminOperationRecordMapper;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.AdminMonitorActionReq;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.IProcessResultNotificationService;
import com.lawoffice.workflow.service.IRuntimeViewAssemblerService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AdminOperationRecordVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMonitorServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";
    private static final String FORM_INSTANCE_ID = "form-instance-1";
    private static final String FLOWABLE_INSTANCE_ID = "flowable-instance-1";
    private static final String TASK_ID = "task-1";
    private static final String FLOWABLE_TASK_ID = "flowable-task-1";
    private static final String TARGET_USER_ID = "user-2";

    @Mock
    private AdminOperationRecordMapper adminOperationRecordMapper;
    @Mock
    private CcRecordMapper ccRecordMapper;
    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private IFlowableService flowableService;
    @Mock
    private IInstanceStateService instanceStateService;
    @Mock
    private IProcessResultNotificationService processResultNotificationService;
    @Mock
    private IRuntimeViewAssemblerService runtimeViewAssemblerService;
    @Mock
    private ITaskNotificationService taskNotificationService;
    @Mock
    private IUserService userService;
    @Mock
    private OperationRecordMapper operationRecordMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserMapper userMapper;

    private AdminMonitorServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new AdminMonitorServiceImpl(
                adminOperationRecordMapper,
                ccRecordMapper,
                formInstanceMapper,
                flowableService,
                instanceStateService,
                processResultNotificationService,
                runtimeViewAssemblerService,
                taskNotificationService,
                userService,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                operationRecordMapper,
                processInstanceMapper,
                processModelMapper,
                taskCandidateMapper,
                taskMapper,
                new NoOpTransactionManager(),
                userMapper
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId("admin-1")
                .username("admin")
                .build();
    }

    @Test
    void shouldReassignRunningTodoAndWriteSuccessRecord() {
        ProcessInstance processInstance = runningProcessInstance();
        Task task = todoTask();
        User targetUser = targetUser();
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(userService.getTenantUserIds(TENANT_ID)).thenReturn(List.of(TARGET_USER_ID));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(targetUser, operatorUser());

        BaseResult<AdminOperationRecordVO> result = service.reassign(reassignReq(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(WorkflowConstants.AdminOperationType.REASSIGN, result.getData().getOperationType());
        assertEquals(WorkflowConstants.AdminOperationStatus.SUCCESS, result.getData().getStatus());
        assertEquals(TARGET_USER_ID, task.getAssigneeUserId());
        assertEquals("target", task.getAssigneeUsername());
        assertEquals("目标处理人", task.getAssigneeRealname());
        verify(taskNotificationService).expireTodoMessageActions(List.of(TASK_ID), TENANT_ID, context);
        verify(taskMapper).updateById(same(task));
        verify(taskCandidateMapper).update(eq(null), any(UpdateWrapper.class));
        verify(flowableService).setTaskAssignee(FLOWABLE_TASK_ID, TARGET_USER_ID);
        verify(instanceStateService).refreshCurrentTaskSummary(same(processInstance), eq(TENANT_ID));
        verify(processInstanceMapper).updateById(same(processInstance));
        verify(taskNotificationService).sendTodoArrivalMessage(processInstance, task, List.of(TARGET_USER_ID), context);
        ArgumentCaptor<AdminOperationRecord> recordCaptor = ArgumentCaptor.forClass(AdminOperationRecord.class);
        verify(adminOperationRecordMapper).insert(recordCaptor.capture());
        AdminOperationRecord record = recordCaptor.getValue();
        assertEquals(WorkflowConstants.AdminOperationType.REASSIGN, record.getOperationType());
        assertEquals(WorkflowConstants.AdminOperationStatus.SUCCESS, record.getStatus());
        assertEquals("管理员", record.getOperatorRealname());
        assertTrue(record.getAfterSnapshotJson().contains(TARGET_USER_ID));
    }

    @Test
    void shouldRejectReassignWhenGroupTargetAlreadyHasTodoAndWriteFailedRecord() {
        ProcessInstance processInstance = runningProcessInstance();
        Task task = todoTask();
        task.setTaskGroupId("group-1");
        User targetUser = targetUser();
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(userService.getTenantUserIds(TENANT_ID)).thenReturn(List.of(TARGET_USER_ID));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(targetUser, operatorUser());
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BaseResult<AdminOperationRecordVO> result = service.reassign(reassignReq(), context);

        assertEquals(400, result.getCode());
        assertEquals("该人员已存在当前会签/或签待办，不能重复改派", result.getMessage());
        verify(taskMapper, never()).updateById(any(Task.class));
        verify(flowableService, never()).setTaskAssignee(any(), any());
        ArgumentCaptor<AdminOperationRecord> recordCaptor = ArgumentCaptor.forClass(AdminOperationRecord.class);
        verify(adminOperationRecordMapper).insert(recordCaptor.capture());
        AdminOperationRecord record = recordCaptor.getValue();
        assertEquals(WorkflowConstants.AdminOperationType.REASSIGN, record.getOperationType());
        assertEquals(WorkflowConstants.AdminOperationStatus.FAILED, record.getStatus());
        assertEquals(result.getMessage(), record.getErrorMessage());
    }

    @Test
    void shouldTerminateRunningInstanceCancelTodosAndWriteSuccessRecord() {
        ProcessInstance processInstance = runningProcessInstance();
        FormInstance formInstance = formInstance();
        List<Task> tasks = List.of(todoTask(), todoTask("task-2", "flowable-task-2"));
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        when(formInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(formInstance);

        BaseResult<AdminOperationRecordVO> result = service.terminate(terminateReq(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(WorkflowConstants.AdminOperationType.TERMINATE, result.getData().getOperationType());
        assertEquals(WorkflowConstants.Status.TERMINATED, processInstance.getStatus());
        verify(flowableService).terminateProcessInstance(FLOWABLE_INSTANCE_ID, "异常终止");
        verify(taskMapper).update(eq(null), any(UpdateWrapper.class));
        verify(taskCandidateMapper).update(eq(null), any(UpdateWrapper.class));
        verify(taskNotificationService).expireTodoMessageActions(List.of("task-1", "task-2"), TENANT_ID, context);
        verify(processInstanceMapper).updateById(same(processInstance));
        verify(instanceStateService).archiveFormInstance(same(formInstance), same(context));
        verify(processResultNotificationService).sendProcessResultMessage(processInstance, context);
        ArgumentCaptor<AdminOperationRecord> recordCaptor = ArgumentCaptor.forClass(AdminOperationRecord.class);
        verify(adminOperationRecordMapper).insert(recordCaptor.capture());
        AdminOperationRecord record = recordCaptor.getValue();
        assertEquals(WorkflowConstants.AdminOperationType.TERMINATE, record.getOperationType());
        assertEquals(WorkflowConstants.AdminOperationStatus.SUCCESS, record.getStatus());
        assertTrue(record.getAfterSnapshotJson().contains(WorkflowConstants.Status.TERMINATED));
        assertTrue(record.getAfterSnapshotJson().contains("task-1"));
    }

    @Test
    void shouldRejectMaintenanceForFinishedInstanceAndWriteFailedRecord() {
        ProcessInstance processInstance = runningProcessInstance();
        processInstance.setStatus(WorkflowConstants.Status.APPROVED);
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);

        BaseResult<AdminOperationRecordVO> result = service.terminate(terminateReq(), context);

        assertEquals(400, result.getCode());
        assertEquals("当前流程状态不允许维护", result.getMessage());
        verify(flowableService, never()).terminateProcessInstance(any(), any());
        verify(taskMapper, never()).update(eq(null), any(UpdateWrapper.class));
        ArgumentCaptor<AdminOperationRecord> recordCaptor = ArgumentCaptor.forClass(AdminOperationRecord.class);
        verify(adminOperationRecordMapper).insert(recordCaptor.capture());
        AdminOperationRecord record = recordCaptor.getValue();
        assertEquals(WorkflowConstants.AdminOperationType.TERMINATE, record.getOperationType());
        assertEquals(WorkflowConstants.AdminOperationStatus.FAILED, record.getStatus());
    }

    @Test
    void shouldResendNoticeToCurrentTodoReceiversWithoutChangingRuntimeState() {
        ProcessInstance processInstance = runningProcessInstance();
        Task task = todoTask();
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(taskCandidateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(candidate("candidate-1")));

        BaseResult<AdminOperationRecordVO> result = service.resendNotice(resendNoticeReq(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(WorkflowConstants.AdminOperationType.RESEND_NOTICE, result.getData().getOperationType());
        verify(taskNotificationService).sendTodoArrivalMessage(processInstance, task, List.of("candidate-1"), context);
        verify(taskMapper, never()).updateById(any(Task.class));
        verify(taskMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(processInstanceMapper, never()).updateById(any(ProcessInstance.class));
        verify(flowableService, never()).setTaskAssignee(any(), any());
        ArgumentCaptor<AdminOperationRecord> recordCaptor = ArgumentCaptor.forClass(AdminOperationRecord.class);
        verify(adminOperationRecordMapper).insert(recordCaptor.capture());
        AdminOperationRecord record = recordCaptor.getValue();
        assertEquals(WorkflowConstants.AdminOperationType.RESEND_NOTICE, record.getOperationType());
        assertEquals(WorkflowConstants.AdminOperationStatus.SUCCESS, record.getStatus());
        assertTrue(record.getAfterSnapshotJson().contains("candidate-1"));
    }

    @Test
    void shouldReturnDetailWithAdminOperationRecords() {
        ProcessInstance processInstance = runningProcessInstance();
        FormInstance formInstance = formInstance();
        AdminOperationRecord adminRecord = successRecord();
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(formInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(formInstance);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(todoTask()));
        when(operationRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new OperationRecord()));
        when(ccRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new CcRecord()));
        when(runtimeViewAssemblerService.buildInstanceDetail(any(), any(), any(), any(), any(), same(context)))
                .thenReturn(new InstanceDetailVO());
        when(adminOperationRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(adminRecord));

        BaseResult<AdminMonitorDetailVO> result = service.detail(PROCESS_INSTANCE_ID, context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getAdminOperationRecords().size());
        assertEquals(WorkflowConstants.AdminOperationType.REASSIGN,
                result.getData().getAdminOperationRecords().get(0).getOperationType());
    }

    private AdminMonitorActionReq reassignReq() {
        AdminMonitorActionReq req = baseReq("测试改派");
        req.setTaskId(TASK_ID);
        req.setTargetUserId(TARGET_USER_ID);
        return req;
    }

    private AdminMonitorActionReq terminateReq() {
        return baseReq("异常终止");
    }

    private AdminMonitorActionReq resendNoticeReq() {
        return baseReq("补发通知");
    }

    private AdminMonitorActionReq baseReq(String reason) {
        AdminMonitorActionReq req = new AdminMonitorActionReq();
        req.setProcessInstanceId(PROCESS_INSTANCE_ID);
        req.setOperationReason(reason);
        return req;
    }

    private ProcessInstance runningProcessInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(PROCESS_INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setProcessModelId("model-1");
        instance.setFormInstanceId(FORM_INSTANCE_ID);
        instance.setFlowableProcessInstanceId(FLOWABLE_INSTANCE_ID);
        instance.setStatus(WorkflowConstants.Status.RUNNING);
        instance.setCurrentTaskNames("部门审批");
        instance.setCurrentAssigneeNames("原处理人");
        return instance;
    }

    private FormInstance formInstance() {
        FormInstance formInstance = new FormInstance();
        formInstance.setId(FORM_INSTANCE_ID);
        formInstance.setTenantId(TENANT_ID);
        formInstance.setProcessInstanceId(PROCESS_INSTANCE_ID);
        formInstance.setStatus(WorkflowConstants.Status.ACTIVE);
        return formInstance;
    }

    private Task todoTask() {
        return todoTask(TASK_ID, FLOWABLE_TASK_ID);
    }

    private Task todoTask(String taskId, String flowableTaskId) {
        Task task = new Task();
        task.setId(taskId);
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId(PROCESS_INSTANCE_ID);
        task.setFlowableTaskId(flowableTaskId);
        task.setTaskName("部门审批");
        task.setAssigneeUserId("user-1");
        task.setAssigneeUsername("origin");
        task.setAssigneeRealname("原处理人");
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private User targetUser() {
        User user = new User();
        user.setId(TARGET_USER_ID);
        user.setUsername("target");
        user.setRealname("目标处理人");
        user.setStatus(1);
        return user;
    }

    private User operatorUser() {
        User user = new User();
        user.setId("admin-1");
        user.setUsername("admin");
        user.setRealname("管理员");
        user.setStatus(1);
        return user;
    }

    private TaskCandidate candidate(String userId) {
        TaskCandidate candidate = new TaskCandidate();
        candidate.setTaskId(TASK_ID);
        candidate.setCandidateUserId(userId);
        candidate.setStatus(WorkflowConstants.Status.ACTIVE);
        return candidate;
    }

    private AdminOperationRecord successRecord() {
        AdminOperationRecord record = new AdminOperationRecord();
        record.setId("record-1");
        record.setTenantId(TENANT_ID);
        record.setProcessInstanceId(PROCESS_INSTANCE_ID);
        record.setTaskId(TASK_ID);
        record.setOperationType(WorkflowConstants.AdminOperationType.REASSIGN);
        record.setOperationReason("测试改派");
        record.setStatus(WorkflowConstants.AdminOperationStatus.SUCCESS);
        return record;
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
