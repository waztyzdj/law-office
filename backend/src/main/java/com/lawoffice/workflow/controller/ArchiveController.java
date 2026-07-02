package com.lawoffice.workflow.controller;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.util.HttpDownloadUtils;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;
import com.lawoffice.workflow.req.ArchiveActionReq;
import com.lawoffice.workflow.req.ArchivePageReq;
import com.lawoffice.workflow.service.IArchiveService;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AttachmentVO;
import com.lawoffice.workflow.vo.ArchiveRecordVO;
import com.lawoffice.workflow.vo.ArchiveTreeNodeVO;
import com.lawoffice.workflow.vo.InstanceDiagramVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/workflow/admin/archive")
@Tag(name = "流程归档", description = "审批中心流程归档查询与归档维护")
public class ArchiveController {

    private final IArchiveService archiveService;

    public ArchiveController(IArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/tree")
    @Operation(summary = "查询流程归档树")
    @RequiresPermission("workflow:archive:view")
    public BaseResult<List<ArchiveTreeNodeVO>> tree(HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.tree(context);
    }

    @PostMapping("/archived-page")
    @Operation(summary = "分页查询已归档流程")
    @RequiresPermission("workflow:archive:view")
    public BaseResult<PageVO<ArchiveRecordVO>> archivedPage(@RequestBody(required = false) ArchivePageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.pageArchived(req, context);
    }

    @PostMapping("/unarchived-page")
    @Operation(summary = "分页查询未归档已结束流程")
    @RequiresPermission("workflow:archive:view")
    public BaseResult<PageVO<ArchiveRecordVO>> unarchivedPage(@RequestBody(required = false) ArchivePageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.pageUnarchived(req, context);
    }

    @PostMapping("/detail")
    @Operation(summary = "查询流程归档详情")
    @RequiresPermission("workflow:archive:view")
    public BaseResult<AdminMonitorDetailVO> detail(@RequestBody ArchiveActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.detail(req == null ? null : req.getProcessInstanceId(), context);
    }

    @PostMapping("/diagram")
    @Operation(summary = "查询流程归档图谱")
    @RequiresPermission("workflow:archive:view")
    public BaseResult<InstanceDiagramVO> diagram(@RequestBody ArchiveActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.diagram(req == null ? null : req.getProcessInstanceId(), context);
    }

    @PostMapping("/attachment/list")
    @Operation(summary = "查询流程归档附件")
    @RequiresPermission("workflow:archive:view")
    public BaseResult<List<AttachmentVO>> listAttachments(@RequestBody ArchiveActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.listAttachments(req == null ? null : req.getProcessInstanceId(), context);
    }

    @PostMapping("/attachment/download")
    @Operation(summary = "下载流程归档附件")
    @RequiresPermission("workflow:archive:view")
    public void downloadAttachment(@RequestBody ArchiveActionReq req,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        RequestContext context = RequestContextUtils.buildContext(request);
        String attachmentId = req == null ? null : req.getId();
        FileUploadVO file = archiveService.requireAttachmentFile(attachmentId, context);
        String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveContentType(file.getFileType()));
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        try (InputStream inputStream = archiveService.downloadAttachmentContent(attachmentId, context)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    @PostMapping("/attachment/download-all")
    @Operation(summary = "打包下载流程归档附件")
    @RequiresPermission("workflow:archive:view")
    public void downloadAllAttachments(@RequestBody ArchiveActionReq req,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        RequestContext context = RequestContextUtils.buildContext(request);
        WorkflowDownloadFile downloadFile = archiveService.downloadAttachmentPackage(
                req == null ? null : req.getProcessInstanceId(), context);
        writeDownloadFile(response, downloadFile);
    }

    @PostMapping("/archive")
    @Operation(summary = "手动归档终止流程")
    @RequiresPermission("workflow:archive:manage")
    public BaseResult<ArchiveRecordVO> archive(@Valid @RequestBody ArchiveActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.archiveFromArchiveMenu(req, context);
    }

    @PostMapping("/batch-archive")
    @Operation(summary = "批量归档已结束流程")
    @RequiresPermission("workflow:archive:manage")
    public BaseResult<List<ArchiveRecordVO>> batchArchive(@Valid @RequestBody ArchiveActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.batchArchiveFromArchiveMenu(req, context);
    }

    @PostMapping("/batch-archive-by-query")
    @Operation(summary = "按查询条件批量归档已结束流程")
    @RequiresPermission("workflow:archive:manage")
    public BaseResult<List<ArchiveRecordVO>> batchArchiveByQuery(@RequestBody(required = false) ArchivePageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return archiveService.batchArchiveByQueryFromArchiveMenu(req, context);
    }

    @PostMapping("/download")
    @Operation(summary = "下载流程归档材料包")
    @RequiresPermission("workflow:archive:view")
    public void download(@RequestBody ArchiveActionReq req,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        RequestContext context = RequestContextUtils.buildContext(request);
        WorkflowDownloadFile downloadFile = archiveService.downloadPackage(
                req == null ? null : req.getProcessInstanceId(), context);
        writeDownloadFile(response, downloadFile);
    }

    private void writeDownloadFile(HttpServletResponse response, WorkflowDownloadFile downloadFile) throws IOException {
        String fileName = HttpDownloadUtils.resolveDownloadFileName(downloadFile.fileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(downloadFile.contentType());
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.getOutputStream().write(downloadFile.content());
    }

    private String resolveContentType(String fileType) {
        if (fileType != null && fileType.contains("/")) {
            return fileType;
        }
        return "application/octet-stream";
    }
}
