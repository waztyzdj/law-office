package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.SysFileConstants.*;

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
import com.lawoffice.system.service.ISysFileMetadataService;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SysFilesServiceImpl extends BaseServiceImpl<SysFilesMapper, SysFiles, SysFilesVO> implements ISysFilesService {

    private final SysFileRelationMapper fileRelationMapper;
    private final ISysFileMetadataService fileMetadataService;
    private final MinioUtils minioUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadVO uploadFile(String username, MultipartFile file, FileUploadReq req) {
        fileMetadataService.validateUploadFile(file);
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
            fileEntity.setFileName(fileMetadataService.resolveFileName(file));
            fileEntity.setUrl(objectName);
            fileEntity.setFileType(fileMetadataService.resolveBaseFileType(file));
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
