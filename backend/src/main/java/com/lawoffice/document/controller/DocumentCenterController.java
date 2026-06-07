package com.lawoffice.document.controller;

import com.lawoffice.document.req.DocumentBatchDeleteReq;
import com.lawoffice.document.req.DocumentBatchMoveReq;
import com.lawoffice.document.req.DocumentCopyReq;
import com.lawoffice.document.req.DocumentFolderReq;
import com.lawoffice.document.req.DocumentMoveReq;
import com.lawoffice.document.req.DocumentPageReq;
import com.lawoffice.document.req.DocumentRenameReq;
import com.lawoffice.document.req.DocumentShareReq;
import com.lawoffice.document.req.DocumentTreeBatchReq;
import com.lawoffice.document.req.DocumentTreePrefetchReq;
import com.lawoffice.document.req.DocumentUploadReq;
import com.lawoffice.document.service.IDocumentCenterService;
import com.lawoffice.document.vo.DocumentFileVO;
import com.lawoffice.document.vo.DocumentShareVO;
import com.lawoffice.document.vo.DocumentStatusVO;
import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.util.HttpDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/document/files")
@RequiredArgsConstructor
@Tag(name = "文档中心", description = "文档中心浏览、共享、回收站和在线文档")
public class DocumentCenterController {

    private static final Set<String> IMAGE_PREVIEW_EXTENSIONS = Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");

    private final IDocumentCenterService documentCenterService;
    private final ISysFilesService sysFilesService;

