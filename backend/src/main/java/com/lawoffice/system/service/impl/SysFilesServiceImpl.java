package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.dto.BusinessDocumentAccessContext;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFileVersion;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.RoleMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.SysFileAclMapper;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFileVersionMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.system.req.DocumentBatchDeleteReq;
import com.lawoffice.system.req.DocumentBatchMoveReq;
import com.lawoffice.system.req.DocumentCopyReq;
import com.lawoffice.system.req.DocumentFolderReq;
import com.lawoffice.system.req.DocumentMoveReq;
import com.lawoffice.system.req.DocumentPageReq;
import com.lawoffice.system.req.DocumentRenameReq;
import com.lawoffice.system.req.DocumentShareReq;
import com.lawoffice.system.req.DocumentShareTargetReq;
import com.lawoffice.system.req.DocumentTreeBatchReq;
import com.lawoffice.system.req.DocumentTreePrefetchReq;
import com.lawoffice.system.req.DocumentUploadReq;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.service.IBusinessDocumentProvider;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.DocumentShareVO;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.SysFilesVO;
import com.lawoffice.util.EntityFillUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysFilesServiceImpl extends BaseServiceImpl<SysFilesMapper, SysFiles, SysFilesVO> implements ISysFilesService {

    private static final String DEFAULT_STORE_TYPE = "minio";
    private static final String DOCUMENT_STORE_TYPE = "manage";
    private static final String TENANT_SHARED_STORE_TYPE = "tenant_shared";
    private static final String DEPART_SHARED_STORE_TYPE = "depart_shared";
    private static final String SHARED_VIEW_STORE_TYPE = "shared_view";
    private static final String SHARED_OWNER_VIEW_STORE_TYPE = "shared_owner_view";
    private static final String SHARED_BY_ME_STORE_TYPE = "shared_by_me";
    private static final String BUSINESS_VIEW_STORE_TYPE = "business_view";
    private static final String BUSINESS_MODULE_VIEW_STORE_TYPE = "business_module_view";
    private static final String BUSINESS_RECORD_VIEW_STORE_TYPE = "business_record_view";
    private static final String SHARED_OWNER_PREFIX = "so:";
    private static final String BUSINESS_MODULE_PREFIX = "bm:";
    private static final String BUSINESS_RECORD_PREFIX = "br:";
    private static final Integer DEFAULT_RELATION_TYPE = 1;
    private static final String PERSONAL_SHARED_RELATION_PREFIX = "document_shared:";
    private static final String PERSONAL_BUSINESS_RELATION_PREFIX = "document_business:";
    private static final String DEPART_SHARED_RELATION_BIZ_TYPE = "document_depart_shared";
    private static final Set<String> BUSINESS_DOCUMENT_EXCLUDED_BIZ_TYPES = Set.of("user-avatar");
    private static final Integer PERSONAL_SHARED_RELATION_TYPE = 2;
    private static final Integer PERSONAL_BUSINESS_RELATION_TYPE = 3;
    private static final Integer DEPART_SHARED_RELATION_TYPE = 4;
    private static final String FOLDER_TYPE = "folder";
    private static final String FLAG_YES = "1";
    private static final String FLAG_NO = "0";
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MY = "my";
    private static final String SCOPE_STARRED = "starred";
    private static final String SCOPE_BUSINESS = "business";
    private static final String SCOPE_SHARED = "shared";
    private static final String SCOPE_SHARED_BY_ME = "sharedByMe";
    private static final String SCOPE_TRASH = "trash";
    private static final String TARGET_USER = "user";
    private static final String TARGET_DEPART = "depart";
    private static final String TARGET_ROLE = "role";
    private static final String TARGET_TENANT = "tenant";
    private static final String ALL_ACLS_CACHE_KEY = "__all__";
    private static final String PERMISSION_READ = "read";
    private static final String PERMISSION_DOWNLOAD = "download";
    private static final String PERMISSION_UPDATE = "update";
    private static final String PERMISSION_MANAGE = "manage";
    private static final String VERSION_TYPE_UPLOAD = "upload";
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
    private static final Set<String> INITIAL_HISTORY_EXTENSIONS = Set.of(
            "doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx"
    );
    private static final int TREE_PREFETCH_PARENT_LIMIT = 100;
    private static final int TREE_PREFETCH_PAGE_SIZE = 500;
    private static final int TREE_PREFETCH_MAX_PAGE_NUM = 20;
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
    private final SysFileAclMapper fileAclMapper;
    private final UserMapper userMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserTenantMapper userTenantMapper;
    private final TenantMapper tenantMapper;
    private final SysDepartMapper sysDepartMapper;
    private final RoleMapper roleMapper;
    private final SysFileVersionMapper sysFileVersionMapper;
    private final List<IBusinessDocumentProvider> businessDocumentProviders;
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

    @Override
    public PageVO<DocumentFileVO> pageDocuments(String username, DocumentPageReq req) {
        DocumentPageReq pageReq = req == null ? new DocumentPageReq() : req;
        String tenantId = requireTenantId();
        UserAccessContext context = buildUserAccessContext(username, tenantId);
        return pageDocuments(context, pageReq);
    }

    private PageVO<DocumentFileVO> pageDocuments(UserAccessContext context, DocumentPageReq pageReq) {
        String scope = normalizeScope(pageReq.getScope());
        if (isSharedInboxRequest(pageReq, scope)) {
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

        if (isSharedTargetScope(pageReq, scope)) {
            return pageSharedTargetDocuments(context, pageReq);
        }

        if (SCOPE_SHARED_BY_ME.equals(scope) && !StringUtils.hasText(pageReq.getParentId())) {
            return pageSharedByMeDocuments(context, pageReq);
        }

        if (SCOPE_SHARED.equals(scope) && StringUtils.hasText(pageReq.getParentId())) {
            SysFiles parent = getActiveFile(pageReq.getParentId());
            int inheritedRank = resolveSharedDocumentPermissionRank(parent, context);
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
        List<DocumentFileVO> records = buildDocumentVOList(result.getRecords(), context);
        fillFolderChildFlags(records, context);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public Map<String, List<DocumentFileVO>> batchLoadDocumentFolderTree(String username, DocumentTreeBatchReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return Collections.emptyMap();
        }
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
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
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
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
            UserAccessContext context,
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
        validateUploadFile(file);
        String tenantId = requireTenantId();
        UserAccessContext context = buildUserAccessContext(username, tenantId);
        String scope = normalizeScope(req == null ? null : req.getScope());
        if (SCOPE_BUSINESS.equals(scope)) {
            throw new IllegalArgumentException("业务文档需从业务模块上传");
        }
        if (isSharedInboxRequest(req, scope)) {
            throw new IllegalArgumentException("共享给我的文件夹不支持上传文件");
        }
        String parentId = trimToNull(req == null ? null : req.getParentId());
        SharedTargetContext sharedTarget = resolveSharedTargetContext(
                context,
                req == null ? null : req.getShareTargetType(),
                req == null ? null : req.getShareTargetId(),
                false);
        String storeType = resolveDocumentStoreType(scope, sharedTarget);
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            assertCanManageDocument(parent, context);
            validateSharedSpaceParent(parent, sharedTarget);
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
            fileEntity.setFileName(resolveFileName(file));
            fileEntity.setUrl(objectName);
            fileEntity.setFileType(resolveFileType(file));
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
            bindDepartSharedRootIfNeeded(fileEntity, sharedTarget);
            return buildDocumentVO(fileEntity, context);
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
        UserAccessContext context = buildUserAccessContext(username, tenantId);
        String scope = normalizeScope(req.getScope());
        boolean sharedInbox = isSharedInboxRequest(req, scope);
        boolean businessScope = SCOPE_BUSINESS.equals(scope);
        if (businessScope) {
            throw new IllegalArgumentException("业务文档只允许查看，不支持新建文件夹");
        }
        String parentId = trimToNull(req.getParentId());
        SharedTargetContext sharedTarget = resolveSharedTargetContext(
                context,
                req.getShareTargetType(),
                req.getShareTargetId(),
                false);
        String storeType = sharedInbox ? SHARED_VIEW_STORE_TYPE : resolveDocumentStoreType(scope, sharedTarget);
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            assertCanManageDocument(parent, context);
            validateSharedSpaceParent(parent, sharedTarget);
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
        bindDepartSharedRootIfNeeded(folder, sharedTarget);
        return buildDocumentVO(folder, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO renameDocument(String username, DocumentRenameReq req) {
        SysFiles file = getActiveFile(req.getId());
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        assertCanManageDocument(file, context);
        assertNotBusinessReadonlyDocument(file);
        file.setFileName(trimToNull(req.getFileName()));
        fillUpdate(file, username);
        baseMapper.updateById(file);
        return buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO moveDocument(String username, DocumentMoveReq req) {
        SysFiles file = getActiveFile(req.getId());
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        String parentId = trimToNull(req.getParentId());
        String scope = normalizeScope(req.getScope());
        SharedTargetContext sharedTarget = resolveSharedTargetContext(
                context,
                req.getShareTargetType(),
                req.getShareTargetId(),
                false);
        if (SCOPE_BUSINESS.equals(scope)) {
            throw new IllegalArgumentException("业务文档只允许查看，不支持移动或归类");
        }
        assertNotBusinessReadonlyDocument(file);
        if (isSharedInboxRequest(req, scope) && !Objects.equals(file.getCreateBy(), username)) {
            moveSharedInboxPlacement(context, file, parentId);
            return buildDocumentVO(file, context);
        }
        assertCanManageDocument(file, context);
        if (Objects.equals(file.getId(), parentId)) {
            throw new IllegalArgumentException("不能移动到自身下");
        }
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            assertCanManageDocument(parent, context);
            validateSharedSpaceParent(parent, sharedTarget);
            if (!FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("目标必须是文件夹");
            }
            if (SHARED_VIEW_STORE_TYPE.equals(file.getStoreType())
                    && !SHARED_VIEW_STORE_TYPE.equals(parent.getStoreType())) {
                throw new IllegalArgumentException("共享给我的整理文件夹只能移动到个人整理目录下");
            }
            validateNotMoveToDescendant(file.getId(), parentId);
        }
        validateSharedSpaceMember(file, sharedTarget);
        updateDocumentParent(file, parentId, username);
        updateSharedSpaceStoreType(file, parentId, sharedTarget, username);
        bindDepartSharedRootIfNeeded(file, sharedTarget);
        return buildDocumentVO(file, context);
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
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        CopyTarget copyTarget = resolveCopyTarget(context, req);
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
                validateNotMoveToDescendant(source.getId(), copyTarget.parentId());
            }
            SysFiles copied = copyDocumentTree(context, source, copyTarget.parentId(), copyTarget.storeType());
            bindDepartSharedRootIfNeeded(copied, copyTarget.sharedTarget());
            copiedFiles.add(buildDocumentVO(copied, context));
        }
        return copiedFiles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String username, String fileId) {
        SysFiles file = getActiveFile(fileId);
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        assertCanManageDocument(file, context);
        assertDocumentCanBeDeleted(file);
        softDeleteDocumentTree(file, username);
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
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        assertCanManageDocument(file, context);
        if (StringUtils.hasText(file.getParentId())) {
            SysFiles parent = getFileIncludingDeleted(file.getParentId());
            if (parent != null && Objects.equals(parent.getDeleteFlag(), 1)) {
                throw new IllegalArgumentException("请先恢复父级文件夹");
            }
        }
        restoreDocumentTree(file, username);
        SysFiles restored = getActiveFile(fileId);
        return buildDocumentVO(restored, context);
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
        assertCanManageDocument(file, buildUserAccessContext(username, requireTenantId()));
        if (!Objects.equals(file.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("只能彻底删除回收站中的文档");
        }
        assertDocumentCanBeDeleted(file);
        hardDeleteDocumentTree(file);
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
                .filter(file -> !hasActiveBusinessRelation(file.getId(), file.getTenantId()))
                .forEach(this::hardDeleteDocumentTree);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO toggleDocumentStar(String username, String fileId) {
        SysFiles file = getActiveFile(fileId);
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        assertCanManageDocument(file, context);
        assertNotBusinessReadonlyDocument(file);
        file.setIzStar(FLAG_YES.equals(file.getIzStar()) ? FLAG_NO : FLAG_YES);
        fillUpdate(file, username);
        baseMapper.updateById(file);
        return buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentShareVO> shareDocument(String username, DocumentShareReq req) {
        if (req == null) {
            throw new IllegalArgumentException("共享信息不能为空");
        }
        SysFiles file = getActiveFile(req.getFileId());
        String tenantId = requireTenantId();
        assertCanManageDocument(file, buildUserAccessContext(username, tenantId));
        assertNotBusinessReadonlyDocument(file);
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<SysFileAcl> deleteWrapper = Wrappers.lambdaUpdate(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, tenantId)
                .eq(SysFileAcl::getFileId, file.getId())
                .eq(SysFileAcl::getDeleteFlag, 0)
                .set(SysFileAcl::getDeleteFlag, 1)
                .set(SysFileAcl::getDeleteTime, now)
                .set(SysFileAcl::getDeleteBy, username);
        fileAclMapper.update(null, deleteWrapper);

        Set<String> seenTargets = new HashSet<>();
        for (DocumentShareTargetReq target : req.getTargets()) {
            validateShareTarget(target, tenantId);
            String targetKey = target.getTargetType() + ":" + target.getTargetId();
            if (!seenTargets.add(targetKey)) {
                continue;
            }
            SysFileAcl acl = new SysFileAcl();
            acl.setId(newId());
            acl.setTenantId(tenantId);
            acl.setFileId(file.getId());
            acl.setTargetType(target.getTargetType());
            acl.setTargetId(target.getTargetId());
            acl.setPermission(StringUtils.hasText(target.getPermission()) ? target.getPermission() : PERMISSION_DOWNLOAD);
            acl.setExpireTime(req.getExpireTime());
            acl.setCreateBy(username);
            acl.setCreateTime(now);
            acl.setDeleteFlag(0);
            fileAclMapper.insert(acl);
        }

        file.setSharePerms(seenTargets.isEmpty() ? "1" : "2");
        file.setEnableDown(normalizeBinaryFlag(req.getEnableDown(), file.getEnableDown(), FLAG_YES));
        file.setEnableUpdat(normalizeBinaryFlag(req.getEnableUpdat(), file.getEnableUpdat(), FLAG_NO));
        fillUpdate(file, username);
        baseMapper.updateById(file);
        return listDocumentShares(username, file.getId());
    }

    @Override
    public List<DocumentShareVO> listDocumentShares(String username, String fileId) {
        SysFiles file = getActiveFile(fileId);
        assertCanManageDocument(file, buildUserAccessContext(username, requireTenantId()));
        List<SysFileAcl> acls = fileAclMapper.selectList(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, requireTenantId())
                .eq(SysFileAcl::getFileId, fileId)
                .eq(SysFileAcl::getDeleteFlag, 0)
                .orderByAsc(SysFileAcl::getTargetType, SysFileAcl::getCreateTime));
        return acls.stream()
                .map(this::buildDocumentShareVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeDocumentShare(String username, String aclId) {
        SysFileAcl acl = fileAclMapper.selectOne(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getId, aclId)
                .eq(SysFileAcl::getTenantId, requireTenantId())
                .eq(SysFileAcl::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (acl == null) {
            throw new IllegalArgumentException("共享记录不存在");
        }
        SysFiles file = getActiveFile(acl.getFileId());
        assertCanManageDocument(file, buildUserAccessContext(username, requireTenantId()));
        EntityFillUtils.fillDeleteFields(acl, username);
        fileAclMapper.updateById(acl);
        if (!hasActiveAcl(file.getId(), file.getTenantId())) {
            file.setSharePerms("1");
            fillUpdate(file, username);
            baseMapper.updateById(file);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO checkDocumentDownload(String fileId, String username) {
        SysFiles file = getReadableDocumentFile(fileId, username);
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        if (FLAG_YES.equals(file.getIzFolder())) {
            throw new IllegalArgumentException("文件夹不能下载");
        }
        if (!canDownload(file, context)) {
            throw new IllegalArgumentException("无权下载该文件");
        }
        file.setDownCount((file.getDownCount() == null ? 0 : file.getDownCount()) + 1);
        baseMapper.updateById(file);
        return buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO checkDocumentPreview(String fileId, String username) {
        DocumentReadAccess access = checkDocumentReadAccess(fileId, username);
        SysFiles file = access.file();
        file.setReadCount((file.getReadCount() == null ? 0 : file.getReadCount()) + 1);
        baseMapper.updateById(file);
        return buildDocumentVO(file, access.context());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFileVO checkDocumentRead(String fileId, String username) {
        DocumentReadAccess access = checkDocumentReadAccess(fileId, username);
        return buildDocumentVO(access.file(), access.context());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFileVO checkDocumentEdit(String fileId, String username) {
        DocumentReadAccess access = checkDocumentReadAccess(fileId, username);
        if (Objects.equals(access.file().getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("回收站中的文档不允许编辑");
        }
        assertNotBusinessReadonlyDocument(access.file());
        if (!canUpdate(access.file(), access.context())) {
            throw new IllegalArgumentException("无权编辑该文档");
        }
        return buildDocumentVO(access.file(), access.context());
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
        DocumentReadAccess access = checkDocumentReadAccess(fileId, username);
        SysFiles file = access.file();
        if (Objects.equals(file.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("回收站中的文档不允许编辑");
        }
        assertNotBusinessReadonlyDocument(file);
        if (!canUpdate(file, access.context())) {
            throw new IllegalArgumentException("无权保存该文档");
        }
        if (!StringUtils.hasText(file.getUrl())) {
            throw new IllegalArgumentException("文档内容不存在，无法保存");
        }
        String safeContentType = StringUtils.hasText(contentType)
                ? contentType.split(";", 2)[0].trim()
                : "application/octet-stream";
        minioUtils.replaceFile(file.getUrl(), inputStream, safeContentType);
        if (contentLength != null && contentLength > 0) {
            file.setFileSize(contentLength / 1024.0);
        }
        fillUpdate(file, username);
        baseMapper.updateById(file);
    }

    private DocumentReadAccess checkDocumentReadAccess(String fileId, String username) {
        SysFiles file = getReadableDocumentFile(fileId, username);
        UserAccessContext context = buildUserAccessContext(username, requireTenantId());
        if (FLAG_YES.equals(file.getIzFolder())) {
            throw new IllegalArgumentException("文件夹不能预览");
        }
        assertCanViewDocument(file, context);
        return new DocumentReadAccess(file, context);
    }

    private SysFiles getReadableDocumentFile(String fileId, String username) {
        SysFiles file = getFileIncludingDeleted(fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或已删除");
        }
        if (Objects.equals(file.getDeleteFlag(), 1)) {
            assertOwner(file, username);
        }
        return file;
    }

    private PageVO<DocumentFileVO> pageDocumentChildrenByParent(
            UserAccessContext context,
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
        List<DocumentFileVO> records = buildDocumentVOList(result.getRecords(), context);
        fillFolderChildFlags(records, context);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private PageVO<DocumentFileVO> pageSharedFolderChildren(
            UserAccessContext context,
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
        boolean inheritedDownload = inheritedRank >= permissionRank(PERMISSION_DOWNLOAD)
                && !FLAG_NO.equals(parent.getEnableDown());
        boolean inheritedUpdate = inheritedRank >= permissionRank(PERMISSION_UPDATE)
                && !FLAG_NO.equals(parent.getEnableUpdat());
        List<DocumentFileVO> records = result.getRecords().stream()
                .map(file -> buildSharedFolderChildVO(file, context, inheritedDownload, inheritedUpdate))
                .toList();
        fillFolderChildFlags(records, context);
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 回收站保持原文件夹层级：根目录只展示已删除树的顶层节点，进入已删除文件夹后再展示直接子级。
     */
    private PageVO<DocumentFileVO> pageTrashDocuments(UserAccessContext context, DocumentPageReq pageReq) {
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
            List<DocumentFileVO> records = buildDocumentVOList(result.getRecords(), context);
            fillFolderChildFlags(records, context);
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
    private PageVO<DocumentFileVO> pageStarredDocuments(UserAccessContext context, DocumentPageReq pageReq) {
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

    private PageVO<DocumentFileVO> pageSharedInboxDocuments(UserAccessContext context, DocumentPageReq pageReq) {
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            if (isSharedOwnerVirtualId(parentId)) {
                return pageSharedOwnerDocuments(context, pageReq, parseSharedOwner(parentId));
            }
            SysFiles parent = getActiveFile(parentId);
            if (isPersonalSharedFolder(parent, context.username())) {
                return pagePersonalSharedFolderChildren(context, pageReq, parentId);
            }
            int inheritedRank = resolveSharedDocumentPermissionRank(parent, context);
            return pageSharedFolderChildren(context, pageReq, parent, inheritedRank);
        }

        List<SysFiles> personalFolders = selectPersonalSharedFolders(context, null);
        List<String> fileIds = filterSharedRootFileIds(findSharedFileIds(context, pageReq), context);
        Set<String> placedFileIds = findPersonalSharedPlacedFileIds(context);
        fileIds = fileIds.stream()
                .filter(fileId -> !placedFileIds.contains(fileId))
                .toList();
        List<SysFiles> sharedFiles = selectActiveFilesByIds(context, fileIds).stream()
                .filter(file -> !Objects.equals(file.getCreateBy(), context.username()))
                .filter(file -> matchesSharedOwnerSourceFilters(file, pageReq))
                .toList();
        List<SysFiles> sharedOwnerFolders = buildSharedOwnerFolders(context, sharedFiles);
        List<SysFiles> folders = new ArrayList<>(personalFolders);
        folders.addAll(sharedOwnerFolders);
        return pageCombinedDocuments(context, pageReq, folders, Collections.emptyList());
    }

    private PageVO<DocumentFileVO> pageSharedOwnerDocuments(
            UserAccessContext context,
            DocumentPageReq pageReq,
            String owner) {
        if (!StringUtils.hasText(owner)) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        Set<String> placedFileIds = findPersonalSharedPlacedFileIds(context);
        List<String> fileIds = filterSharedRootFileIds(findSharedFileIds(context, pageReq), context).stream()
                .filter(fileId -> !placedFileIds.contains(fileId))
                .toList();
        List<SysFiles> sharedFiles = selectActiveFilesByIds(context, fileIds, pageReq.getFolderOnly()).stream()
                .filter(file -> !Objects.equals(file.getCreateBy(), context.username()))
                .filter(file -> Objects.equals(file.getCreateBy(), owner))
                .toList();
        return pageCombinedDocuments(context, pageReq, Collections.emptyList(), sharedFiles);
    }

    private PageVO<DocumentFileVO> pageSharedByMeDocuments(UserAccessContext context, DocumentPageReq pageReq) {
        List<String> sharedIds = filterSharedRootFileIds(findFileIdsSharedByOwner(context), context);
        List<SysFiles> sharedByMeFolders = selectSharedByMeFolders(context, null);
        List<SysFiles> sharedFiles = sharedIds.isEmpty()
                ? Collections.emptyList()
                : selectActiveFilesByIds(context, sharedIds, pageReq.getFolderOnly());
        return pageCombinedDocuments(context, pageReq, sharedByMeFolders, sharedFiles);
    }

    private PageVO<DocumentFileVO> pageSharedTargetDocuments(UserAccessContext context, DocumentPageReq pageReq) {
        SharedTargetContext sharedTarget = resolveSharedTargetContext(
                context,
                pageReq.getShareTargetType(),
                pageReq.getShareTargetId(),
                true);
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            assertCanViewSharedSpace(parent, context);
            validateSharedSpaceParent(parent, sharedTarget);
            return pageDocumentChildrenByParent(context, pageReq, parentId);
        }
        List<SysFiles> sharedFiles = selectSharedSpaceRootFiles(context, sharedTarget, pageReq.getFolderOnly());
        return pageCombinedDocuments(context, pageReq, Collections.emptyList(), sharedFiles);
    }

    private PageVO<DocumentFileVO> pageBusinessDocuments(UserAccessContext context, DocumentPageReq pageReq) {
        String parentId = trimToNull(pageReq.getParentId());
        if (StringUtils.hasText(parentId)) {
            if (isBusinessModuleVirtualId(parentId)) {
                return pageBusinessModuleRecords(context, pageReq, parseBusinessModuleBizType(parentId));
            }
            if (isBusinessRecordVirtualId(parentId)) {
                return pageBusinessRecordChildren(context, pageReq, parseBusinessRecordNode(parentId));
            }
            SysFiles parent = getActiveFile(parentId);
            assertOwner(parent, context.username());
            if (!isBusinessFolder(parent, context.username())) {
                throw new IllegalArgumentException("只能打开业务文档归类文件夹");
            }
            return pageBusinessFolderChildren(context, pageReq, parentId);
        }

        List<SysFiles> modules = findAccessibleBusinessRelations(context).stream()
                .map(SysFileRelation::getBizType)
                .filter(StringUtils::hasText)
                .distinct()
                .map(bizType -> buildBusinessVirtualFolder(
                        context,
                        businessModuleId(bizType),
                        resolveBusinessModuleName(bizType),
                        BUSINESS_MODULE_VIEW_STORE_TYPE,
                        null))
                .toList();
        return pageCombinedDocuments(context, pageReq, modules, Collections.emptyList());
    }

    private PageVO<DocumentFileVO> pageBusinessModuleRecords(
            UserAccessContext context,
            DocumentPageReq pageReq,
            String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        List<String> bizIds = findAccessibleBusinessRelations(context).stream()
                .filter(relation -> Objects.equals(relation.getBizType(), bizType))
                .map(SysFileRelation::getBizId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, String> recordNames = resolveBusinessRecordNames(bizType, bizIds, context);
        List<SysFiles> records = bizIds.stream()
                .map(bizId -> buildBusinessVirtualFolder(
                        context,
                        businessRecordId(bizType, bizId),
                        recordNames.getOrDefault(bizId, bizType + "-" + bizId),
                        BUSINESS_RECORD_VIEW_STORE_TYPE,
                        businessModuleId(bizType)))
                .toList();
        return pageCombinedDocuments(context, pageReq, records, Collections.emptyList());
    }

    private PageVO<DocumentFileVO> pageBusinessRecordChildren(
            UserAccessContext context,
            DocumentPageReq pageReq,
            BusinessRecordNode recordNode) {
        if (recordNode == null || !StringUtils.hasText(recordNode.bizType()) || !StringUtils.hasText(recordNode.bizId())) {
            return pageCombinedDocuments(context, pageReq, Collections.emptyList(), Collections.emptyList());
        }
        String virtualParentId = businessRecordId(recordNode.bizType(), recordNode.bizId());
        List<SysFiles> personalFolders = selectBusinessFolders(context, virtualParentId);
        Set<String> placedFileIds = findPersonalBusinessPlacedFileIds(context);
        List<String> fileIds = findAccessibleBusinessRelations(context).stream()
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

    private PageVO<DocumentFileVO> pageBusinessFolderChildren(
            UserAccessContext context,
            DocumentPageReq pageReq,
            String parentId) {
        List<SysFiles> personalFolders = selectBusinessFolders(context, parentId);
        List<String> fileIds = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, personalBusinessRelationBizType(context))
                        .eq(SysFileRelation::getBizId, parentId)
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<SysFiles> businessFiles = selectActiveFilesByIds(context, fileIds, pageReq.getFolderOnly()).stream()
                .filter(file -> hasBusinessDocumentAccess(file, context))
                .toList();
        return pageCombinedDocuments(context, pageReq, personalFolders, businessFiles);
    }

    private PageVO<DocumentFileVO> pagePersonalSharedFolderChildren(
            UserAccessContext context,
            DocumentPageReq pageReq,
            String parentId) {
        List<SysFiles> personalFolders = selectPersonalSharedFolders(context, parentId);
        List<String> fileIds = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(SysFileRelation::getFileId)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getBizType, personalSharedRelationBizType(context))
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
                        assertCanViewDocument(file, context);
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
            UserAccessContext context,
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
        List<DocumentFileVO> records = buildDocumentVOList(combined.subList(fromIndex, toIndex), context);
        fillFolderChildFlags(records, context);
        return new PageVO<>(records, total, current, size);
    }

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

    private boolean matchesDocumentFilters(SysFiles file, DocumentPageReq req) {
        if (Boolean.TRUE.equals(req.getFolderOnly()) && !FLAG_YES.equals(file.getIzFolder())) {
            return false;
        }
        if (isSharedOwnerVirtualFolder(file)) {
            return true;
        }
        if (StringUtils.hasText(req.getKeyword())
                && !String.valueOf(file.getFileName()).contains(req.getKeyword().trim())) {
            return false;
        }
        return !StringUtils.hasText(req.getFileType())
                || Objects.equals(file.getFileType(), req.getFileType().trim());
    }

    private boolean matchesSharedOwnerSourceFilters(SysFiles file, DocumentPageReq req) {
        if (StringUtils.hasText(req.getKeyword())
                && !String.valueOf(file.getFileName()).contains(req.getKeyword().trim())) {
            return false;
        }
        return !StringUtils.hasText(req.getFileType())
                || Objects.equals(file.getFileType(), req.getFileType().trim());
    }

    private List<SysFiles> selectPersonalSharedFolders(UserAccessContext context, String parentId) {
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

    private List<SysFiles> selectSharedByMeFolders(UserAccessContext context, String parentId) {
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

    private List<SysFiles> selectBusinessFolders(UserAccessContext context, String parentId) {
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

    private List<SysFiles> selectActiveFilesByIds(UserAccessContext context, List<String> fileIds) {
        return selectActiveFilesByIds(context, fileIds, false);
    }

    private List<SysFiles> selectActiveFilesByIds(UserAccessContext context, List<String> fileIds, Boolean folderOnly) {
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

    private void applyDocumentScope(
            LambdaQueryWrapper<SysFiles> wrapper,
            UserAccessContext context,
            DocumentPageReq req,
            String scope) {
        wrapper.eq(SysFiles::getDeleteFlag, 0);
        if (SCOPE_ALL.equals(scope)) {
            List<String> sharedFileIds = findSharedFileIdsWithDescendants(context);
            List<String> businessFileIds = findAccessibleBusinessFileIds(context);
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
            if (isSharedTargetScope(req, scope)) {
                SharedTargetContext sharedTarget = resolveSharedTargetContext(
                        context,
                        req.getShareTargetType(),
                        req.getShareTargetId(),
                        true);
                List<String> fileIds = selectSharedSpaceRootFiles(context, sharedTarget, req.getFolderOnly()).stream()
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
        List<String> businessFileIds = findActiveBusinessFileIds(context);
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

    private List<String> findSharedFileIds(UserAccessContext context, DocumentPageReq req) {
        List<SysFileAcl> acls = selectActiveAclsForContext(null, context);
        return acls.stream()
                .filter(acl -> matchesSharedTargetFilter(acl, context, req))
                .map(SysFileAcl::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> findSharedFileIdsWithDescendants(UserAccessContext context) {
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

    private List<String> findAccessibleSharedSpaceFileIds(UserAccessContext context) {
        LinkedHashSet<String> fileIds = new LinkedHashSet<>();
        SharedTargetContext tenantTarget = new SharedTargetContext(TARGET_TENANT, context.tenantId());
        selectSharedSpaceRootFiles(context, tenantTarget, false).stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .forEach(fileIds::add);
        for (String departId : context.departIds()) {
            SharedTargetContext departTarget = new SharedTargetContext(TARGET_DEPART, departId);
            selectSharedSpaceRootFiles(context, departTarget, false).stream()
                    .map(SysFiles::getId)
                    .filter(StringUtils::hasText)
                    .forEach(fileIds::add);
        }
        collectDescendantFileIds(context.tenantId(), fileIds);
        return new ArrayList<>(fileIds);
    }

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
     * 业务文档只识别业务模块创建的附件关系，排除文档中心自己的虚拟归类关系。
     */
    private List<String> findActiveBusinessFileIds(UserAccessContext context) {
        return findActiveBusinessRelations(context)
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<SysFileRelation> findActiveBusinessRelations(UserAccessContext context) {
        if (context.cache().activeBusinessRelations != null) {
            return context.cache().activeBusinessRelations;
        }
        context.cache().activeBusinessRelations = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(
                                SysFileRelation::getFileId,
                                SysFileRelation::getBizType,
                                SysFileRelation::getBizId,
                                SysFileRelation::getRelationType)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .filter(this::isBusinessRelation)
                .toList();
        return context.cache().activeBusinessRelations;
    }

    private List<String> findAccessibleBusinessFileIds(UserAccessContext context) {
        return findAccessibleBusinessRelations(context).stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<SysFileRelation> findAccessibleBusinessRelations(UserAccessContext context) {
        if (context.cache().accessibleBusinessRelations != null) {
            return context.cache().accessibleBusinessRelations;
        }
        List<SysFileRelation> relations = findActiveBusinessRelations(context);
        if (relations.isEmpty()) {
            context.cache().accessibleBusinessRelations = Collections.emptyList();
            return context.cache().accessibleBusinessRelations;
        }
        List<String> fileIds = relations.stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, SysFiles> fileMap = selectActiveFilesByIds(context, fileIds).stream()
                .collect(Collectors.toMap(SysFiles::getId, file -> file, (left, right) -> left));
        Map<String, Set<String>> accessibleBizIds = resolveAccessibleBusinessBizIds(relations, fileMap, context);
        context.cache().accessibleBusinessRelations = relations.stream()
                .filter(relation -> fileMap.containsKey(relation.getFileId()))
                .filter(relation -> accessibleBizIds.getOrDefault(relation.getBizType(), Collections.emptySet())
                        .contains(relation.getBizId()))
                .toList();
        return context.cache().accessibleBusinessRelations;
    }

    private Map<String, Set<String>> resolveAccessibleBusinessBizIds(
            List<SysFileRelation> relations,
            Map<String, SysFiles> fileMap,
            UserAccessContext context) {
        Map<String, Set<String>> bizIdsByType = new LinkedHashMap<>();
        for (SysFileRelation relation : relations) {
            if (relation == null
                    || !StringUtils.hasText(relation.getBizType())
                    || !StringUtils.hasText(relation.getBizId())
                    || !fileMap.containsKey(relation.getFileId())) {
                continue;
            }
            bizIdsByType.computeIfAbsent(relation.getBizType(), key -> new LinkedHashSet<>())
                    .add(relation.getBizId());
        }
        Map<String, Set<String>> accessibleBizIds = new LinkedHashMap<>();
        BusinessDocumentAccessContext accessContext = toBusinessDocumentAccessContext(context);
        for (Map.Entry<String, Set<String>> entry : bizIdsByType.entrySet()) {
            IBusinessDocumentProvider provider = findBusinessDocumentProvider(entry.getKey());
            if (provider == null) {
                accessibleBizIds.put(entry.getKey(), Collections.emptySet());
                continue;
            }
            Set<String> ids = provider.filterAccessibleBizIds(entry.getValue(), accessContext);
            accessibleBizIds.put(entry.getKey(), ids == null ? Collections.emptySet() : ids);
        }
        return accessibleBizIds;
    }

    private boolean hasBusinessRelationAccess(
            SysFileRelation relation,
            SysFiles file,
            UserAccessContext context) {
        if (relation == null || file == null) {
            return false;
        }
        IBusinessDocumentProvider provider = findBusinessDocumentProvider(relation.getBizType());
        return provider != null && provider.canAccess(relation.getBizId(), toBusinessDocumentAccessContext(context));
    }

    private boolean hasAccessibleBusinessRecord(BusinessRecordNode recordNode, UserAccessContext context) {
        if (recordNode == null
                || !StringUtils.hasText(recordNode.bizType())
                || !StringUtils.hasText(recordNode.bizId())) {
            return false;
        }
        return findAccessibleBusinessRelations(context).stream()
                .anyMatch(relation -> Objects.equals(relation.getBizType(), recordNode.bizType())
                        && Objects.equals(relation.getBizId(), recordNode.bizId()));
    }

    private boolean hasAccessibleBusinessRecordFile(
            String fileId,
            BusinessRecordNode recordNode,
            UserAccessContext context) {
        if (!StringUtils.hasText(fileId) || recordNode == null) {
            return false;
        }
        return findAccessibleBusinessRelations(context).stream()
                .anyMatch(relation -> Objects.equals(relation.getFileId(), fileId)
                        && Objects.equals(relation.getBizType(), recordNode.bizType())
                        && Objects.equals(relation.getBizId(), recordNode.bizId()));
    }

    private Set<String> findPersonalBusinessPlacedFileIds(UserAccessContext context) {
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

    private boolean hasActiveBusinessRelation(String fileId, String tenantId) {
        if (!StringUtils.hasText(fileId)) {
            return false;
        }
        return fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .eq(SysFileRelation::getTenantId, tenantId)
                        .eq(SysFileRelation::getFileId, fileId)
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .anyMatch(this::isBusinessRelation);
    }

    private boolean hasBusinessDocumentAccess(SysFiles file, UserAccessContext context) {
        if (file == null) {
            return false;
        }
        List<SysFileRelation> relations = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getFileId, file.getId())
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .filter(this::isBusinessRelation)
                .toList();
        if (relations.isEmpty()) {
            return false;
        }
        return relations.stream()
                .anyMatch(relation -> hasBusinessRelationAccess(relation, file, context));
    }

    /**
     * 关系表被文档中心复用了两类个人整理关系，因此这里按前缀和关系类型双保险排除。
     */
    private boolean isBusinessRelation(SysFileRelation relation) {
        if (relation == null) {
            return false;
        }
        String bizType = relation.getBizType();
        if (!StringUtils.hasText(bizType)
                || BUSINESS_DOCUMENT_EXCLUDED_BIZ_TYPES.contains(bizType)
                || DEPART_SHARED_RELATION_BIZ_TYPE.equals(bizType)
                || bizType.startsWith(PERSONAL_SHARED_RELATION_PREFIX)
                || bizType.startsWith(PERSONAL_BUSINESS_RELATION_PREFIX)) {
            return false;
        }
        Integer relationType = relation.getRelationType();
        return !Objects.equals(relationType, PERSONAL_SHARED_RELATION_TYPE)
                && !Objects.equals(relationType, PERSONAL_BUSINESS_RELATION_TYPE)
                && !Objects.equals(relationType, DEPART_SHARED_RELATION_TYPE);
    }

    private SysFiles buildBusinessVirtualFolder(
            UserAccessContext context,
            String id,
            String fileName,
            String storeType,
            String parentId) {
        SysFiles folder = new SysFiles();
        folder.setId(id);
        folder.setTenantId(context.tenantId());
        folder.setFileName(fileName);
        folder.setFileType(FOLDER_TYPE);
        folder.setStoreType(storeType);
        folder.setParentId(parentId);
        folder.setFileSize(0D);
        folder.setIzFolder(FLAG_YES);
        folder.setIzRootFolder(StringUtils.hasText(parentId) ? FLAG_NO : FLAG_YES);
        folder.setIzStar(FLAG_NO);
        folder.setDownCount(0);
        folder.setReadCount(0);
        folder.setEnableDown(FLAG_NO);
        folder.setEnableUpdat(FLAG_NO);
        folder.setDeleteFlag(0);
        return folder;
    }

    private List<SysFiles> buildSharedOwnerFolders(UserAccessContext context, List<SysFiles> sharedFiles) {
        if (sharedFiles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<SysFiles>> filesByOwner = sharedFiles.stream()
                .filter(file -> StringUtils.hasText(file.getCreateBy()))
                .collect(Collectors.groupingBy(
                        SysFiles::getCreateBy,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, String> displayNames = resolveUsernameDisplayNames(filesByOwner.keySet());
        return filesByOwner.entrySet().stream()
                .map(entry -> buildSharedOwnerVirtualFolder(
                        context,
                        entry.getKey(),
                        displayNames.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private SysFiles buildSharedOwnerVirtualFolder(
            UserAccessContext context,
            String owner,
            String ownerDisplayName,
            List<SysFiles> sharedFiles) {
        SysFiles folder = new SysFiles();
        folder.setId(sharedOwnerId(owner));
        folder.setTenantId(context.tenantId());
        folder.setFileName(ownerDisplayName + "的共享");
        folder.setFileType(FOLDER_TYPE);
        folder.setStoreType(SHARED_OWNER_VIEW_STORE_TYPE);
        folder.setFileSize(0D);
        folder.setIzFolder(FLAG_YES);
        folder.setIzRootFolder(FLAG_YES);
        folder.setIzStar(FLAG_NO);
        folder.setDownCount(0);
        folder.setReadCount(0);
        folder.setEnableDown(FLAG_NO);
        folder.setEnableUpdat(FLAG_NO);
        folder.setCreateBy(owner);
        LocalDateTime latestTime = sharedFiles.stream()
                .map(file -> file.getUpdateTime() != null ? file.getUpdateTime() : file.getCreateTime())
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        folder.setCreateTime(latestTime);
        folder.setUpdateTime(latestTime);
        folder.setDeleteFlag(0);
        return folder;
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

    private String sharedOwnerId(String owner) {
        return SHARED_OWNER_PREFIX + owner;
    }

    private boolean isSharedOwnerVirtualId(String id) {
        return StringUtils.hasText(id) && id.startsWith(SHARED_OWNER_PREFIX);
    }

    private String parseSharedOwner(String id) {
        if (!isSharedOwnerVirtualId(id)) {
            return null;
        }
        return id.substring(SHARED_OWNER_PREFIX.length());
    }

    private String resolveBusinessModuleName(String bizType) {
        IBusinessDocumentProvider provider = findBusinessDocumentProvider(bizType);
        return provider == null ? bizType : provider.moduleName();
    }

    private Map<String, String> resolveBusinessRecordNames(
            String bizType,
            Collection<String> bizIds,
            UserAccessContext context) {
        if (bizIds.isEmpty()) {
            return Collections.emptyMap();
        }
        IBusinessDocumentProvider provider = findBusinessDocumentProvider(bizType);
        if (provider != null) {
            Map<String, String> recordNames = provider.resolveRecordNames(
                    bizIds,
                    toBusinessDocumentAccessContext(context));
            if (recordNames != null && !recordNames.isEmpty()) {
                return recordNames;
            }
        }
        return bizIds.stream()
                .collect(Collectors.toMap(
                        bizId -> bizId,
                        bizId -> bizType + "-" + bizId,
                        (left, right) -> left));
    }

    private IBusinessDocumentProvider findBusinessDocumentProvider(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        return businessDocumentProviders.stream()
                .filter(provider -> Objects.equals(provider.bizType(), bizType))
                .findFirst()
                .orElse(null);
    }

    private BusinessDocumentAccessContext toBusinessDocumentAccessContext(UserAccessContext context) {
        return new BusinessDocumentAccessContext(
                context.username(),
                context.userId(),
                context.tenantId(),
                context.departIds(),
                context.roleIds());
    }

    private String businessModuleId(String bizType) {
        return BUSINESS_MODULE_PREFIX + bizType;
    }

    private String businessRecordId(String bizType, String bizId) {
        return BUSINESS_RECORD_PREFIX + bizType + ":" + bizId;
    }

    private boolean isBusinessModuleVirtualId(String id) {
        return StringUtils.hasText(id) && id.startsWith(BUSINESS_MODULE_PREFIX);
    }

    private boolean isBusinessRecordVirtualId(String id) {
        return StringUtils.hasText(id) && id.startsWith(BUSINESS_RECORD_PREFIX);
    }

    private String parseBusinessModuleBizType(String id) {
        if (!isBusinessModuleVirtualId(id)) {
            return null;
        }
        return id.substring(BUSINESS_MODULE_PREFIX.length());
    }

    private BusinessRecordNode parseBusinessRecordNode(String id) {
        if (!isBusinessRecordVirtualId(id)) {
            return null;
        }
        String value = id.substring(BUSINESS_RECORD_PREFIX.length());
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return new BusinessRecordNode(parts[0], parts[1]);
    }

    private Set<String> findPersonalSharedPlacedFileIds(UserAccessContext context) {
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

    /**
     * 共享根目录只展示最上层授权项；子级由打开被共享文件夹后再按 parent_id 查询。
     */
    private List<String> filterSharedRootFileIds(List<String> fileIds, UserAccessContext context) {
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

    private void validateSharedTargetFilter(DocumentPageReq req, UserAccessContext context) {
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

    private boolean matchesSharedTargetFilter(SysFileAcl acl, UserAccessContext context, DocumentPageReq req) {
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

    private SharedTargetContext resolveSharedTargetContext(
            UserAccessContext context,
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
            validateShareTarget(target, context.tenantId());
            return new SharedTargetContext(TARGET_TENANT, resolvedTargetId);
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
            validateShareTarget(target, context.tenantId());
            return new SharedTargetContext(TARGET_DEPART, targetId);
        }
        if (required) {
            throw new IllegalArgumentException("共享目标类型不正确");
        }
        return null;
    }

    private boolean isSharedTargetScope(DocumentPageReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && req != null
                && (TARGET_TENANT.equals(trimToNull(req.getShareTargetType()))
                || TARGET_DEPART.equals(trimToNull(req.getShareTargetType())));
    }

    private String resolveDocumentStoreType(String scope, SharedTargetContext sharedTarget) {
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

    private void validateSharedSpaceParent(SysFiles parent, SharedTargetContext sharedTarget) {
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

    private void validateSharedSpaceMember(SysFiles file, SharedTargetContext sharedTarget) {
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

    private boolean isSharedSpaceMember(SysFiles file, SharedTargetContext sharedTarget) {
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

    private void bindDepartSharedRootIfNeeded(SysFiles file, SharedTargetContext sharedTarget) {
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

    private void updateSharedSpaceStoreType(
            SysFiles file,
            String parentId,
            SharedTargetContext sharedTarget,
            String username) {
        String targetStoreType = sharedTarget == null
                ? DOCUMENT_STORE_TYPE
                : resolveDocumentStoreType(SCOPE_SHARED, sharedTarget);
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            targetStoreType = StringUtils.hasText(parent.getStoreType()) ? parent.getStoreType() : targetStoreType;
        }
        updateDocumentTreeStoreType(file, targetStoreType, username);
        if (StringUtils.hasText(parentId) || sharedTarget == null || !TARGET_DEPART.equals(sharedTarget.targetType())) {
            softDeleteDepartSharedRootRelations(file.getId(), file.getTenantId(), username);
        }
    }

    private void updateDocumentTreeStoreType(SysFiles file, String storeType, String username) {
        if (file == null || Objects.equals(file.getStoreType(), storeType)) {
            return;
        }
        file.setStoreType(storeType);
        fillUpdate(file, username);
        baseMapper.update(null, Wrappers.lambdaUpdate(SysFiles.class)
                .eq(SysFiles::getId, file.getId())
                .eq(SysFiles::getTenantId, file.getTenantId())
                .set(SysFiles::getStoreType, storeType)
                .set(SysFiles::getUpdateBy, file.getUpdateBy())
                .set(SysFiles::getUpdateTime, file.getUpdateTime()));
        for (SysFiles child : selectActiveChildren(file.getTenantId(), file.getId())) {
            updateDocumentTreeStoreType(child, storeType, username);
        }
    }

    private List<SysFiles> selectSharedSpaceRootFiles(
            UserAccessContext context,
            SharedTargetContext sharedTarget,
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
            return baseMapper.selectList(wrapper);
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
        return baseMapper.selectList(wrapper);
    }

    private void assertCanViewSharedSpace(SysFiles file, UserAccessContext context) {
        if (!hasSharedSpaceAccess(file, context)) {
            throw new IllegalArgumentException("无权访问该共享空间");
        }
    }

    private void assertCanManageDocument(SysFiles file, UserAccessContext context) {
        if (file != null && Objects.equals(file.getCreateBy(), context.username())) {
            return;
        }
        if (hasSharedSpaceAccess(file, context)) {
            return;
        }
        throw new IllegalArgumentException("无权管理该文档");
    }

    private boolean hasSharedSpaceAccess(SysFiles file, UserAccessContext context) {
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
            current = getFileIncludingDeleted(current.getParentId());
        }
        return current;
    }

    private void validateSharedTargetParent(SysFiles parent, SharedTargetContext sharedTarget) {
        if (sharedTarget == null) {
            return;
        }
        if (parent == null
                || (!hasActiveAclForTarget(parent.getId(), parent.getTenantId(), sharedTarget)
                && !hasSharedTargetAncestor(parent.getParentId(), parent.getTenantId(), sharedTarget))) {
            throw new IllegalArgumentException("目标文件夹不属于当前共享节点");
        }
    }

    private void validateSharedTargetMember(SysFiles file, SharedTargetContext sharedTarget) {
        if (sharedTarget == null || file == null || !StringUtils.hasText(file.getParentId())) {
            return;
        }
        if (!hasActiveAclForTarget(file.getId(), file.getTenantId(), sharedTarget)
                && !hasSharedTargetAncestor(file.getParentId(), file.getTenantId(), sharedTarget)) {
            throw new IllegalArgumentException("只能在当前共享节点内移动文件");
        }
    }

    private boolean hasSharedTargetAncestor(String parentId, String tenantId, SharedTargetContext sharedTarget) {
        String currentId = parentId;
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            if (hasActiveAclForTarget(currentId, tenantId, sharedTarget)) {
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

    private boolean hasActiveAclForTarget(String fileId, String tenantId, SharedTargetContext sharedTarget) {
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

    private void syncSharedTargetAcl(SysFiles file, SharedTargetContext sharedTarget) {
        if (file == null || sharedTarget == null || StringUtils.hasText(file.getParentId())) {
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
        baseMapper.updateById(file);
    }

    private List<String> findFileIdsSharedByOwner(UserAccessContext context) {
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

    private List<String> findFileIdsSharedByOwnerAndTarget(
            UserAccessContext context,
            SharedTargetContext sharedTarget) {
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

    private void assertSharedByMeFolderBrowsable(SysFiles folder, UserAccessContext context) {
        if (folder == null
                || !FLAG_YES.equals(folder.getIzFolder())
                || !Objects.equals(folder.getCreateBy(), context.username())
                || !Objects.equals(folder.getDeleteFlag(), 0)) {
            throw new IllegalArgumentException("无权浏览该共享文件夹");
        }
        if (SHARED_BY_ME_STORE_TYPE.equals(folder.getStoreType()) || hasActiveAcl(folder.getId(), context)) {
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
                    || hasActiveAcl(parent.getId(), context)) {
                return;
            }
            parentId = parent.getParentId();
        }
        throw new IllegalArgumentException("无权浏览该共享文件夹");
    }

    private List<DocumentFileVO> buildDocumentVOList(List<SysFiles> files, UserAccessContext context) {
        Set<String> sharedFileIds = findActiveAclFileIds(files.stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .toList(), context.tenantId());
        return files.stream()
                .map(file -> buildDocumentVO(file, context, sharedFileIds))
                .toList();
    }

    private Set<String> findActiveAclFileIds(Collection<String> fileIds, String tenantId) {
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

    private DocumentFileVO buildDocumentVO(SysFiles file, UserAccessContext context) {
        Set<String> sharedFileIds = StringUtils.hasText(file.getId()) && hasActiveAcl(file.getId(), context)
                ? Set.of(file.getId())
                : Collections.emptySet();
        return buildDocumentVO(file, context, sharedFileIds);
    }

    private DocumentFileVO buildDocumentVO(
            SysFiles file,
            UserAccessContext context,
            Set<String> sharedFileIds) {
        DocumentFileVO vo = buildBaseDocumentVO(file, context);
        if (isSharedOwnerVirtualFolder(file)) {
            vo.setOwnerFlag(false);
            vo.setSharedFlag(false);
            vo.setCanManage(false);
            vo.setCanDownload(false);
            vo.setCanUpdate(false);
            return vo;
        }
        vo.setSharedFlag(sharedFileIds.contains(file.getId()));
        vo.setCanManage(Boolean.TRUE.equals(vo.getOwnerFlag()) || hasSharedSpaceAccess(file, context));
        vo.setCanDownload(canDownload(file, context));
        vo.setCanUpdate(canUpdate(file, context));
        return vo;
    }

    private DocumentFileVO buildSharedFolderChildVO(
            SysFiles file,
            UserAccessContext context,
            boolean inheritedDownload,
            boolean inheritedUpdate) {
        DocumentFileVO vo = buildBaseDocumentVO(file, context);
        vo.setSharedFlag(false);
        vo.setCanManage(Boolean.TRUE.equals(vo.getOwnerFlag()) || hasSharedSpaceAccess(file, context));
        vo.setCanDownload(vo.getOwnerFlag() || inheritedDownload);
        vo.setCanUpdate(vo.getOwnerFlag() || inheritedUpdate);
        return vo;
    }

    private DocumentFileVO buildBaseDocumentVO(SysFiles file, UserAccessContext context) {
        DocumentFileVO vo = new DocumentFileVO();
        vo.setId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileType(file.getFileType());
        vo.setStoreType(file.getStoreType());
        vo.setParentId(file.getParentId());
        vo.setFileSize(file.getFileSize() == null ? 0L : Math.round(file.getFileSize() * 1024));
        vo.setIzFolder(file.getIzFolder());
        vo.setIzRootFolder(file.getIzRootFolder());
        vo.setIzStar(file.getIzStar());
        vo.setDownCount(file.getDownCount());
        vo.setReadCount(file.getReadCount());
        vo.setEnableDown(file.getEnableDown());
        vo.setEnableUpdat(file.getEnableUpdat());
        vo.setOwner(file.getCreateBy());
        vo.setOwnerFlag(Objects.equals(file.getCreateBy(), context.username()));
        vo.setHasChild(false);
        vo.setDeleteFlag(file.getDeleteFlag());
        vo.setDeleteTime(file.getDeleteTime());
        vo.setCreateBy(file.getCreateBy());
        vo.setCreateTime(file.getCreateTime());
        vo.setUpdateBy(file.getUpdateBy());
        vo.setUpdateTime(file.getUpdateTime());
        return vo;
    }

    private void fillFolderChildFlags(List<DocumentFileVO> records, UserAccessContext context) {
        List<DocumentFileVO> folders = records.stream()
                .filter(record -> FLAG_YES.equals(record.getIzFolder()))
                .filter(record -> StringUtils.hasText(record.getId()))
                .toList();
        if (folders.isEmpty()) {
            return;
        }

        Set<String> folderIds = folders.stream()
                .map(DocumentFileVO::getId)
                .collect(Collectors.toSet());
        Set<String> parentIdsWithChildren = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                        .select(SysFiles::getParentId)
                        .eq(SysFiles::getTenantId, context.tenantId())
                        .eq(SysFiles::getDeleteFlag, 0)
                        .eq(SysFiles::getIzFolder, FLAG_YES)
                        .in(SysFiles::getParentId, folderIds))
                .stream()
                .map(SysFiles::getParentId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<SysFileRelation> businessRelations = Collections.emptyList();
        if (folders.stream().anyMatch(this::isBusinessVirtualFolder)) {
            businessRelations = findAccessibleBusinessRelations(context);
        }
        for (DocumentFileVO folder : folders) {
            folder.setHasChild(parentIdsWithChildren.contains(folder.getId())
                    || isSharedOwnerVirtualFolder(folder)
                    || hasBusinessVirtualFolderChildren(folder, businessRelations));
        }
    }

    private boolean isSharedOwnerVirtualFolder(DocumentFileVO folder) {
        return SHARED_OWNER_VIEW_STORE_TYPE.equals(folder.getStoreType())
                && isSharedOwnerVirtualId(folder.getId());
    }

    private boolean isSharedOwnerVirtualFolder(SysFiles folder) {
        return SHARED_OWNER_VIEW_STORE_TYPE.equals(folder.getStoreType())
                && isSharedOwnerVirtualId(folder.getId());
    }

    private boolean isBusinessVirtualFolder(DocumentFileVO folder) {
        return BUSINESS_MODULE_VIEW_STORE_TYPE.equals(folder.getStoreType())
                || BUSINESS_RECORD_VIEW_STORE_TYPE.equals(folder.getStoreType());
    }

    private boolean hasBusinessVirtualFolderChildren(
            DocumentFileVO folder,
            List<SysFileRelation> businessRelations) {
        String folderId = folder.getId();
        if (BUSINESS_MODULE_VIEW_STORE_TYPE.equals(folder.getStoreType())) {
            String bizType = parseBusinessModuleBizType(folderId);
            return businessRelations.stream()
                    .anyMatch(relation -> Objects.equals(relation.getBizType(), bizType)
                            && StringUtils.hasText(relation.getBizId()));
        }
        if (BUSINESS_RECORD_VIEW_STORE_TYPE.equals(folder.getStoreType())) {
            return false;
        }
        return false;
    }

    private DocumentShareVO buildDocumentShareVO(SysFileAcl acl) {
        DocumentShareVO vo = new DocumentShareVO();
        vo.setId(acl.getId());
        vo.setFileId(acl.getFileId());
        vo.setTargetType(acl.getTargetType());
        vo.setTargetId(acl.getTargetId());
        vo.setTargetName(resolveTargetName(acl.getTargetType(), acl.getTargetId()));
        vo.setPermission(acl.getPermission());
        vo.setExpireTime(acl.getExpireTime());
        vo.setCreateBy(acl.getCreateBy());
        vo.setCreateTime(acl.getCreateTime());
        vo.setUpdateBy(acl.getUpdateBy());
        vo.setUpdateTime(acl.getUpdateTime());
        return vo;
    }

    private boolean hasActiveAcl(String fileId, String tenantId) {
        return fileAclMapper.selectCount(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, tenantId)
                .eq(SysFileAcl::getFileId, fileId)
                .eq(SysFileAcl::getDeleteFlag, 0)) > 0;
    }

    private boolean hasActiveAcl(String fileId, UserAccessContext context) {
        if (!StringUtils.hasText(fileId)) {
            return false;
        }
        return context.cache().activeAclFlags.computeIfAbsent(fileId, id -> hasActiveAcl(id, context.tenantId()));
    }

    private boolean canDownload(SysFiles file, UserAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return true;
        }
        if (hasSharedSpaceAccess(file, context)) {
            return true;
        }
        if (hasBusinessDocumentAccess(file, context)) {
            return true;
        }
        if (FLAG_NO.equals(file.getEnableDown())) {
            return false;
        }
        return resolvePermissionRank(file, context) >= permissionRank(PERMISSION_DOWNLOAD);
    }

    private boolean canUpdate(SysFiles file, UserAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return true;
        }
        if (hasSharedSpaceAccess(file, context)) {
            return true;
        }
        return resolveUpdatePermission(file, context);
    }

    private void assertCanViewDocument(SysFiles file, UserAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return;
        }
        if (hasSharedSpaceAccess(file, context)) {
            return;
        }
        if (hasBusinessDocumentAccess(file, context)) {
            return;
        }
        if (resolvePermissionRank(file, context) < permissionRank(PERMISSION_READ)) {
            throw new IllegalArgumentException("无权访问该文档");
        }
    }

    /**
     * 共享权限继承父级文件夹，保证被共享文件夹下的子文件无需逐条写 ACL。
     */
    private int resolveSharedDocumentPermissionRank(SysFiles file, UserAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return permissionRank(PERMISSION_MANAGE);
        }
        int rank = resolvePermissionRank(file, context);
        if (rank < permissionRank(PERMISSION_READ)) {
            throw new IllegalArgumentException("无权访问该文档");
        }
        return rank;
    }

    private int resolvePermissionRank(SysFiles file, UserAccessContext context) {
        int directRank = maxAclRank(file.getId(), context);
        if (directRank > 0) {
            return directRank;
        }
        String parentId = file.getParentId();
        int guard = 0;
        while (StringUtils.hasText(parentId) && guard++ < 20) {
            SysFiles parent = getFileIncludingDeleted(parentId);
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

    /**
     * 共享文件夹下的子文件继承父级 ACL，编辑开关也应以提供 ACL 的文件或文件夹为准。
     */
    private boolean resolveUpdatePermission(SysFiles file, UserAccessContext context) {
        int directRank = maxAclRank(file.getId(), context);
        if (directRank > 0) {
            return directRank >= permissionRank(PERMISSION_UPDATE)
                    && !FLAG_NO.equals(file.getEnableUpdat());
        }
        String parentId = file.getParentId();
        int guard = 0;
        while (StringUtils.hasText(parentId) && guard++ < 20) {
            SysFiles parent = getFileIncludingDeleted(parentId);
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

    private int maxAclRank(String fileId, UserAccessContext context) {
        return selectActiveAclsForContext(fileId, context).stream()
                .map(SysFileAcl::getPermission)
                .mapToInt(this::permissionRank)
                .max()
                .orElse(0);
    }

    private List<SysFileAcl> selectActiveAclsForContext(String fileId, UserAccessContext context) {
        DocumentRequestCache cache = context.cache();
        if (!StringUtils.hasText(fileId)) {
            return cache.activeAclsByFileId.computeIfAbsent(ALL_ACLS_CACHE_KEY, key -> queryActiveAclsForContext(null, context));
        }
        List<SysFileAcl> allAcls = cache.activeAclsByFileId.get(ALL_ACLS_CACHE_KEY);
        if (allAcls != null) {
            return allAcls.stream()
                    .filter(acl -> Objects.equals(acl.getFileId(), fileId))
                    .toList();
        }
        return cache.activeAclsByFileId.computeIfAbsent(fileId, key -> queryActiveAclsForContext(key, context));
    }

    private List<SysFileAcl> queryActiveAclsForContext(String fileId, UserAccessContext context) {
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

    private int permissionRank(String permission) {
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

    private void validateShareTarget(DocumentShareTargetReq target, String tenantId) {
        if (TARGET_TENANT.equals(target.getTargetType())) {
            if (!Objects.equals(target.getTargetId(), tenantId)) {
                throw new IllegalArgumentException("只能共享给当前租户");
            }
            Tenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null || Objects.equals(tenant.getDeleteFlag(), 1)) {
                throw new IllegalArgumentException("共享租户不存在");
            }
            return;
        }
        if (TARGET_USER.equals(target.getTargetType())) {
            User user = userMapper.selectById(target.getTargetId());
            if (user == null || Objects.equals(user.getDeleteFlag(), 1)) {
                throw new IllegalArgumentException("共享用户不存在");
            }
            Long tenantCount = userTenantMapper.selectCount(Wrappers.lambdaQuery(UserTenant.class)
                    .eq(UserTenant::getTenantId, tenantId)
                    .eq(UserTenant::getUserId, target.getTargetId())
                    .eq(UserTenant::getDeleteFlag, 0));
            if (tenantCount == 0) {
                throw new IllegalArgumentException("只能共享给当前租户用户");
            }
            return;
        }
        if (TARGET_DEPART.equals(target.getTargetType())) {
            SysDepart depart = sysDepartMapper.selectOne(Wrappers.lambdaQuery(SysDepart.class)
                    .eq(SysDepart::getId, target.getTargetId())
                    .eq(SysDepart::getTenantId, tenantId)
                    .eq(SysDepart::getDeleteFlag, 0)
                    .last("LIMIT 1"));
            if (depart == null) {
                throw new IllegalArgumentException("共享部门不存在");
            }
            return;
        }
        Role role = roleMapper.selectOne(Wrappers.lambdaQuery(Role.class)
                .eq(Role::getId, target.getTargetId())
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (role == null) {
            throw new IllegalArgumentException("共享角色不存在");
        }
    }

    private String resolveTargetName(String targetType, String targetId) {
        if (TARGET_TENANT.equals(targetType)) {
            Tenant tenant = tenantMapper.selectById(targetId);
            return tenant == null ? targetId : tenant.getName();
        }
        if (TARGET_USER.equals(targetType)) {
            User user = userMapper.selectById(targetId);
            return user == null ? targetId : (StringUtils.hasText(user.getRealname()) ? user.getRealname() : user.getUsername());
        }
        if (TARGET_DEPART.equals(targetType)) {
            SysDepart depart = sysDepartMapper.selectById(targetId);
            return depart == null ? targetId : depart.getDepartName();
        }
        Role role = roleMapper.selectById(targetId);
        return role == null ? targetId : role.getRoleName();
    }

    private UserAccessContext buildUserAccessContext(String username, String tenantId) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        User user = userMapper.selectOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getUsername, username)
                .eq(User::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new IllegalArgumentException("当前用户不存在");
        }
        List<String> departIds = userDepartMapper.selectList(Wrappers.lambdaQuery(UserDepart.class)
                        .select(UserDepart::getDepId)
                        .eq(UserDepart::getTenantId, tenantId)
                        .eq(UserDepart::getUserId, user.getId())
                        .eq(UserDepart::getDeleteFlag, 0))
                .stream()
                .map(UserDepart::getDepId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        departIds = resolveDepartIdsWithAncestors(departIds, tenantId);
        List<String> roleIds = userRoleMapper.selectList(Wrappers.lambdaQuery(UserRole.class)
                        .select(UserRole::getRoleId)
                        .eq(UserRole::getTenantId, tenantId)
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getDeleteFlag, 0))
                .stream()
                .map(UserRole::getRoleId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return new UserAccessContext(username, user.getId(), tenantId, departIds, roleIds, new DocumentRequestCache());
    }

    private List<String> resolveDepartIdsWithAncestors(List<String> departIds, String tenantId) {
        if (departIds == null || departIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> parentIdByDepartId = new HashMap<>();
        for (SysDepart depart : sysDepartMapper.selectList(Wrappers.lambdaQuery(SysDepart.class)
                        .select(SysDepart::getId, SysDepart::getParentId)
                        .eq(SysDepart::getTenantId, tenantId)
                        .eq(SysDepart::getDeleteFlag, 0))) {
            if (StringUtils.hasText(depart.getId())) {
                parentIdByDepartId.put(depart.getId(), depart.getParentId());
            }
        }
        LinkedHashSet<String> visibleIds = new LinkedHashSet<>();
        for (String departId : departIds) {
            String currentId = departId;
            int guard = 0;
            while (StringUtils.hasText(currentId) && guard++ < 20 && visibleIds.add(currentId)) {
                currentId = parentIdByDepartId.get(currentId);
            }
        }
        return new ArrayList<>(visibleIds);
    }

    private void softDeleteDocumentTree(SysFiles file, String username) {
        EntityFillUtils.fillDeleteFields(file, username);
        baseMapper.updateById(file);
        List<SysFiles> children = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, file.getTenantId())
                .eq(SysFiles::getParentId, file.getId())
                .eq(SysFiles::getDeleteFlag, 0));
        for (SysFiles child : children) {
            softDeleteDocumentTree(child, username);
        }
    }

    private void restoreDocumentTree(SysFiles file, String username) {
        file.setDeleteFlag(0);
        file.setDeleteTime(null);
        file.setDeleteBy(null);
        fillUpdate(file, username);
        baseMapper.updateById(file);
        List<SysFiles> children = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
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
    private void hardDeleteDocumentTree(SysFiles file) {
        List<SysFiles> children = baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
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
        baseMapper.deleteById(file.getId());
    }

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

    private boolean isVersionObjectReferencedByOtherFile(SysFiles file, String objectName) {
        return sysFileVersionMapper.selectCount(Wrappers.lambdaQuery(SysFileVersion.class)
                .eq(SysFileVersion::getTenantId, file.getTenantId())
                .ne(SysFileVersion::getFileId, file.getId())
                .and(wrapper -> wrapper
                        .eq(SysFileVersion::getObjectName, objectName)
                        .or()
                        .eq(SysFileVersion::getChangesObjectName, objectName))) > 0;
    }

    private void validateNotMoveToDescendant(String sourceId, String targetParentId) {
        String currentId = targetParentId;
        int guard = 0;
        while (StringUtils.hasText(currentId) && guard++ < 20) {
            if (Objects.equals(sourceId, currentId)) {
                throw new IllegalArgumentException("不能移动到自身子级下");
            }
            SysFiles current = getActiveFile(currentId);
            currentId = current.getParentId();
        }
    }

    private CopyTarget resolveCopyTarget(UserAccessContext context, DocumentCopyReq req) {
        String scope = normalizeScope(req.getScope());
        if (SCOPE_TRASH.equals(scope) || SCOPE_BUSINESS.equals(scope) || isSharedInboxRequest(req, scope)) {
            throw new IllegalArgumentException("当前目录不支持复制粘贴");
        }
        String parentId = trimToNull(req.getParentId());
        SharedTargetContext sharedTarget = resolveSharedTargetContext(
                context,
                req.getShareTargetType(),
                req.getShareTargetId(),
                false);
        String storeType = SCOPE_SHARED_BY_ME.equals(scope) ? SHARED_BY_ME_STORE_TYPE : DOCUMENT_STORE_TYPE;
        if (StringUtils.hasText(parentId)) {
            SysFiles parent = getActiveFile(parentId);
            assertCanManageDocument(parent, context);
            validateSharedSpaceParent(parent, sharedTarget);
            if (!FLAG_YES.equals(parent.getIzFolder())) {
                throw new IllegalArgumentException("目标必须是文件夹");
            }
            storeType = StringUtils.hasText(parent.getStoreType()) ? parent.getStoreType() : storeType;
        }
        if (!StringUtils.hasText(parentId) && sharedTarget != null) {
            storeType = resolveDocumentStoreType(scope, sharedTarget);
        }
        return new CopyTarget(parentId, storeType, sharedTarget);
    }

    private SysFiles copyDocumentTree(
            UserAccessContext context,
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
        baseMapper.insert(copied);
        if (FLAG_YES.equals(source.getIzFolder())) {
            for (SysFiles child : selectActiveChildren(context.tenantId(), source.getId())) {
                copyDocumentTree(context, child, copied.getId(), targetStoreType);
            }
        }
        return copied;
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
        return baseMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getParentId, parentId)
                .eq(SysFiles::getDeleteFlag, 0)
                .orderByDesc(SysFiles::getIzFolder)
                .orderByAsc(SysFiles::getCreateTime));
    }

    private void assertCanCopyDocument(SysFiles file, UserAccessContext context) {
        if (FLAG_YES.equals(file.getIzFolder())) {
            assertCanViewDocument(file, context);
            return;
        }
        if (!canDownload(file, context)) {
            throw new IllegalArgumentException("无权复制该文档");
        }
    }

    private void moveSharedInboxPlacement(UserAccessContext context, SysFiles file, String parentId) {
        assertCanViewDocument(file, context);
        if (Objects.equals(file.getId(), parentId)) {
            throw new IllegalArgumentException("不能移动到自身下");
        }
        if (!StringUtils.hasText(parentId)) {
            clearPersonalSharedPlacement(context, file.getId());
            return;
        }
        SysFiles parent = getActiveFile(parentId);
        if (!isPersonalSharedFolder(parent, context.username())) {
            throw new IllegalArgumentException("共享给我的文件只能归类到个人整理文件夹");
        }
        upsertPersonalSharedPlacement(context, file.getId(), parentId);
    }

    private void clearPersonalSharedPlacement(UserAccessContext context, String fileId) {
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
     * 共享给我的归类是当前用户视图，不修改原文件 parent_id，避免影响文件所有者和其他接收人。
     */
    private void upsertPersonalSharedPlacement(UserAccessContext context, String fileId, String parentId) {
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

    private boolean isPersonalSharedFolder(SysFiles file, String username) {
        return file != null
                && Objects.equals(file.getCreateBy(), username)
                && FLAG_YES.equals(file.getIzFolder())
                && SHARED_VIEW_STORE_TYPE.equals(file.getStoreType());
    }

    private boolean isBusinessFolder(SysFiles file, String username) {
        return file != null
                && Objects.equals(file.getCreateBy(), username)
                && FLAG_YES.equals(file.getIzFolder())
                && BUSINESS_VIEW_STORE_TYPE.equals(file.getStoreType());
    }

    private boolean isSharedInboxRequest(DocumentPageReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && (req == null || !StringUtils.hasText(req.getShareTargetType()));
    }

    private boolean isSharedInboxRequest(DocumentFolderReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && (req == null || !StringUtils.hasText(req.getShareTargetType()));
    }

    private boolean isSharedInboxRequest(DocumentUploadReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && (req == null || !StringUtils.hasText(req.getShareTargetType()));
    }

    private boolean isSharedInboxRequest(DocumentMoveReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && (req == null || !StringUtils.hasText(req.getShareTargetType()));
    }

    private boolean isSharedInboxRequest(DocumentCopyReq req, String scope) {
        return SCOPE_SHARED.equals(scope)
                && (req == null || !StringUtils.hasText(req.getShareTargetType()));
    }

    private String personalSharedRelationBizType(UserAccessContext context) {
        return PERSONAL_SHARED_RELATION_PREFIX + context.userId();
    }

    private String personalBusinessRelationBizType(UserAccessContext context) {
        return PERSONAL_BUSINESS_RELATION_PREFIX + context.userId();
    }

    private SysFiles getFileIncludingDeleted(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        return baseMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getId, fileId)
                .eq(SysFiles::getTenantId, requireTenantId())
                .last("LIMIT 1"));
    }

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
                || hasActiveBusinessRelation(file.getId(), file.getTenantId())) {
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
                || hasActiveBusinessRelation(file.getId(), file.getTenantId())) {
            throw new IllegalArgumentException("业务文档需由业务数据删除后再清理");
        }
    }

    private String normalizeScope(String scope) {
        if (SCOPE_ALL.equals(scope)
                || SCOPE_SHARED.equals(scope)
                || SCOPE_SHARED_BY_ME.equals(scope)
                || SCOPE_STARRED.equals(scope)
                || SCOPE_BUSINESS.equals(scope)
                || SCOPE_TRASH.equals(scope)) {
            return scope;
        }
        return SCOPE_MY;
    }

    private String normalizeBinaryFlag(String value, String currentValue, String defaultValue) {
        if (FLAG_YES.equals(value) || FLAG_NO.equals(value)) {
            return value;
        }
        if (FLAG_YES.equals(currentValue) || FLAG_NO.equals(currentValue)) {
            return currentValue;
        }
        return defaultValue;
    }

    private void fillUpdate(SysFiles file, String username) {
        file.setUpdateBy(username);
        file.setUpdateTime(LocalDateTime.now());
    }

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

    private void createInitialHistoryVersion(SysFiles fileEntity, MultipartFile file, String username) {
        if (!supportsInitialHistoryVersion(fileEntity.getFileName())) {
            return;
        }
        MessageDigest digest = sha256Digest();
        String contentType = safeContentType(file.getContentType());
        String objectName = null;
        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            objectName = minioUtils.uploadFileAndReturnObjectName(
                    digestInputStream,
                    buildVersionFileName(fileEntity.getFileName(), 1),
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

    private boolean supportsInitialHistoryVersion(String fileName) {
        return INITIAL_HISTORY_EXTENSIONS.contains(resolveExtension(fileName));
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
        if (!StringUtils.hasText(extension) || !ALLOWED_UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持上传常规文档、图片和视频文件");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new IllegalArgumentException("文件MIME类型过长");
        }
        String normalizedContentType = StringUtils.hasText(contentType)
                ? contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)
                : "";
        if (StringUtils.hasText(normalizedContentType)
                && BLOCKED_UPLOAD_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new IllegalArgumentException("不支持上传可执行或脚本类型文件");
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
        if (PDF_EXTENSIONS.contains(extension)) {
            return "pdf";
        }
        if (TEXT_EXTENSIONS.contains(extension) || OFFICE_COMPAT_EXTENSIONS.contains(extension)) {
            return "doc";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        return "doc";
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

    private record DocumentReadAccess(SysFiles file, UserAccessContext context) {
    }

    private record UserAccessContext(
            String username,
            String userId,
            String tenantId,
            List<String> departIds,
            List<String> roleIds,
            DocumentRequestCache cache) {
    }

    private static final class DocumentRequestCache {
        private final Map<String, List<SysFileAcl>> activeAclsByFileId = new HashMap<>();
        private final Map<String, Boolean> activeAclFlags = new HashMap<>();
        private List<SysFileRelation> activeBusinessRelations;
        private List<SysFileRelation> accessibleBusinessRelations;
    }

    private record SharedTargetContext(String targetType, String targetId) {
    }

    private record CopyTarget(String parentId, String storeType, SharedTargetContext sharedTarget) {
    }

    private record BusinessRecordNode(String bizType, String bizId) {
    }
}
