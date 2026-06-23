package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.vo.ReminderRecordVO;
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
class ReminderRuntimeServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "starter-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";

    @Mock
    private OperationRecordMapper operationRecordMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private ReminderRecordMapper reminderRecordMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private IMessageService messageService;

    private ReminderRuntimeServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ReminderRuntimeServiceImpl(
                operationRecordMapper,
                processInstanceMapper,
                reminderRecordMapper,
                taskCandidateMapper,
                taskMapper,
                messageService
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .username("starter")
                .build();
    }

    @Test
    void shouldUrgeCurrentTodoReceiversWithoutChangingTaskStatus() {
        MessageSendResultVO sendResult = new MessageSendResultVO();
        sendResult.setMessageId("message-1");
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance());
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task()));
        when(reminderRecordMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskCandidateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(candidate()));
        when(messageService.sendMessage(any(SendMessageReq.class), eq("starter"))).thenReturn(sendResult);

        BaseResult<List<ReminderRecordVO>> result = service.urge(PROCESS_INSTANCE_ID, "请尽快处理", context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        ArgumentCaptor<ReminderRecord> reminderCaptor = ArgumentCaptor.forClass(ReminderRecord.class);
        verify(reminderRecordMapper, org.mockito.Mockito.times(2)).insert(reminderCaptor.capture());
        assertEquals(WorkflowConstants.RemindType.URGE, reminderCaptor.getAllValues().get(0).getRemindType());
        ArgumentCaptor<OperationRecord> operationCaptor = ArgumentCaptor.forClass(OperationRecord.class);
        verify(operationRecordMapper).insert(operationCaptor.capture());
        assertEquals(WorkflowConstants.Action.URGE, operationCaptor.getValue().getAction());
        verify(taskMapper, never()).updateById(any());
        ArgumentCaptor<SendMessageReq> messageCaptor = ArgumentCaptor.forClass(SendMessageReq.class);
        verify(messageService, org.mockito.Mockito.times(2)).sendMessage(messageCaptor.capture(), eq("starter"));
        assertEquals("{\"instanceId\":\"instance-1\",\"taskId\":\"task-1\"}",
                messageCaptor.getAllValues().get(0).getActions().get(0).getRouteQuery());
    }

    @Test
    void shouldRejectWhenCurrentUserIsNotStarter() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BaseResult<List<ReminderRecordVO>> result = service.urge(PROCESS_INSTANCE_ID, null, context);

        assertEquals(400, result.getCode());
        verify(reminderRecordMapper, never()).insert(any());
        verify(messageService, never()).sendMessage(any(), any());
    }

    @Test
    void shouldRejectWhenRecentlyUrged() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance());
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task()));
        when(reminderRecordMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BaseResult<List<ReminderRecordVO>> result = service.urge(PROCESS_INSTANCE_ID, null, context);

        assertEquals(400, result.getCode());
        verify(reminderRecordMapper, never()).insert(any());
        verify(messageService, never()).sendMessage(any(), any());
    }

    private ProcessInstance processInstance() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(PROCESS_INSTANCE_ID);
        processInstance.setTenantId(TENANT_ID);
        processInstance.setInstanceTitle("测试审批");
        processInstance.setStarterUserId(USER_ID);
        processInstance.setStarterUsername("starter");
        processInstance.setStarterRealname("发起人");
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        return processInstance;
    }

    private Task task() {
        Task task = new Task();
        task.setId("task-1");
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId(PROCESS_INSTANCE_ID);
        task.setFlowableTaskId("flowable-task-1");
        task.setNodeId("approve_1");
        task.setTaskName("部门审批");
        task.setAssigneeUserId("assignee-1");
        task.setAssigneeUsername("assignee");
        task.setAssigneeRealname("审批人");
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private TaskCandidate candidate() {
        TaskCandidate candidate = new TaskCandidate();
        candidate.setTaskId("task-1");
        candidate.setFlowableTaskId("flowable-task-1");
        candidate.setCandidateUserId("candidate-1");
        candidate.setCandidateUsername("candidate");
        candidate.setCandidateRealname("候选人");
        candidate.setStatus(WorkflowConstants.Status.ACTIVE);
        return candidate;
    }
}
