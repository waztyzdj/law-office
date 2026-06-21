package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAccessServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String INSTANCE_ID = "instance-1";
    private static final String STARTER_ID = "starter-1";
    private static final String USER_ID = "user-1";

    @Mock
    private OperationRecordMapper operationRecordMapper;
    @Mock
    private CcRecordMapper ccRecordMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private IWorkflowRuntimeLookupService workflowRuntimeLookupService;

    private RuntimeAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuntimeAccessServiceImpl(
                operationRecordMapper,
                ccRecordMapper,
                taskCandidateMapper,
                taskMapper,
                workflowRuntimeLookupService
        );
    }

    @Test
    void shouldAllowStarterWithoutQueryingTaskOrRecord() {
        ProcessInstance instance = processInstance();
        when(workflowRuntimeLookupService.requireUserId(any(RequestContext.class))).thenReturn(STARTER_ID);

        assertDoesNotThrow(() -> service.ensureInstanceAccess(instance, context(STARTER_ID)));

        verifyNoInteractions(taskMapper, taskCandidateMapper, operationRecordMapper, ccRecordMapper);
    }

    @Test
    void shouldAllowDirectTaskAssignee() {
        when(workflowRuntimeLookupService.requireUserId(any(RequestContext.class))).thenReturn(USER_ID);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertDoesNotThrow(() -> service.ensureInstanceAccess(processInstance(), context(USER_ID)));

        verifyNoInteractions(taskCandidateMapper, operationRecordMapper);
    }

    @Test
    void shouldAllowCandidateTaskUser() {
        Task task = new Task();
        task.setId("task-1");
        when(workflowRuntimeLookupService.requireUserId(any(RequestContext.class))).thenReturn(USER_ID);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(taskCandidateMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertDoesNotThrow(() -> service.ensureInstanceAccess(processInstance(), context(USER_ID)));

        verifyNoInteractions(operationRecordMapper);
    }

    @Test
    void shouldAllowOperationRecordUser() {
        Task task = new Task();
        task.setId("task-1");
        when(workflowRuntimeLookupService.requireUserId(any(RequestContext.class))).thenReturn(USER_ID);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(taskCandidateMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(operationRecordMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertDoesNotThrow(() -> service.ensureInstanceAccess(processInstance(), context(USER_ID)));
    }

    @Test
    void shouldRejectUnrelatedUser() {
        Task task = new Task();
        task.setId("task-1");
        when(workflowRuntimeLookupService.requireUserId(any(RequestContext.class))).thenReturn(USER_ID);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(taskCandidateMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(operationRecordMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.ensureInstanceAccess(processInstance(), context(USER_ID))
        );

        assertEquals("当前用户无权查看该审批实例", exception.getMessage());
    }

    private ProcessInstance processInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setStarterUserId(STARTER_ID);
        return instance;
    }

    private RequestContext context(String userId) {
        return RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(userId)
                .username(userId)
                .build();
    }
}
