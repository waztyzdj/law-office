package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeoutReminderRuntimeServiceImplTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private IMessageService messageService;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private ProcessNodeConfigMapper processNodeConfigMapper;
    @Mock
    private ReminderRecordMapper reminderRecordMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TenantMapper tenantMapper;

    private TimeoutReminderRuntimeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TimeoutReminderRuntimeServiceImpl(
                messageService,
                processInstanceMapper,
                processNodeConfigMapper,
                reminderRecordMapper,
                taskCandidateMapper,
                taskMapper,
                tenantMapper
        );
    }

    @Test
    void shouldSendTimeoutReminderToAssignedTask() {
        MessageSendResultVO sendResult = new MessageSendResultVO();
        sendResult.setMessageId("message-1");
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(assignedTask()));
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance());
        when(processNodeConfigMapper.selectOne(any(Wrapper.class))).thenReturn(timeoutNodeConfig());
        when(reminderRecordMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(messageService.sendMessage(any(SendMessageReq.class), eq("starter"))).thenReturn(sendResult);

        int count = service.scanTenant(TENANT_ID);

        assertEquals(1, count);
        ArgumentCaptor<ReminderRecord> recordCaptor = ArgumentCaptor.forClass(ReminderRecord.class);
        verify(reminderRecordMapper).insert(recordCaptor.capture());
        assertEquals(WorkflowConstants.RemindType.TIMEOUT, recordCaptor.getValue().getRemindType());
        assertEquals("assignee-1", recordCaptor.getValue().getReceiverUserId());
        verify(taskMapper).update(any(), any());
        verify(messageService).sendMessage(any(SendMessageReq.class), eq("starter"));
    }

    @Test
    void shouldSkipWhenReminderIntervalNotReached() {
        Task task = assignedTask();
        task.setRemindCount(1);
        task.setLastRemindTime(LocalDateTime.now().minusMinutes(30));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance());
        when(processNodeConfigMapper.selectOne(any(Wrapper.class))).thenReturn(timeoutNodeConfig());

        int count = service.scanTenant(TENANT_ID);

        assertEquals(0, count);
        verify(reminderRecordMapper, never()).insert(any());
        verify(messageService, never()).sendMessage(any(), any());
    }

    @Test
    void shouldSendTimeoutReminderToActiveCandidatesWhenTaskUnassigned() {
        Task task = assignedTask();
        task.setAssigneeUserId(null);
        task.setAssigneeUsername(null);
        task.setAssigneeRealname(null);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance());
        when(processNodeConfigMapper.selectOne(any(Wrapper.class))).thenReturn(timeoutNodeConfig());
        when(taskCandidateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(candidate("u1"), candidate("u2")));
        when(reminderRecordMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        int count = service.scanTenant(TENANT_ID);

        assertEquals(2, count);
        ArgumentCaptor<ReminderRecord> recordCaptor = ArgumentCaptor.forClass(ReminderRecord.class);
        verify(reminderRecordMapper, org.mockito.Mockito.times(2)).insert(recordCaptor.capture());
        assertEquals(List.of("u1", "u2"), recordCaptor.getAllValues().stream()
                .map(ReminderRecord::getReceiverUserId)
                .toList());
    }

    private Task assignedTask() {
        Task task = new Task();
        task.setId("task-1");
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId("instance-1");
        task.setFlowableTaskId("flowable-task-1");
        task.setNodeId("approve_1");
        task.setTaskName("部门审批");
        task.setAssigneeUserId("assignee-1");
        task.setAssigneeUsername("assignee");
        task.setAssigneeRealname("审批人");
        task.setDueTime(LocalDateTime.now().minusMinutes(5));
        task.setRemindCount(0);
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private ProcessInstance processInstance() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId("instance-1");
        processInstance.setTenantId(TENANT_ID);
        processInstance.setProcessModelId("model-1");
        processInstance.setInstanceTitle("测试审批");
        processInstance.setStarterUsername("starter");
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        return processInstance;
    }

    private ProcessNodeConfig timeoutNodeConfig() {
        ProcessNodeConfig config = new ProcessNodeConfig();
        config.setTenantId(TENANT_ID);
        config.setProcessModelId("model-1");
        config.setNodeId("approve_1");
        config.setTimeoutJson("""
                {"enabled":true,"timeoutMinutes":1,"remindIntervalMinutes":60,"maxRemindCount":3,"channels":["site"]}
                """);
        return config;
    }

    private TaskCandidate candidate(String userId) {
        TaskCandidate candidate = new TaskCandidate();
        candidate.setTaskId("task-1");
        candidate.setCandidateUserId(userId);
        candidate.setCandidateUsername(userId);
        candidate.setCandidateRealname(userId);
        candidate.setStatus(WorkflowConstants.Status.ACTIVE);
        return candidate;
    }
}
