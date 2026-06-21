package com.lawoffice.workflow.controller;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AttachmentBindReq;
import com.lawoffice.workflow.req.AssigneePreviewReq;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.BranchRecordReq;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.req.CcReadReq;
import com.lawoffice.workflow.req.InstanceDiagramReq;
import com.lawoffice.workflow.req.InstanceReq;
import com.lawoffice.workflow.req.StartFormReq;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.req.TaskFormReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.req.TaskUrgeReq;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.vo.AttachmentVO;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.BranchRecordVO;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workflow")
@Tag(name = "审批运行时", description = "审批中心发起与办理运行时接口")
public class RuntimeController {

    private final IRuntimeService runtimeService;

    @Autowired
    public RuntimeController(IRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/available/page")
    @Operation(summary = "可发起流程")
    public BaseResult<PageVO<AvailableProcessVO>> pageAvailable(@RequestBody(required = false) AvailableProcessPageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.pageAvailableProcesses(req, context);
    }

    @PostMapping("/start/form")
    @Operation(summary = "获取发起表单")
    public BaseResult<StartFormVO> getStartForm(@RequestBody StartFormReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.getStartForm(req == null ? null : req.getProcessModelId(), context);
    }

    @PostMapping("/start")
    @Operation(summary = "发起申请")
    public BaseResult<StartProcessVO> start(@RequestBody StartProcessReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.start(req, context);
    }

    @PostMapping("/start/draft")
    @Operation(summary = "保存发起申请草稿")
    public BaseResult<StartProcessVO> saveStartDraft(@RequestBody StartProcessReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.saveStartDraft(req, context);
    }

    @PostMapping("/todo/page")
    @Operation(summary = "我的待办")
    public BaseResult<PageVO<RuntimeTaskVO>> pageTodo(@RequestBody(required = false) TaskPageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.pageTodo(req, context);
    }

    @PostMapping("/done/page")
    @Operation(summary = "我的已办")
    public BaseResult<PageVO<RuntimeTaskVO>> pageDone(@RequestBody(required = false) TaskPageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.pageDone(req, context);
    }

    @PostMapping("/started/page")
    @Operation(summary = "我发起的")
    public BaseResult<PageVO<StartedInstanceVO>> pageStarted(@RequestBody(required = false) StartedInstancePageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.pageStartedInstances(req, context);
    }

    @PostMapping("/instance/detail")
    @Operation(summary = "审批详情")
    public BaseResult<InstanceDetailVO> getInstanceDetail(@RequestBody InstanceReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.getInstanceDetail(req == null ? null : req.getId(), context);
    }

    @PostMapping("/instance/records")
    @Operation(summary = "审批记录")
    public BaseResult<List<OperationRecordVO>> listInstanceRecords(@RequestBody InstanceReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.listInstanceRecords(req == null ? null : req.getId(), context);
    }

    @PostMapping("/task/form")
    @Operation(summary = "获取任务办理表单")
    public BaseResult<TaskFormVO> getTaskForm(@RequestBody TaskFormReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.getTaskForm(req == null ? null : req.getTaskId(), context);
    }

    @PostMapping("/assignee/preview")
    @Operation(summary = "棰勫垽涓嬩竴瀹℃壒浜洪€夋嫨")
    public BaseResult<List<AssigneeSelectNodeVO>> previewNextAssigneeSelectNodes(@RequestBody AssigneePreviewReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.previewNextAssigneeSelectNodes(req, context);
    }

    @PostMapping("/task/approve")
    @Operation(summary = "审批通过")
    public BaseResult<TaskActionVO> approve(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.approve(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/task/start-draft/submit")
    @Operation(summary = "提交发起申请草稿")
    public BaseResult<TaskActionVO> submitStartDraft(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.submitStartDraft(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/task/start-draft/save")
    @Operation(summary = "保存发起申请草稿任务")
    public BaseResult<TaskActionVO> saveStartDraftTask(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.saveStartDraftTask(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/task/reject")
    @Operation(summary = "审批不通过")
    public BaseResult<TaskActionVO> reject(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.reject(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/task/transfer")
    @Operation(summary = "转办")
    public BaseResult<TaskActionVO> transfer(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.transfer(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/task/return")
    @Operation(summary = "退回")
    public BaseResult<TaskActionVO> returnTask(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.returnTask(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/task/add-sign")
    @Operation(summary = "加签")
    public BaseResult<TaskActionVO> addSign(@RequestBody(required = false) TaskActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.addSign(req == null ? null : req.getTaskId(), req, context);
    }

    @PostMapping("/cc/page")
    @Operation(summary = "我的抄送")
    public BaseResult<PageVO<CcRecordVO>> pageCc(@RequestBody(required = false) CcPageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.pageCc(req, context);
    }

    @PostMapping("/cc/read")
    @Operation(summary = "标记抄送已读")
    public BaseResult<CcRecordVO> markCcRead(@RequestBody CcReadReq req, HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.markCcRead(req == null ? null : req.getId(), context);
    }

    @PostMapping("/task/urge")
    @Operation(summary = "催办任务")
    public BaseResult<ReminderRecordVO> urgeTask(@RequestBody TaskUrgeReq req, HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.urgeTask(req == null ? null : req.getTaskId(),
                req == null ? null : req.getRemark(), context);
    }

    @PostMapping("/attachment/list")
    @Operation(summary = "查询审批附件")
    public BaseResult<List<AttachmentVO>> listAttachments(@RequestBody InstanceReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.listAttachments(req == null ? null : req.getId(), context);
    }

    @PostMapping("/attachment/bind")
    @Operation(summary = "绑定审批附件")
    public BaseResult<AttachmentVO> bindAttachment(@RequestBody AttachmentBindReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.bindAttachment(req, context);
    }

    @PostMapping("/attachment/delete")
    @Operation(summary = "删除审批附件")
    public BaseResult<Void> deleteAttachment(@RequestBody InstanceReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.deleteAttachment(req == null ? null : req.getId(), context);
    }

    @PostMapping("/branch/record")
    @Operation(summary = "记录分支命中")
    public BaseResult<BranchRecordVO> recordBranch(@RequestBody BranchRecordReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.recordBranch(req, context);
    }

    @PostMapping("/branch/list")
    @Operation(summary = "查询分支记录")
    public BaseResult<List<BranchRecordVO>> listBranches(@RequestBody InstanceReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.listBranches(req == null ? null : req.getId(), context);
    }

    @PostMapping("/instance/diagram")
    @Operation(summary = "获取实例图谱")
    public BaseResult<InstanceDiagramVO> getInstanceDiagram(@RequestBody InstanceDiagramReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return runtimeService.getInstanceDiagram(req == null ? null : req.getId(), context);
    }
}
