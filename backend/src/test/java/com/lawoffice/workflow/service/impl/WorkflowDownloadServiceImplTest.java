package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.service.IWorkflowRuntimeLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDownloadServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";

    @Mock
    private IWorkflowRuntimeLookupService workflowRuntimeLookupService;
    @Mock
    private IRuntimeAccessService runtimeAccessService;
    @Mock
    private OperationRecordMapper operationRecordMapper;
    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private ISysFilesService sysFilesService;

    private WorkflowDownloadServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new WorkflowDownloadServiceImpl(
                workflowRuntimeLookupService,
                runtimeAccessService,
                operationRecordMapper,
                attachmentMapper,
                sysFilesService
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId("user-1")
                .username("user")
                .build();
    }

    @Test
    void shouldRejectRuntimeDownloadWhenUserCannotAccessInstance() {
        ProcessInstance processInstance = processInstance(WorkflowConstants.Status.APPROVED);
        when(workflowRuntimeLookupService.requireTenantId(context)).thenReturn(TENANT_ID);
        when(workflowRuntimeLookupService.requireProcessInstance(PROCESS_INSTANCE_ID, TENANT_ID)).thenReturn(processInstance);
        doThrow(new IllegalArgumentException("无权访问审批实例"))
                .when(runtimeAccessService).ensureInstanceAccess(processInstance, context);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.downloadPackage(PROCESS_INSTANCE_ID, context));

        assertEquals("无权访问审批实例", exception.getMessage());
        verify(workflowRuntimeLookupService, never()).requireFormInstance(any(), any());
        verify(operationRecordMapper, never()).selectList(any(Wrapper.class));
        verify(attachmentMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void shouldRejectArchiveDownloadForRunningInstance() {
        when(workflowRuntimeLookupService.requireTenantId(context)).thenReturn(TENANT_ID);
        when(workflowRuntimeLookupService.requireProcessInstance(PROCESS_INSTANCE_ID, TENANT_ID))
                .thenReturn(processInstance(WorkflowConstants.Status.RUNNING));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.downloadArchivePackage(PROCESS_INSTANCE_ID, context));

        assertEquals("只有已结束流程可以下载归档材料", exception.getMessage());
        verify(runtimeAccessService, never()).ensureInstanceAccess(any(), any());
        verify(workflowRuntimeLookupService, never()).requireFormInstance(any(), any());
    }

    private ProcessInstance processInstance(String status) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(PROCESS_INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setFormInstanceId("form-instance-1");
        instance.setStatus(status);
        return instance;
    }
}
