package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.*;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_NO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.dto.DocumentRequestCache;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileAclMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.service.IDocumentAclPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentAclPermissionServiceImpl implements IDocumentAclPermissionService {

    private final SysFileAclMapper fileAclMapper;
    private final SysFilesMapper sysFilesMapper;

    @Override
    public int permissionRank(String permission) {
        if (PERMISSION_MANAGE.equals(permission)) {
            return 4;
        }
        if (PERMISSION_UPDATE.equals(permission)) {
            return 3;
        }
        if (PERMISSION_DOWNLOAD.equals(permission)) {
            return 2;
        }
        if (PERMISSION_READ.equals(permission)) {
            return 1;
        }
        return 0;
    }

    @Override
    public boolean hasActiveAcl(String fileId, String tenantId) {
        return fileAclMapper.selectCount(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, tenantId)
                .eq(SysFileAcl::getFileId, fileId)
                .eq(SysFileAcl::getDeleteFlag, 0)) > 0;
    }

    @Override
    public boolean hasActiveAcl(String fileId, DocumentAccessContext context) {
        if (!StringUtils.hasText(fileId)) {
            return false;
        }
        return context.cache().getActiveAclFlags().computeIfAbsent(fileId, id -> hasActiveAcl(id, context.tenantId()));
    }

    @Override
    public Set<String> findActiveAclFileIds(Collection<String> fileIds, String tenantId) {
        if (fileIds.isEmpty()) {
            return Collections.emptySet();
        }
        return fileAclMapper.selectList(Wrappers.lambdaQuery(SysFileAcl.class)
                        .select(SysFileAcl::getFileId)
                        .eq(SysFileAcl::getTenantId, tenantId)
                        .eq(SysFileAcl::getDeleteFlag, 0)
                        .in(SysFileAcl::getFileId, fileIds))
                .stream()
                .map(SysFileAcl::getFileId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    @Override
    public List<SysFileAcl> listActiveDirectAcls(String fileId, String tenantId) {
        if (!StringUtils.hasText(fileId)) {
            return Collections.emptyList();
        }
        return fileAclMapper.selectList(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, tenantId)
                .eq(SysFileAcl::getFileId, fileId)
                .eq(SysFileAcl::getDeleteFlag, 0)
                .and(item -> item.isNull(SysFileAcl::getExpireTime).or().ge(SysFileAcl::getExpireTime, LocalDateTime.now()))
                .orderByAsc(SysFileAcl::getTargetType, SysFileAcl::getCreateTime));
    }

    @Override
    public List<SysFileAcl> selectActiveAclsForContext(String fileId, DocumentAccessContext context) {
        DocumentRequestCache cache = context.cache();
        if (!StringUtils.hasText(fileId)) {
            return cache.getActiveAclsByFileId().computeIfAbsent(
                    ALL_ACLS_CACHE_KEY,
                    key -> queryActiveAclsForContext(null, context));
        }
        List<SysFileAcl> allAcls = cache.getActiveAclsByFileId().get(ALL_ACLS_CACHE_KEY);
        if (allAcls != null) {
            return allAcls.stream()
                    .filter(acl -> Objects.equals(acl.getFileId(), fileId))
                    .toList();
        }
        return cache.getActiveAclsByFileId().computeIfAbsent(fileId, key -> queryActiveAclsForContext(key, context));
    }

    @Override
    public int resolvePermissionRank(SysFiles file, DocumentAccessContext context) {
        int directRank = maxAclRank(file.getId(), context);
        if (directRank > 0) {
            return directRank;
        }
        String parentId = file.getParentId();
        int guard = 0;
        while (StringUtils.hasText(parentId) && guard++ < 20) {
            SysFiles parent = getFileIncludingDeleted(parentId, file.getTenantId());
            if (parent == null || Objects.equals(parent.getDeleteFlag(), 1)) {
                return 0;
            }
            int parentRank = maxAclRank(parent.getId(), context);
            if (parentRank > 0) {
                return parentRank;
            }
            parentId = parent.getParentId();
        }
        return 0;
    }

    @Override
    public int resolveSharedDocumentPermissionRank(SysFiles file, DocumentAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return permissionRank(PERMISSION_MANAGE);
        }
        int rank = resolvePermissionRank(file, context);
        if (rank < permissionRank(PERMISSION_READ)) {
            throw new IllegalArgumentException("无权访问该文档");
        }
        return rank;
    }

    @Override
    public boolean resolveUpdatePermission(SysFiles file, DocumentAccessContext context) {
        int directRank = maxAclRank(file.getId(), context);
        if (directRank > 0) {
            return directRank >= permissionRank(PERMISSION_UPDATE)
                    && !FLAG_NO.equals(file.getEnableUpdat());
        }
        String parentId = file.getParentId();
        int guard = 0;
        while (StringUtils.hasText(parentId) && guard++ < 20) {
            SysFiles parent = getFileIncludingDeleted(parentId, file.getTenantId());
            if (parent == null || Objects.equals(parent.getDeleteFlag(), 1)) {
                return false;
            }
            int parentRank = maxAclRank(parent.getId(), context);
            if (parentRank > 0) {
                return parentRank >= permissionRank(PERMISSION_UPDATE)
                        && !FLAG_NO.equals(parent.getEnableUpdat());
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    private List<SysFileAcl> queryActiveAclsForContext(String fileId, DocumentAccessContext context) {
        LambdaQueryWrapper<SysFileAcl> wrapper = Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, context.tenantId())
                .eq(SysFileAcl::getDeleteFlag, 0)
                .and(item -> item.isNull(SysFileAcl::getExpireTime).or().ge(SysFileAcl::getExpireTime, LocalDateTime.now()))
                .and(item -> {
                    item.eq(SysFileAcl::getTargetType, TARGET_TENANT)
                            .eq(SysFileAcl::getTargetId, context.tenantId())
                            .or(or -> or.eq(SysFileAcl::getTargetType, TARGET_USER)
                                    .eq(SysFileAcl::getTargetId, context.userId()));
                    if (!context.departIds().isEmpty()) {
                        item.or(or -> or.eq(SysFileAcl::getTargetType, TARGET_DEPART)
                                .in(SysFileAcl::getTargetId, context.departIds()));
                    }
                    if (!context.roleIds().isEmpty()) {
                        item.or(or -> or.eq(SysFileAcl::getTargetType, TARGET_ROLE)
                                .in(SysFileAcl::getTargetId, context.roleIds()));
                    }
                });
        if (StringUtils.hasText(fileId)) {
            wrapper.eq(SysFileAcl::getFileId, fileId);
        }
        return fileAclMapper.selectList(wrapper);
    }

    private int maxAclRank(String fileId, DocumentAccessContext context) {
        return selectActiveAclsForContext(fileId, context).stream()
                .map(SysFileAcl::getPermission)
                .mapToInt(this::permissionRank)
                .max()
                .orElse(0);
    }

    private SysFiles getFileIncludingDeleted(String fileId, String tenantId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        return sysFilesMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getId, fileId)
                .eq(SysFiles::getTenantId, tenantId)
                .last("LIMIT 1"));
    }
}
