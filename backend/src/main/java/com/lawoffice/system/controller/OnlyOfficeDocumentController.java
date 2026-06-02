package com.lawoffice.system.controller;

import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.dto.OnlyOfficeDownloadContext;
import com.lawoffice.system.service.IOnlyOfficeDocumentService;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.OnlyOfficePreviewVO;
import com.lawoffice.util.HttpDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files/document/onlyoffice")
@Tag(name = "ONLYOFFICE 文档预览", description = "ONLYOFFICE 只读预览配置和文件回源")
public class OnlyOfficeDocumentController {

    private final IOnlyOfficeDocumentService onlyOfficeDocumentService;
    private final ISysFilesService sysFilesService;

    @GetMapping("/config/{fileId}")
    @Operation(summary = "生成 ONLYOFFICE 预览配置", description = "按文档中心权限生成只读预览配置")
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

    private String resolveContentType(String fileType) {
        if (fileType != null && fileType.contains("/")) {
            return fileType;
        }
        return "application/octet-stream";
    }
}
