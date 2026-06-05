package com.lawoffice.system.controller;

import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.req.DocumentBatchDeleteReq;
import com.lawoffice.system.req.DocumentBatchMoveReq;
import com.lawoffice.system.req.DocumentCopyReq;
import com.lawoffice.system.req.DocumentFolderReq;
import com.lawoffice.system.req.DocumentMoveReq;
import com.lawoffice.system.req.DocumentPageReq;
import com.lawoffice.system.req.DocumentRenameReq;
import com.lawoffice.system.req.DocumentShareReq;
import com.lawoffice.system.req.DocumentUploadReq;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.req.SysFilesReq;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.DocumentShareVO;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.SysFilesVO;
import com.lawoffice.util.HttpDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/files")
@Tag(name = "文件中心", description = "文件元数据和业务关联管理")
public class SysFilesController extends BaseController<ISysFilesService, SysFiles, SysFilesVO, SysFilesReq> {

    private static final Set<String> IMAGE_PREVIEW_EXTENSIONS = Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");

    public SysFilesController(ISysFilesService sysFilesService) {
        this.baseService = sysFilesService;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件并写入文件元数据，支持同步绑定业务")
    @AutoLog(value = "上传文件", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<FileUploadVO> upload(
            @RequestPart("file") MultipartFile file,
            @Valid FileUploadReq req,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(baseService.uploadFile(username, file, req));
    }

    @PostMapping("/document/page")
    @Operation(summary = "文档中心分页", description = "分页查询我的文档、业务文档、共享目录和回收站")
    public BaseResult<PageVO<DocumentFileVO>> pageDocuments(
            @Valid @RequestBody(required = false) DocumentPageReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.pageDocuments(getUsername(request), req));
    }

    @PostMapping("/document/upload")
    @Operation(summary = "上传文档中心文件", description = "上传文件并写入文档中心")
    @AutoLog(value = "上传文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @Valid DocumentUploadReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.uploadDocument(getUsername(request), file, req));
    }

    @PostMapping("/document/folder")
    @Operation(summary = "创建文档文件夹", description = "在文档中心创建文件夹")
    @AutoLog(value = "创建文档文件夹", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> createDocumentFolder(
            @Valid @RequestBody DocumentFolderReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.createDocumentFolder(getUsername(request), req));
    }

    @PostMapping("/document/rename")
    @Operation(summary = "重命名文档", description = "重命名本人拥有的文档或文件夹")
    @AutoLog(value = "重命名文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> renameDocument(
            @Valid @RequestBody DocumentRenameReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.renameDocument(getUsername(request), req));
    }

