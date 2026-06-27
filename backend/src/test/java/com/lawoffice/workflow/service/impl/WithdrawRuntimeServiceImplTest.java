package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceAssigneeMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IProcessResultNotificationService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import com.lawoffice.workflow.vo.TaskActionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawRuntimeServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "starter-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";
    private static final String FLOWABLE_INSTANCE_ID = "flowable-instance-1";
    private static final String FORM_INSTANCE_ID = "form-instance-1";

    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private OperationRecordMapper operationRecordMapper;
    @Mock
    private ProcessInstanceAssigneeMapper processInstanceAssigneeMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private IFlowableService flowableService;
    @Mock
    private IMessageService messageService;
    @Mock
    private IProcessResultNotificationService processResultNotificationService;
    @Mock
    private ITaskNotificationService taskNotificationService;

    private WithdrawRuntimeServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new WithdrawRuntimeServiceImpl(
                formInstanceMapper,
                operationRecordMapper,
                processInstanceAssigneeMapper,
                processInstanceMapper,
                taskCandidateMapper,
                taskMapper,
                flowableService,
                messageService,
                processResultNotificationService,
                taskNotificationService
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .username("starter")
                .build();
    }

    @Test
    void shouldWithdrawRunningInstanceBeforeApproverHandled() {
        ProcessInstance processInstance = processInstance();
        FormInstance formInstance = formInstance();
        Task task = task();
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance);
        when(operationRecordMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(formInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(formInstance);
        when(taskCandidateMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BaseResult<TaskActionVO> result = service.withdraw(PROCESS_INSTANCE_ID, context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(WorkflowConstants.Status.WITHDRAWN, result.getData().getProcessStatus());
        assertEquals(WorkflowConstants.Status.WITHDRAWN, processInstance.getStatus());
        verify(flowableService).terminateProcessInstance(FLOWABLE_INSTANCE_ID, "发起人撤回");
        verify(taskMapper).update(eq(null), any(UpdateWrapper.class));
        verify(taskCandidateMapper).update(eq(null), any(UpdateWrapper.class));
        verify(processInstanceAssigneeMapper).update(eq(null), any(UpdateWrapper.class));
        verify(processInstanceMapper).updateById(processInstance);
        verify(formInstanceMapper).updateById(formInstance);
        ArgumentCaptor<OperationRecord> recordCaptor = ArgumentCaptor.forClass(OperationRecord.class);
        verify(operationRecordMapper).insert(recordCaptor.capture());
        assertEquals(WorkflowConstants.Action.WITHDRAW, recordCaptor.getValue().getAction());
        verify(messageService, never()).sendMessage(any(SendMessageReq.class), eq("starter"));
        verify(processResultNotificationService).sendProcessResultMessage(processInstance, context);
        verify(taskNotificationService).expireTodoMessageActions(List.of(task.getId()), TENANT_ID, context);
    }

    @Test
    void shouldRejectWhenCurrentUserIsNotStarter() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BaseResult<TaskActionVO> result = service.withdraw(PROCESS_INSTANCE_ID, context);

        assertEquals(400, result.getCode());
        verify(flowableService, never()).terminateProcessInstance(any(), any());
    }

    @Test
    void shouldRejectWhenApproverAlreadyHandled() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance());
        when(operationRecordMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BaseResult<TaskActionVO> result = service.withdraw(PROCESS_INSTANCE_ID, context);

        assertEquals(400, result.getCode());
        verify(taskMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(flowableService, never()).terminateProcessInstance(any(), any());
    }

    private ProcessInstance processInstance() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(PROCESS_INSTANCE_ID);
        processInstance.setTenantId(TENANT_ID);
        processInstance.setStarterUserId(USER_ID);
        processInstance.setStarterUsername("starter");
        processInstance.setStarterRealname("发起人");
        processInstance.setFormInstanceId(FORM_INSTANCE_ID);
        processInstance.setFlowableProcessInstanceId(FLOWABLE_INSTANCE_ID);
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        processInstance.setCurrentTaskNames("部门审批");
        processInstance.setCurrentAssigneeNames("审批人");
        return processInstance;
    }

    private FormInstance formInstance() {
        FormInstance formInstance = new FormInstance();
        formInstance.setId(FORM_INSTANCE_ID);
        formInstance.setTenantId(TENANT_ID);
        formInstance.setProcessInstanceId(PROCESS_INSTANCE_ID);
        formInstance.setStatus(WorkflowConstants.Status.ACTIVE);
        formInstance.setFormDataJson("{\"title\":\"test\"}");
        return formInstance;
    }

    private Task task() {
        Task task = new Task();
        task.setId("task-1");
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId(PROCESS_INSTANCE_ID);
        task.setFlowableTaskId("flowable-task-1");
        task.setNodeId("node-1");
        task.setTaskName("部门审批");
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }
}
