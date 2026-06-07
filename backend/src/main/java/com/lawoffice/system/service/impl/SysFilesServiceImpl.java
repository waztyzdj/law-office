package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.req.DocumentBatchDeleteReq;
import com.lawoffice.system.req.DocumentBatchMoveReq;
import com.lawoffice.system.req.DocumentCopyReq;
import com.lawoffice.system.req.DocumentFolderReq;
import com.lawoffice.system.req.DocumentMoveReq;
import com.lawoffice.system.req.DocumentPageReq;
import com.lawoffice.system.req.DocumentRenameReq;
import com.lawoffice.system.req.DocumentShareReq;
import com.lawoffice.system.req.DocumentTreeBatchReq;
import com.lawoffice.system.req.DocumentTreePrefetchReq;
import com.lawoffice.system.req.DocumentUploadReq;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.service.IDocumentCenterService;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.DocumentShareVO;
import com.lawoffice.system.vo.DocumentStatusVO;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.SysFilesVO;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.util.MinioUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SysFilesServiceImpl extends BaseServiceImpl<SysFilesMapper, SysFiles, SysFilesVO> implements ISysFilesService {

    private static final String DEFAULT_STORE_TYPE = "minio";
    private static final Integer DEFAULT_RELATION_TYPE = 1;
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> PPT_EXTENSIONS = Set.of("ppt", "pptx");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("csv", "md", "rtf", "txt");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");
    private static final Set<String> OFFICE_COMPAT_EXTENSIONS = Set.of("dps", "et", "odp", "ods", "odt", "wps");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("avi", "flv", "mkv", "mov", "mp4", "wmv");
    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = Set.of(
            "avi", "bmp", "csv", "doc", "docx", "dps", "et", "flv", "gif", "jpeg", "jpg",
            "md", "mkv", "mov", "mp4", "odp", "ods", "odt", "pdf", "png", "ppt", "pptx",
            "rtf", "txt", "webp", "wmv", "wps", "xls", "xlsx"
    );
    private static final Set<String> BLOCKED_UPLOAD_CONTENT_TYPES = Set.of(
            "application/bat",
            "application/cmd",
            "application/javascript",
            "application/msdos-windows",
            "application/powershell",
            "application/vnd.microsoft.portable-executable",
            "application/x-bat",
            "application/x-cmd",
            "application/x-dosexec",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-msi",
            "application/x-powershell",
            "application/x-sh",
            "application/x-shellscript",
            "text/javascript",
            "text/vbscript",
            "text/x-powershell",
            "text/x-python",
            "text/x-script",
            "text/x-shellscript",
            "text/x-sh"
    );

    private final SysFileRelationMapper fileRelationMapper;
    private final IDocumentCenterService documentCenterService;
    private final MinioUtils minioUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadVO uploadFile(String username, MultipartFile file, FileUploadReq req) {
        validateUploadFile(file);
        String tenantId = requireTenantId();
        String objectName;
        try {
            objectName = minioUtils.uploadFileAndReturnObjectName(file);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("文件上传失败，请检查对象存储配置");
        }

        try {
            SysFiles fileEntity = new SysFiles();
            fileEntity.setId(newId());
            fileEntity.setTenantId(tenantId);
            fileEntity.setFileName(resolveFileName(file));
            fileEntity.setUrl(objectName);
            fileEntity.setFileType(resolveFileType(file));
            fileEntity.setStoreType(DEFAULT_STORE_TYPE);
            fileEntity.setFileSize(file.getSize() > 0 ? file.getSize() / 1024.0 : 0D);
            fileEntity.setCreateBy(username);
            fileEntity.setCreateTime(LocalDateTime.now());
            fileEntity.setDeleteFlag(0);
            baseMapper.insert(fileEntity);

            FileUploadVO vo = buildUploadVO(fileEntity);
            if (req != null && StringUtils.hasText(req.getBizType()) && StringUtils.hasText(req.getBizId())) {
                FileRelationReq relationReq = new FileRelationReq();
                relationReq.setFileId(fileEntity.getId());
                relationReq.setBizType(req.getBizType());
                relationReq.setBizId(req.getBizId());
                relationReq.setRelationType(DEFAULT_RELATION_TYPE);
                relationReq.setSortOrder(0);
                FileRelationVO relationVO = bindFile(username, relationReq);
                vo.setRelationId(relationVO.getId());
                vo.setBizType(relationVO.getBizType());
                vo.setBizId(relationVO.getBizId());
            }
            return vo;
        } catch (RuntimeException ex) {
            deleteObjectQuietly(objectName);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileRelationVO bindFile(String username, FileRelationReq req) {
        if (req == null) {
            throw new IllegalArgumentException("文件关联信息不能为空");
        }
        SysFiles file = getActiveFile(req.getFileId());
        String tenantId = requireTenantId();
        String relationBizType = trimToNull(req.getBizType());
        String relationBizId = trimToNull(req.getBizId());
        if (!StringUtils.hasText(relationBizType) || !StringUtils.hasText(relationBizId)) {
            throw new IllegalArgumentException("业务类型和业务ID不能为空");
        }

        SysFileRelation relation = new SysFileRelation();
        relation.setId(newId());
        relation.setTenantId(tenantId);
        relation.setFileId(file.getId());
        relation.setBizType(relationBizType);
        relation.setBizId(relationBizId);
        relation.setRelationType(req.getRelationType() == null ? DEFAULT_RELATION_TYPE : req.getRelationType());
        relation.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        relation.setCreateBy(username);
        relation.setCreateTime(LocalDateTime.now());
        relation.setDeleteFlag(0);
        fileRelationMapper.insert(relation);
        return BeanUtil.copyProperties(relation, FileRelationVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindFile(String username, String relationId) {
        SysFileRelation relation = getActiveRelation(relationId);
        EntityFillUtils.fillDeleteFields(relation, username);
        fileRelationMapper.updateById(relation);
    }

    @Override
    public List<FileUploadVO> listFilesByBiz(String bizType, String bizId) {
        return listFilesByBiz(bizType, bizId, null);
    }

    @Override
    public List<FileUploadVO> listFilesByBizForOwner(String bizType, String bizId, String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("无权访问业务文件");
        }
        return listFilesByBiz(bizType, bizId, username);
    }

    @Override
    public FileUploadVO getFileById(String fileId) {
        return buildUploadVO(getActiveFile(fileId));
    }

    @Override
    public void checkFileOwner(String fileId, String username) {
        SysFiles file = getActiveFile(fileId);
        if (!StringUtils.hasText(username) || !username.equals(file.getCreateBy())) {
            throw new IllegalArgumentException("无权访问该文件");
        }
    }

    @Override
    public InputStream downloadFileContent(String fileId) {
        SysFiles file = getActiveFile(fileId);
        return minioUtils.downloadFile(file.getUrl());
    }

    @Override
    public PageVO<DocumentFileVO> pageDocuments(String username, DocumentPageReq req) {
        return documentCenterService.pageDocuments(username, req);
    }

    @Override
    public Map<String, List<DocumentFileVO>> batchLoadDocumentFolderTree(String username, DocumentTreeBatchReq req) {
        return documentCenterService.batchLoadDocumentFolderTree(username, req);
    }

    @Override
    public Map<String, List<DocumentFileVO>> prefetchDocumentFolderTree(String username, DocumentTreePrefetchReq req) {
        return documentCenterService.prefetchDocumentFolderTree(username, req);
    }

    @Override
    public DocumentFileVO uploadDocument(String username, MultipartFile file, DocumentUploadReq req) {
        return documentCenterService.uploadDocument(username, file, req);
    }

    @Override
    public DocumentFileVO createDocumentFolder(String username, DocumentFolderReq req) {
        return documentCenterService.createDocumentFolder(username, req);
    }

    @Override
    public DocumentFileVO renameDocument(String username, DocumentRenameReq req) {
        return documentCenterService.renameDocument(username, req);
    }

    @Override
    public DocumentFileVO moveDocument(String username, DocumentMoveReq req) {
        return documentCenterService.moveDocument(username, req);
    }

    @Override
    public List<DocumentFileVO> batchMoveDocuments(String username, DocumentBatchMoveReq req) {
        return documentCenterService.batchMoveDocuments(username, req);
    }

    @Override
    public List<DocumentFileVO> copyDocuments(String username, DocumentCopyReq req) {
        return documentCenterService.copyDocuments(username, req);
    }

    @Override
    public void deleteDocument(String username, String fileId) {
        documentCenterService.deleteDocument(username, fileId);
    }

    @Override
    public void batchDeleteDocuments(String username, DocumentBatchDeleteReq req) {
        documentCenterService.batchDeleteDocuments(username, req);
    }

    @Override
    public DocumentFileVO restoreDocument(String username, String fileId) {
        return documentCenterService.restoreDocument(username, fileId);
    }

    @Override
    public List<DocumentFileVO> batchRestoreDocuments(String username, DocumentBatchDeleteReq req) {
        return documentCenterService.batchRestoreDocuments(username, req);
    }

    @Override
    public void purgeDocument(String username, String fileId) {
        documentCenterService.purgeDocument(username, fileId);
    }

    @Override
    public void clearDocumentTrash(String username) {
        documentCenterService.clearDocumentTrash(username);
    }

    @Override
    public DocumentFileVO toggleDocumentStar(String username, String fileId) {
        return documentCenterService.toggleDocumentStar(username, fileId);
    }

    @Override
    public List<DocumentShareVO> shareDocument(String username, DocumentShareReq req) {
        return documentCenterService.shareDocument(username, req);
    }

    @Override
    public List<DocumentShareVO> listDocumentShares(String username, String fileId) {
        return documentCenterService.listDocumentShares(username, fileId);
    }

    @Override
    public DocumentStatusVO getDocumentStatus(String username, String fileId) {
        return documentCenterService.getDocumentStatus(username, fileId);
    }

    @Override
    public void revokeDocumentShare(String username, String aclId) {
        documentCenterService.revokeDocumentShare(username, aclId);
    }

    @Override
    public DocumentFileVO checkDocumentDownload(String fileId, String username) {
        return documentCenterService.checkDocumentDownload(fileId, username);
    }

    @Override
    public DocumentFileVO checkDocumentPreview(String fileId, String username) {
        return documentCenterService.checkDocumentPreview(fileId, username);
    }

    @Override
    public DocumentFileVO checkDocumentRead(String fileId, String username) {
        return documentCenterService.checkDocumentRead(fileId, username);
    }

    @Override
    public DocumentFileVO checkDocumentEdit(String fileId, String username) {
        return documentCenterService.checkDocumentEdit(fileId, username);
    }

    @Override
    public void saveDocumentEdit(
            String fileId,
            String username,
            InputStream inputStream,
            String contentType,
            Long contentLength,
            boolean touchUpdateTime) {
        documentCenterService.saveDocumentEdit(
                fileId,
                username,
                inputStream,
                contentType,
                contentLength,
                touchUpdateTime);
    }

    private List<FileUploadVO> listFilesByBiz(String bizType, String bizId, String ownerUsername) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return new ArrayList<>();
        }
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysFileRelation> relationWrapper = Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, tenantId)
                .eq(SysFileRelation::getBizType, bizType)
                .eq(SysFileRelation::getBizId, bizId)
                .eq(SysFileRelation::getDeleteFlag, 0)
                .orderByAsc(SysFileRelation::getSortOrder, SysFileRelation::getCreateTime);
        List<SysFileRelation> relations = fileRelationMapper.selectList(relationWrapper);
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> fileIds = relations.stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .toList();
        if (fileIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<SysFiles> fileWrapper = Wrappers.lambdaQuery(SysFiles.class)
                .in(SysFiles::getId, fileIds)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getDeleteFlag, 0);
        if (StringUtils.hasText(ownerUsername)) {
            fileWrapper.eq(SysFiles::getCreateBy, ownerUsername);
        }
        List<SysFiles> files = baseMapper.selectList(fileWrapper);
        Map<String, SysFiles> fileMap = new LinkedHashMap<>();
        for (SysFiles file : files) {
            fileMap.put(file.getId(), file);
        }
        List<FileUploadVO> result = new ArrayList<>();
        for (String fileId : fileIds) {
            SysFiles file = fileMap.get(fileId);
            if (file != null) {
                result.add(buildUploadVO(file));
            }
        }
        return result;
    }

    /**
     * 查询未删除文件并校验当前租户，避免业务附件接口绕过逻辑删除和租户边界。
     */
    private SysFiles getActiveFile(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        SysFiles file = baseMapper.selectById(fileId);
        if (file == null || !Integer.valueOf(0).equals(file.getDeleteFlag())) {
            throw new IllegalArgumentException("文件不存在或已删除");
        }
        String tenantId = requireTenantId();
        if (!tenantId.equals(file.getTenantId())) {
            throw new IllegalArgumentException("无权访问该文件");
        }
        return file;
    }

    /**
     * 查询未删除业务文件关系并校验当前租户，解绑时只做逻辑删除以保留审计链路。
     */
    private SysFileRelation getActiveRelation(String relationId) {
        if (!StringUtils.hasText(relationId)) {
            throw new IllegalArgumentException("文件关联ID不能为空");
        }
        SysFileRelation relation = fileRelationMapper.selectById(relationId);
        if (relation == null || !Integer.valueOf(0).equals(relation.getDeleteFlag())) {
            throw new IllegalArgumentException("文件关联不存在或已删除");
        }
        if (!requireTenantId().equals(relation.getTenantId())) {
            throw new IllegalArgumentException("无权访问该文件关联");
        }
        return relation;
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过50MB");
        }
        String fileName = resolveFileName(file);
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("文件名长度不能超过255个字符");
        }
        String extension = resolveExtension(fileName);
        if (!ALLOWED_UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String normalizedContentType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (normalizedContentType.length() > MAX_CONTENT_TYPE_LENGTH) {
                throw new IllegalArgumentException("文件内容类型过长");
            }
            if (BLOCKED_UPLOAD_CONTENT_TYPES.contains(normalizedContentType)) {
                throw new IllegalArgumentException("不支持的文件内容类型");
            }
        }
    }

    private String resolveFileName(MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            return "未命名文件";
        }
        String normalized = original.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        normalized = normalized.trim();
        if (!StringUtils.hasText(normalized) || ".".equals(normalized) || "..".equals(normalized)) {
            return "未命名文件";
        }
        return normalized;
    }

    private String resolveFileType(MultipartFile file) {
        String extension = resolveExtension(resolveFileName(file));
        if (EXCEL_EXTENSIONS.contains(extension)) {
            return "excel";
        }
        if (WORD_EXTENSIONS.contains(extension)) {
            return "word";
        }
        if (PPT_EXTENSIONS.contains(extension)) {
            return "ppt";
        }
        if (PDF_EXTENSIONS.contains(extension)) {
            return "pdf";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        if (OFFICE_COMPAT_EXTENSIONS.contains(extension)) {
            return "office";
        }
        return "file";
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private FileUploadVO buildUploadVO(SysFiles file) {
        FileUploadVO vo = BeanUtil.copyProperties(file, FileUploadVO.class);
        vo.setFileId(file.getId());
        vo.setObjectName(file.getUrl());
        return vo;
    }

    private String requireTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("缺少租户上下文");
        }
        return tenantId;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void deleteObjectQuietly(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioUtils.deleteFile(objectName);
        } catch (RuntimeException ignored) {
            // 主流程已经失败，清理对象存储失败不能覆盖原始业务异常。
        }
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