    @PostMapping("/document/move")
    @Operation(summary = "移动文档", description = "移动本人拥有的文档或文件夹")
    @AutoLog(value = "移动文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> moveDocument(
            @Valid @RequestBody DocumentMoveReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.moveDocument(getUsername(request), req));
    }

    @PostMapping("/document/batch-move")
    @Operation(summary = "批量移动文档", description = "批量移动本人拥有的文档或文件夹，任一失败则整体回滚")
    @AutoLog(value = "批量移动文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentFileVO>> batchMoveDocuments(
            @Valid @RequestBody DocumentBatchMoveReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.batchMoveDocuments(getUsername(request), req));
    }

    @PostMapping("/document/copy")
    @Operation(summary = "复制文档", description = "复制当前用户可下载的文档或文件夹到目标目录")
    @AutoLog(value = "复制文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentFileVO>> copyDocuments(
            @Valid @RequestBody DocumentCopyReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.copyDocuments(getUsername(request), req));
    }

    @PostMapping("/document/delete/{fileId}")
    @Operation(summary = "删除文档", description = "将本人拥有的文档移入回收站")
    @AutoLog(value = "删除文档", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> deleteDocument(
            @PathVariable String fileId,
            HttpServletRequest request) {
        baseService.deleteDocument(getUsername(request), fileId);
        return BaseResult.success();
    }

    @PostMapping("/document/batch-delete")
    @Operation(summary = "批量删除文档", description = "批量将本人拥有的文档或文件夹移入回收站，任一失败则整体回滚")
    @AutoLog(value = "批量删除文档", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> batchDeleteDocuments(
            @Valid @RequestBody DocumentBatchDeleteReq req,
            HttpServletRequest request) {
        baseService.batchDeleteDocuments(getUsername(request), req);
        return BaseResult.success();
    }

    @PostMapping("/document/restore/{fileId}")
    @Operation(summary = "恢复文档", description = "从回收站恢复本人拥有的文档")
    @AutoLog(value = "恢复文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> restoreDocument(
            @PathVariable String fileId,
            HttpServletRequest request) {
        return BaseResult.success(baseService.restoreDocument(getUsername(request), fileId));
    }

    @PostMapping("/document/batch-restore")
    @Operation(summary = "批量恢复文档", description = "批量从回收站恢复本人拥有的文档，任一失败则整体回滚")
    @AutoLog(value = "批量恢复文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentFileVO>> batchRestoreDocuments(
            @Valid @RequestBody DocumentBatchDeleteReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.batchRestoreDocuments(getUsername(request), req));
    }

    @PostMapping("/document/purge/{fileId}")
    @Operation(summary = "彻底删除文档", description = "从回收站彻底删除本人拥有的文档")
    @AutoLog(value = "彻底删除文档", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> purgeDocument(
            @PathVariable String fileId,
            HttpServletRequest request) {
        baseService.purgeDocument(getUsername(request), fileId);
        return BaseResult.success();
    }

    @PostMapping("/document/trash/clear")
    @Operation(summary = "清空回收站", description = "彻底删除本人回收站中的全部文档")
    @AutoLog(value = "清空回收站", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> clearDocumentTrash(HttpServletRequest request) {
        baseService.clearDocumentTrash(getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/document/star/{fileId}")
    @Operation(summary = "收藏文档", description = "切换本人文档收藏状态")
    @AutoLog(value = "收藏文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> toggleDocumentStar(
            @PathVariable String fileId,
            HttpServletRequest request) {
        return BaseResult.success(baseService.toggleDocumentStar(getUsername(request), fileId));
    }

    @PostMapping("/document/share")
    @Operation(summary = "共享文档", description = "共享本人文档给当前租户用户、部门或角色")
    @AutoLog(value = "共享文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentShareVO>> shareDocument(
            @Valid @RequestBody DocumentShareReq req,
            HttpServletRequest request) {
        return BaseResult.success(baseService.shareDocument(getUsername(request), req));
    }

    @GetMapping("/document/shares/{fileId}")
    @Operation(summary = "查询文档共享", description = "查询本人文档的共享目标")
    public BaseResult<List<DocumentShareVO>> listDocumentShares(
            @PathVariable String fileId,
            HttpServletRequest request) {
        return BaseResult.success(baseService.listDocumentShares(getUsername(request), fileId));
    }

    @PostMapping("/document/share/revoke/{aclId}")
    @Operation(summary = "撤销文档共享", description = "撤销本人文档的一条共享授权")
    @AutoLog(value = "撤销文档共享", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> revokeDocumentShare(
            @PathVariable String aclId,
            HttpServletRequest request) {
        baseService.revokeDocumentShare(getUsername(request), aclId);
        return BaseResult.success();
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定文件", description = "将已上传文件绑定到业务对象")
    @AutoLog(value = "绑定文件", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<FileRelationVO> bind(
            @Valid @RequestBody FileRelationReq req,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(baseService.bindFile(username, req));
    }

    @PostMapping("/unbind/{relationId}")
    @Operation(summary = "解绑文件", description = "逻辑删除文件与业务之间的关联")
    @AutoLog(value = "解绑文件", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> unbind(
            @PathVariable String relationId,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        baseService.unbindFile(username, relationId);
        return BaseResult.success();
    }

    @GetMapping("/biz/{bizType}/{bizId}")
    @Operation(summary = "按业务查询文件", description = "查询指定业务下的文件列表")
    public BaseResult<List<FileUploadVO>> listByBiz(
            @PathVariable String bizType,
            @PathVariable String bizId,
            HttpServletRequest request) {
        return BaseResult.success(baseService.listFilesByBizForOwner(bizType, bizId, getUsername(request)));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "查询文件详情", description = "根据文件ID查询文件元数据和访问地址")
    public BaseResult<FileUploadVO> getFileById(@PathVariable String fileId, HttpServletRequest request) {
        baseService.checkFileOwner(fileId, getUsername(request));
        return BaseResult.success(baseService.getFileById(fileId));
    }

    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文件", description = "按原始上传文件名下载文件")
    public void download(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        baseService.checkFileOwner(fileId, getUsername(request));
        FileUploadVO file = baseService.getFileById(fileId);
        String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveContentType(file.getFileType()));
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (InputStream inputStream = baseService.downloadFileContent(fileId)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    @GetMapping("/document/download/{fileId}")
    @Operation(summary = "下载文档中心文件", description = "按文档中心权限下载文件")
    public void downloadDocument(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        DocumentFileVO file = baseService.checkDocumentDownload(fileId, getUsername(request));
        String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveContentType(file.getFileType()));
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (InputStream inputStream = baseService.downloadFileContent(fileId)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    @GetMapping("/document/thumbnail/{fileId}")
    @Operation(summary = "预览文档中心图片缩略图", description = "按文档中心读取权限返回图片内容")
    public void previewDocumentImageThumbnail(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        DocumentFileVO file = baseService.checkDocumentRead(fileId, getUsername(request));
        String fileType = file.getFileType() == null ? "" : file.getFileType().toLowerCase(Locale.ROOT);
        String extension = resolveExtension(file.getFileName());
        if ("svg".equals(extension) || (!IMAGE_PREVIEW_EXTENSIONS.contains(extension)
                && !"image".equals(fileType) && !fileType.startsWith("image/"))) {
            throw new IllegalArgumentException("仅图片文件支持缩略图预览");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveImageContentType(file.getFileName()));
        response.setHeader("Content-Disposition", "inline");

        try (InputStream inputStream = baseService.downloadFileContent(fileId)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    private String resolveContentType(String fileType) {
        if (fileType != null && fileType.contains("/")) {
            return fileType;
        }
        return "application/octet-stream";
    }

    private String resolveImageContentType(String fileName) {
        String extension = resolveExtension(fileName);
        if (extension == null) {
            return "image/*";
        }
        return switch (extension) {
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/*";
        };
    }

    private String resolveExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String getUsername(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}
