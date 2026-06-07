package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.BUSINESS_MODULE_VIEW_STORE_TYPE;
import static com.lawoffice.system.constant.DocumentCenterConstants.BUSINESS_RECORD_VIEW_STORE_TYPE;
import static com.lawoffice.system.constant.DocumentCenterConstants.SHARED_OWNER_VIEW_STORE_TYPE;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_NO;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;
import static com.lawoffice.system.constant.SysFileConstants.FOLDER_TYPE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.dto.DocumentBusinessRecordNode;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IDocumentAclPermissionService;
import com.lawoffice.system.service.IDocumentBusinessAccessService;
import com.lawoffice.system.service.IDocumentFileAccessService;
import com.lawoffice.system.service.IDocumentFileViewService;
import com.lawoffice.system.service.IDocumentSharedSpaceService;
import com.lawoffice.system.service.IDocumentStatisticsService;
import com.lawoffice.system.service.IDocumentVirtualNodeService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.DocumentStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentFileViewServiceImpl implements IDocumentFileViewService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final UserMapper userMapper;
    private final IDocumentAclPermissionService documentAclPermissionService;
    private final IDocumentBusinessAccessService documentBusinessAccessService;
    private final IDocumentFileAccessService documentFileAccessService;
    private final IDocumentSharedSpaceService documentSharedSpaceService;
    private final IDocumentStatisticsService documentStatisticsService;
    private final IDocumentVirtualNodeService documentVirtualNodeService;

    @Override
    public List<DocumentFileVO> buildDocumentVOList(List<SysFiles> files, DocumentAccessContext context) {
        Set<String> sharedFileIds = documentAclPermissionService.findActiveAclFileIds(files.stream()
                .map(SysFiles::getId)
                .filter(StringUtils::hasText)
                .toList(), context.tenantId());
        return files.stream()
                .map(file -> buildDocumentVO(file, context, sharedFileIds))
                .toList();
    }

    @Override
    public DocumentFileVO buildDocumentVO(SysFiles file, DocumentAccessContext context) {
        Set<String> sharedFileIds = StringUtils.hasText(file.getId())
                && documentAclPermissionService.hasActiveAcl(file.getId(), context)
                ? Set.of(file.getId())
                : Collections.emptySet();
        return buildDocumentVO(file, context, sharedFileIds);
    }

    @Override
    public DocumentFileVO buildSharedFolderChildVO(
            SysFiles file,
            DocumentAccessContext context,
            boolean inheritedDownload,
            boolean inheritedUpdate) {
        DocumentFileVO vo = buildBaseDocumentVO(file, context);
        vo.setSharedFlag(false);
        vo.setCanManage(Boolean.TRUE.equals(vo.getOwnerFlag()) || documentSharedSpaceService.hasSharedSpaceAccess(file, context));
        vo.setCanDownload(vo.getOwnerFlag() || inheritedDownload);
        vo.setCanUpdate(vo.getOwnerFlag() || inheritedUpdate);
        return vo;
    }

    @Override
    public void fillFolderChildFlags(List<DocumentFileVO> records, DocumentAccessContext context) {
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
        Set<String> parentIdsWithChildren = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
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
            businessRelations = documentBusinessAccessService.findAccessibleBusinessRelations(context);
        }
        for (DocumentFileVO folder : folders) {
            folder.setHasChild(parentIdsWithChildren.contains(folder.getId())
                    || isSharedOwnerVirtualFolder(folder)
                    || hasBusinessVirtualFolderChildren(folder, businessRelations));
        }
    }

    @Override
    public SysFiles buildBusinessVirtualFolder(
            DocumentAccessContext context,
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

    @Override
    public List<SysFiles> buildSharedOwnerFolders(DocumentAccessContext context, List<SysFiles> sharedFiles) {
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

    @Override
    public boolean isSharedOwnerVirtualFolder(SysFiles folder) {
        return SHARED_OWNER_VIEW_STORE_TYPE.equals(folder.getStoreType())
                && documentVirtualNodeService.isSharedOwnerVirtualId(folder.getId());
    }

    @Override
    public void fillBusinessStatus(DocumentStatusVO status, SysFiles file, DocumentAccessContext context) {
        if (BUSINESS_MODULE_VIEW_STORE_TYPE.equals(file.getStoreType())) {
            String bizType = documentVirtualNodeService.parseBusinessModuleBizType(file.getId());
            status.setBusinessBizType(bizType);
            status.setBusinessModuleName(documentVirtualNodeService.resolveBusinessModuleName(bizType));
            return;
        }
        if (BUSINESS_RECORD_VIEW_STORE_TYPE.equals(file.getStoreType())) {
            DocumentBusinessRecordNode recordNode = documentVirtualNodeService.parseBusinessRecordNode(file.getId());
            if (recordNode == null) {
                return;
            }
            status.setBusinessBizType(recordNode.bizType());
            status.setBusinessBizId(recordNode.bizId());
            status.setBusinessModuleName(documentVirtualNodeService.resolveBusinessModuleName(recordNode.bizType()));
            status.setBusinessRecordName(documentVirtualNodeService.resolveBusinessRecordNames(
                    recordNode.bizType(),
                    List.of(recordNode.bizId()),
                    documentBusinessAccessService.toBusinessDocumentAccessContext(context)).get(recordNode.bizId()));
            return;
        }
        List<SysFileRelation> relations = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getFileId, file.getId())
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .filter(documentBusinessAccessService::isBusinessRelation)
                .filter(relation -> documentBusinessAccessService.hasBusinessRelationAccess(relation, context))
                .toList();
        if (relations.isEmpty()) {
            return;
        }
        SysFileRelation relation = relations.get(0);
        status.setBusinessBizType(relation.getBizType());
        status.setBusinessBizId(relation.getBizId());
        status.setBusinessModuleName(documentVirtualNodeService.resolveBusinessModuleName(relation.getBizType()));
        status.setBusinessRecordName(documentVirtualNodeService.resolveBusinessRecordNames(
                relation.getBizType(),
                List.of(relation.getBizId()),
                documentBusinessAccessService.toBusinessDocumentAccessContext(context)).get(relation.getBizId()));
    }

    private DocumentFileVO buildDocumentVO(
            SysFiles file,
            DocumentAccessContext context,
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
        vo.setCanManage(Boolean.TRUE.equals(vo.getOwnerFlag()) || documentSharedSpaceService.hasSharedSpaceAccess(file, context));
        vo.setCanDownload(documentFileAccessService.canDownload(file, context));
        vo.setCanUpdate(documentFileAccessService.canUpdate(file, context));
        return vo;
    }

    private DocumentFileVO buildBaseDocumentVO(SysFiles file, DocumentAccessContext context) {
        DocumentFileVO vo = new DocumentFileVO();
        vo.setId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileType(file.getFileType());
        vo.setStoreType(file.getStoreType());
        vo.setParentId(file.getParentId());
        vo.setFileSize(documentStatisticsService.toFileSizeBytes(file));
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

    /**
     * “共享给我”根目录按共享人聚合成虚拟文件夹，名称优先展示真实姓名。
     */
    private SysFiles buildSharedOwnerVirtualFolder(
            DocumentAccessContext context,
            String owner,
            String ownerDisplayName,
            List<SysFiles> sharedFiles) {
        SysFiles folder = new SysFiles();
        folder.setId(documentVirtualNodeService.sharedOwnerId(owner));
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

    private boolean isSharedOwnerVirtualFolder(DocumentFileVO folder) {
        return SHARED_OWNER_VIEW_STORE_TYPE.equals(folder.getStoreType())
                && documentVirtualNodeService.isSharedOwnerVirtualId(folder.getId());
    }

    private boolean isBusinessVirtualFolder(DocumentFileVO folder) {
        return BUSINESS_MODULE_VIEW_STORE_TYPE.equals(folder.getStoreType())
                || BUSINESS_RECORD_VIEW_STORE_TYPE.equals(folder.getStoreType());
    }

    /**
     * 业务模块虚拟目录有业务数据子目录，业务数据虚拟目录本身不再挂虚拟子级。
     */
    private boolean hasBusinessVirtualFolderChildren(
            DocumentFileVO folder,
            List<SysFileRelation> businessRelations) {
        String folderId = folder.getId();
        if (BUSINESS_MODULE_VIEW_STORE_TYPE.equals(folder.getStoreType())) {
            String bizType = documentVirtualNodeService.parseBusinessModuleBizType(folderId);
            return businessRelations.stream()
                    .anyMatch(relation -> Objects.equals(relation.getBizType(), bizType)
                            && StringUtils.hasText(relation.getBizId()));
        }
        if (BUSINESS_RECORD_VIEW_STORE_TYPE.equals(folder.getStoreType())) {
            return false;
        }
        return false;
    }

    /**
     * 共享人虚拟目录不能显示账号，批量解析真实姓名以避免列表逐条查询。
     */
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
