package com.lawoffice.system.controller;

import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.dto.OnlyOfficeCallbackReq;
import com.lawoffice.system.dto.OnlyOfficeDownloadContext;
import com.lawoffice.system.dto.OnlyOfficeHistoryDownloadContext;
import com.lawoffice.system.dto.OnlyOfficeHistoryFileContent;
import com.lawoffice.system.service.IOnlyOfficeDocumentService;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.OnlyOfficeHistoryVersionVO;
import com.lawoffice.system.vo.OnlyOfficePreviewVO;
import com.lawoffice.util.HttpDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/files/document/onlyoffice")
@Tag(name = "ONLYOFFICE 文档协作", description = "ONLYOFFICE 在线预览、编辑保存和文档回源")
public class OnlyOfficeDocumentController {

    private final IOnlyOfficeDocumentService onlyOfficeDocumentService;
    private final ISysFilesService sysFilesService;

    @GetMapping("/config/{fileId}")
    @Operation(summary = "生成 ONLYOFFICE 配置", description = "按文档中心权限生成预览或编辑配置")
    public BaseResult<OnlyOfficePreviewVO> getPreviewConfig(
            @PathVariable String fileId,
            @RequestParam(required = false, defaultValue = "view") String mode,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        String userId = (String) request.getAttribute("userId");
        return BaseResult.success(onlyOfficeDocumentService.buildPreviewConfig(username, userId, fileId, mode));
    }

    @GetMapping("/download/{token}")
    @Operation(summary = "ONLYOFFICE 文件回源", description = "Document Server 使用短期令牌拉取文件内容")
    public void downloadForOnlyOffice(
            @PathVariable String token,
            HttpServletResponse response) throws IOException {
        OnlyOfficeDownloadContext context = onlyOfficeDocumentService.parseDownloadToken(token);
        try {
            TenantContextHolder.setCurrentTenantId(context.tenantId());
            FileUploadVO file = sysFilesService.getFileById(context.fileId());
            String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(resolveContentType(file.getFileType()));
            response.setHeader("Content-Disposition", HttpDownloadUtils.buildInlineContentDisposition(fileName));
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            try (InputStream inputStream = sysFilesService.downloadFileContent(context.fileId())) {
                inputStream.transferTo(response.getOutputStream());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    @PostMapping("/callback/{token}")
    @Operation(summary = "ONLYOFFICE 保存回调", description = "Document Server 保存在线编辑内容时调用")
    public Map<String, Integer> handleCallback(
            @PathVariable String token,
            @RequestBody(required = false) OnlyOfficeCallbackReq req) {
        try {
            onlyOfficeDocumentService.handleCallback(token, req);
            return Map.of("error", 0);
        } catch (RuntimeException ex) {
            log.error("ONLYOFFICE 保存回调处理失败", ex);
            return Map.of("error", 1);
        }
    }

    @GetMapping("/history/{fileId}")
    @Operation(summary = "ONLYOFFICE 历史版本列表", description = "查询文件在线编辑历史版本")
    public BaseResult<List<OnlyOfficeHistoryVersionVO>> listHistory(
            @PathVariable String fileId,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(onlyOfficeDocumentService.listHistory(username, fileId));
    }

    @GetMapping("/history/config/{versionId}")
    @Operation(summary = "ONLYOFFICE 历史版本预览配置", description = "生成历史版本只读预览配置")
    public BaseResult<OnlyOfficePreviewVO> getHistoryPreviewConfig(
            @PathVariable String versionId,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        String userId = (String) request.getAttribute("userId");
        return BaseResult.success(onlyOfficeDocumentService.buildHistoryPreviewConfig(username, userId, versionId));
    }

    @GetMapping("/history/download/{token}")
    @Operation(summary = "ONLYOFFICE 历史版本回源", description = "Document Server 使用短期令牌拉取历史版本内容")
    public void downloadHistoryForOnlyOffice(
            @PathVariable String token,
            HttpServletResponse response) throws IOException {
        OnlyOfficeHistoryDownloadContext context = onlyOfficeDocumentService.parseHistoryDownloadToken(token);
        try {
            TenantContextHolder.setCurrentTenantId(context.tenantId());
            OnlyOfficeHistoryFileContent content = onlyOfficeDocumentService.openHistoryFileContent(context.versionId());
            String fileName = HttpDownloadUtils.resolveDownloadFileName(content.fileName());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(resolveContentType(content.fileType()));
            response.setHeader("Content-Disposition", HttpDownloadUtils.buildInlineContentDisposition(fileName));
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            try (InputStream inputStream = content.inputStream()) {
                inputStream.transferTo(response.getOutputStream());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    @PostMapping("/history/{versionId}/restore")
    @Operation(summary = "ONLYOFFICE 恢复历史版本", description = "将历史版本恢复为当前文件")
    public BaseResult<OnlyOfficeHistoryVersionVO> restoreHistoryVersion(
            @PathVariable String versionId,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(onlyOfficeDocumentService.restoreHistoryVersion(username, versionId));
    }

    private String resolveContentType(String fileType) {
        if (fileType != null && fileType.contains("/")) {
            return fileType;
        }
        return "application/octet-stream";
    }
}
