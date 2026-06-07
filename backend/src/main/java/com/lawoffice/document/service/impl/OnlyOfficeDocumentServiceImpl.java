package com.lawoffice.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.document.config.OnlyOfficeProperties;
import com.lawoffice.document.dto.OnlyOfficeCallbackContext;
import com.lawoffice.document.dto.OnlyOfficeCallbackReq;
import com.lawoffice.document.dto.OnlyOfficeDownloadContext;
import com.lawoffice.document.dto.OnlyOfficeHistoryDownloadContext;
import com.lawoffice.document.dto.OnlyOfficeHistoryFileContent;
import com.lawoffice.system.entity.SysFileVersion;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.SysFileVersionMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.document.service.IDocumentCenterService;
import com.lawoffice.document.service.IOnlyOfficeDocumentService;
import com.lawoffice.document.vo.DocumentFileVO;
import com.lawoffice.document.vo.OnlyOfficeHistoryVersionVO;
import com.lawoffice.document.vo.OnlyOfficePreviewVO;
import com.lawoffice.util.MinioUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnlyOfficeDocumentServiceImpl implements IOnlyOfficeDocumentService {

    private static final int CALLBACK_STATUS_READY_FOR_SAVE = 2;
    private static final int CALLBACK_STATUS_FORCE_SAVE = 6;
    private static final String MODE_EDIT = "edit";
    private static final String MODE_VIEW = "view";
    private static final String PURPOSE_CALLBACK = "onlyoffice-callback";
    private static final String PURPOSE_DOWNLOAD = "onlyoffice-download";
    private static final String PURPOSE_HISTORY_DOWNLOAD = "onlyoffice-history-download";
    private static final String VERSION_TYPE_FINAL = "final";
    private static final String VERSION_TYPE_RESTORE = "restore";
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> CELL_EXTENSIONS = Set.of("xls", "xlsx");
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");
    private static final Set<String> EDITABLE_EXTENSIONS = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");
    private static final Duration CALLBACK_DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final OnlyOfficeProperties properties;
    private final IDocumentCenterService documentCenterService;
    private final UserMapper userMapper;
    private final SysFileVersionMapper sysFileVersionMapper;
    private final MinioUtils minioUtils;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public OnlyOfficePreviewVO buildPreviewConfig(String username, String userId, String fileId, String mode) {
        assertEnabled();
        String editorMode = normalizeMode(mode);
        DocumentFileVO file = MODE_EDIT.equals(editorMode)
                ? documentCenterService.checkDocumentEdit(fileId, username)
                : documentCenterService.checkDocumentPreview(fileId, username);
        String extension = resolveSupportedExtension(file.getFileName());
        if (MODE_EDIT.equals(editorMode) && !EDITABLE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Current file type does not support online editing");
        }
        String documentType = resolveDocumentType(extension);
        String downloadToken = generateDownloadToken(file.getId());
        String callbackToken = MODE_EDIT.equals(editorMode) ? generateCallbackToken(file.getId(), username) : null;

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", extension);
        document.put("key", buildDocumentKey(file));
        document.put("title", file.getFileName());
        document.put("url", joinUrl(properties.getServerBaseUrl(), "/document/files/onlyoffice/download/" + downloadToken));
        document.put("permissions", Map.of(
                "comment", MODE_EDIT.equals(editorMode),
                "copy", true,
                "download", Boolean.TRUE.equals(file.getCanDownload()),
                "edit", MODE_EDIT.equals(editorMode),
                "print", true,
                "review", false
        ));

        Map<String, Object> editorConfig = buildEditorConfig(username, userId, editorMode);
        if (MODE_EDIT.equals(editorMode)) {
            editorConfig.put("callbackUrl", joinUrl(properties.getServerBaseUrl(), "/document/files/onlyoffice/callback/" + callbackToken));
            editorConfig.put("coEditing", Map.of(
                    "change", false,
                    "mode", "fast"
            ));
        }
        editorConfig.put("customization", buildCustomization(editorMode));

        return buildPreviewVO(document, documentType, editorConfig);
    }

    @Override
    public OnlyOfficeDownloadContext parseDownloadToken(String token) {
        assertEnabled();
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("ONLYOFFICE download token cannot be empty");
        }
        Claims claims = parseToken(token);
        if (!PURPOSE_DOWNLOAD.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid ONLYOFFICE download token");
        }
        String fileId = claims.get("fileId", String.class);
        String tenantId = claims.get("tenantId", String.class);
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("Invalid ONLYOFFICE download token payload");
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
            throw new IllegalArgumentException("ONLYOFFICE callback missing download url");
        }

        DownloadedCallbackFile downloaded = null;
        try {
            downloaded = downloadCallbackFile(req.getUrl());
            TenantContextHolder.setCurrentTenantId(context.tenantId());
            try (InputStream inputStream = Files.newInputStream(downloaded.path())) {
                documentCenterService.saveDocumentEdit(
                        context.fileId(),
                        context.username(),
                        inputStream,
                        downloaded.contentType(),
                        downloaded.contentLength(),
                        req.getStatus() == CALLBACK_STATUS_READY_FOR_SAVE);
            }
            if (req.getStatus() == CALLBACK_STATUS_READY_FOR_SAVE) {
                createFinalHistoryVersion(context, req, downloaded);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("ONLYOFFICE saved file read failed");
        } finally {
            TenantContextHolder.clear();
            deleteQuietly(downloaded);
        }
    }

    @Override
    public List<OnlyOfficeHistoryVersionVO> listHistory(String username, String fileId) {
        assertEnabled();
        documentCenterService.checkDocumentRead(fileId, username);
        return sysFileVersionMapper.selectList(Wrappers.lambdaQuery(SysFileVersion.class)
                        .eq(SysFileVersion::getFileId, fileId)
                        .eq(SysFileVersion::getDeleteFlag, 0)
                        .orderByDesc(SysFileVersion::getVersionNo)
                        .orderByDesc(SysFileVersion::getCreateTime))
                .stream()
                .map(this::buildHistoryVO)
                .toList();
    }

    @Override
    public OnlyOfficePreviewVO buildHistoryPreviewConfig(String username, String userId, String versionId) {
        assertEnabled();
        SysFileVersion version = getActiveVersion(versionId);
        DocumentFileVO file = documentCenterService.checkDocumentRead(version.getFileId(), username);
        String fileName = StringUtils.hasText(version.getFileName()) ? version.getFileName() : file.getFileName();
        String extension = resolveSupportedExtension(fileName);
        String documentType = resolveDocumentType(extension);
        String downloadToken = generateHistoryDownloadToken(version.getId());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", extension);
        document.put("key", buildHistoryDocumentKey(version));
        document.put("title", fileName + " V" + version.getVersionNo());
        document.put("url", joinUrl(properties.getServerBaseUrl(), "/document/files/onlyoffice/history/download/" + downloadToken));
        document.put("permissions", Map.of(
                "comment", false,
                "copy", true,
                "download", Boolean.TRUE.equals(file.getCanDownload()),
                "edit", false,
                "print", true,
                "review", false
        ));

        Map<String, Object> editorConfig = buildEditorConfig(username, userId, MODE_VIEW);
        editorConfig.put("customization", buildCustomization(MODE_VIEW));
        return buildPreviewVO(document, documentType, editorConfig);
    }

    @Override
    public OnlyOfficeHistoryDownloadContext parseHistoryDownloadToken(String token) {
        assertEnabled();
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("ONLYOFFICE history download token cannot be empty");
        }
        Claims claims = parseToken(token);
        if (!PURPOSE_HISTORY_DOWNLOAD.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid ONLYOFFICE history download token");
        }
        String versionId = claims.get("versionId", String.class);
        String tenantId = claims.get("tenantId", String.class);
        if (!StringUtils.hasText(versionId) || !StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("Invalid ONLYOFFICE history download token payload");
        }
        return new OnlyOfficeHistoryDownloadContext(versionId, tenantId);
    }

    @Override
    public OnlyOfficeHistoryFileContent openHistoryFileContent(String versionId) {
        SysFileVersion version = getActiveVersion(versionId);
        return new OnlyOfficeHistoryFileContent(
                version.getFileName(),
                version.getContentType(),
                minioUtils.downloadFile(version.getObjectName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OnlyOfficeHistoryVersionVO restoreHistoryVersion(String username, String versionId) {
        assertEnabled();
        SysFileVersion source = getActiveVersion(versionId);
        DocumentFileVO file = documentCenterService.checkDocumentEdit(source.getFileId(), username);
        try (InputStream inputStream = minioUtils.downloadFile(source.getObjectName())) {
            documentCenterService.saveDocumentEdit(
                    source.getFileId(),
                    username,
                    inputStream,
                    safeContentType(source.getContentType()),
                    source.getFileSize(),
                    true);
        } catch (IOException ex) {
            throw new IllegalArgumentException("History version restore failed");
        }
        return buildHistoryVO(createRestoreHistoryVersion(source, file, username));
    }

    private void createFinalHistoryVersion(
            OnlyOfficeCallbackContext context,
            OnlyOfficeCallbackReq req,
            DownloadedCallbackFile downloaded) throws IOException {
        SysFileVersion latest = getLatestVersion(context.fileId());
        if (latest != null && downloaded.checksum().equals(latest.getChecksum())) {
            return;
        }
        DocumentFileVO file = documentCenterService.checkDocumentRead(context.fileId(), context.username());
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        String contentType = safeContentType(downloaded.contentType());
        String versionFileName = buildVersionFileName(file.getFileName(), versionNo);
        String objectName;
        try (InputStream versionInput = Files.newInputStream(downloaded.path())) {
            objectName = minioUtils.uploadFileAndReturnObjectName(versionInput, versionFileName, contentType);
        }

        try {
            SysFileVersion version = new SysFileVersion();
            fillNewVersion(version, file, context.username(), versionNo, VERSION_TYPE_FINAL);
            version.setObjectName(objectName);
            version.setFileName(file.getFileName());
            version.setFileType(file.getFileType());
            version.setContentType(contentType);
            version.setFileSize(downloaded.contentLength());
            version.setChecksum(downloaded.checksum());
            version.setDocumentKey(req.getKey());
            version.setServerVersion(resolveServerVersion(req.getHistory()));
            version.setHistoryJson(serializeHistory(req.getHistory()));
            version.setRemark("ONLYOFFICE final save");
            sysFileVersionMapper.insert(version);
        } catch (RuntimeException ex) {
            deleteObjectQuietly(objectName);
            throw ex;
        }
    }

    private SysFileVersion createRestoreHistoryVersion(SysFileVersion source, DocumentFileVO file, String username) {
        SysFileVersion latest = getLatestVersion(source.getFileId());
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;

        SysFileVersion version = new SysFileVersion();
        fillNewVersion(version, file, username, versionNo, VERSION_TYPE_RESTORE);
        version.setObjectName(source.getObjectName());
        version.setFileName(StringUtils.hasText(source.getFileName()) ? source.getFileName() : file.getFileName());
        version.setFileType(StringUtils.hasText(source.getFileType()) ? source.getFileType() : file.getFileType());
        version.setContentType(source.getContentType());
        version.setFileSize(source.getFileSize());
        version.setChecksum(source.getChecksum());
        version.setDocumentKey(source.getDocumentKey());
        version.setServerVersion(source.getServerVersion());
        version.setHistoryJson(source.getHistoryJson());
        version.setRemark("Restore from V" + source.getVersionNo());
        sysFileVersionMapper.insert(version);
        return version;
    }

    private void fillNewVersion(
            SysFileVersion version,
            DocumentFileVO file,
            String username,
            int versionNo,
            String versionType) {
        LocalDateTime now = LocalDateTime.now();
        User editor = findUserByUsername(username);
        version.setId(newId());
        version.setFileId(file.getId());
        version.setVersionNo(versionNo);
        version.setVersionType(versionType);
        version.setTenantId(TenantContextHolder.getCurrentTenantId());
        version.setEditorId(editor == null ? null : editor.getId());
        version.setEditorName(resolveUserDisplayName(editor, username));
        version.setCreateBy(username);
        version.setCreateTime(now);
        version.setUpdateBy(username);
        version.setUpdateTime(now);
        version.setDeleteFlag(0);
    }

    private DownloadedCallbackFile downloadCallbackFile(String url) {
        URI uri = URI.create(url);
        validateCallbackDownloadUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(CALLBACK_DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new IllegalArgumentException("ONLYOFFICE saved file download failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ONLYOFFICE saved file download interrupted");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new IllegalArgumentException("ONLYOFFICE saved file download failed");
        }

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream");
        Path tempFile;
        try {
            tempFile = Files.createTempFile("onlyoffice-save-", ".bin");
        } catch (IOException ex) {
            closeQuietly(response.body());
            throw new IllegalArgumentException("ONLYOFFICE temp file create failed");
        }

        MessageDigest digest = sha256Digest();
        try (InputStream inputStream = response.body();
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            Files.copy(digestInputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            long contentLength = Files.size(tempFile);
            String checksum = HexFormat.of().formatHex(digest.digest());
            return new DownloadedCallbackFile(tempFile, safeContentType(contentType), contentLength, checksum);
        } catch (IOException ex) {
            deleteQuietly(tempFile);
            throw new IllegalArgumentException("ONLYOFFICE saved file download failed");
        }
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
        throw new IllegalArgumentException("ONLYOFFICE mode only supports view or edit");
    }

    private String generateDownloadToken(String fileId) {
        String tenantId = requireTenantId();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("purpose", PURPOSE_DOWNLOAD);
        claims.put("fileId", fileId);
        claims.put("tenantId", tenantId);
        return buildToken(claims, previewTokenTtl());
    }

    private String generateHistoryDownloadToken(String versionId) {
        String tenantId = requireTenantId();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("purpose", PURPOSE_HISTORY_DOWNLOAD);
        claims.put("versionId", versionId);
        claims.put("tenantId", tenantId);
        return buildToken(claims, previewTokenTtl());
    }

    private String generateCallbackToken(String fileId, String username) {
        String tenantId = requireTenantId();
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Current user cannot be empty");
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
            throw new IllegalArgumentException("ONLYOFFICE callback token cannot be empty");
        }
        Claims claims = parseToken(token);
        if (!PURPOSE_CALLBACK.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid ONLYOFFICE callback token");
        }
        String fileId = claims.get("fileId", String.class);
        String tenantId = claims.get("tenantId", String.class);
        String username = claims.get("username", String.class);
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(tenantId) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Invalid ONLYOFFICE callback token payload");
        }
        return new OnlyOfficeCallbackContext(fileId, tenantId, username);
    }

    private OnlyOfficePreviewVO buildPreviewVO(
            Map<String, Object> document,
            String documentType,
            Map<String, Object> editorConfig) {
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

    private Map<String, Object> buildEditorConfig(String username, String userId, String editorMode) {
        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("mode", editorMode);
        editorConfig.put("user", Map.of(
                "id", StringUtils.hasText(userId) ? userId : username,
                "name", resolveUserDisplayName(userId, username)
        ));
        return editorConfig;
    }

    private Map<String, Object> buildCustomization(String editorMode) {
        return Map.of(
                "autosave", MODE_EDIT.equals(editorMode),
                "compactToolbar", true,
                "forcesave", MODE_EDIT.equals(editorMode),
                "logo", Map.of("visible", false)
        );
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
            throw new IllegalArgumentException("ONLYOFFICE token is invalid or expired");
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
            throw new IllegalArgumentException("ONLYOFFICE JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private void assertEnabled() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new IllegalArgumentException("ONLYOFFICE is not enabled");
        }
        if (!StringUtils.hasText(properties.getDocumentServerUrl())) {
            throw new IllegalArgumentException("ONLYOFFICE Document Server url cannot be empty");
        }
        if (!StringUtils.hasText(properties.getServerBaseUrl())) {
            throw new IllegalArgumentException("ONLYOFFICE backend source url cannot be empty");
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
        throw new IllegalArgumentException("Current file type does not support ONLYOFFICE preview");
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

    private String resolveUserDisplayName(User user, String username) {
        if (user != null && StringUtils.hasText(user.getRealname())) {
            return user.getRealname();
        }
        return username;
    }

    private String buildDocumentKey(DocumentFileVO file) {
        String renderVersion = resolveRenderVersion();
        String versionPart = file.getUpdateTime() == null ? "0" : String.valueOf(file.getUpdateTime().hashCode());
        return sanitizeDocumentKey("file-" + file.getId() + "-" + versionPart + "-" + renderVersion);
    }

    private String buildHistoryDocumentKey(SysFileVersion version) {
        return sanitizeDocumentKey("file-version-" + version.getId() + "-" + version.getVersionNo() + "-" + resolveRenderVersion());
    }

    private String sanitizeDocumentKey(String key) {
        return key.replaceAll("[^A-Za-z0-9._=-]", "_");
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
            throw new IllegalArgumentException("ONLYOFFICE callback download url is invalid");
        }
        URI documentServerUri = URI.create(normalizeBaseUrl(properties.getDocumentServerUrl()));
        if (!StringUtils.hasText(uri.getScheme())
                || !uri.getScheme().equalsIgnoreCase(documentServerUri.getScheme())) {
            throw new IllegalArgumentException("ONLYOFFICE callback download source is not trusted");
        }
        if (!StringUtils.hasText(uri.getHost())
                || !uri.getHost().equalsIgnoreCase(documentServerUri.getHost())) {
            throw new IllegalArgumentException("ONLYOFFICE callback download source is not trusted");
        }
        if (effectivePort(uri) != effectivePort(documentServerUri)) {
            throw new IllegalArgumentException("ONLYOFFICE callback download source is not trusted");
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

    private SysFileVersion getActiveVersion(String versionId) {
        if (!StringUtils.hasText(versionId)) {
            throw new IllegalArgumentException("History version id cannot be empty");
        }
        SysFileVersion version = sysFileVersionMapper.selectOne(Wrappers.lambdaQuery(SysFileVersion.class)
                .eq(SysFileVersion::getId, versionId)
                .eq(SysFileVersion::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (version == null) {
            throw new IllegalArgumentException("History version does not exist");
        }
        return version;
    }

    private SysFileVersion getLatestVersion(String fileId) {
        return sysFileVersionMapper.selectOne(Wrappers.lambdaQuery(SysFileVersion.class)
                .eq(SysFileVersion::getFileId, fileId)
                .eq(SysFileVersion::getDeleteFlag, 0)
                .orderByDesc(SysFileVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    private OnlyOfficeHistoryVersionVO buildHistoryVO(SysFileVersion version) {
        OnlyOfficeHistoryVersionVO vo = new OnlyOfficeHistoryVersionVO();
        vo.setId(version.getId());
        vo.setFileId(version.getFileId());
        vo.setVersionNo(version.getVersionNo());
        vo.setVersion("V" + version.getVersionNo());
        vo.setVersionType(version.getVersionType());
        vo.setEditor(version.getEditorName());
        vo.setEditorName(version.getEditorName());
        vo.setEditTime(version.getCreateTime());
        vo.setFileSize(version.getFileSize());
        vo.setRemark(version.getRemark());
        return vo;
    }

    private User findUserByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userMapper.selectOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getUsername, username)
                .eq(User::getDeleteFlag, 0)
                .last("LIMIT 1"));
    }

    private String requireTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("Current tenant cannot be empty");
        }
        return tenantId;
    }

    private String resolveServerVersion(Map<String, Object> history) {
        if (history == null) {
            return null;
        }
        Object value = history.get("serverVersion");
        return value == null ? null : String.valueOf(value);
    }

    private String serializeHistory(Map<String, Object> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(history);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private String safeContentType(String contentType) {
        return StringUtils.hasText(contentType)
                ? contentType.split(";", 2)[0].trim()
                : "application/octet-stream";
    }

    private String buildVersionFileName(String fileName, int versionNo) {
        if (!StringUtils.hasText(fileName)) {
            return "document-v" + versionNo;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName + "-v" + versionNo;
        }
        return fileName.substring(0, dotIndex) + "-v" + versionNo + fileName.substring(dotIndex);
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

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // ignore close failure
        }
    }

    private void deleteQuietly(DownloadedCallbackFile downloaded) {
        if (downloaded != null) {
            deleteQuietly(downloaded.path());
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore temp cleanup failure
        }
    }

    private void deleteObjectQuietly(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioUtils.deleteFile(objectName);
        } catch (RuntimeException ignored) {
            // object cleanup is compensating work and should not hide the original error
        }
    }

    private record DownloadedCallbackFile(
            Path path,
            String contentType,
            Long contentLength,
            String checksum) {
    }
}
