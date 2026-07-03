package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;
import com.lawoffice.workflow.entity.ArchiveRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.mapper.ArchiveRecordMapper;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.req.AdminMonitorPageReq;
import com.lawoffice.workflow.req.ArchiveActionReq;
import com.lawoffice.workflow.service.IAdminMonitorService;
import com.lawoffice.workflow.service.IAttachmentRuntimeService;
import com.lawoffice.workflow.service.IDiagramService;
import com.lawoffice.workflow.service.IWorkflowDownloadService;
import com.lawoffice.workflow.vo.ArchiveRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";

    @Mock
    private ArchiveRecordMapper archiveRecordMapper;
    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private ProcessCategoryMapper processCategoryMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private IAdminMonitorService adminMonitorService;
    @Mock
    private IAttachmentRuntimeService attachmentRuntimeService;
    @Mock
    private IDiagramService diagramService;
    @Mock
    private IWorkflowDownloadService workflowDownloadService;

    private ArchiveServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ArchiveServiceImpl(
                archiveRecordMapper,
                attachmentMapper,
                processCategoryMapper,
                processInstanceMapper,
                processModelMapper,
                adminMonitorService,
                attachmentRuntimeService,
                diagramService,
                workflowDownloadService,
                null
        );
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId("archive-admin")
                .username("archive-admin")
                .build();
    }

    @Test
    void shouldRejectManualArchiveForRunningInstance() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance(WorkflowConstants.Status.RUNNING));

        BaseResult<ArchiveRecordVO> result = service.archiveFromArchiveMenu(singleArchiveReq(), context);

        assertEquals(400, result.getCode());
        assertEquals("只有已结束且未归档的流程才能手动归档", result.getMessage());
        verify(archiveRecordMapper, never()).insert(any(ArchiveRecord.class));
    }

    @Test
    void shouldRejectAutoArchiveForTerminatedInstance() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance(WorkflowConstants.Status.TERMINATED));

        BaseResult<ArchiveRecordVO> result = service.archiveAutomatically(PROCESS_INSTANCE_ID, context);

        assertEquals(400, result.getCode());
        assertEquals("只有正常结束流程才能自动归档", result.getMessage());
        verify(archiveRecordMapper, never()).insert(any(ArchiveRecord.class));
    }

    @Test
    void shouldReturnExistingRecordWhenAutoArchiveAlreadyExists() {
        ArchiveRecord existing = archiveRecord();
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance(WorkflowConstants.Status.APPROVED));
        when(archiveRecordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        BaseResult<ArchiveRecordVO> result = service.archiveAutomatically(PROCESS_INSTANCE_ID, context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertEquals(existing.getId(), result.getData().getId());
        verify(archiveRecordMapper, never()).insert(any(ArchiveRecord.class));
    }

    @Test
    void shouldRejectManualArchiveWhenRecordAlreadyExists() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(processInstance(WorkflowConstants.Status.APPROVED));
        when(archiveRecordMapper.selectOne(any(Wrapper.class))).thenReturn(archiveRecord());

        BaseResult<ArchiveRecordVO> result = service.archiveFromArchiveMenu(singleArchiveReq(), context);

        assertEquals(400, result.getCode());
        assertEquals("当前流程已归档", result.getMessage());
        verify(archiveRecordMapper, never()).insert(any(ArchiveRecord.class));
    }

    @Test
    void shouldRejectBatchArchiveByQueryWhenResultExceedsLimit() {
        when(processInstanceMapper.selectCount(any(Wrapper.class))).thenReturn(1001L);

        BaseResult<List<ArchiveRecordVO>> result = service.batchArchiveByQueryFromMonitor(new AdminMonitorPageReq(), context);

        assertEquals(400, result.getCode());
        assertEquals("单次最多归档1000个流程，请缩小查询范围", result.getMessage());
        verify(processInstanceMapper, never()).selectList(any(Wrapper.class));
        verify(archiveRecordMapper, never()).insert(any(ArchiveRecord.class));
    }

    @Test
    void shouldRejectDownloadPackageWhenArchiveRecordMissing() {
        when(archiveRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.downloadPackage(PROCESS_INSTANCE_ID, context));

        assertEquals("流程尚未归档，不能下载归档材料", exception.getMessage());
        verify(workflowDownloadService, never()).downloadArchivePackage(any(), any());
    }

    @Test
    void shouldDelegateDownloadPackageWhenArchived() {
        WorkflowDownloadFile downloadFile = new WorkflowDownloadFile("审批单.zip", "application/zip", new byte[] {1});
        when(archiveRecordMapper.selectOne(any(Wrapper.class))).thenReturn(archiveRecord());
        when(workflowDownloadService.downloadArchivePackage(PROCESS_INSTANCE_ID, context)).thenReturn(downloadFile);

        WorkflowDownloadFile result = service.downloadPackage(PROCESS_INSTANCE_ID, context);

        assertSame(downloadFile, result);
    }

    private ArchiveActionReq singleArchiveReq() {
        ArchiveActionReq req = new ArchiveActionReq();
        req.setProcessInstanceId(PROCESS_INSTANCE_ID);
        req.setArchiveReason("补归档");
        return req;
    }

    private ProcessInstance processInstance(String status) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(PROCESS_INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setProcessModelId("model-1");
        instance.setFormInstanceId("form-instance-1");
        instance.setFormDefinitionId("form-1");
        instance.setStatus(status);
        return instance;
    }

    private ArchiveRecord archiveRecord() {
        ArchiveRecord record = new ArchiveRecord();
        record.setId("archive-1");
        record.setTenantId(TENANT_ID);
        record.setProcessInstanceId(PROCESS_INSTANCE_ID);
        record.setInstanceStatus(WorkflowConstants.Status.APPROVED);
        record.setArchiveSource(WorkflowConstants.ArchiveSource.AUTO);
        return record;
    }
}
