package com.lawoffice.document.service.impl;

import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFileVersion;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileAclMapper;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFileVersionMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.document.service.IDocumentTreeLifecycleService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.util.MinioUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTreeLifecycleServiceImpl implements IDocumentTreeLifecycleService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileAclMapper fileAclMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final SysFileVersionMapper sysFileVersionMapper;
    private final MinioUtils minioUtils;

    @Override
    public void softDeleteDocumentTree(SysFiles file, String username) {
        EntityFillUtils.fillDeleteFields(file, username);
        sysFilesMapper.updateById(file);
        List<SysFiles> children = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, file.getTenantId())
                .eq(SysFiles::getParentId, file.getId())
                .eq(SysFiles::getDeleteFlag, 0));
        for (SysFiles child : children) {
            softDeleteDocumentTree(child, username);
        }
    }

    @Override
    public void restoreDocumentTree(SysFiles file, String username) {
        file.setDeleteFlag(0);
        file.setDeleteTime(null);
        file.setDeleteBy(null);
        fillUpdate(file, username);
        sysFilesMapper.updateById(file);
        List<SysFiles> children = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, file.getTenantId())
                .eq(SysFiles::getParentId, file.getId())
                .eq(SysFiles::getDeleteFlag, 1));
        for (SysFiles child : children) {
            restoreDocumentTree(child, username);
        }
    }

    /**
     * 物理清理时同时删除对象存储文件、共享授权和个人归类关系，避免残留孤儿数据。
     */
    @Override
    public void hardDeleteDocumentTree(SysFiles file) {
        List<SysFiles> children = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, file.getTenantId())
                .eq(SysFiles::getParentId, file.getId()));
        for (SysFiles child : children) {
            hardDeleteDocumentTree(child);
        }
        if (!FLAG_YES.equals(file.getIzFolder()) && StringUtils.hasText(file.getUrl())) {
            deleteObjectAfterCommit(file.getUrl());
        }
        hardDeleteDocumentVersions(file);
        fileAclMapper.delete(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, file.getTenantId())
                .eq(SysFileAcl::getFileId, file.getId()));
        fileRelationMapper.delete(Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, file.getTenantId())
                .eq(SysFileRelation::getFileId, file.getId()));
        sysFilesMapper.deleteById(file.getId());
    }

    /**
     * 版本文件和变更文件可能被其他业务文件复用，物理清理前必须先检查其他引用。
     */
    private void hardDeleteDocumentVersions(SysFiles file) {
        List<SysFileVersion> versions = sysFileVersionMapper.selectList(Wrappers.lambdaQuery(SysFileVersion.class)
                .eq(SysFileVersion::getTenantId, file.getTenantId())
                .eq(SysFileVersion::getFileId, file.getId()));
        if (versions.isEmpty()) {
            return;
        }
        Set<String> objectNames = versions.stream()
                .flatMap(version -> {
                    List<String> names = new ArrayList<>();
                    names.add(version.getObjectName());
                    names.add(version.getChangesObjectName());
                    return names.stream();
                })
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String objectName : objectNames) {
            if (!isVersionObjectReferencedByOtherFile(file, objectName)) {
                deleteObjectAfterCommit(objectName);
            }
        }
        sysFileVersionMapper.delete(Wrappers.lambdaQuery(SysFileVersion.class)
                .eq(SysFileVersion::getTenantId, file.getTenantId())
                .eq(SysFileVersion::getFileId, file.getId()));
    }

    /**
     * 只有在其他文件没有复用同一个对象存储对象时，才允许清理该版本对象。
     */
    private boolean isVersionObjectReferencedByOtherFile(SysFiles file, String objectName) {
        return sysFileVersionMapper.selectCount(Wrappers.lambdaQuery(SysFileVersion.class)
                .eq(SysFileVersion::getTenantId, file.getTenantId())
                .ne(SysFileVersion::getFileId, file.getId())
                .and(wrapper -> wrapper
                        .eq(SysFileVersion::getObjectName, objectName)
                        .or()
                        .eq(SysFileVersion::getChangesObjectName, objectName))) > 0;
    }

    /**
     * 数据库事务提交后再删除对象存储文件，避免库回滚但物理文件已经被提前删除。
     */
    private void deleteObjectAfterCommit(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteObjectQuietly(objectName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteObjectQuietly(objectName);
            }
        });
    }

    /**
     * 对象存储清理是补偿动作，失败不能覆盖原始业务结果。
     */
    private void deleteObjectQuietly(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioUtils.deleteFile(objectName);
        } catch (Exception ignored) {
            // 对象存储清理失败不应覆盖数据库事务结果，后续可通过对象存储巡检补偿。
        }
    }

    /**
     * 清理动作只负责去掉空白值，不改变业务语义，只是让后续删除和比对更稳定。
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    /**
     * 恢复/软删流程只需要回写审计字段，不能引入额外业务分支。
     */
    private void fillUpdate(SysFiles file, String username) {
        file.setUpdateTime(LocalDateTime.now());
        file.setUpdateBy(username);
    }
}
