package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.Attachment;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.AttachmentBindReq;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.vo.AttachmentVO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentRuntimeServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String INSTANCE_ID = "instance-1";
    private static final String STARTER_ID = "starter-1";
    private static final String TASK_ID = "task-1";
    private static final String USER_ID = "user-1";

    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private IRuntimeAccessService runtimeAccessService;
    @Mock
    private ISysFilesService sysFilesService;

    private AttachmentRuntimeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AttachmentRuntimeServiceImpl(
                attachmentMapper,
                processInstanceMapper,
                taskCandidateMapper,
                taskMapper,
                runtimeAccessService,
                sysFilesService
        );
    }

    @Test
    void shouldListAttachmentsAfterInstanceAccessCheck() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());
        when(attachmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(attachment("attachment-1", STARTER_ID)));
        when(sysFilesService.getFileById(any())).thenReturn(fileUpload());

        BaseResult<List<AttachmentVO>> result = service.listByInstance(INSTANCE_ID, context(STARTER_ID));

        assertEquals(200, result.getCode(), result.getMessage());
        assertEquals(1, result.getData().size());
        assertEquals("审批附件.xlsx", result.getData().get(0).getFileName());
        verify(runtimeAccessService).ensureInstanceAccess(any(ProcessInstance.class), any(RequestContext.class));
    }

    @Test
    void shouldAllowStarterToBindAttachmentWhileRunning() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());
        when(sysFilesService.bindFile(any(), any())).thenReturn(fileRelation());

        BaseResult<AttachmentVO> result = service.bind(bindReq(null), context(STARTER_ID));

        assertEquals(200, result.getCode(), result.getMessage());
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertEquals(STARTER_ID, captor.getValue().getUploaderUserId());
        assertEquals("relation-1", captor.getValue().getFileRelationId());
        assertEquals(WorkflowConstants.AttachmentStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void shouldAllowStarterToBindAttachmentWhileDraft() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(draftInstance());
        when(sysFilesService.bindFile(any(), any())).thenReturn(fileRelation());

        BaseResult<AttachmentVO> result = service.bind(bindReq(null), context(STARTER_ID));

        assertEquals(200, result.getCode(), result.getMessage());
        verify(attachmentMapper).insert(any());
    }

    @Test
    void shouldRejectViewerUploadWhenNotStarterOrCurrentOperator() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());

        BaseResult<AttachmentVO> result = service.bind(bindReq(null), context(USER_ID));

        assertEquals(400, result.getCode());
        assertEquals("只有发起人或当前办理人可以上传审批附件", result.getMessage());
        verify(attachmentMapper, never()).insert(any());
    }

    @Test
    void shouldAllowCurrentTaskAssigneeToBindAttachment() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(todoTask(USER_ID));
        when(sysFilesService.bindFile(any(), any())).thenReturn(fileRelation());

        BaseResult<AttachmentVO> result = service.bind(bindReq(TASK_ID), context(USER_ID));

        assertEquals(200, result.getCode(), result.getMessage());
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertEquals(TASK_ID, captor.getValue().getTaskId());
        assertEquals(USER_ID, captor.getValue().getUploaderUserId());
    }

    @Test
    void shouldRejectDeleteWhenOperatorIsNotUploader() {
        when(attachmentMapper.selectOne(any(Wrapper.class))).thenReturn(attachment("attachment-1", STARTER_ID));
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());

        BaseResult<Void> result = service.delete("attachment-1", context(USER_ID));

        assertEquals(400, result.getCode());
        assertEquals("只有附件上传人可以删除附件", result.getMessage());
        verify(attachmentMapper, never()).updateById(any());
    }

    @Test
    void shouldRejectDeleteWhenProcessFinished() {
        when(attachmentMapper.selectOne(any(Wrapper.class))).thenReturn(attachment("attachment-1", STARTER_ID));
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(finishedInstance());

        BaseResult<Void> result = service.delete("attachment-1", context(STARTER_ID));

        assertEquals(400, result.getCode());
        assertEquals("流程已结束，不能删除附件", result.getMessage());
        verify(attachmentMapper, never()).updateById(any());
    }

    @Test
    void shouldRejectDeleteWhenBoundTaskIsDone() {
        Attachment attachment = attachment("attachment-1", STARTER_ID);
        attachment.setTaskId(TASK_ID);
        when(attachmentMapper.selectOne(any(Wrapper.class))).thenReturn(attachment);
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());
        Task task = todoTask(STARTER_ID);
        task.setStatus(WorkflowConstants.Status.DONE);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        BaseResult<Void> result = service.delete("attachment-1", context(STARTER_ID));

        assertEquals(400, result.getCode());
        assertEquals("任务已完成，不能删除附件", result.getMessage());
        verify(attachmentMapper, never()).updateById(any());
    }

    @Test
    void shouldAllowUploaderToDeleteRunningInstanceAttachment() {
        Attachment attachment = attachment("attachment-1", STARTER_ID);
        attachment.setFileRelationId("relation-1");
        when(attachmentMapper.selectOne(any(Wrapper.class))).thenReturn(attachment);
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());

        BaseResult<Void> result = service.delete("attachment-1", context(STARTER_ID));

        assertEquals(200, result.getCode(), result.getMessage());
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).updateById(captor.capture());
        assertEquals(WorkflowConstants.AttachmentStatus.DELETED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getDeleteFlag());
        assertNotNull(captor.getValue().getDeleteTime());
        verify(sysFilesService).unbindFile(STARTER_ID, "relation-1");
    }

    @Test
    void shouldAllowUploaderToDeleteDraftInstanceAttachment() {
        Attachment attachment = attachment("attachment-1", STARTER_ID);
        when(attachmentMapper.selectOne(any(Wrapper.class))).thenReturn(attachment);
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(draftInstance());

        BaseResult<Void> result = service.delete("attachment-1", context(STARTER_ID));

        assertEquals(200, result.getCode(), result.getMessage());
        verify(attachmentMapper).updateById(any());
    }

    @Test
    void shouldAllowInstanceAccessibleUserToReadAttachmentFileMetadata() {
        Attachment attachment = attachment("attachment-1", STARTER_ID);
        attachment.setFileId("file-1");
        when(attachmentMapper.selectOne(any(Wrapper.class))).thenReturn(attachment);
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(runningInstance());
        when(sysFilesService.getFileById("file-1")).thenReturn(fileUpload());

        FileUploadVO file = service.requireFile("attachment-1", context(USER_ID));

        assertEquals("审批附件.xlsx", file.getFileName());
        verify(runtimeAccessService).ensureInstanceAccess(any(ProcessInstance.class), any(RequestContext.class));
    }

    private FileRelationVO fileRelation() {
        FileRelationVO relation = new FileRelationVO();
        relation.setId("relation-1");
        relation.setFileId("file-1");
        relation.setBizType(WorkflowConstants.BusinessDocument.APPROVAL_BIZ_TYPE);
        relation.setBizId(INSTANCE_ID);
        return relation;
    }

    private FileUploadVO fileUpload() {
        FileUploadVO file = new FileUploadVO();
        file.setFileId("file-1");
        file.setFileName("审批附件.xlsx");
        file.setFileType("excel");
        file.setFileSize(12L);
        return file;
    }

    private AttachmentBindReq bindReq(String taskId) {
        AttachmentBindReq req = new AttachmentBindReq();
        req.setProcessInstanceId(INSTANCE_ID);
        req.setTaskId(taskId);
        req.setFileId("file-1");
        req.setAttachmentSource(taskId == null
                ? WorkflowConstants.AttachmentSource.START
                : WorkflowConstants.AttachmentSource.TASK);
        return req;
    }

    private Attachment attachment(String id, String uploaderUserId) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setTenantId(TENANT_ID);
        attachment.setProcessInstanceId(INSTANCE_ID);
        attachment.setFileId("file-1");
        attachment.setUploaderUserId(uploaderUserId);
        attachment.setStatus(WorkflowConstants.AttachmentStatus.ACTIVE);
        return attachment;
    }

    private ProcessInstance runningInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setStarterUserId(STARTER_ID);
        instance.setStatus(WorkflowConstants.Status.RUNNING);
        return instance;
    }

    private ProcessInstance finishedInstance() {
        ProcessInstance instance = runningInstance();
        instance.setStatus(WorkflowConstants.Status.APPROVED);
        return instance;
    }

    private ProcessInstance draftInstance() {
        ProcessInstance instance = runningInstance();
        instance.setStatus(WorkflowConstants.Status.DRAFT);
        return instance;
    }

    private Task todoTask(String assigneeUserId) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId(INSTANCE_ID);
        task.setAssigneeUserId(assigneeUserId);
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private RequestContext context(String userId) {
        return RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(userId)
                .username(userId)
                .build();
    }
}