    @PostMapping("/page")
    @Operation(summary = "文档中心分页", description = "分页查询我的文档、业务文档、共享目录和回收站")
    public BaseResult<PageVO<DocumentFileVO>> pageDocuments(
            @Valid @RequestBody(required = false) DocumentPageReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.pageDocuments(getUsername(request), req));
    }

    @PostMapping("/tree/batch")
    @Operation(summary = "批量加载文档树节点", description = "一次加载多个树节点的下一层文件夹，用于文档中心左侧树初始化")
    public BaseResult<Map<String, List<DocumentFileVO>>> batchLoadDocumentTree(
            @Valid @RequestBody(required = false) DocumentTreeBatchReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.batchLoadDocumentFolderTree(getUsername(request), req));
    }

    @PostMapping("/tree/prefetch")
    @Operation(summary = "批量预取文档树子目录", description = "按多个父级目录批量查询下一层文件夹，用于树展开时预热缓存")
    public BaseResult<Map<String, List<DocumentFileVO>>> prefetchDocumentTree(
            @Valid @RequestBody(required = false) DocumentTreePrefetchReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.prefetchDocumentFolderTree(getUsername(request), req));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文档中心文件", description = "上传文件并写入文档中心")
    @AutoLog(value = "上传文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @Valid DocumentUploadReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.uploadDocument(getUsername(request), file, req));
    }

    @PostMapping("/folder")
    @Operation(summary = "创建文档文件夹", description = "在文档中心创建文件夹")
    @AutoLog(value = "创建文档文件夹", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> createDocumentFolder(
            @Valid @RequestBody DocumentFolderReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.createDocumentFolder(getUsername(request), req));
    }

    @PostMapping("/rename")
    @Operation(summary = "重命名文档", description = "重命名本人拥有的文档或文件夹")
    @AutoLog(value = "重命名文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> renameDocument(
            @Valid @RequestBody DocumentRenameReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.renameDocument(getUsername(request), req));
    }

    @PostMapping("/move")
    @Operation(summary = "移动文档", description = "移动本人拥有的文档或文件夹")
    @AutoLog(value = "移动文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> moveDocument(
            @Valid @RequestBody DocumentMoveReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.moveDocument(getUsername(request), req));
    }

    @PostMapping("/batch-move")
    @Operation(summary = "批量移动文档", description = "批量移动本人拥有的文档或文件夹，任一失败则整体回滚")
    @AutoLog(value = "批量移动文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentFileVO>> batchMoveDocuments(
            @Valid @RequestBody DocumentBatchMoveReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.batchMoveDocuments(getUsername(request), req));
    }

    @PostMapping("/copy")
    @Operation(summary = "复制文档", description = "复制当前用户可下载的文档或文件夹到目标目录")
    @AutoLog(value = "复制文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentFileVO>> copyDocuments(
            @Valid @RequestBody DocumentCopyReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.copyDocuments(getUsername(request), req));
    }

    @PostMapping("/delete/{fileId}")
    @Operation(summary = "删除文档", description = "将本人拥有的文档移入回收站")
    @AutoLog(value = "删除文档", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> deleteDocument(@PathVariable String fileId, HttpServletRequest request) {
        documentCenterService.deleteDocument(getUsername(request), fileId);
        return BaseResult.success();
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除文档", description = "批量将本人拥有的文档或文件夹移入回收站，任一失败则整体回滚")
    @AutoLog(value = "批量删除文档", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> batchDeleteDocuments(
            @Valid @RequestBody DocumentBatchDeleteReq req,
            HttpServletRequest request) {
        documentCenterService.batchDeleteDocuments(getUsername(request), req);
        return BaseResult.success();
    }

    @PostMapping("/restore/{fileId}")
    @Operation(summary = "恢复文档", description = "从回收站恢复本人拥有的文档")
    @AutoLog(value = "恢复文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> restoreDocument(@PathVariable String fileId, HttpServletRequest request) {
        return BaseResult.success(documentCenterService.restoreDocument(getUsername(request), fileId));
    }

    @PostMapping("/batch-restore")
    @Operation(summary = "批量恢复文档", description = "批量从回收站恢复本人拥有的文档，任一失败则整体回滚")
    @AutoLog(value = "批量恢复文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentFileVO>> batchRestoreDocuments(
            @Valid @RequestBody DocumentBatchDeleteReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.batchRestoreDocuments(getUsername(request), req));
    }

    @PostMapping("/purge/{fileId}")
    @Operation(summary = "彻底删除文档", description = "从回收站彻底删除本人拥有的文档")
    @AutoLog(value = "彻底删除文档", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> purgeDocument(@PathVariable String fileId, HttpServletRequest request) {
        documentCenterService.purgeDocument(getUsername(request), fileId);
        return BaseResult.success();
    }

    @PostMapping("/trash/clear")
    @Operation(summary = "清空回收站", description = "彻底删除本人回收站中的全部文档")
    @AutoLog(value = "清空回收站", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> clearDocumentTrash(HttpServletRequest request) {
        documentCenterService.clearDocumentTrash(getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/star/{fileId}")
    @Operation(summary = "收藏文档", description = "切换本人文档收藏状态")
    @AutoLog(value = "收藏文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<DocumentFileVO> toggleDocumentStar(@PathVariable String fileId, HttpServletRequest request) {
        return BaseResult.success(documentCenterService.toggleDocumentStar(getUsername(request), fileId));
    }

    @PostMapping("/share")
    @Operation(summary = "共享文档", description = "共享本人文档给当前租户用户、部门或角色")
    @AutoLog(value = "共享文档", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<List<DocumentShareVO>> shareDocument(
            @Valid @RequestBody DocumentShareReq req,
            HttpServletRequest request) {
        return BaseResult.success(documentCenterService.shareDocument(getUsername(request), req));
    }

    @GetMapping("/shares/{fileId}")
    @Operation(summary = "查询文档共享", description = "查询本人文档的共享目标")
    public BaseResult<List<DocumentShareVO>> listDocumentShares(@PathVariable String fileId, HttpServletRequest request) {
        return BaseResult.success(documentCenterService.listDocumentShares(getUsername(request), fileId));
    }

    @GetMapping("/status/{fileId}")
    @Operation(summary = "查询文档状态栏详情", description = "查询文档中心状态栏需要的共享来源、统计信息和业务来源")
    public BaseResult<DocumentStatusVO> getDocumentStatus(@PathVariable String fileId, HttpServletRequest request) {
        return BaseResult.success(documentCenterService.getDocumentStatus(getUsername(request), fileId));
    }

    @PostMapping("/share/revoke/{aclId}")
    @Operation(summary = "撤销文档共享", description = "撤销本人文档的一条共享授权")
    @AutoLog(value = "撤销文档共享", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> revokeDocumentShare(@PathVariable String aclId, HttpServletRequest request) {
        documentCenterService.revokeDocumentShare(getUsername(request), aclId);
        return BaseResult.success();
    }

    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文档中心文件", description = "按文档中心权限下载文件")
    public void downloadDocument(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        DocumentFileVO file = documentCenterService.checkDocumentDownload(fileId, getUsername(request));
        String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveContentType(file.getFileType()));
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (InputStream inputStream = sysFilesService.downloadFileContent(fileId)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    @GetMapping("/thumbnail/{fileId}")
    @Operation(summary = "预览文档中心图片缩略图", description = "按文档中心读取权限返回图片内容")
    public void previewDocumentImageThumbnail(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        DocumentFileVO file = documentCenterService.checkDocumentRead(fileId, getUsername(request));
        writeDocumentImage(fileId, file, response);
    }

    @GetMapping("/preview/image/{fileId}")
    @Operation(summary = "预览文档中心图片", description = "按文档中心读取权限返回图片内容，并记录阅读次数")
    public void previewDocumentImage(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        DocumentFileVO file = documentCenterService.checkDocumentPreview(fileId, getUsername(request));
        writeDocumentImage(fileId, file, response);
    }

    private void writeDocumentImage(
            String fileId,
            DocumentFileVO file,
            HttpServletResponse response) throws IOException {
        String fileType = file.getFileType() == null ? "" : file.getFileType().toLowerCase(Locale.ROOT);
        String extension = resolveExtension(file.getFileName());
        if ("svg".equals(extension) || (!IMAGE_PREVIEW_EXTENSIONS.contains(extension)
                && !"image".equals(fileType) && !fileType.startsWith("image/"))) {
            throw new IllegalArgumentException("仅图片文件支持预览");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveImageContentType(file.getFileName()));
        response.setHeader("Content-Disposition", "inline");

        try (InputStream inputStream = sysFilesService.downloadFileContent(fileId)) {
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
