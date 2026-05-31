package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.service.ISysFilesService;
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
    private static final String DEFAULT_RELATION_TYPE = "1";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "bat", "cmd", "com", "exe", "js", "jsp", "msi", "ps1", "sh", "vbs"
    );
    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> PPT_EXTENSIONS = Set.of("ppt", "pptx");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("avi", "flv", "mkv", "mov", "mp4", "wmv");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("7z", "rar", "tar", "gz", "zip");

    private final SysFileRelationMapper fileRelationMapper;
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
            relationReq.setRelationType(Integer.parseInt(DEFAULT_RELATION_TYPE));
            relationReq.setSortOrder(0);
            FileRelationVO relationVO = bindFile(username, relationReq);
            vo.setRelationId(relationVO.getId());
            vo.setBizType(relationVO.getBizType());
            vo.setBizId(relationVO.getBizId());
        }
        return vo;
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
        relation.setRelationType(req.getRelationType() == null ? 1 : req.getRelationType());
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

    private SysFiles getActiveFile(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        SysFiles file = baseMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getId, fileId)
                .eq(SysFiles::getTenantId, requireTenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或已删除");
        }
        return file;
    }

    private SysFileRelation getActiveRelation(String relationId) {
        if (!StringUtils.hasText(relationId)) {
            throw new IllegalArgumentException("文件关联ID不能为空");
        }
        LambdaQueryWrapper<SysFileRelation> wrapper = Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getId, relationId)
                .eq(SysFileRelation::getTenantId, requireTenantId())
                .eq(SysFileRelation::getDeleteFlag, 0)
                .last("LIMIT 1");
        SysFileRelation relation = fileRelationMapper.selectOne(wrapper);
        if (relation == null) {
            throw new IllegalArgumentException("文件关联不存在或已删除");
        }
        return relation;
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() <= 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("单个文件不能超过50MB");
        }
        String fileName = resolveFileName(file);
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("文件名不能超过255个字符");
        }
        String extension = resolveExtension(fileName);
        if (StringUtils.hasText(extension) && BLOCKED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持上传该文件类型");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new IllegalArgumentException("文件MIME类型过长");
        }
    }

    private String resolveFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String filename = StringUtils.hasText(originalFilename)
                ? StringUtils.getFilename(originalFilename)
                : "file";
        if (!StringUtils.hasText(filename) || filename.contains("..")) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return filename;
    }

    private String resolveFileType(MultipartFile file) {
        String name = resolveFileName(file);
        String extension = resolveExtension(name);
        if (!StringUtils.hasText(extension)) {
            return "unknown";
        }
        if (EXCEL_EXTENSIONS.contains(extension)) {
            return "excel";
        }
        if (WORD_EXTENSIONS.contains(extension)) {
            return "doc";
        }
        if (PPT_EXTENSIONS.contains(extension)) {
            return "ppt";
        }
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        if (ARCHIVE_EXTENSIONS.contains(extension)) {
            return "archive";
        }
        return extension.length() <= 64 ? extension : "archive";
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private FileUploadVO buildUploadVO(SysFiles file) {
        FileUploadVO vo = new FileUploadVO();
        vo.setFileId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setObjectName(file.getUrl());
        vo.setFileType(file.getFileType());
        vo.setFileSize(file.getFileSize() == null ? 0L : Math.round(file.getFileSize() * 1024));
        vo.setFileUrl(minioUtils.getObjectUrl(file.getUrl()));
        return vo;
    }

    private String requireTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        return tenantId;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
