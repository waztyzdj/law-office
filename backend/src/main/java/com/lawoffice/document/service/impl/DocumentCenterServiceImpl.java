package com.lawoffice.document.service.impl;

import static com.lawoffice.document.constant.DocumentCenterConstants.*;
import static com.lawoffice.system.constant.SysFileConstants.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.dto.DocumentBusinessGroupNode;
import com.lawoffice.document.dto.DocumentBusinessRecordNode;
import com.lawoffice.document.dto.DocumentCopyTarget;
import com.lawoffice.document.dto.DocumentSharedTargetContext;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFileVersion;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.SysFileAclMapper;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFileVersionMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.document.req.DocumentBatchDeleteReq;
import com.lawoffice.document.req.DocumentBatchMoveReq;
import com.lawoffice.document.req.DocumentCopyReq;
import com.lawoffice.document.req.DocumentFolderReq;
import com.lawoffice.document.req.DocumentMoveReq;
import com.lawoffice.document.req.DocumentPageReq;
import com.lawoffice.document.req.DocumentRenameReq;
import com.lawoffice.document.req.DocumentShareReq;
import com.lawoffice.document.req.DocumentTreeBatchReq;
import com.lawoffice.document.req.DocumentTreePrefetchReq;
import com.lawoffice.document.req.DocumentUploadReq;
import com.lawoffice.document.service.IDocumentAccessContextService;
import com.lawoffice.document.service.IDocumentAclPermissionService;
import com.lawoffice.document.service.IDocumentBusinessAccessService;
import com.lawoffice.document.service.IDocumentCenterService;
import com.lawoffice.document.service.IDocumentContentAccessService;
import com.lawoffice.document.service.IDocumentCopyMoveService;
import com.lawoffice.document.service.IDocumentFileAccessService;
import com.lawoffice.document.service.IDocumentFileViewService;
import com.lawoffice.document.service.IDocumentShareManagementService;
import com.lawoffice.document.service.IDocumentShareSourceService;
import com.lawoffice.document.service.IDocumentScopeService;
import com.lawoffice.document.service.IDocumentSharedSpaceService;
import com.lawoffice.document.service.IDocumentStatisticsService;
import com.lawoffice.document.service.IDocumentTreeLifecycleService;
import com.lawoffice.document.service.IDocumentVirtualNodeService;
import com.lawoffice.system.service.ISysFileMetadataService;
import com.lawoffice.document.vo.DocumentFileVO;
import com.lawoffice.document.vo.DocumentShareVO;
import com.lawoffice.document.vo.DocumentStatusVO;
import com.lawoffice.system.vo.SysFilesVO;
import com.lawoffice.util.MinioUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentCenterServiceImpl extends BaseServiceImpl<SysFilesMapper, SysFiles, SysFilesVO> implements IDocumentCenterService {

    private final SysFileRelationMapper fileRelationMapper;
    private final SysFileAclMapper fileAclMapper;
    private final UserMapper userMapper;
    private final SysFileVersionMapper sysFileVersionMapper;
    private final IDocumentAccessContextService documentAccessContextService;
    private final IDocumentAclPermissionService documentAclPermissionService;
    private final IDocumentBusinessAccessService documentBusinessAccessService;
    private final IDocumentContentAccessService documentContentAccessService;
    private final IDocumentCopyMoveService documentCopyMoveService;
    private final IDocumentFileAccessService documentFileAccessService;
    private final IDocumentFileViewService documentFileViewService;
    private final IDocumentShareManagementService documentShareManagementService;
    private final IDocumentShareSourceService documentShareSourceService;
    private final IDocumentScopeService documentScopeService;
    private final IDocumentSharedSpaceService documentSharedSpaceService;
    private final IDocumentStatisticsService documentStatisticsService;
    private final IDocumentTreeLifecycleService documentTreeLifecycleService;
    private final IDocumentVirtualNodeService documentVirtualNodeService;
    private final ISysFileMetadataService fileMetadataService;
    private final MinioUtils minioUtils;

    @Override
    @Transactional(readOnly = true)
    public PageVO<DocumentFileVO> pageDocuments(String username, DocumentPageReq req) {
        DocumentPageReq pageReq = req == null ? new DocumentPageReq() : req;
        String tenantId = requireTenantId();
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, tenantId);
        return pageDocuments(context, pageReq);
    }

    /**
     * 文档中心列表入口按 scope 分流到个人、共享、业务文档和回收站视图。
     * 不同视图的数据权限和目录语义不同，必须在进入普通文件查询前先完成分流。
     */
    private PageVO<DocumentFileVO> pageDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        String scope = documentScopeService.normalizeScope(pageReq.getScope());
        if (documentScopeService.isSharedInboxRequest(pageReq, scope)) {
            return pageSharedInboxDocuments(context, pageReq);
        }

        if (SCOPE_BUSINESS.equals(scope)) {
            return pageBusinessDocuments(context, pageReq);
        }

        if (SCOPE_TRASH.equals(scope)) {
            return pageTrashDocuments(context, pageReq);
        }

        if (SCOPE_STARRED.equals(scope)) {
            return pageStarredDocuments(context, pageReq);
        }

        if (documentSharedSpaceService.isSharedTargetScope(pageReq, scope)) {
            return pageSharedTargetDocuments(context, pageReq);
        }

        if (SCOPE_SHARED_BY_ME.equals(scope) && !StringUtils.hasText(pageReq.getParentId())) {
            return pageSharedByMeDocuments(context, pageReq);
        }

        if (SCOPE_SHARED.equals(scope) && StringUtils.hasText(pageReq.getParentId())) {
            SysFiles parent = getActiveFile(pageReq.getParentId());
            int inheritedRank = documentAclPermissionService.resolveSharedDocumentPermissionRank(parent, context);
            return pageSharedFolderChildren(context, pageReq, parent, inheritedRank);
        }

        if (SCOPE_SHARED_BY_ME.equals(scope) && StringUtils.hasText(pageReq.getParentId())) {
            SysFiles parent = getActiveFile(pageReq.getParentId());
            assertSharedByMeFolderBrowsable(parent, context);
            return pageDocumentChildrenByParent(context, pageReq, parent.getId());
        }

        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId());
        applyDocumentScope(wrapper, context, pageReq, scope);
        applyDocumentFilters(wrapper, pageReq);
        wrapper.orderByDesc(SysFiles::getIzFolder)
                .orderByDesc(SysFiles::getUpdateTime)
                .orderByDesc(SysFiles::getCreateTime);

        Page<SysFiles> page = new Page<>(pageReq.getPageNum(), pageReq.getPageSize());
        Page<SysFiles> result = baseMapper.selectPage(page, wrapper);
        List<DocumentFileVO> records = documentFileViewService.buildDocumentVOList(result.getRecords(), context);
        documentFileViewService.fillFolderChildFlags(records, context);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public Map<String, List<DocumentFileVO>> batchLoadDocumentFolderTree(String username, DocumentTreeBatchReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return Collections.emptyMap();
        }
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        Map<String, List<DocumentFileVO>> result = new LinkedHashMap<>();
        req.getItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getKey()))
                .limit(TREE_PREFETCH_PARENT_LIMIT)
                .forEach(item -> {
                    String key = item.getKey().trim();
                    if (result.containsKey(key)) {
                        return;
                    }
                    result.put(key, listDocumentFolderChildrenForTree(
                            context,
                            item.getScope(),
                            item.getShareTargetType(),
                            item.getShareTargetId(),
                            item.getParentId()));
                });
        return result;
    }

    @Override
    public Map<String, List<DocumentFileVO>> prefetchDocumentFolderTree(String username, DocumentTreePrefetchReq req) {
        if (req == null || req.getParentIds() == null || req.getParentIds().isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> parentIds = req.getParentIds().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(TREE_PREFETCH_PARENT_LIMIT)
                .toList();
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        Map<String, List<DocumentFileVO>> result = new LinkedHashMap<>();
        for (String parentId : parentIds) {
            result.put(parentId, listDocumentFolderChildrenForTree(
                    context,
                    req.getScope(),
                    req.getShareTargetType(),
                    req.getShareTargetId(),
                    parentId));
        }
        return result;
    }

    private List<DocumentFileVO> listDocumentFolderChildrenForTree(
            DocumentAccessContext context,
            String scope,
            String shareTargetType,
            String shareTargetId,
            String parentId) {
        List<DocumentFileVO> records = new ArrayList<>();
        int pageNum = 1;
        long total;
        do {
            DocumentPageReq pageReq = new DocumentPageReq();
            pageReq.setFolderOnly(true);
            pageReq.setPageNum(pageNum);
            pageReq.setPageSize(TREE_PREFETCH_PAGE_SIZE);
            pageReq.setParentId(parentId);
            pageReq.setScope(scope);
            pageReq.setShareTargetId(shareTargetId);
            pageReq.setShareTargetType(shareTargetType);

            PageVO<DocumentFileVO> page = pageDocuments(context, pageReq);
            List<DocumentFileVO> pageRecords = page.getRecords() == null
                    ? Collections.emptyList()
                    : page.getRecords();
            records.addAll(pageRecords.stream()
                    .filter(item -> FLAG_YES.equals(item.getIzFolder()))
                    .filter(item -> StringUtils.hasText(item.getId()))
                    .toList());
            total = page.getTotal();
            pageNum++;
        } while (records.size() < total && pageNum <= TREE_PREFETCH_MAX_PAGE_NUM);
        return records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO uploadDocument(String username, MultipartFile file, DocumentUploadReq req) {
        fileMetadataService.validateUploadFile(file);
        String tenantId = requireTenantId();
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, tenantId);
        String scope = documentScopeService.normalizeScope(req == null ? null : req.getScope());
        if (SCOPE_BUSINESS.equals(scope)) {
            throw new IllegalArgumentException("业务文档需从业务模块上传");
        }
        if (documentScopeService.isSharedInboxRequest(req, scope)) {
            throw new IllegalArgumentException("共享给我的文件夹不支持上传文件");
        }
        String parentId = trimToNull(req == null ? null : req.getParentId());
        DocumentSharedTargetContext sharedTarget = documentSharedSpaceService.resolveSharedTargetContext(
                context,
                req == null ? null : req.getShareTargetType(),
                req == null ? null : req.getShareTargetId(),
                false);
        String storeType = documentSharedSpaceService.resolveDocumentStoreType(scope, sharedTarget);
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            documentSharedSpaceService.assertCanManageDocument(parent, context);
            documentSharedSpaceService.validateSharedSpaceParent(parent, sharedTarget);
            storeType = StringUtils.hasText(parent.getStoreType()) ? parent.getStoreType() : storeType;
            if (!FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("只能上传到文件夹下");
            }
        }

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
            fileEntity.setFileType(fileMetadataService.resolveDocumentFileType(file));
            fileEntity.setStoreType(storeType);
            fileEntity.setParentId(parentId);
            fileEntity.setFileSize(file.getSize() > 0 ? file.getSize() / 1024.0 : 0D);
            fileEntity.setIzFolder(FLAG_NO);
            fileEntity.setIzRootFolder(StringUtils.hasText(parentId) ? FLAG_NO : FLAG_YES);
            fileEntity.setIzStar(FLAG_NO);
            fileEntity.setDownCount(0);
            fileEntity.setReadCount(0);
            fileEntity.setSharePerms("1");
            fileEntity.setEnableDown(FLAG_YES);
            fileEntity.setEnableUpdat(FLAG_NO);
            fileEntity.setCreateBy(username);
            fileEntity.setCreateTime(LocalDateTime.now());
            fileEntity.setUpdateBy(username);
            fileEntity.setUpdateTime(LocalDateTime.now());
            fileEntity.setDeleteFlag(0);
            baseMapper.insert(fileEntity);
            createInitialHistoryVersion(fileEntity, file, username);
            documentSharedSpaceService.bindDepartSharedRootIfNeeded(fileEntity, sharedTarget);
            return documentFileViewService.buildDocumentVO(fileEntity, context);
        } catch (RuntimeException ex) {
            deleteObjectQuietly(objectName);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO createDocumentFolder(String username, DocumentFolderReq req) {
        if (req == null) {
            throw new IllegalArgumentException("文件夹信息不能为空");
        }
        String tenantId = requireTenantId();
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, tenantId);
        String scope = documentScopeService.normalizeScope(req.getScope());
        boolean sharedInbox = documentScopeService.isSharedInboxRequest(req, scope);
        boolean businessScope = SCOPE_BUSINESS.equals(scope);
        if (businessScope) {
            throw new IllegalArgumentException("业务文档只允许查看，不支持新建文件夹");
        }
        String parentId = trimToNull(req.getParentId());
        DocumentSharedTargetContext sharedTarget = documentSharedSpaceService.resolveSharedTargetContext(
                context,
                req.getShareTargetType(),
                req.getShareTargetId(),
                false);
        String storeType = sharedInbox ? SHARED_VIEW_STORE_TYPE : documentSharedSpaceService.resolveDocumentStoreType(scope, sharedTarget);
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            documentSharedSpaceService.assertCanManageDocument(parent, context);
            documentSharedSpaceService.validateSharedSpaceParent(parent, sharedTarget);
            if (!FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("父级必须是文件夹");
            }
            if (sharedInbox && !SHARED_VIEW_STORE_TYPE.equals(parent.getStoreType())) {
                throw new IllegalArgumentException("共享给我的整理文件夹只能建在个人整理目录下");
            }
            storeType = StringUtils.hasText(parent.getStoreType()) ? parent.getStoreType() : storeType;
        }

        SysFiles folder = new SysFiles();
        folder.setId(newId());
        folder.setTenantId(tenantId);
        folder.setFileName(trimToNull(req.getFileName()));
        folder.setFileType(FOLDER_TYPE);
        folder.setStoreType(storeType);
        folder.setParentId(parentId);
        folder.setFileSize(0D);
        folder.setIzFolder(FLAG_YES);
        folder.setIzRootFolder(StringUtils.hasText(parentId) ? FLAG_NO : FLAG_YES);
        folder.setIzStar(FLAG_NO);
        folder.setDownCount(0);
        folder.setReadCount(0);
        folder.setSharePerms("1");
        folder.setEnableDown(FLAG_YES);
        folder.setEnableUpdat(FLAG_NO);
        folder.setCreateBy(username);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateBy(username);
        folder.setUpdateTime(LocalDateTime.now());
        folder.setDeleteFlag(0);
        baseMapper.insert(folder);
        documentSharedSpaceService.bindDepartSharedRootIfNeeded(folder, sharedTarget);
        return documentFileViewService.buildDocumentVO(folder, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO renameDocument(String username, DocumentRenameReq req) {
        SysFiles file = getActiveFile(req.getId());
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        assertNotBusinessReadonlyDocument(file);
        file.setFileName(trimToNull(req.getFileName()));
        fillUpdate(file, username);
        baseMapper.updateById(file);
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO moveDocument(String username, DocumentMoveReq req) {
        SysFiles file = getActiveFile(req.getId());
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        String parentId = trimToNull(req.getParentId());
        String scope = documentScopeService.normalizeScope(req.getScope());
        DocumentSharedTargetContext sharedTarget = documentSharedSpaceService.resolveSharedTargetContext(
                context,
                req.getShareTargetType(),
                req.getShareTargetId(),
                false);
        if (SCOPE_BUSINESS.equals(scope)) {
            throw new IllegalArgumentException("业务文档只允许查看，不支持移动或归类");
        }
        assertNotBusinessReadonlyDocument(file);
        if (documentScopeService.isSharedInboxRequest(req, scope) && !Objects.equals(file.getCreateBy(), username)) {
            documentCopyMoveService.moveSharedInboxPlacement(context, file, parentId);
            return documentFileViewService.buildDocumentVO(file, context);
        }
        documentSharedSpaceService.assertCanManageDocument(file, context);
        if (Objects.equals(file.getId(), parentId)) {
            throw new IllegalArgumentException("不能移动到自身下");
        }
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            documentSharedSpaceService.assertCanManageDocument(parent, context);
            documentSharedSpaceService.validateSharedSpaceParent(parent, sharedTarget);
            if (!FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("目标必须是文件夹");
            }
            if (SHARED_VIEW_STORE_TYPE.equals(file.getStoreType())
                    && !SHARED_VIEW_STORE_TYPE.equals(parent.getStoreType())) {
                throw new IllegalArgumentException("共享给我的整理文件夹只能移动到个人整理目录下");
            }
            documentCopyMoveService.validateNotMoveToDescendant(file.getId(), parentId, context.tenantId());
        }
        documentSharedSpaceService.validateSharedSpaceMember(file, sharedTarget);
        updateDocumentParent(file, parentId, username);
        documentSharedSpaceService.updateSharedSpaceStoreType(file, parentId, sharedTarget, username);
        documentSharedSpaceService.bindDepartSharedRootIfNeeded(file, sharedTarget);
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentFileVO> batchMoveDocuments(String username, DocumentBatchMoveReq req) {
        List<String> ids = normalizeDocumentIds(req == null ? null : req.getIds());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        List<DocumentFileVO> movedFiles = new ArrayList<>();
        for (String id : ids) {
            DocumentMoveReq moveReq = new DocumentMoveReq();
            moveReq.setId(id);
            moveReq.setParentId(req.getParentId());
            moveReq.setScope(req.getScope());
            moveReq.setShareTargetType(req.getShareTargetType());
            moveReq.setShareTargetId(req.getShareTargetId());
            movedFiles.add(moveDocument(username, moveReq));
        }
        return movedFiles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentFileVO> copyDocuments(String username, DocumentCopyReq req) {
        if (req == null || req.getIds() == null || req.getIds().isEmpty()) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        DocumentCopyTarget copyTarget = documentCopyMoveService.resolveCopyTarget(context, req);
        List<String> sourceIds = req.getIds().stream()
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (sourceIds.isEmpty()) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        List<DocumentFileVO> copiedFiles = new ArrayList<>();
        for (String sourceId : sourceIds) {
            SysFiles source = getActiveFile(sourceId);
            if (Objects.equals(source.getId(), copyTarget.parentId())) {
                throw new IllegalArgumentException("不能复制到自身下");
            }
            assertNotBusinessReadonlyDocument(source);
            if (StringUtils.hasText(copyTarget.parentId())) {
                documentCopyMoveService.validateNotMoveToDescendant(source.getId(), copyTarget.parentId(), context.tenantId());
            }
            SysFiles copied = documentCopyMoveService.copyDocumentTree(context, source, copyTarget.parentId(), copyTarget.storeType());
            documentSharedSpaceService.bindDepartSharedRootIfNeeded(copied, copyTarget.sharedTarget());
            copiedFiles.add(documentFileViewService.buildDocumentVO(copied, context));
        }
        return copiedFiles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String username, String fileId) {
        SysFiles file = getActiveFile(fileId);
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        assertDocumentCanBeDeleted(file);
        documentTreeLifecycleService.softDeleteDocumentTree(file, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteDocuments(String username, DocumentBatchDeleteReq req) {
        List<String> ids = normalizeDocumentIds(req == null ? null : req.getIds());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        for (String id : ids) {
            deleteDocument(username, id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO restoreDocument(String username, String fileId) {
        SysFiles file = getFileIncludingDeleted(fileId);
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        if (StringUtils.hasText(file.getParentId())) {
            SysFiles parent = getFileIncludingDeleted(file.getParentId());
            if (parent != null && Objects.equals(parent.getDeleteFlag(), 1)) {
                throw new IllegalArgumentException("请先恢复父级文件夹");
            }
        }
        documentTreeLifecycleService.restoreDocumentTree(file, username);
        SysFiles restored = getActiveFile(fileId);
        return documentFileViewService.buildDocumentVO(restored, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentFileVO> batchRestoreDocuments(String username, DocumentBatchDeleteReq req) {
        List<String> ids = normalizeDocumentIds(req == null ? null : req.getIds());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        List<DocumentFileVO> restoredFiles = new ArrayList<>();
        for (String id : ids) {
            restoredFiles.add(restoreDocument(username, id));
        }
        return restoredFiles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeDocument(String username, String fileId) {
        SysFiles file = getFileIncludingDeleted(fileId);
        documentSharedSpaceService.assertCanManageDocument(file, documentAccessContextService.buildDocumentAccessContext(username, requireTenantId()));
        if (!Objects.equals(file.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("只能彻底删除回收站中的文档");
        }
        assertDocumentCanBeDeleted(file);
        documentTreeLifecycleService.hardDeleteDocumentTree(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearDocumentTrash(String username) {
        String tenantId = requireTenantId();
        List<SysFiles> files = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getCreateBy, username)
                .eq(SysFiles::getDeleteFlag, 1));
        if (files.isEmpty()) {
            return;
        }
        Set<String> deletedIds = files.stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        files.stream()
                .filter(file -> !StringUtils.hasText(file.getParentId()) || !deletedIds.contains(file.getParentId()))
                .filter(file -> !BUSINESS_VIEW_STORE_TYPE.equals(file.getStoreType()))
                .filter(file -> !documentBusinessAccessService.hasActiveBusinessRelation(file.getId(), file.getTenantId()))
                .forEach(documentTreeLifecycleService::hardDeleteDocumentTree);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO toggleDocumentStar(String username, String fileId) {
        SysFiles file = getActiveFile(fileId);
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        assertNotBusinessReadonlyDocument(file);
        file.setIzStar(FLAG_YES.equals(file.getIzStar()) ? FLAG_NO : FLAG_YES);
        fillUpdate(file, username);
        baseMapper.updateById(file);
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentShareVO> shareDocument(String username, DocumentShareReq req) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentShareManagementService.shareDocument(context, req);
    }

    @Override
    public List<DocumentShareVO> listDocumentShares(String username, String fileId) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentShareManagementService.listDocumentShares(context, fileId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentStatusVO getDocumentStatus(String username, String fileId) {
        SysFiles file = getFileIncludingDeleted(fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或已删除");
        }
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        if (Objects.equals(file.getDeleteFlag(), 1)) {
            assertOwner(file, username);
        } else {
            documentFileAccessService.assertCanViewDocument(file, context);
        }

        DocumentStatusVO status = new DocumentStatusVO();
        status.setFile(documentFileViewService.buildDocumentVO(file, context));
        status.setDeleteBy(file.getDeleteBy());
        status.setOriginalPath(documentStatisticsService.resolveDocumentPath(file));
        status.setAccessShareSource(documentShareSourceService.resolveAccessShareSource(file, context));
        status.setInheritedShareSource(documentShareSourceService.resolveInheritedShareSource(file, context));
        status.setFavoriteSource(documentShareSourceService.resolveFavoriteSource(file, context));
        if (documentShareSourceService.canSeeDirectShares(file, context)) {
            status.setDirectShares(documentShareSourceService.listActiveDirectShareVOs(file.getId(), file.getTenantId()));
        }
        if (FLAG_YES.equals(file.getIzFolder())) {
            status.setFolderStats(documentStatisticsService.calculateFolderStats(file));
        }
        documentFileViewService.fillBusinessStatus(status, file, context);
        return status;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeDocumentShare(String username, String aclId) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        documentShareManagementService.revokeDocumentShare(context, aclId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO checkDocumentDownload(String fileId, String username) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentContentAccessService.checkDocumentDownload(fileId, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO checkDocumentPreview(String fileId, String username) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentContentAccessService.checkDocumentPreview(fileId, context);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFileVO checkDocumentRead(String fileId, String username) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentContentAccessService.checkDocumentRead(fileId, context);
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream openDocumentContent(String fileId, String username) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentContentAccessService.openDocumentContent(fileId, context);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFileVO checkDocumentEdit(String fileId, String username) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        return documentContentAccessService.checkDocumentEdit(fileId, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDocumentEdit(
            String fileId,
            String username,
            InputStream inputStream,
            String contentType,
            Long contentLength,
            boolean touchUpdateTime) {
        DocumentAccessContext context = documentAccessContextService.buildDocumentAccessContext(username, requireTenantId());
        documentContentAccessService.saveDocumentEdit(
                fileId,
                context,
                inputStream,
                contentType,
                contentLength,
                touchUpdateTime);
    }

    private PageVO<DocumentFileVO> pageDocumentChildrenByParent(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            String parentId) {
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .eq(SysFiles::getParentId, parentId);
        applyDocumentFilters(wrapper, pageReq);
        wrapper.orderByDesc(SysFiles::getIzFolder)
                .orderByDesc(SysFiles::getUpdateTime)
                .orderByDesc(SysFiles::getCreateTime);
        Page<SysFiles> page = new Page<>(pageReq.getPageNum(), pageReq.getPageSize());
        Page<SysFiles> result = baseMapper.selectPage(page, wrapper);
        List<DocumentFileVO> records = documentFileViewService.buildDocumentVOList(result.getRecords(), context);
        documentFileViewService.fillFolderChildFlags(records, context);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private PageVO<DocumentFileVO> pageSharedFolderChildren(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            SysFiles parent,
            int inheritedRank) {
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .eq(SysFiles::getParentId, parent.getId());
        applyDocumentFilters(wrapper, pageReq);
        wrapper.orderByDesc(SysFiles::getIzFolder)
                .orderByDesc(SysFiles::getUpdateTime)
                .orderByDesc(SysFiles::getCreateTime);
        Page<SysFiles> page = new Page<>(pageReq.getPageNum(), pageReq.getPageSize());
        Page<SysFiles> result = baseMapper.selectPage(page, wrapper);
        boolean inheritedDownload = inheritedRank >= documentAclPermissionService.permissionRank(PERMISSION_DOWNLOAD)
                && !FLAG_NO.equals(parent.getEnableDown());
        boolean inheritedUpdate = inheritedRank >= documentAclPermissionService.permissionRank(PERMISSION_UPDATE)
                && !FLAG_NO.equals(parent.getEnableUpdat());
        List<DocumentFileVO> records = result.getRecords().stream()
                .map(file -> documentFileViewService.buildSharedFolderChildVO(file, context, inheritedDownload, inheritedUpdate))
                .toList();
        documentFileViewService.fillFolderChildFlags(records, context);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 回收站保持原文件夹层级：根目录只展示已删除树的顶层节点，进入已删除文件夹后再展示直接子级。
     */
    private PageVO<DocumentFileVO> pageTrashDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getFileIncludingDeleted(parentId);
            assertOwner(parent, context.username());
            if (!Objects.equals(parent.getDeleteFlag(), 1) || !FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("只能浏览回收站中的文件夹");
            }
            LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                    .eq(SysFiles::getTenantId, context.tenantId())
                    .eq(SysFiles::getCreateBy, context.username())
                    .eq(SysFiles::getDeleteFlag, 1)
                    .eq(SysFiles::getParentId, parentId);
            applyDocumentFilters(wrapper, pageReq);
            wrapper.orderByDesc(SysFiles::getIzFolder)
                    .orderByDesc(SysFiles::getUpdateTime)
                    .orderByDesc(SysFiles::getCreateTime);
            Page<SysFiles> page = new Page<>(pageReq.getPageNum(), pageReq.getPageSize());
            Page<SysFiles> result = baseMapper.selectPage(page, wrapper);
            List<DocumentFileVO> records = documentFileViewService.buildDocumentVOList(result.getRecords(), context);
            documentFileViewService.fillFolderChildFlags(records, context);
            return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
        }

        List<SysFiles> deletedFiles = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getCreateBy, context.username())
                .eq(SysFiles::getDeleteFlag, 1));
        Set<String> deletedIds = deletedFiles.stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        List<SysFiles> rootItems = deletedFiles.stream()
                .filter(file -> !StringUtils.hasText(file.getParentId()) || !deletedIds.contains(file.getParentId()))
                .toList();
        List<SysFiles> rootFolders = rootItems.stream()
                .filter(file -> FLAG_YES.equals(file.getIzFolder()))
                .toList();
        List<SysFiles> rootFiles = rootItems.stream()
                .filter(file -> !FLAG_YES.equals(file.getIzFolder()))
                .toList();
        return pageCombinedDocuments(context, pageReq, rootFolders, rootFiles);
    }

    /**
     * 收藏根目录按收藏入口合并展示，避免父子文件夹都收藏时在根层重复出现。
     */
    private PageVO<DocumentFileVO> pageStarredDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            assertOwner(parent, context.username());
            return pageDocumentChildrenByParent(context, pageReq, parentId);
        }

        List<SysFiles> starredFiles = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getCreateBy, context.username())
                .eq(SysFiles::getIzStar, FLAG_YES)
                .eq(SysFiles::getDeleteFlag, 0));
        Set<String> starredIds = starredFiles.stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        List<SysFiles> rootItems = starredFiles.stream()
                .filter(file -> !hasStarredAncestor(file.getParentId(), starredIds, context.tenantId()))
                .toList();
        List<SysFiles> rootFolders = rootItems.stream()
                .filter(file -> FLAG_YES.equals(file.getIzFolder()))
                .toList();
        List<SysFiles> rootFiles = rootItems.stream()
                .filter(file -> !FLAG_YES.equals(file.getIzFolder()))
                .toList();
        return pageCombinedDocuments(context, pageReq, rootFolders, rootFiles);
    }

    /**
     * “共享给我”根目录同时展示个人整理文件夹和按共享人聚合的虚拟目录。
     * 已被个人整理的共享文件不再重复出现在共享人虚拟目录下。
     */
    private PageVO<DocumentFileVO> pageSharedInboxDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            if (documentVirtualNodeService.isSharedOwnerVirtualId(parentId)) {
                return pageSharedOwnerDocuments(context, pageReq, documentVirtualNodeService.parseSharedOwner(parentId));
            }
            SysFiles parent = getActiveFile(parentId);
            if (documentCopyMoveService.isPersonalSharedFolder(parent, context.username())) {
                return pagePersonalSharedFolderChildren(context, pageReq, parentId);
            }
            int inheritedRank = documentAclPermissionService.resolveSharedDocumentPermissionRank(parent, context);
            return pageSharedFolderChildren(context, pageReq, parent, inheritedRank);
        }

        List<SysFiles> personalFolders = selectPersonalSharedFolders(context, null);
        List<String> fileIds = filterSharedRootFileIds(findSharedFileIds(context, pageReq), context);
        Set<String> placedFileIds = documentCopyMoveService.findPersonalSharedPlacedFileIds(context);
        fileIds = fileIds.stream()
                .filter(fileId -> !placedFileIds.contains(fileId))
                .toList();
        List<SysFiles> sharedFiles = selectActiveFilesByIds(context, fileIds).stream()
                .filter(file -> !Objects.equals(file.getCreateBy(), context.username()))
                .filter(file -> matchesSharedOwnerSourceFilters(file, pageReq))
                .toList();
        List<SysFiles> sharedOwnerFolders = documentFileViewService.buildSharedOwnerFolders(context, sharedFiles);
        List<SysFiles> folders = new ArrayList<>(personalFolders);
        folders.addAll(sharedOwnerFolders);
        return pageCombinedDocuments(context, pageReq, folders, Collections.emptyList());
    }

    /**
     * “共享给我”下按共享人展开时，只展示指定共享人的源文件，不再混入其他共享来源。
     */
    private PageVO<DocumentFileVO> pageSharedOwnerDocuments(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            String owner) {
        if (!StringUtils.hasText(owner)) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        Set<String> placedFileIds = documentCopyMoveService.findPersonalSharedPlacedFileIds(context);
        List<String> fileIds = filterSharedRootFileIds(findSharedFileIds(context, pageReq), context).stream()
                .filter(fileId -> !placedFileIds.contains(fileId))
                .toList();
        List<SysFiles> sharedFiles = selectActiveFilesByIds(context, fileIds, pageReq.getFolderOnly()).stream()
                .filter(file -> !Objects.equals(file.getCreateBy(), context.username()))
                .filter(file -> Objects.equals(file.getCreateBy(), owner))
                .toList();
        return pageCombinedDocuments(context, pageReq, Collections.emptyList(), sharedFiles);
    }

    /**
     * “我共享的”只展示当前用户拥有且仍有有效授权的文件，避免暴露他人共享源。
     */
    private PageVO<DocumentFileVO> pageSharedByMeDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        List<String> sharedIds = filterSharedRootFileIds(findFileIdsSharedByOwner(context), context);
        List<SysFiles> sharedByMeFolders = selectSharedByMeFolders(context, null);
        List<SysFiles> sharedFiles = sharedIds.isEmpty()
                ? Collections.emptyList()
                : selectActiveFilesByIds(context, sharedIds, pageReq.getFolderOnly());
        return pageCombinedDocuments(context, pageReq, sharedByMeFolders, sharedFiles);
    }

    /**
     * 共享空间按租户、部门等目标过滤，入口处先解析并校验目标，防止用户枚举无权共享空间。
     */
    private PageVO<DocumentFileVO> pageSharedTargetDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        DocumentSharedTargetContext sharedTarget = documentSharedSpaceService.resolveSharedTargetContext(
                context,
                pageReq.getShareTargetType(),
                pageReq.getShareTargetId(),
                true);
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            documentSharedSpaceService.assertCanViewSharedSpace(parent, context);
            documentSharedSpaceService.validateSharedSpaceParent(parent, sharedTarget);
            return pageDocumentChildrenByParent(context, pageReq, parentId);
        }
        List<SysFiles> sharedFiles = documentSharedSpaceService.selectSharedSpaceRootFiles(context, sharedTarget, pageReq.getFolderOnly());
        return pageCombinedDocuments(context, pageReq, Collections.emptyList(), sharedFiles);
    }

    /**
     * 业务文档根目录仅生成模块和业务记录虚拟目录，真实附件访问必须通过业务关系授权判断。
     */
    private PageVO<DocumentFileVO> pageBusinessDocuments(DocumentAccessContext context, DocumentPageReq pageReq) {
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            if (documentVirtualNodeService.isBusinessModuleVirtualId(parentId)) {
                return pageBusinessModuleChildren(
                        context,
                        pageReq,
                        documentVirtualNodeService.parseBusinessModuleBizType(parentId));
            }
            if (documentVirtualNodeService.isBusinessGroupVirtualId(parentId)) {
                return pageBusinessGroupRecords(
                        context,
                        pageReq,
                        documentVirtualNodeService.parseBusinessGroupNode(parentId));
            }
            if (documentVirtualNodeService.isBusinessRecordVirtualId(parentId)) {
                return pageBusinessRecordChildren(
                        context,
                        pageReq,
                        documentVirtualNodeService.parseBusinessRecordNode(parentId));
            }
            SysFiles parent = getActiveFile(parentId);
            assertOwner(parent, context.username());
            if (!documentCopyMoveService.isBusinessFolder(parent, context.username())) {
                throw new IllegalArgumentException("只能打开业务文档归类文件夹");
            }
            return pageBusinessFolderChildren(context, pageReq, parentId);
        }

        List<SysFiles> modules = documentBusinessAccessService.findAccessibleBusinessRelations(context).stream()
                .map(SysFileRelation::getBizType)
                .filter(StringUtils::hasText)
                .distinct()
                .map(bizType -> documentFileViewService.buildBusinessVirtualFolder(
                        context,
                        documentVirtualNodeService.businessModuleId(bizType),
                        documentVirtualNodeService.resolveBusinessModuleName(bizType),
                        BUSINESS_MODULE_VIEW_STORE_TYPE,
                        null))
                .toList();
        return pageCombinedDocuments(context, pageReq, modules, Collections.emptyList());
    }

    /**
     * 业务模块虚拟目录下展示可选业务分组目录；未分组业务仍直接展示业务记录目录。
     */
    private PageVO<DocumentFileVO> pageBusinessModuleChildren(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        List<String> bizIds = documentBusinessAccessService.findAccessibleBusinessRelations(context).stream()
                .filter(relation -> Objects.equals(relation.getBizType(), bizType))
                .map(SysFileRelation::getBizId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, String> groupIdsByBizId = documentVirtualNodeService.resolveBusinessRecordGroupIds(
                bizType,
                bizIds,
                documentBusinessAccessService.toBusinessDocumentAccessContext(context));
        List<String> groupIds = bizIds.stream()
                .map(groupIdsByBizId::get)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, String> groupNames = documentVirtualNodeService.resolveBusinessGroupNames(
                bizType,
                groupIds,
                documentBusinessAccessService.toBusinessDocumentAccessContext(context));
        List<SysFiles> groups = groupIds.stream()
                .map(groupId -> documentFileViewService.buildBusinessVirtualFolder(
                        context,
                        documentVirtualNodeService.businessGroupId(bizType, groupId),
                        groupNames.getOrDefault(groupId, groupId),
                        BUSINESS_GROUP_VIEW_STORE_TYPE,
                        documentVirtualNodeService.businessModuleId(bizType)))
                .toList();
        List<String> ungroupedBizIds = bizIds.stream()
                .filter(bizId -> !StringUtils.hasText(groupIdsByBizId.get(bizId)))
                .toList();
        if (ungroupedBizIds.isEmpty()) {
            return pageCombinedDocuments(context, pageReq, groups, Collections.emptyList());
        }
        Map<String, String> recordNames = documentVirtualNodeService.resolveBusinessRecordNames(
                bizType,
                ungroupedBizIds,
                documentBusinessAccessService.toBusinessDocumentAccessContext(context));
        List<SysFiles> records = ungroupedBizIds.stream()
                .map(bizId -> documentFileViewService.buildBusinessVirtualFolder(
                        context,
                        documentVirtualNodeService.businessRecordId(bizType, bizId),
                        recordNames.getOrDefault(bizId, bizType + "-" + bizId),
                        BUSINESS_RECORD_VIEW_STORE_TYPE,
                        documentVirtualNodeService.businessModuleId(bizType)))
                .toList();
        List<SysFiles> children = new ArrayList<>(groups);
        children.addAll(records);
        return pageCombinedDocuments(context, pageReq, children, Collections.emptyList());
    }

    /**
     * 业务分组虚拟目录下展示当前分组内的业务记录目录。
     */
    private PageVO<DocumentFileVO> pageBusinessGroupRecords(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            DocumentBusinessGroupNode groupNode) {
        if (groupNode == null
                || !StringUtils.hasText(groupNode.bizType())
                || !StringUtils.hasText(groupNode.groupId())) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        List<String> bizIds = documentBusinessAccessService.findAccessibleBusinessRelations(context).stream()
                .filter(relation -> Objects.equals(relation.getBizType(), groupNode.bizType()))
                .map(SysFileRelation::getBizId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, String> groupIdsByBizId = documentVirtualNodeService.resolveBusinessRecordGroupIds(
                groupNode.bizType(),
                bizIds,
                documentBusinessAccessService.toBusinessDocumentAccessContext(context));
        List<String> groupedBizIds = bizIds.stream()
                .filter(bizId -> Objects.equals(groupNode.groupId(), groupIdsByBizId.get(bizId)))
                .toList();
        Map<String, String> recordNames = documentVirtualNodeService.resolveBusinessRecordNames(
                groupNode.bizType(),
                groupedBizIds,
                documentBusinessAccessService.toBusinessDocumentAccessContext(context));
        String groupParentId = documentVirtualNodeService.businessGroupId(groupNode.bizType(), groupNode.groupId());
        List<SysFiles> records = groupedBizIds.stream()
                .map(bizId -> documentFileViewService.buildBusinessVirtualFolder(
                        context,
                        documentVirtualNodeService.businessRecordId(groupNode.bizType(), bizId),
                        recordNames.getOrDefault(bizId, groupNode.bizType() + "-" + bizId),
                        BUSINESS_RECORD_VIEW_STORE_TYPE,
                        groupParentId))
                .toList();
        return pageCombinedDocuments(context, pageReq, records, Collections.emptyList());
    }

    /**
     * 业务记录虚拟目录下展示业务附件和个人整理文件夹，已经被个人整理的业务附件不重复展示。
     */
    private PageVO<DocumentFileVO> pageBusinessRecordChildren(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            DocumentBusinessRecordNode recordNode) {
        if (recordNode == null || !StringUtils.hasText(recordNode.bizType()) || !StringUtils.hasText(recordNode.bizId())) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        String virtualParentId = documentVirtualNodeService.businessRecordId(recordNode.bizType(), recordNode.bizId());
        List<SysFiles> personalFolders = selectBusinessFolders(context, virtualParentId);
        Set<String> placedFileIds = documentCopyMoveService.findPersonalBusinessPlacedFileIds(context);
        List<String> fileIds = documentBusinessAccessService.findAccessibleBusinessRelations(context).stream()
                .filter(relation -> Objects.equals(relation.getBizType(), recordNode.bizType()))
                .filter(relation -> Objects.equals(relation.getBizId(), recordNode.bizId()))
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .filter(fileId -> !placedFileIds.contains(fileId))
                .distinct()
                .toList();
        List<SysFiles> businessFiles = selectActiveFilesByIds(context, fileIds, pageReq.getFolderOnly());
        return pageCombinedDocuments(context, pageReq, personalFolders, businessFiles);
    }

    /**
     * 业务文档个人整理文件夹只展示当前归类关系下仍有业务访问权限的文件。
     */
    private PageVO<DocumentFileVO> pageBusinessFolderChildren(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            String parentId) {
        List<SysFiles> personalFolders = selectBusinessFolders(context, parentId);
        List<String> fileIds = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, documentCopyMoveService.personalBusinessRelationBizType(context))
                        .eq(SysFileRelation::getBizId, parentId)
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<SysFiles> businessFiles = selectActiveFilesByIds(context, fileIds, pageReq.getFolderOnly()).stream()
                .filter(file -> documentBusinessAccessService.hasBusinessDocumentAccess(file, context))
                .toList();
        return pageCombinedDocuments(context, pageReq, personalFolders, businessFiles);
    }

    /**
     * 共享文档个人整理文件夹需要重新校验源文件访问权，避免共享被撤销后仍能通过整理目录看到文件。
     */
    private PageVO<DocumentFileVO> pagePersonalSharedFolderChildren(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            String parentId) {
        List<SysFiles> personalFolders = selectPersonalSharedFolders(context, parentId);
        List<String> fileIds = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, documentCopyMoveService.personalSharedRelationBizType(context))
                        .eq(SysFileRelation::getBizId, parentId)
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<SysFiles> sharedFiles = selectActiveFilesByIds(context, fileIds, pageReq.getFolderOnly()).stream()
                .filter(file -> {
                    try {
                        documentFileAccessService.assertCanViewDocument(file, context);
                        return true;
                    } catch (IllegalArgumentException ex) {
                        return false;
                    }
                })
                .toList();
        return pageCombinedDocuments(context, pageReq, personalFolders, sharedFiles);
    }

    /**
     * 合并虚拟整理文件夹和真实文件后做内存分页。
     * 这些场景的数据来自不同存储语义，不能直接用单个 SQL parent_id 表达。
     */
    private PageVO<DocumentFileVO> pageCombinedDocuments(
            DocumentAccessContext context,
            DocumentPageReq pageReq,
            List<SysFiles> folders,
            List<SysFiles> files) {
        List<SysFiles> combined = new ArrayList<>();
        combined.addAll(folders);
        combined.addAll(files);
        combined = combined.stream()
                .filter(file -> matchesDocumentFilters(file, pageReq))
                .sorted((left, right) -> {
                    int folderCompare = Boolean.compare(FLAG_YES.equals(right.getIzFolder()), FLAG_YES.equals(left.getIzFolder()));
                    if (folderCompare != 0) {
                        return folderCompare;
                    }
                    LocalDateTime rightTime = right.getUpdateTime() != null ? right.getUpdateTime() : right.getCreateTime();
                    LocalDateTime leftTime = left.getUpdateTime() != null ? left.getUpdateTime() : left.getCreateTime();
                    int timeCompare = compareNullableTimeDesc(leftTime, rightTime);
                    if (timeCompare != 0) {
                        return timeCompare;
                    }
                    return String.valueOf(left.getFileName()).compareToIgnoreCase(String.valueOf(right.getFileName()));
                })
                .toList();
        long total = combined.size();
        long current = pageReq.getPageNum();
        long size = pageReq.getPageSize();
        int fromIndex = (int) Math.min(Math.max(current - 1, 0) * size, total);
        int toIndex = (int) Math.min(fromIndex + size, total);
        List<DocumentFileVO> records = documentFileViewService.buildDocumentVOList(combined.subList(fromIndex, toIndex), context);
        documentFileViewService.fillFolderChildFlags(records, context);
        return new PageVO<>(records, total, current, size);
    }

    /**
     * 时间排序按最近更新时间优先，空时间统一沉到后面，保证分页结果稳定。
     */
    private int compareNullableTimeDesc(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    /**
     * 虚拟目录和真实文件合并分页时无法复用 SQL 条件，这里保持与数据库查询一致的筛选口径。
     */
    private boolean matchesDocumentFilters(SysFiles file, DocumentPageReq req) {
        if (Boolean.TRUE.equals(req.getFolderOnly()) && !FLAG_YES.equals(file.getIzFolder())) {
            return false;
        }
        if (documentFileViewService.isSharedOwnerVirtualFolder(file)) {
            return true;
        }
        if (StringUtils.hasText(req.getKeyword())
                && !String.valueOf(file.getFileName()).contains(req.getKeyword().trim())) {
            return false;
        }
        return !StringUtils.hasText(req.getFileType())
                || Objects.equals(file.getFileType(), req.getFileType().trim());
    }

    /**
     * 共享人虚拟目录根节点只按共享源文件过滤，不套用个人整理目录的 folderOnly 语义。
     */
    private boolean matchesSharedOwnerSourceFilters(SysFiles file, DocumentPageReq req) {
        if (StringUtils.hasText(req.getKeyword())
                && !String.valueOf(file.getFileName()).contains(req.getKeyword().trim())) {
            return false;
        }
        return !StringUtils.hasText(req.getFileType())
                || Objects.equals(file.getFileType(), req.getFileType().trim());
    }

    /**
     * 共享给我的根目录和个人整理目录依赖固定的存储语义，查询条件必须显式区分根节点与子节点。
     */
    private List<SysFiles> selectPersonalSharedFolders(DocumentAccessContext context, String parentId) {
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getCreateBy, context.username())
                .eq(SysFiles::getStoreType, SHARED_VIEW_STORE_TYPE)
                .eq(SysFiles::getIzFolder, FLAG_YES)
                .eq(SysFiles::getDeleteFlag, 0);
        if (StringUtils.hasText(parentId)) {
            wrapper.eq(SysFiles::getParentId, parentId);
        } else {
            wrapper.and(item -> item.isNull(SysFiles::getParentId).or().eq(SysFiles::getParentId, ""));
        }
        return baseMapper.selectList(wrapper);
    }

    /**
     * 我共享的整理文件夹和共享给我的整理文件夹使用不同的 storeType，不能混用同一查询条件。
     */
    private List<SysFiles> selectSharedByMeFolders(DocumentAccessContext context, String parentId) {
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getCreateBy, context.username())
                .eq(SysFiles::getStoreType, SHARED_BY_ME_STORE_TYPE)
                .eq(SysFiles::getIzFolder, FLAG_YES)
                .eq(SysFiles::getDeleteFlag, 0);
        if (StringUtils.hasText(parentId)) {
            wrapper.eq(SysFiles::getParentId, parentId);
        } else {
            wrapper.and(item -> item.isNull(SysFiles::getParentId).or().eq(SysFiles::getParentId, ""));
        }
        return baseMapper.selectList(wrapper);
    }

    /**
     * 业务整理文件夹只允许出现在当前用户创建且未删除的业务视图下。
     */
    private List<SysFiles> selectBusinessFolders(DocumentAccessContext context, String parentId) {
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getCreateBy, context.username())
                .eq(SysFiles::getStoreType, BUSINESS_VIEW_STORE_TYPE)
                .eq(SysFiles::getIzFolder, FLAG_YES)
                .eq(SysFiles::getDeleteFlag, 0);
        if (StringUtils.hasText(parentId)) {
            wrapper.eq(SysFiles::getParentId, parentId);
        } else {
            wrapper.and(item -> item.isNull(SysFiles::getParentId).or().eq(SysFiles::getParentId, ""));
        }
        return baseMapper.selectList(wrapper);
    }

    /**
     * 统一按租户和逻辑删除条件批量取文件，避免在不同分支里重复写同一组主查询条件。
     */
    private List<SysFiles> selectActiveFilesByIds(DocumentAccessContext context, List<String> fileIds) {
        return selectActiveFilesByIds(context, fileIds, false);
    }

    /**
     * folderOnly 只影响结果是否限定为文件夹，不影响租户、删除态和 ID 白名单。
     */
    private List<SysFiles> selectActiveFilesByIds(DocumentAccessContext context, List<String> fileIds, Boolean folderOnly) {
        if (fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysFiles> wrapper = Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .in(SysFiles::getId, fileIds);
        if (Boolean.TRUE.equals(folderOnly)) {
            wrapper.eq(SysFiles::getIzFolder, FLAG_YES);
        }
        return baseMapper.selectList(wrapper);
    }

    /**
     * scope 决定文档数据边界，所有共享、业务文档和个人文件的可见范围都在这里收口。
     */
    private void applyDocumentScope(
            LambdaQueryWrapper<SysFiles> wrapper,
            DocumentAccessContext context,
            DocumentPageReq req,
            String scope) {
        wrapper.eq(SysFiles::getDeleteFlag, 0);
        if (SCOPE_ALL.equals(scope)) {
            List<String> sharedFileIds = findSharedFileIdsWithDescendants(context);
            List<String> businessFileIds = documentBusinessAccessService.findAccessibleBusinessFileIds(context);
            List<String> sharedSpaceFileIds = findAccessibleSharedSpaceFileIds(context);
            wrapper.and(item -> {
                item.eq(SysFiles::getCreateBy, context.username());
                if (!sharedFileIds.isEmpty()) {
                    item.or().in(SysFiles::getId, sharedFileIds);
                }
                if (!businessFileIds.isEmpty()) {
                    item.or().in(SysFiles::getId, businessFileIds);
                }
                if (!sharedSpaceFileIds.isEmpty()) {
                    item.or().in(SysFiles::getId, sharedSpaceFileIds);
                }
            });
            return;
        }
        if (SCOPE_SHARED.equals(scope)) {
            if (documentSharedSpaceService.isSharedTargetScope(req, scope)) {
                DocumentSharedTargetContext sharedTarget = documentSharedSpaceService.resolveSharedTargetContext(
                        context,
                        req.getShareTargetType(),
                        req.getShareTargetId(),
                        true);
                List<String> fileIds = documentSharedSpaceService.selectSharedSpaceRootFiles(context, sharedTarget, req.getFolderOnly()).stream()
                        .map(SysFiles::getId)
                        .filter(StringUtils::hasText)
                        .toList();
                if (fileIds.isEmpty()) {
                    wrapper.eq(SysFiles::getId, "__none__");
                    return;
                }
                wrapper.in(SysFiles::getId, fileIds);
                return;
            }
            validateSharedTargetFilter(req, context);
            List<String> fileIds = findSharedFileIds(context, req);
            if (!StringUtils.hasText(req.getParentId())) {
                fileIds = filterSharedRootFileIds(fileIds, context);
            }
            if (fileIds.isEmpty()) {
                wrapper.eq(SysFiles::getId, "__none__");
                return;
            }
            wrapper.in(SysFiles::getId, fileIds);
            return;
        }
        if (SCOPE_SHARED_BY_ME.equals(scope)) {
            List<String> sharedIds = findFileIdsSharedByOwner(context);
            wrapper.eq(SysFiles::getCreateBy, context.username())
                    .and(item -> {
                        item.eq(SysFiles::getStoreType, SHARED_BY_ME_STORE_TYPE);
                        if (!sharedIds.isEmpty()) {
                            item.or().in(SysFiles::getId, sharedIds);
                        }
                    });
            wrapper.eq(SysFiles::getParentId, req.getParentId());
            return;
        }
        wrapper.eq(SysFiles::getCreateBy, context.username());
        List<String> businessFileIds = documentBusinessAccessService.findActiveBusinessFileIds(context);
        if (!businessFileIds.isEmpty()) {
            wrapper.notIn(SysFiles::getId, businessFileIds);
        }
        wrapper.and(item -> item.isNull(SysFiles::getStoreType)
                .or()
                .eq(SysFiles::getStoreType, "")
                .or()
                .eq(SysFiles::getStoreType, DOCUMENT_STORE_TYPE));
        if (StringUtils.hasText(req.getParentId())) {
            wrapper.eq(SysFiles::getParentId, req.getParentId());
        } else {
            wrapper.and(item -> item.isNull(SysFiles::getParentId).or().eq(SysFiles::getParentId, ""));
        }
    }

    /**
     * 普通数据库分页的筛选条件集中在这里，保证各 scope 不各自拼接一套 keyword/fileType 规则。
     */
    private void applyDocumentFilters(LambdaQueryWrapper<SysFiles> wrapper, DocumentPageReq req) {
        if (Boolean.TRUE.equals(req.getFolderOnly())) {
            wrapper.eq(SysFiles::getIzFolder, FLAG_YES);
        }
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.like(SysFiles::getFileName, req.getKeyword().trim());
        }
        if (StringUtils.hasText(req.getFileType())) {
            wrapper.eq(SysFiles::getFileType, req.getFileType().trim());
        }
    }

    /**
     * 共享文件 ID 从 ACL 统一筛出，并同步套用共享目标过滤规则。
     */
    private List<String> findSharedFileIds(DocumentAccessContext context, DocumentPageReq req) {
        List<SysFileAcl> acls = documentAclPermissionService.selectActiveAclsForContext(null, context);
        return acls.stream()
                .filter(acl -> matchesSharedTargetFilter(acl, context, req))
                .map(SysFileAcl::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * “全部”视图需要展示共享文件夹子孙节点，因此共享根授权要展开到可访问后代。
     */
    private List<String> findSharedFileIdsWithDescendants(DocumentAccessContext context) {
        LinkedHashSet<String> fileIds = new LinkedHashSet<>(findSharedFileIds(context, new DocumentPageReq()));
        if (fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cursor = new ArrayList<>(fileIds);
        int guard = 0;
        while (!cursor.isEmpty() && guard++ < 20) {
            List<SysFiles> children = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                    .select(SysFiles::getId)
                    .eq(SysFiles::getTenantId, context.tenantId())
                    .eq(SysFiles::getDeleteFlag, 0)
                    .in(SysFiles::getParentId, cursor));
            cursor = children.stream()
                    .map(SysFiles::getId)
                    .filter(StringUtils::hasText)
                    .filter(fileIds::add)
                    .toList();
        }
        return new ArrayList<>(fileIds);
    }

    /**
     * “全部”视图需要把当前用户可进入的租户共享空间和部门共享空间都纳入可见范围。
     */
    private List<String> findAccessibleSharedSpaceFileIds(DocumentAccessContext context) {
        LinkedHashSet<String> fileIds = new LinkedHashSet<>();
        DocumentSharedTargetContext tenantTarget = new DocumentSharedTargetContext(TARGET_TENANT, context.tenantId());
        documentSharedSpaceService.selectSharedSpaceRootFiles(context, tenantTarget, false).stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .forEach(fileIds::add);
        for (String departId : context.departIds()) {
            DocumentSharedTargetContext departTarget = new DocumentSharedTargetContext(TARGET_DEPART, departId);
            documentSharedSpaceService.selectSharedSpaceRootFiles(context, departTarget, false).stream()
                    .map(SysFiles::getId)
                    .filter(StringUtils::hasText)
                    .forEach(fileIds::add);
        }
        collectDescendantFileIds(context.tenantId(), fileIds);
        return new ArrayList<>(fileIds);
    }

    /**
     * 共享和共享空间需要把可见根文件夹的子孙纳入查询；设置层级上限避免异常数据导致死循环。
     */
    private void collectDescendantFileIds(String tenantId, LinkedHashSet<String> fileIds) {
        List<String> cursor = new ArrayList<>(fileIds);
        int guard = 0;
        while (!cursor.isEmpty() && guard++ < 20) {
            List<SysFiles> children = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                    .select(SysFiles::getId)
                    .eq(SysFiles::getTenantId, tenantId)
                    .eq(SysFiles::getDeleteFlag, 0)
                    .in(SysFiles::getParentId, cursor));
            cursor = children.stream()
                    .map(SysFiles::getId)
                    .filter(StringUtils::hasText)
                    .filter(fileIds::add)
                    .toList();
        }
    }

    /**
     * 共享根目录只展示最上层授权项；子级由打开被共享文件夹后再按 parent_id 查询。
     */
    private List<String> filterSharedRootFileIds(List<String> fileIds, DocumentAccessContext context) {
        if (fileIds.isEmpty()) {
            return fileIds;
        }
        Set<String> sharedIds = new HashSet<>(fileIds);
        List<SysFiles> files = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .select(SysFiles::getId, SysFiles::getParentId)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .in(SysFiles::getId, fileIds));
        Map<String, SysFiles> fileMap = files.stream()
                .filter(file -> StringUtils.hasText(file.getId()))
                .collect(Collectors.toMap(SysFiles::getId, file -> file, (left, right) -> left));
        return fileIds.stream()
                .filter(fileId -> {
                    SysFiles file = fileMap.get(fileId);
                    return file != null && !hasSharedAncestor(file.getParentId(), sharedIds, context.tenantId());
                })
                .toList();
    }

    /**
     * 共享根目录只展示最上层授权项，父链已有授权时子项不再作为根项重复展示。
     */
    private boolean hasSharedAncestor(String parentId, Set<String> sharedIds, String tenantId) {
        String currentId = parentId;
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            if (sharedIds.contains(currentId)) {
                return true;
            }
            SysFiles parent = baseMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                    .select(SysFiles::getId, SysFiles::getParentId)
                    .eq(SysFiles::getId, currentId)
                    .eq(SysFiles::getTenantId, tenantId)
                    .eq(SysFiles::getDeleteFlag, 0)
                    .last("LIMIT 1"));
            currentId = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    /**
     * 判断收藏项父链上是否已有收藏入口；有则当前项并入上层收藏目录展示。
     */
    private boolean hasStarredAncestor(String parentId, Set<String> starredIds, String tenantId) {
        String currentId = parentId;
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            if (starredIds.contains(currentId)) {
                return true;
            }
            SysFiles parent = baseMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                    .select(SysFiles::getId, SysFiles::getParentId)
                    .eq(SysFiles::getId, currentId)
                    .eq(SysFiles::getTenantId, tenantId)
                    .eq(SysFiles::getDeleteFlag, 0)
                    .last("LIMIT 1"));
            currentId = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    /**
     * 共享目标过滤由后端按当前用户上下文校验，不能信任前端传入的租户或部门目标。
     */
    private void validateSharedTargetFilter(DocumentPageReq req, DocumentAccessContext context) {
        String targetType = trimToNull(req.getShareTargetType());
        String targetId = trimToNull(req.getShareTargetId());
        if (!StringUtils.hasText(targetType)) {
            return;
        }
        if (TARGET_TENANT.equals(targetType)) {
            if (StringUtils.hasText(targetId) && !Objects.equals(targetId, context.tenantId())) {
                throw new IllegalArgumentException("无权访问该租户共享文件夹");
            }
            return;
        }
        if (TARGET_DEPART.equals(targetType)) {
            if (StringUtils.hasText(targetId) && !context.departIds().contains(targetId)) {
                throw new IllegalArgumentException("无权访问该部门共享文件夹");
            }
            return;
        }
        if (TARGET_USER.equals(targetType) || TARGET_ROLE.equals(targetType)) {
            return;
        }
        throw new IllegalArgumentException("共享目标类型不正确");
    }

    /**
     * 共享列表的内存过滤必须同时匹配授权目标和当前用户所属范围，避免跨部门、跨角色查看。
     */
    private boolean matchesSharedTargetFilter(SysFileAcl acl, DocumentAccessContext context, DocumentPageReq req) {
        String targetType = trimToNull(req.getShareTargetType());
        String targetId = trimToNull(req.getShareTargetId());
        if (!StringUtils.hasText(targetType)) {
            return true;
        }
        if (TARGET_TENANT.equals(targetType)) {
            return TARGET_TENANT.equals(acl.getTargetType())
                    && Objects.equals(acl.getTargetId(), context.tenantId());
        }
        if (TARGET_DEPART.equals(targetType)) {
            if (!TARGET_DEPART.equals(acl.getTargetType())) {
                return false;
            }
            if (StringUtils.hasText(targetId)) {
                return Objects.equals(acl.getTargetId(), targetId);
            }
            return context.departIds().contains(acl.getTargetId());
        }
        if (TARGET_USER.equals(targetType)) {
            return TARGET_USER.equals(acl.getTargetType())
                    && Objects.equals(acl.getTargetId(), context.userId());
        }
        if (TARGET_ROLE.equals(targetType)) {
            if (!TARGET_ROLE.equals(acl.getTargetType())) {
                return false;
            }
            if (StringUtils.hasText(targetId)) {
                return Objects.equals(acl.getTargetId(), targetId);
            }
            return context.roleIds().contains(acl.getTargetId());
        }
        return false;
    }

    /**
     * “我共享的”必须同时满足 ACL 由当前用户创建且源文件仍属于当前用户，避免展示已转移或无效的授权。
     */
    private List<String> findFileIdsSharedByOwner(DocumentAccessContext context) {
        List<String> sharedFileIds = fileAclMapper.selectList(Wrappers.lambdaQuery(SysFileAcl.class)
                        .select(SysFileAcl::getFileId)
                        .eq(SysFileAcl::getTenantId, context.tenantId())
                        .eq(SysFileAcl::getCreateBy, context.username())
                        .eq(SysFileAcl::getDeleteFlag, 0))
                .stream()
                .map(SysFileAcl::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (sharedFileIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ownedFileIds = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                        .select(SysFiles::getId)
                        .eq(SysFiles::getTenantId, context.tenantId())
                        .eq(SysFiles::getCreateBy, context.username())
                        .eq(SysFiles::getDeleteFlag, 0)
                        .in(SysFiles::getId, sharedFileIds))
                .stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return sharedFileIds.stream()
                .filter(ownedFileIds::contains)
                .toList();
    }

    /**
     * 按共享目标查看“我共享的”时，仍要回查文件所有者，不能只依赖 ACL 创建人。
     */
    private List<String> findFileIdsSharedByOwnerAndTarget(
            DocumentAccessContext context,
            DocumentSharedTargetContext sharedTarget) {
        if (sharedTarget == null) {
            return Collections.emptyList();
        }
        List<String> sharedFileIds = fileAclMapper.selectList(Wrappers.lambdaQuery(SysFileAcl.class)
                        .select(SysFileAcl::getFileId)
                        .eq(SysFileAcl::getTenantId, context.tenantId())
                        .eq(SysFileAcl::getCreateBy, context.username())
                        .eq(SysFileAcl::getTargetType, sharedTarget.targetType())
                        .eq(SysFileAcl::getTargetId, sharedTarget.targetId())
                        .eq(SysFileAcl::getDeleteFlag, 0))
                .stream()
                .map(SysFileAcl::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (sharedFileIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ownedFileIds = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                        .select(SysFiles::getId)
                        .eq(SysFiles::getTenantId, context.tenantId())
                        .eq(SysFiles::getCreateBy, context.username())
                        .eq(SysFiles::getDeleteFlag, 0)
                        .in(SysFiles::getId, sharedFileIds))
                .stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return sharedFileIds.stream()
                .filter(ownedFileIds::contains)
                .toList();
    }

    /**
     * “我共享的”整理目录必须属于当前用户，并且自身或父链能追溯到共享入口，防止用普通文件夹 ID 越权浏览。
     */
    private void assertSharedByMeFolderBrowsable(SysFiles folder, DocumentAccessContext context) {
        if (folder == null
                || !FLAG_YES.equals(folder.getIzFolder())
                || !Objects.equals(folder.getCreateBy(), context.username())
                || !Objects.equals(folder.getDeleteFlag(), 0)) {
            throw new IllegalArgumentException("无权浏览该共享文件夹");
        }
        if (SHARED_BY_ME_STORE_TYPE.equals(folder.getStoreType())
                || documentAclPermissionService.hasActiveAcl(folder.getId(), context)) {
            return;
        }
        String parentId = folder.getParentId();
        int guard = 0;
        while (StringUtils.hasText(parentId) && guard++ < 20) {
            SysFiles parent = getFileIncludingDeleted(parentId);
            if (parent == null
                    || !Objects.equals(parent.getCreateBy(), context.username())
                    || Objects.equals(parent.getDeleteFlag(), 1)) {
                break;
            }
            if (SHARED_BY_ME_STORE_TYPE.equals(parent.getStoreType())
                    || documentAclPermissionService.hasActiveAcl(parent.getId(), context)) {
                return;
            }
            parentId = parent.getParentId();
        }
        throw new IllegalArgumentException("无权浏览该共享文件夹");
    }

    /**
     * 共享入口父链校验需要读取已删除父节点，避免因中间节点删除而错误放行浏览权限。
     */
    private SysFiles getFileIncludingDeleted(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        return baseMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getId, fileId)
                .eq(SysFiles::getTenantId, requireTenantId())
                .last("LIMIT 1"));
    }

    /**
     * 文档中心的管理动作以文件创建人为所有者口径，不能由前端传入的 scope 代替所有权校验。
     */
    private void assertOwner(SysFiles file, String username) {
        if (!StringUtils.hasText(username) || file == null || !Objects.equals(file.getCreateBy(), username)) {
            throw new IllegalArgumentException("无权管理该文档");
        }
    }

    /**
     * 业务文档在文档中心只承担聚合查看入口，生命周期和内容维护必须回到对应业务模块处理。
     */
    private void assertNotBusinessReadonlyDocument(SysFiles file) {
        if (file == null) {
            return;
        }
        if (BUSINESS_VIEW_STORE_TYPE.equals(file.getStoreType())
                || documentBusinessAccessService.hasActiveBusinessRelation(file.getId(), file.getTenantId())) {
            throw new IllegalArgumentException("业务文档只允许查看，请在业务模块中维护");
        }
    }

    /**
     * 业务文档和业务整理文件夹不允许从文档中心删除，必须等业务关系解除后再走普通清理。
     */
    private void assertDocumentCanBeDeleted(SysFiles file) {
        if (file == null) {
            return;
        }
        if (BUSINESS_VIEW_STORE_TYPE.equals(file.getStoreType())
                || documentBusinessAccessService.hasActiveBusinessRelation(file.getId(), file.getTenantId())) {
            throw new IllegalArgumentException("业务文档需由业务数据删除后再清理");
        }
    }

    private void fillUpdate(SysFiles file, String username) {
        file.setUpdateBy(username);
        file.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 移动文件时同步维护 parentId 和根目录标识，避免树查询和根目录查询出现不一致。
     */
    private void updateDocumentParent(SysFiles file, String parentId, String username) {
        String rootFlag = StringUtils.hasText(parentId) ? FLAG_NO : FLAG_YES;
        fillUpdate(file, username);
        baseMapper.update(null, Wrappers.lambdaUpdate(SysFiles.class)
                .eq(SysFiles::getId, file.getId())
                .eq(SysFiles::getTenantId, file.getTenantId())
                .set(SysFiles::getParentId, parentId)
                .set(SysFiles::getIzRootFolder, rootFlag)
                .set(SysFiles::getUpdateBy, file.getUpdateBy())
                .set(SysFiles::getUpdateTime, file.getUpdateTime()));
        file.setParentId(parentId);
        file.setIzRootFolder(rootFlag);
    }

    /**
     * 所有对外操作统一只读取当前租户未删除文件，避免绕过租户隔离或操作回收站数据。
     */
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

    /**
     * 业务文档个人归类关系必须限定当前租户且未删除，避免操作其他租户或已失效关系。
     */
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

    /**
     * 可预览编辑文档上传时立即生成 V1 历史版本，保证后续 OnlyOffice 编辑和版本恢复有基线文件。
     */
    private void createInitialHistoryVersion(SysFiles fileEntity, MultipartFile file, String username) {
        if (!fileMetadataService.supportsInitialHistoryVersion(fileEntity.getFileName())) {
            return;
        }
        MessageDigest digest = sha256Digest();
        String contentType = fileMetadataService.safeContentType(file.getContentType());
        String objectName = null;
        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            objectName = minioUtils.uploadFileAndReturnObjectName(
                    digestInputStream,
                    fileMetadataService.buildVersionFileName(fileEntity.getFileName(), 1),
                    contentType);
        } catch (IOException | RuntimeException ex) {
            deleteObjectQuietly(objectName);
            throw new IllegalArgumentException("Initial history version create failed", ex);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = findUserByUsername(username);
        SysFileVersion version = new SysFileVersion();
        version.setId(newId());
        version.setTenantId(fileEntity.getTenantId());
        version.setFileId(fileEntity.getId());
        version.setVersionNo(1);
        version.setVersionType(VERSION_TYPE_UPLOAD);
        version.setObjectName(objectName);
        version.setFileName(fileEntity.getFileName());
        version.setFileType(fileEntity.getFileType());
        version.setContentType(contentType);
        version.setFileSize(file.getSize());
        version.setChecksum(HexFormat.of().formatHex(digest.digest()));
        version.setEditorId(user == null ? null : user.getId());
        version.setEditorName(resolveUserDisplayName(user, username));
        version.setRemark("Upload initial version");
        version.setCreateBy(username);
        version.setCreateTime(now);
        version.setUpdateBy(username);
        version.setUpdateTime(now);
        version.setDeleteFlag(0);
        try {
            sysFileVersionMapper.insert(version);
        } catch (RuntimeException ex) {
            deleteObjectQuietly(objectName);
            throw ex;
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
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

    private String resolveUserDisplayName(User user, String username) {
        if (user != null && StringUtils.hasText(user.getRealname())) {
            return user.getRealname();
        }
        return username;
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

    private List<String> normalizeDocumentIds(List<String> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 对象存储补偿清理不能覆盖原始业务异常，因此这里只吞掉清理失败。
     */
    private void deleteObjectQuietly(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioUtils.deleteFile(objectName);
        } catch (RuntimeException ignored) {
            // Compensation cleanup must not hide the original business failure.
        }
    }

    /**
     * 数据库事务提交后再删除对象存储文件，避免数据库回滚但物理文件已被提前删除。
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

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
