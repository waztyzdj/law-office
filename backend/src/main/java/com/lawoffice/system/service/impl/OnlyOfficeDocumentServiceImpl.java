package com.lawoffice.system.service.impl;

import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.system.config.OnlyOfficeProperties;
import com.lawoffice.system.dto.OnlyOfficeCallbackContext;
import com.lawoffice.system.dto.OnlyOfficeCallbackReq;
import com.lawoffice.system.dto.OnlyOfficeDownloadContext;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IOnlyOfficeDocumentService;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.OnlyOfficeHistoryVersionVO;
import com.lawoffice.system.vo.OnlyOfficePreviewVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnlyOfficeDocumentServiceImpl implements IOnlyOfficeDocumentService {

    private static final int CALLBACK_STATUS_READY_FOR_SAVE = 2;
    private static final int CALLBACK_STATUS_FORCE_SAVE = 6;
    private static final String MODE_EDIT = "edit";
    private static final String MODE_VIEW = "view";
    private static final String PURPOSE_CALLBACK = "onlyoffice-callback";
    private static final String PURPOSE_DOWNLOAD = "onlyoffice-download";
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> CELL_EXTENSIONS = Set.of("xls", "xlsx");
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");
    private static final Set<String> EDITABLE_EXTENSIONS = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");
    private static final Duration CALLBACK_DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final OnlyOfficeProperties properties;
    private final ISysFilesService sysFilesService;
    private final UserMapper userMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public OnlyOfficePreviewVO buildPreviewConfig(String username, String userId, String fileId, String mode) {
        assertEnabled();
        String editorMode = normalizeMode(mode);
        DocumentFileVO file = MODE_EDIT.equals(editorMode)
                ? sysFilesService.checkDocumentEdit(fileId, username)
                : sysFilesService.checkDocumentPreview(fileId, username);
        String extension = resolveSupportedExtension(file.getFileName());
        if (MODE_EDIT.equals(editorMode) && !EDITABLE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("当前文件类型不支持在线编辑");
        }
        String documentType = resolveDocumentType(extension);
        String downloadToken = generateDownloadToken(file.getId());
        String callbackToken = MODE_EDIT.equals(editorMode) ? generateCallbackToken(file.getId(), username) : null;

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", extension);
        document.put("key", buildDocumentKey(file));
        document.put("title", file.getFileName());
        document.put("url", joinUrl(properties.getServerBaseUrl(), "/files/document/onlyoffice/download/" + downloadToken));
        document.put("permissions", Map.of(
                "comment", MODE_EDIT.equals(editorMode),
                "copy", true,
                "download", Boolean.TRUE.equals(file.getCanDownload()),
                "edit", MODE_EDIT.equals(editorMode),
                "print", true,
                "review", false
        ));

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("mode", editorMode);
        editorConfig.put("user", Map.of(
                "id", StringUtils.hasText(userId) ? userId : username,
                "name", resolveUserDisplayName(userId, username)
        ));
        if (MODE_EDIT.equals(editorMode)) {
            editorConfig.put("callbackUrl", joinUrl(properties.getServerBaseUrl(), "/files/document/onlyoffice/callback/" + callbackToken));
            editorConfig.put("coEditing", Map.of(
                    "change", false,
                    "mode", "fast"
            ));
        }
        editorConfig.put("customization", Map.of(
                "autosave", MODE_EDIT.equals(editorMode),
                "compactToolbar", true,
                "forcesave", MODE_EDIT.equals(editorMode),
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

    @Override
    public void handleCallback(String token, OnlyOfficeCallbackReq req) {
        assertEnabled();
        OnlyOfficeCallbackContext context = parseCallbackToken(token);
        if (req == null || !isSaveStatus(req.getStatus())) {
            return;
        }
        if (!StringUtils.hasText(req.getUrl())) {
            throw new IllegalArgumentException("ONLYOFFICE 保存回调缺少下载地址");
        }
        URI uri = URI.create(req.getUrl());
        validateCallbackDownloadUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(CALLBACK_DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new IllegalArgumentException("ONLYOFFICE 保存文件下载失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ONLYOFFICE 保存文件下载被中断");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("ONLYOFFICE 保存文件下载失败");
        }
        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream");
        Long contentLength = response.headers()
                .firstValue("Content-Length")
                .map(this::parseLongOrNull)
                .orElse(null);
        try (InputStream inputStream = response.body()) {
            TenantContextHolder.setCurrentTenantId(context.tenantId());
            sysFilesService.saveDocumentEdit(
                    context.fileId(),
                    context.username(),
                    inputStream,
                    contentType,
                    contentLength,
                    req.getStatus() == CALLBACK_STATUS_READY_FOR_SAVE);
        } catch (IOException ex) {
            throw new IllegalArgumentException("ONLYOFFICE 保存文件读取失败");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Override
    public List<OnlyOfficeHistoryVersionVO> listHistory(String username, String fileId) {
        assertEnabled();
        sysFilesService.checkDocumentRead(fileId, username);
        return Collections.emptyList();
    }

    private boolean isSaveStatus(Integer status) {
        return status != null
                && (status == CALLBACK_STATUS_READY_FOR_SAVE || status == CALLBACK_STATUS_FORCE_SAVE);
    }

    private String normalizeMode(String mode) {
        String value = StringUtils.hasText(mode) ? mode.trim().toLowerCase(Locale.ROOT) : MODE_VIEW;
        if (MODE_VIEW.equals(value)) {
            return MODE_VIEW;
        }
        if (MODE_EDIT.equals(value)) {
            return MODE_EDIT;
        }
        throw new IllegalArgumentException("ONLYOFFICE 模式仅支持 view 或 edit");
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

    private String generateCallbackToken(String fileId, String username) {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("purpose", PURPOSE_CALLBACK);
        claims.put("fileId", fileId);
        claims.put("tenantId", tenantId);
        claims.put("username", username);
        return buildToken(claims, callbackTokenTtl());
    }

    private OnlyOfficeCallbackContext parseCallbackToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("ONLYOFFICE 回调令牌不能为空");
        }
        Claims claims = parseToken(token);
        if (!PURPOSE_CALLBACK.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("ONLYOFFICE 回调令牌不正确");
        }
        String fileId = claims.get("fileId", String.class);
        String tenantId = claims.get("tenantId", String.class);
        String username = claims.get("username", String.class);
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(tenantId) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("ONLYOFFICE 回调令牌已失效");
        }
        return new OnlyOfficeCallbackContext(fileId, tenantId, username);
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
            throw new IllegalArgumentException("ONLYOFFICE 令牌无效或已过期");
        }
    }

    private Duration previewTokenTtl() {
        int minutes = properties.getPreviewTokenMinutes() == null ? 10 : properties.getPreviewTokenMinutes();
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private Duration callbackTokenTtl() {
        int minutes = properties.getCallbackTokenMinutes() == null ? 1440 : properties.getCallbackTokenMinutes();
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

    private String resolveUserDisplayName(String userId, String username) {
        if (StringUtils.hasText(userId)) {
            User user = userMapper.selectById(userId);
            if (user != null && !Integer.valueOf(1).equals(user.getDeleteFlag())
                    && StringUtils.hasText(user.getRealname())) {
                return user.getRealname();
            }
        }
        return username;
    }

    private String buildDocumentKey(DocumentFileVO file) {
        String renderVersion = resolveRenderVersion();
        String versionPart = file.getUpdateTime() == null ? "0" : String.valueOf(file.getUpdateTime().hashCode());
        return ("file-" + file.getId() + "-" + versionPart + "-" + renderVersion)
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

    private void validateCallbackDownloadUri(URI uri) {
        if (uri == null || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("ONLYOFFICE 保存下载地址不合法");
        }
        URI documentServerUri = URI.create(normalizeBaseUrl(properties.getDocumentServerUrl()));
        if (!StringUtils.hasText(uri.getScheme())
                || !uri.getScheme().equalsIgnoreCase(documentServerUri.getScheme())) {
            throw new IllegalArgumentException("ONLYOFFICE 保存下载地址来源不受信任");
        }
        if (!StringUtils.hasText(uri.getHost())
                || !uri.getHost().equalsIgnoreCase(documentServerUri.getHost())) {
            throw new IllegalArgumentException("ONLYOFFICE 保存下载地址来源不受信任");
        }
        if (effectivePort(uri) != effectivePort(documentServerUri)) {
            throw new IllegalArgumentException("ONLYOFFICE 保存下载地址来源不受信任");
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return -1;
    }

    private Long parseLongOrNull(String value) {
        try {
            return StringUtils.hasText(value) ? Long.parseLong(value) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
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
