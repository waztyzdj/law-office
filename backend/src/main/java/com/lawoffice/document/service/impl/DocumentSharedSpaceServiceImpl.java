package com.lawoffice.document.service.impl;

import static com.lawoffice.document.constant.DocumentCenterConstants.*;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_NO;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.dto.DocumentSharedTargetContext;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileAclMapper;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.document.req.DocumentPageReq;
import com.lawoffice.document.req.DocumentShareTargetReq;
import com.lawoffice.document.service.IDocumentShareTargetService;
import com.lawoffice.document.service.IDocumentSharedSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentSharedSpaceServiceImpl implements IDocumentSharedSpaceService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final SysFileAclMapper fileAclMapper;
    private final IDocumentShareTargetService documentShareTargetService;

    @Override
    public DocumentSharedTargetContext resolveSharedTargetContext(
            DocumentAccessContext context,
            String rawTargetType,
            String rawTargetId,
            boolean required) {
        String targetType = trimToNull(rawTargetType);
        String targetId = trimToNull(rawTargetId);
        if (!StringUtils.hasText(targetType)) {
            if (required) {
                throw new IllegalArgumentException("共享目标类型不能为空");
            }
            return null;
        }
        if (TARGET_TENANT.equals(targetType)) {
            String resolvedTargetId = StringUtils.hasText(targetId) ? targetId : context.tenantId();
            if (!Objects.equals(resolvedTargetId, context.tenantId())) {
                throw new IllegalArgumentException("无权访问该租户共享文件夹");
            }
            DocumentShareTargetReq target = new DocumentShareTargetReq();
            target.setTargetType(TARGET_TENANT);
            target.setTargetId(resolvedTargetId);
            documentShareTargetService.validateShareTarget(target, context.tenantId());
            return new DocumentSharedTargetContext(TARGET_TENANT, resolvedTargetId);
        }
        if (TARGET_DEPART.equals(targetType)) {
            if (!StringUtils.hasText(targetId)) {
                throw new IllegalArgumentException("部门共享目标不能为空");
            }
            if (!context.departIds().contains(targetId)) {
                throw new IllegalArgumentException("无权访问该部门共享文件夹");
            }
            DocumentShareTargetReq target = new DocumentShareTargetReq();
            target.setTargetType(TARGET_DEPART);
            target.setTargetId(targetId);
            documentShareTargetService.validateShareTarget(target, context.tenantId());
            return new DocumentSharedTargetContext(TARGET_DEPART, targetId);
        }
        if (required) {
            throw new IllegalArgumentException("共享目标类型不正确");
        }
        return null;
    }

    @Override
    public boolean isSharedTargetScope(DocumentPageReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && req != null
                && (TARGET_TENANT.equals(trimToNull(req.getShareTargetType()))
                || TARGET_DEPART.equals(trimToNull(req.getShareTargetType())));
    }

    @Override
    public String resolveDocumentStoreType(String scope, DocumentSharedTargetContext sharedTarget) {
        if (SCOPE_SHARED_BY_ME.equals(scope)) {
            return SHARED_BY_ME_STORE_TYPE;
        }
        if (SCOPE_SHARED.equals(scope) && sharedTarget != null) {
            if (TARGET_TENANT.equals(sharedTarget.targetType())) {
                return TENANT_SHARED_STORE_TYPE;
            }
            if (TARGET_DEPART.equals(sharedTarget.targetType())) {
                return DEPART_SHARED_STORE_TYPE;
            }
        }
        return DOCUMENT_STORE_TYPE;
    }

    @Override
    public void validateSharedSpaceParent(SysFiles parent, DocumentSharedTargetContext sharedTarget) {
        if (sharedTarget == null) {
            if (isSharedSpaceDocument(parent)) {
                throw new IllegalArgumentException("目标文件夹不属于当前目录");
            }
            return;
        }
        if (parent == null || !isSharedSpaceMember(parent, sharedTarget)) {
            throw new IllegalArgumentException("目标文件夹不属于当前共享空间");
        }
    }

    @Override
    public void validateSharedSpaceMember(SysFiles file, DocumentSharedTargetContext sharedTarget) {
        if (file == null) {
            return;
        }
        if (sharedTarget == null) {
            if (isSharedSpaceDocument(file)) {
                throw new IllegalArgumentException("只能在当前共享空间内移动文件");
            }
            return;
        }
        if (isSharedSpaceDocument(file) && !isSharedSpaceMember(file, sharedTarget)) {
            throw new IllegalArgumentException("只能在当前共享空间内移动文件");
        }
    }

    @Override
    public void bindDepartSharedRootIfNeeded(SysFiles file, DocumentSharedTargetContext sharedTarget) {
        if (file == null
                || sharedTarget == null
                || !TARGET_DEPART.equals(sharedTarget.targetType())
                || StringUtils.hasText(file.getParentId())) {
            return;
        }
        SysFileRelation existing = fileRelationMapper.selectOne(Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, file.getTenantId())
                .eq(SysFileRelation::getFileId, file.getId())
                .eq(SysFileRelation::getBizType, DEPART_SHARED_RELATION_BIZ_TYPE)
                .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                .eq(SysFileRelation::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (existing != null && Objects.equals(existing.getBizId(), sharedTarget.targetId())) {
            return;
        }
        softDeleteDepartSharedRootRelations(file.getId(), file.getTenantId(), file.getCreateBy());
        SysFileRelation relation = new SysFileRelation();
        relation.setId(newId());
        relation.setTenantId(file.getTenantId());
        relation.setFileId(file.getId());
        relation.setBizType(DEPART_SHARED_RELATION_BIZ_TYPE);
        relation.setBizId(sharedTarget.targetId());
        relation.setRelationType(DEPART_SHARED_RELATION_TYPE);
        relation.setSortOrder(0);
        relation.setCreateBy(file.getCreateBy());
        relation.setCreateTime(LocalDateTime.now());
        relation.setDeleteFlag(0);
        fileRelationMapper.insert(relation);
    }

    @Override
    public void updateSharedSpaceStoreType(
            SysFiles file,
            String parentId,
            DocumentSharedTargetContext sharedTarget,
            String username) {
        String targetStoreType = sharedTarget == null
                ? DOCUMENT_STORE_TYPE
                : resolveDocumentStoreType(SCOPE_SHARED, sharedTarget);
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId, file.getTenantId());
            targetStoreType = StringUtils.hasText(parent.getStoreType()) ? parent.getStoreType() : targetStoreType;
        }
        updateDocumentTreeStoreType(file, targetStoreType, username);
        if (StringUtils.hasText(parentId) || sharedTarget == null || !TARGET_DEPART.equals(sharedTarget.targetType())) {
            softDeleteDepartSharedRootRelations(file.getId(), file.getTenantId(), username);
        }
    }

    @Override
    public List<SysFiles> selectSharedSpaceRootFiles(
            DocumentAccessContext context,
            DocumentSharedTargetContext sharedTarget,
            Boolean folderOnly) {
        if (sharedTarget == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .and(item -> item.isNull(SysFiles::getParentId).or().eq(SysFiles::getParentId, ""));
        if (Boolean.TRUE.equals(folderOnly)) {
            wrapper.eq(SysFiles::getIzFolder, FLAG_YES);
        }
        if (TARGET_TENANT.equals(sharedTarget.targetType())) {
            wrapper.eq(SysFiles::getStoreType, TENANT_SHARED_STORE_TYPE);
            return sysFilesMapper.selectList(wrapper);
        }
        List<String> rootIds = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, DEPART_SHARED_RELATION_BIZ_TYPE)
                        .eq(SysFileRelation::getBizId, sharedTarget.targetId())
                        .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (rootIds.isEmpty()) {
            return Collections.emptyList();
        }
        wrapper.eq(SysFiles::getStoreType, DEPART_SHARED_STORE_TYPE)
                .in(SysFiles::getId, rootIds);
        return sysFilesMapper.selectList(wrapper);
    }

    @Override
    public void assertCanViewSharedSpace(SysFiles file, DocumentAccessContext context) {
        if (!hasSharedSpaceAccess(file, context)) {
            throw new IllegalArgumentException("无权访问该共享空间");
        }
    }

    @Override
    public void assertCanManageDocument(SysFiles file, DocumentAccessContext context) {
        if (file != null && Objects.equals(file.getCreateBy(), context.username())) {
            return;
        }
        if (hasSharedSpaceAccess(file, context)) {
            return;
        }
        throw new IllegalArgumentException("无权管理该文档");
    }

    @Override
    public boolean hasSharedSpaceAccess(SysFiles file, DocumentAccessContext context) {
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
                .in(SysFileRelation::getBizId, context.departIds())
                .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                .eq(SysFileRelation::getDeleteFlag, 0)) > 0;
    }

    @Override
    public void validateSharedTargetParent(SysFiles parent, DocumentSharedTargetContext sharedTarget) {
        if (sharedTarget == null) {
            return;
        }
        if (parent == null
                || (!hasActiveAclForTarget(parent.getId(), parent.getTenantId(), sharedTarget)
                && !hasSharedTargetAncestor(parent.getParentId(), parent.getTenantId(), sharedTarget))) {
            throw new IllegalArgumentException("目标文件夹不属于当前共享节点");
        }
    }

    @Override
    public void validateSharedTargetMember(SysFiles file, DocumentSharedTargetContext sharedTarget) {
        if (sharedTarget == null || file == null || !StringUtils.hasText(file.getParentId())) {
            return;
        }
        if (!hasActiveAclForTarget(file.getId(), file.getTenantId(), sharedTarget)
                && !hasSharedTargetAncestor(file.getParentId(), file.getTenantId(), sharedTarget)) {
            throw new IllegalArgumentException("只能在当前共享节点内移动文件");
        }
    }

    @Override
    public void syncSharedTargetAcl(SysFiles file, DocumentSharedTargetContext sharedTarget) {
        if (file == null || sharedTarget == null) {
            return;
        }
        if (hasActiveAclForTarget(file.getId(), file.getTenantId(), sharedTarget)) {
            return;
        }
        SysFileAcl acl = new SysFileAcl();
        acl.setId(newId());
        acl.setTenantId(file.getTenantId());
        acl.setFileId(file.getId());
        acl.setTargetType(sharedTarget.targetType());
        acl.setTargetId(sharedTarget.targetId());
        acl.setPermission(PERMISSION_DOWNLOAD);
        acl.setCreateBy(file.getCreateBy());
        acl.setCreateTime(LocalDateTime.now());
        acl.setDeleteFlag(0);
        fileAclMapper.insert(acl);

        file.setSharePerms("2");
        file.setEnableDown(FLAG_YES);
        file.setEnableUpdat(FLAG_NO);
        fillUpdate(file, file.getCreateBy());
        sysFilesMapper.updateById(file);
    }

    /**
     * 部门共享空间以根节点关系限定部门范围，子节点通过父链继承所属共享空间。
     */
    private boolean isSharedSpaceMember(SysFiles file, DocumentSharedTargetContext sharedTarget) {
        if (file == null || sharedTarget == null) {
            return false;
        }
        if (TARGET_TENANT.equals(sharedTarget.targetType())) {
            return TENANT_SHARED_STORE_TYPE.equals(file.getStoreType());
        }
        return DEPART_SHARED_STORE_TYPE.equals(file.getStoreType())
                && isInDepartSharedSpace(file, sharedTarget.targetId());
    }

    private boolean isSharedSpaceDocument(SysFiles file) {
        return file != null
                && (TENANT_SHARED_STORE_TYPE.equals(file.getStoreType())
                || DEPART_SHARED_STORE_TYPE.equals(file.getStoreType()));
    }

    private boolean isInDepartSharedSpace(SysFiles file, String departId) {
        if (file == null || !StringUtils.hasText(departId)) {
            return false;
        }
        SysFiles root = resolveRootFile(file);
        return root != null
                && fileRelationMapper.selectCount(Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, file.getTenantId())
                .eq(SysFileRelation::getFileId, root.getId())
                .eq(SysFileRelation::getBizType, DEPART_SHARED_RELATION_BIZ_TYPE)
                .eq(SysFileRelation::getBizId, departId)
                .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                .eq(SysFileRelation::getDeleteFlag, 0)) > 0;
    }

    private SysFiles resolveRootFile(SysFiles file) {
        SysFiles current = file;
        int guard = 0;
        while (current != null && StringUtils.hasText(current.getParentId()) && guard++ < 20) {
            current = getFileIncludingDeleted(current.getParentId(), current.getTenantId());
        }
        return current;
    }

    /**
     * 共享 ACL 可由上级文件夹继承，因此写入共享节点时需要沿父链判断目标是否匹配。
     */
    private boolean hasSharedTargetAncestor(String parentId, String tenantId, DocumentSharedTargetContext sharedTarget) {
        String currentId = parentId;
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            if (hasActiveAclForTarget(currentId, tenantId, sharedTarget)) {
                return true;
            }
            SysFiles parent = sysFilesMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                    .select(SysFiles::getId, SysFiles::getParentId)
                    .eq(SysFiles::getId, currentId)
                    .eq(SysFiles::getTenantId, tenantId)
                    .eq(SysFiles::getDeleteFlag, 0)
                    .last("LIMIT 1"));
            currentId = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    private boolean hasActiveAclForTarget(String fileId, String tenantId, DocumentSharedTargetContext sharedTarget) {
        if (!StringUtils.hasText(fileId) || sharedTarget == null) {
            return false;
        }
        return fileAclMapper.selectCount(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, tenantId)
                .eq(SysFileAcl::getFileId, fileId)
                .eq(SysFileAcl::getTargetType, sharedTarget.targetType())
                .eq(SysFileAcl::getTargetId, sharedTarget.targetId())
                .eq(SysFileAcl::getDeleteFlag, 0)) > 0;
    }

    /**
     * 文件移出部门共享根节点时软删根关系，保留审计链路并避免重复根绑定。
     */
    private void softDeleteDepartSharedRootRelations(String fileId, String tenantId, String username) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        fileRelationMapper.update(null, Wrappers.lambdaUpdate(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, tenantId)
                .eq(SysFileRelation::getFileId, fileId)
                .eq(SysFileRelation::getBizType, DEPART_SHARED_RELATION_BIZ_TYPE)
                .eq(SysFileRelation::getRelationType, DEPART_SHARED_RELATION_TYPE)
                .eq(SysFileRelation::getDeleteFlag, 0)
                .set(SysFileRelation::getDeleteFlag, 1)
                .set(SysFileRelation::getDeleteTime, now)
                .set(SysFileRelation::getDeleteBy, username));
    }

    /**
     * 文件夹在个人空间和共享空间之间移动时，子树必须同步 storeType 以保持列表范围查询一致。
     */
    private void updateDocumentTreeStoreType(SysFiles file, String storeType, String username) {
        if (file == null || Objects.equals(file.getStoreType(), storeType)) {
            return;
        }
        file.setStoreType(storeType);
        fillUpdate(file, username);
        sysFilesMapper.update(null, Wrappers.lambdaUpdate(SysFiles.class)
                .eq(SysFiles::getId, file.getId())
                .eq(SysFiles::getTenantId, file.getTenantId())
                .set(SysFiles::getStoreType, storeType)
                .set(SysFiles::getUpdateBy, file.getUpdateBy())
                .set(SysFiles::getUpdateTime, file.getUpdateTime()));
        for (SysFiles child : selectActiveChildren(file.getTenantId(), file.getId())) {
            updateDocumentTreeStoreType(child, storeType, username);
        }
    }

    private List<SysFiles> selectActiveChildren(String tenantId, String parentId) {
        return sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getParentId, parentId)
                .eq(SysFiles::getDeleteFlag, 0));
    }

    private SysFiles getActiveFile(String fileId, String tenantId) {
        SysFiles file = sysFilesMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getId, fileId)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或已删除");
        }
        return file;
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

    private void fillUpdate(SysFiles file, String username) {
        file.setUpdateBy(username);
        file.setUpdateTime(LocalDateTime.now());
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
