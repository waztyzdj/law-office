package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.*;
import static com.lawoffice.system.constant.SysFileConstants.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.dto.DocumentCopyTarget;
import com.lawoffice.system.dto.DocumentSharedTargetContext;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.req.DocumentCopyReq;
import com.lawoffice.system.service.IDocumentCopyMoveService;
import com.lawoffice.system.service.IDocumentFileAccessService;
import com.lawoffice.system.service.IDocumentScopeService;
import com.lawoffice.system.service.IDocumentSharedSpaceService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.util.MinioUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentCopyMoveServiceImpl implements IDocumentCopyMoveService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final IDocumentScopeService documentScopeService;
    private final IDocumentFileAccessService documentFileAccessService;
    private final IDocumentSharedSpaceService documentSharedSpaceService;
    private final MinioUtils minioUtils;

    @Override
    public void validateNotMoveToDescendant(String sourceId, String targetParentId, String tenantId) {
        String currentId = targetParentId;
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            if (Objects.equals(sourceId, currentId)) {
                throw new IllegalArgumentException("不能移动到自身子级下");
            }
            SysFiles current = getActiveFile(currentId, tenantId);
            currentId = current.getParentId();
        }
    }

    @Override
    public DocumentCopyTarget resolveCopyTarget(DocumentAccessContext context, DocumentCopyReq req) {
        String scope = documentScopeService.normalizeScope(req.getScope());
        if (SCOPE_TRASH.equals(scope) || SCOPE_BUSINESS.equals(scope)
                || documentScopeService.isSharedInboxRequest(req, scope)) {
            throw new IllegalArgumentException("当前目录不支持复制粘贴");
        }
        String parentId = trimToNull(req.getParentId());
        DocumentSharedTargetContext sharedTarget = documentSharedSpaceService.resolveSharedTargetContext(
                context,
                req.getShareTargetType(),
                req.getShareTargetId(),
                false);
        String storeType = SCOPE_SHARED_BY_ME.equals(scope) ? SHARED_BY_ME_STORE_TYPE : DOCUMENT_STORE_TYPE;
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId, context.tenantId());
            documentSharedSpaceService.assertCanManageDocument(parent, context);
            documentSharedSpaceService.validateSharedSpaceParent(parent, sharedTarget);
            if (!FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("目标必须是文件夹");
            }
            storeType = StringUtils.hasText(parent.getStoreType()) ? parent.getStoreType() : storeType;
        }
        if (!StringUtils.hasText(parentId) && sharedTarget != null) {
            storeType = documentSharedSpaceService.resolveDocumentStoreType(scope, sharedTarget);
        }
        return new DocumentCopyTarget(parentId, storeType, sharedTarget);
    }

    @Override
    public SysFiles copyDocumentTree(
            DocumentAccessContext context,
            SysFiles source,
            String targetParentId,
            String targetStoreType) {
        assertCanCopyDocument(source, context);
        SysFiles copied = new SysFiles();
        copied.setId(newId());
        copied.setTenantId(context.tenantId());
        copied.setFileName(source.getFileName());
        copied.setFileType(source.getFileType());
        copied.setStoreType(targetStoreType);
        copied.setParentId(targetParentId);
        copied.setFileSize(source.getFileSize());
        copied.setIzFolder(source.getIzFolder());
        copied.setIzRootFolder(StringUtils.hasText(targetParentId) ? FLAG_NO : FLAG_YES);
        copied.setIzStar(FLAG_NO);
        copied.setDownCount(0);
        copied.setReadCount(0);
        copied.setSharePerms("1");
        copied.setEnableDown(FLAG_YES);
        copied.setEnableUpdat(FLAG_NO);
        copied.setCreateBy(context.username());
        copied.setCreateTime(LocalDateTime.now());
        copied.setUpdateBy(context.username());
        copied.setUpdateTime(LocalDateTime.now());
        copied.setDeleteFlag(0);
        if (!FLAG_YES.equals(source.getIzFolder())) {
            copied.setUrl(copyObjectName(source));
        }
        sysFilesMapper.insert(copied);
        if (FLAG_YES.equals(source.getIzFolder())) {
            for (SysFiles child : selectActiveChildren(context.tenantId(), source.getId())) {
                copyDocumentTree(context, child, copied.getId(), targetStoreType);
            }
        }
        return copied;
    }

    @Override
    public void moveSharedInboxPlacement(DocumentAccessContext context, SysFiles file, String parentId) {
        documentFileAccessService.assertCanViewDocument(file, context);
        if (Objects.equals(file.getId(), parentId)) {
            throw new IllegalArgumentException("不能移动到自身下");
        }
        if (!StringUtils.hasText(parentId)) {
            clearPersonalSharedPlacement(context, file.getId());
            return;
        }
        SysFiles parent = getActiveFile(parentId, context.tenantId());
        if (!isPersonalSharedFolder(parent, context.username())) {
            throw new IllegalArgumentException("共享给我的文件只能归类到个人整理文件夹");
        }
        upsertPersonalSharedPlacement(context, file.getId(), parentId);
    }

    @Override
    public Set<String> findPersonalSharedPlacedFileIds(DocumentAccessContext context) {
        return fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, personalSharedRelationBizType(context))
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> findPersonalBusinessPlacedFileIds(DocumentAccessContext context) {
        return fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, personalBusinessRelationBizType(context))
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isPersonalSharedFolder(SysFiles file, String username) {
        return file != null
                && Objects.equals(file.getCreateBy(), username)
                && FLAG_YES.equals(file.getIzFolder())
                && SHARED_VIEW_STORE_TYPE.equals(file.getStoreType());
    }

    @Override
    public boolean isBusinessFolder(SysFiles file, String username) {
        return file != null
                && Objects.equals(file.getCreateBy(), username)
                && FLAG_YES.equals(file.getIzFolder())
                && BUSINESS_VIEW_STORE_TYPE.equals(file.getStoreType());
    }

    @Override
    public String personalSharedRelationBizType(DocumentAccessContext context) {
        return PERSONAL_SHARED_RELATION_PREFIX + context.userId();
    }

    @Override
    public String personalBusinessRelationBizType(DocumentAccessContext context) {
        return PERSONAL_BUSINESS_RELATION_PREFIX + context.userId();
    }

    /**
     * 共享给我的归类是当前用户视图，软删关系即可恢复原列表展示，不修改原文件 parent_id。
     */
    private void clearPersonalSharedPlacement(DocumentAccessContext context, String fileId) {
        List<SysFileRelation> relations = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                .eq(SysFileRelation::getTenantId, context.tenantId())
                .eq(SysFileRelation::getBizType, personalSharedRelationBizType(context))
                .eq(SysFileRelation::getFileId, fileId)
                .eq(SysFileRelation::getDeleteFlag, 0));
        for (SysFileRelation relation : relations) {
            EntityFillUtils.fillDeleteFields(relation, context.username());
            fileRelationMapper.updateById(relation);
        }
    }

    /**
     * 每个用户对同一个共享文件只保留一个个人归类位置，避免左侧目录和右侧列表重复展示。
     */
    private void upsertPersonalSharedPlacement(DocumentAccessContext context, String fileId, String parentId) {
        clearPersonalSharedPlacement(context, fileId);
        SysFileRelation relation = new SysFileRelation();
        relation.setId(newId());
        relation.setTenantId(context.tenantId());
        relation.setFileId(fileId);
        relation.setBizType(personalSharedRelationBizType(context));
        relation.setBizId(parentId);
        relation.setRelationType(PERSONAL_SHARED_RELATION_TYPE);
        relation.setSortOrder(0);
        relation.setCreateBy(context.username());
        relation.setCreateTime(LocalDateTime.now());
        relation.setDeleteFlag(0);
        fileRelationMapper.insert(relation);
    }

    /**
     * 文件复制要求下载权限，目录复制要求浏览权限；目录下具体文件会在递归复制时逐个校验。
     */
    private void assertCanCopyDocument(SysFiles file, DocumentAccessContext context) {
        if (FLAG_YES.equals(file.getIzFolder())) {
            documentFileAccessService.assertCanViewDocument(file, context);
            return;
        }
        if (!documentFileAccessService.canDownload(file, context)) {
            throw new IllegalArgumentException("无权复制该文档");
        }
    }

    private String copyObjectName(SysFiles source) {
        if (!StringUtils.hasText(source.getUrl())) {
            throw new IllegalArgumentException("源文件内容不存在，无法复制");
        }
        try {
            return minioUtils.copyFileAndReturnObjectName(source.getUrl(), source.getFileName());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("文件复制失败，请检查对象存储配置");
        }
    }

    private List<SysFiles> selectActiveChildren(String tenantId, String parentId) {
        if (!StringUtils.hasText(parentId)) {
            return Collections.emptyList();
        }
        return sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getParentId, parentId)
                .eq(SysFiles::getDeleteFlag, 0)
                .orderByDesc(SysFiles::getIzFolder)
                .orderByAsc(SysFiles::getCreateTime));
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
