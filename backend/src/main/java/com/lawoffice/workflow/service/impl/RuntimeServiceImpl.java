package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.AssigneePreviewReq;
import com.lawoffice.workflow.req.AttachmentBindReq;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IAttachmentRuntimeService;
import com.lawoffice.workflow.service.ICcRuntimeService;
import com.lawoffice.workflow.service.IDiagramService;
import com.lawoffice.workflow.service.IProcessStartService;
import com.lawoffice.workflow.service.IReminderRuntimeService;
import com.lawoffice.workflow.service.IRuntimeQueryService;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.service.ITaskActionService;
import com.lawoffice.workflow.vo.AttachmentVO;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.CcRecordVO;
import com.lawoffice.workflow.vo.InstanceDiagramVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.ReminderRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartProcessVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskActionVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeServiceImpl implements IRuntimeService {

    private final IRuntimeQueryService runtimeQueryService;
    private final ITaskActionService taskActionService;
    private final IProcessStartService processStartService;
    private final ICcRuntimeService ccRuntimeService;
    private final IReminderRuntimeService reminderRuntimeService;
    private final IAttachmentRuntimeService attachmentRuntimeService;
    private final IDiagramService diagramService;

    public RuntimeServiceImpl(IRuntimeQueryService runtimeQueryService,
            ITaskActionService taskActionService,
            IProcessStartService processStartService,
            ICcRuntimeService ccRuntimeService,
            IReminderRuntimeService reminderRuntimeService,
            IAttachmentRuntimeService attachmentRuntimeService,
            IDiagramService diagramService) {
        this.runtimeQueryService = runtimeQueryService;
        this.taskActionService = taskActionService;
        this.processStartService = processStartService;
        this.ccRuntimeService = ccRuntimeService;
        this.reminderRuntimeService = reminderRuntimeService;
        this.attachmentRuntimeService = attachmentRuntimeService;
        this.diagramService = diagramService;
    }

    @Override
    public BaseResult<PageVO<AvailableProcessVO>> pageAvailableProcesses(AvailableProcessPageReq req, RequestContext context) {
        return runtimeQueryService.pageAvailableProcesses(req, context);
    }

    @Override
    public BaseResult<StartFormVO> getStartForm(String processModelId, RequestContext context) {
        return runtimeQueryService.getStartForm(processModelId, context);
    }

    @Override
    public BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context) {
        return processStartService.start(req, context);
    }

    @Override
    public BaseResult<StartProcessVO> saveStartDraft(StartProcessReq req, RequestContext context) {
        return processStartService.saveStartDraft(req, context);
    }

    @Override
    public BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.submitStartDraft(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.saveStartDraftTask(taskId, req, context);
    }

    @Override
    public BaseResult<PageVO<StartedInstanceVO>> pageStartedInstances(StartedInstancePageReq req, RequestContext context) {
        return runtimeQueryService.pageStartedInstances(req, context);
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context) {
        return runtimeQueryService.pageTodo(req, context);
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context) {
        return runtimeQueryService.pageDone(req, context);
    }

    @Override
    public BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context) {
        return runtimeQueryService.getTaskForm(taskId, context);
    }

    @Override
    public BaseResult<List<AssigneeSelectNodeVO>> previewNextAssigneeSelectNodes(AssigneePreviewReq req, RequestContext context) {
        return runtimeQueryService.previewNextAssigneeSelectNodes(req, context);
    }

    @Override
    public BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.approve(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.reject(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.transfer(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.returnTask(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.addSign(taskId, req, context);
    }

    @Override
    public BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context) {
        return runtimeQueryService.getInstanceDetail(id, context);
    }

    @Override
    public BaseResult<List<OperationRecordVO>> listInstanceRecords(String id, RequestContext context) {
        return runtimeQueryService.listInstanceRecords(id, context);
    }

    @Override
    public BaseResult<PageVO<CcRecordVO>> pageCc(CcPageReq req, RequestContext context) {
        return ccRuntimeService.pageMine(req, context);
    }

    @Override
    public BaseResult<CcRecordVO> markCcRead(String ccRecordId, RequestContext context) {
        return ccRuntimeService.markRead(ccRecordId, context);
    }

    @Override
    public BaseResult<List<CcRecordVO>> sendCc(String processInstanceId, List<String> receiverUserIds,
            RequestContext context) {
        return ccRuntimeService.sendManual(processInstanceId, receiverUserIds, context);
    }

    @Override
    public BaseResult<ReminderRecordVO> urgeTask(String taskId, String remark, RequestContext context) {
        return reminderRuntimeService.urgeTask(taskId, remark, context);
    }

    @Override
    public BaseResult<List<AttachmentVO>> listAttachments(String processInstanceId, RequestContext context) {
        return attachmentRuntimeService.listByInstance(processInstanceId, context);
    }

    @Override
    public BaseResult<AttachmentVO> bindAttachment(AttachmentBindReq req, RequestContext context) {
        return attachmentRuntimeService.bind(req, context);
    }

    @Override
    public BaseResult<Void> deleteAttachment(String attachmentId, RequestContext context) {
        return attachmentRuntimeService.delete(attachmentId, context);
    }

    @Override
    public BaseResult<InstanceDiagramVO> getInstanceDiagram(String processInstanceId, RequestContext context) {
        return diagramService.getInstanceDiagram(processInstanceId, context);
    }
}
