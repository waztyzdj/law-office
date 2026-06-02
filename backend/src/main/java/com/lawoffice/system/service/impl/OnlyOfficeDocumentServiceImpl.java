package com.lawoffice.system.service.impl;

import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.system.config.OnlyOfficeProperties;
import com.lawoffice.system.dto.OnlyOfficeDownloadContext;
import com.lawoffice.system.service.IOnlyOfficeDocumentService;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.OnlyOfficePreviewVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnlyOfficeDocumentServiceImpl implements IOnlyOfficeDocumentService {

    private static final String MODE_VIEW = "view";
    private static final String PURPOSE_DOWNLOAD = "onlyoffice-download";
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> CELL_EXTENSIONS = Set.of("xls", "xlsx");
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

    private final OnlyOfficeProperties properties;
    private final ISysFilesService sysFilesService;

    @Override
    public OnlyOfficePreviewVO buildPreviewConfig(String username, String userId, String fileId, String mode) {
        assertEnabled();
        if (StringUtils.hasText(mode) && !MODE_VIEW.equals(mode)) {
            throw new IllegalArgumentException("当前阶段仅支持 ONLYOFFICE 只读预览");
        }
        DocumentFileVO file = sysFilesService.checkDocumentPreview(fileId, username);
        String extension = resolveSupportedExtension(file.getFileName());
        String documentType = resolveDocumentType(extension);
        String token = generateDownloadToken(file.getId());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", extension);
        document.put("key", buildDocumentKey(file));
        document.put("title", file.getFileName());
        document.put("url", joinUrl(properties.getServerBaseUrl(), "/files/document/onlyoffice/download/" + token));
        document.put("permissions", Map.of(
                "copy", true,
                "download", Boolean.TRUE.equals(file.getCanDownload()),
                "edit", false,
                "print", true,
                "review", false
        ));

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("mode", MODE_VIEW);
        editorConfig.put("user", Map.of(
                "id", StringUtils.hasText(userId) ? userId : username,
                "name", username
        ));
        editorConfig.put("customization", Map.of(
                "autosave", false,
                "compactToolbar", true,
                "forcesave", false,
                "logo", Map.of("visible", false)
        ));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("document", document);
        config.put("documentType", documentType);
        config.put("editorConfig", editorConfig);
        config.put("type", "desktop");
        config.put("token", generateConfigToken(config));

        OnlyOfficePreviewVO vo = new OnlyOfficePreviewVO();
        vo.setDocumentServerApiUrl(buildDocumentServerApiUrl());
        vo.setConfig(config);
        return vo;
    }

    @Override
    public OnlyOfficeDownloadContext parseDownloadToken(String token) {
        assertEnabled();
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("ONLYOFFICE 下载令牌不能为空");
        }
        Claims claims = parseToken(token);
        if (!PURPOSE_DOWNLOAD.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("ONLYOFFICE 下载令牌不正确");
        }
        String fileId = claims.get("fileId", String.class);
        String tenantId = claims.get("tenantId", String.class);
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("ONLYOFFICE 下载令牌已失效");
        }
        return new OnlyOfficeDownloadContext(fileId, tenantId);
    }

    private String generateDownloadToken(String fileId) {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("purpose", PURPOSE_DOWNLOAD);
        claims.put("fileId", fileId);
        claims.put("tenantId", tenantId);
        return buildToken(claims, previewTokenTtl());
    }

    private String generateConfigToken(Map<String, Object> config) {
        return buildToken(new LinkedHashMap<>(config), previewTokenTtl());
    }

    private String buildToken(Map<String, Object> claims, Duration ttl) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey())
                .compact();
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("ONLYOFFICE 下载令牌无效或已过期");
        }
    }

    private Duration previewTokenTtl() {
        int minutes = properties.getPreviewTokenMinutes() == null ? 10 : properties.getPreviewTokenMinutes();
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private SecretKey secretKey() {
        String secret = properties.getJwtSecret();
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("ONLYOFFICE JWT 密钥至少需要 32 字节");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private void assertEnabled() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new IllegalArgumentException("ONLYOFFICE 未启用");
        }
        if (!StringUtils.hasText(properties.getDocumentServerUrl())) {
            throw new IllegalArgumentException("ONLYOFFICE Document Server 地址不能为空");
        }
        if (!StringUtils.hasText(properties.getServerBaseUrl())) {
            throw new IllegalArgumentException("ONLYOFFICE 后端回源地址不能为空");
        }
    }

    private String resolveSupportedExtension(String fileName) {
        String extension = resolveExtension(fileName);
        if (WORD_EXTENSIONS.contains(extension)
                || CELL_EXTENSIONS.contains(extension)
                || SLIDE_EXTENSIONS.contains(extension)
                || PDF_EXTENSIONS.contains(extension)) {
            return extension;
        }
        throw new IllegalArgumentException("当前文件类型暂不支持 ONLYOFFICE 预览");
    }

    private String resolveDocumentType(String extension) {
        if (WORD_EXTENSIONS.contains(extension)) {
            return "word";
        }
        if (CELL_EXTENSIONS.contains(extension)) {
            return "cell";
        }
        if (SLIDE_EXTENSIONS.contains(extension)) {
            return "slide";
        }
        return "pdf";
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String buildDocumentKey(DocumentFileVO file) {
        String updateTime = file.getUpdateTime() == null ? "0" : String.valueOf(file.getUpdateTime().hashCode());
        String renderVersion = resolveRenderVersion();
        return ("file-" + file.getId() + "-" + updateTime + "-" + renderVersion)
                .replaceAll("[^A-Za-z0-9._=-]", "_");
    }

    private String buildDocumentServerApiUrl() {
        String renderVersion = URLEncoder.encode(resolveRenderVersion(), StandardCharsets.UTF_8);
        return joinUrl(properties.getDocumentServerUrl(), "/web-apps/apps/api/documents/api.js") + "?v=" + renderVersion;
    }

    private String resolveRenderVersion() {
        return StringUtils.hasText(properties.getRenderVersion())
                ? properties.getRenderVersion()
                : "default";
    }

    private String joinUrl(String baseUrl, String path) {
        String base = normalizeBaseUrl(baseUrl);
        String suffix = path == null ? "" : path.trim();
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return base + suffix;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
