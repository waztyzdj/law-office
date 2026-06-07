package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.*;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IDocumentAclPermissionService;
import com.lawoffice.system.service.IDocumentShareSourceService;
import com.lawoffice.system.service.IDocumentShareTargetService;
import com.lawoffice.system.vo.DocumentShareSourceVO;
import com.lawoffice.system.vo.DocumentShareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentShareSourceServiceImpl implements IDocumentShareSourceService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final UserMapper userMapper;
    private final IDocumentAclPermissionService documentAclPermissionService;
    private final IDocumentShareTargetService documentShareTargetService;

    @Override
    public DocumentShareVO buildDocumentShareVO(SysFileAcl acl) {
        DocumentShareVO vo = new DocumentShareVO();
        vo.setId(acl.getId());
        vo.setFileId(acl.getFileId());
        vo.setTargetType(acl.getTargetType());
        vo.setTargetId(acl.getTargetId());
        vo.setTargetName(documentShareTargetService.resolveTargetName(acl.getTargetType(), acl.getTargetId()));
        vo.setPermission(acl.getPermission());
        vo.setExpireTime(acl.getExpireTime());
        vo.setCreateBy(acl.getCreateBy());
        vo.setCreateTime(acl.getCreateTime());
        vo.setUpdateBy(acl.getUpdateBy());
        vo.setUpdateTime(acl.getUpdateTime());
        return vo;
    }

    @Override
    public boolean canSeeDirectShares(SysFiles file, DocumentAccessContext context) {
        return file != null
                && (Objects.equals(file.getCreateBy(), context.username())
                || hasSharedSpaceAccess(file, context));
    }

    @Override
    public List<DocumentShareVO> listActiveDirectShareVOs(String fileId, String tenantId) {
        return documentAclPermissionService.listActiveDirectAcls(fileId, tenantId).stream()
                .map(this::buildDocumentShareVO)
                .toList();
    }

    @Override
    public DocumentShareSourceVO resolveAccessShareSource(SysFiles file, DocumentAccessContext context) {
        if (file == null || Objects.equals(file.getCreateBy(), context.username())
                || Objects.equals(file.getDeleteFlag(), 1)) {
            return null;
        }
        DocumentShareSourceVO sharedSpaceSource = resolveSharedSpaceSource(file, context);
        if (sharedSpaceSource != null) {
            return sharedSpaceSource;
        }
        return resolveShareSource(file, context, true, true);
    }

    @Override
    public DocumentShareSourceVO resolveInheritedShareSource(SysFiles file, DocumentAccessContext context) {
        if (file == null || !StringUtils.hasText(file.getParentId()) || Objects.equals(file.getDeleteFlag(), 1)) {
            return null;
        }
        return resolveShareSource(file, context, !canSeeDirectShares(file, context), false);
    }

    @Override
    public DocumentShareSourceVO resolveFavoriteSource(SysFiles file, DocumentAccessContext context) {
        if (file == null || Objects.equals(file.getDeleteFlag(), 1)
                || !Objects.equals(file.getCreateBy(), context.username())) {
            return null;
        }
        String currentId = file.getId();
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            SysFiles sourceFile = getFileIncludingDeleted(currentId, file.getTenantId());
            if (sourceFile == null || Objects.equals(sourceFile.getDeleteFlag(), 1)) {
                return null;
            }
            if (FLAG_YES.equals(sourceFile.getIzStar())) {
                return buildFavoriteSourceVO(file, sourceFile);
            }
            currentId = sourceFile.getParentId();
        }
        return null;
    }

    private DocumentShareSourceVO resolveShareSource(
            SysFiles file,
            DocumentAccessContext context,
            boolean onlyCurrentUserTargets,
            boolean includeSelf) {
        String currentId = includeSelf ? file.getId() : file.getParentId();
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            SysFiles sourceFile = getFileIncludingDeleted(currentId, file.getTenantId());
            if (sourceFile == null || Objects.equals(sourceFile.getDeleteFlag(), 1)) {
                return null;
            }
            List<SysFileAcl> acls = onlyCurrentUserTargets
                    ? documentAclPermissionService.selectActiveAclsForContext(sourceFile.getId(), context)
                    : documentAclPermissionService.listActiveDirectAcls(sourceFile.getId(), sourceFile.getTenantId());
            if (!acls.isEmpty()) {
                SysFileAcl acl = acls.stream()
                        .max((left, right) -> Integer.compare(
                                documentAclPermissionService.permissionRank(left.getPermission()),
                                documentAclPermissionService.permissionRank(right.getPermission())))
                        .orElse(acls.get(0));
                return buildShareSourceVO(file, sourceFile, acl, acls);
            }
            currentId = sourceFile.getParentId();
        }
        return null;
    }

    private DocumentShareSourceVO buildShareSourceVO(
            SysFiles file,
            SysFiles sourceFile,
            SysFileAcl acl,
            List<SysFileAcl> sourceAcls) {
        DocumentShareSourceVO source = new DocumentShareSourceVO();
        boolean inherited = !Objects.equals(file.getId(), sourceFile.getId());
        source.setSourceType(inherited ? "inherited" : "direct");
        source.setFileId(sourceFile.getId());
        source.setFileName(sourceFile.getFileName());
        source.setSharedBy(resolveUsernameDisplayName(acl.getCreateBy()));
        source.setTargetType(acl.getTargetType());
        source.setTargetId(acl.getTargetId());
        source.setTargetName(documentShareTargetService.resolveTargetName(acl.getTargetType(), acl.getTargetId()));
        source.setTargetSummary(resolveShareTargetSummary(sourceAcls));
        source.setPermission(acl.getPermission());
        source.setExpireTime(acl.getExpireTime());
        source.setCreateTime(acl.getCreateTime());
        if (inherited) {
            source.setInheritedFromFileId(sourceFile.getId());
            source.setInheritedFromFileName(sourceFile.getFileName());
        }
        return source;
    }

    private DocumentShareSourceVO buildFavoriteSourceVO(SysFiles file, SysFiles sourceFile) {
        DocumentShareSourceVO source = new DocumentShareSourceVO();
        boolean inherited = !Objects.equals(file.getId(), sourceFile.getId());
        source.setSourceType(inherited ? "inherited" : "direct");
        source.setFileId(sourceFile.getId());
        source.setFileName(sourceFile.getFileName());
        if (inherited) {
            source.setInheritedFromFileId(sourceFile.getId());
            source.setInheritedFromFileName(sourceFile.getFileName());
        }
        return source;
    }

    private DocumentShareSourceVO resolveSharedSpaceSource(SysFiles file, DocumentAccessContext context) {
        if (TENANT_SHARED_STORE_TYPE.equals(file.getStoreType())) {
            SysFiles root = resolveRootFile(file);
            DocumentShareSourceVO source = buildSharedSpaceSource(file, root, TARGET_TENANT, context.tenantId());
            source.setTargetName(documentShareTargetService.resolveTargetName(TARGET_TENANT, context.tenantId()));
            return source;
        }
        if (!DEPART_SHARED_STORE_TYPE.equals(file.getStoreType())) {
            return null;
        }
        SysFiles root = resolveRootFile(file);
        String departId = resolveDepartSharedRootTarget(root, context);
        if (!StringUtils.hasText(departId)) {
            return null;
        }
        return buildSharedSpaceSource(file, root, TARGET_DEPART, departId);
    }

    private DocumentShareSourceVO buildSharedSpaceSource(
            SysFiles file,
            SysFiles root,
            String targetType,
            String targetId) {
        DocumentShareSourceVO source = new DocumentShareSourceVO();
        SysFiles sourceFile = root == null ? file : root;
        boolean inherited = !Objects.equals(file.getId(), sourceFile.getId());
        source.setSourceType("space");
        source.setFileId(sourceFile.getId());
        source.setFileName(sourceFile.getFileName());
        source.setSharedBy(resolveUsernameDisplayName(sourceFile.getCreateBy()));
        source.setTargetType(targetType);
        source.setTargetId(targetId);
        source.setTargetName(documentShareTargetService.resolveTargetName(targetType, targetId));
        if (inherited) {
            source.setInheritedFromFileId(sourceFile.getId());
            source.setInheritedFromFileName(sourceFile.getFileName());
        }
        return source;
    }

    private String resolveShareTargetSummary(List<SysFileAcl> acls) {
        if (acls == null || acls.isEmpty()) {
            return null;
        }
        return acls.stream()
                .map(this::formatShareTarget)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String formatShareTarget(SysFileAcl acl) {
        String targetName = documentShareTargetService.resolveTargetName(acl.getTargetType(), acl.getTargetId());
        String targetTypeText = documentShareTargetService.resolveTargetTypeText(acl.getTargetType());
        if (StringUtils.hasText(targetTypeText) && StringUtils.hasText(targetName)) {
            return targetTypeText + " " + targetName;
        }
        return StringUtils.hasText(targetName) ? targetName : targetTypeText;
    }

    private boolean hasSharedSpaceAccess(SysFiles file, DocumentAccessContext context) {
        if (file == null || !Objects.equals(file.getTenantId(), context.tenantId())) {
            return false;
        }
        if (TENANT_SHARED_STORE_TYPE.equals(file.getStoreType())) {
            return true;
        }
        if (!DEPART_SHARED_STORE_TYPE.equals(file.getStoreType())) {
            return false;
        }
        SysFiles root = resolveRootFile(file);
        if (root == null || context.departIds().isEmpty()) {
            return false;
        }
        return fileRelationMapper.selectCount(Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, context.tenantId())
                .eq(SysFileRelation::getFileId, root.getId())
                .eq(SysFileRelation::getBizType, DEPART_SHARED_RELATION_BIZ_TYPE)
                .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                .in(SysFileRelation::getBizId, context.departIds())
                .eq(SysFileRelation::getDeleteFlag, 0)) > 0;
    }

    private SysFiles resolveRootFile(SysFiles file) {
        SysFiles current = file;
        int guard = 0;
        while (current != null && StringUtils.hasText(current.getParentId()) && guard++ < 20) {
            SysFiles parent = getFileIncludingDeleted(current.getParentId(), current.getTenantId());
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return current;
    }

    private String resolveDepartSharedRootTarget(SysFiles root, DocumentAccessContext context) {
        if (root == null) {
            return null;
        }
        List<SysFileRelation> relations = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, context.tenantId())
                .eq(SysFileRelation::getFileId, root.getId())
                .eq(SysFileRelation::getBizType, DEPART_SHARED_RELATION_BIZ_TYPE)
                .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                .eq(SysFileRelation::getDeleteFlag, 0));
        return relations.stream()
                .map(SysFileRelation::getBizId)
                .filter(context.departIds()::contains)
                .findFirst()
                .orElseGet(() -> relations.stream()
                        .map(SysFileRelation::getBizId)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null));
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

    private String resolveUsernameDisplayName(String username) {
        if (!StringUtils.hasText(username)) {
            return username;
        }
        return resolveUsernameDisplayNames(List.of(username)).getOrDefault(username, username);
    }

    private Map<String, String> resolveUsernameDisplayNames(Collection<String> usernames) {
        List<String> normalizedUsernames = usernames.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedUsernames.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectList(Wrappers.lambdaQuery(User.class)
                        .select(User::getUsername, User::getRealname)
                        .in(User::getUsername, normalizedUsernames))
                .stream()
                .filter(user -> StringUtils.hasText(user.getUsername()))
                .collect(Collectors.toMap(
                        User::getUsername,
                        user -> StringUtils.hasText(user.getRealname()) ? user.getRealname() : user.getUsername(),
                        (left, right) -> left));
    }
}
