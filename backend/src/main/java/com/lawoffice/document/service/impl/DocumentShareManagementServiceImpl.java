package com.lawoffice.document.service.impl;

import static com.lawoffice.document.constant.DocumentCenterConstants.BUSINESS_VIEW_STORE_TYPE;
import static com.lawoffice.document.constant.DocumentCenterConstants.PERMISSION_DOWNLOAD;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_NO;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileAclMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.document.req.DocumentShareReq;
import com.lawoffice.document.req.DocumentShareTargetReq;
import com.lawoffice.document.service.IDocumentAclPermissionService;
import com.lawoffice.document.service.IDocumentBusinessAccessService;
import com.lawoffice.document.service.IDocumentShareManagementService;
import com.lawoffice.document.service.IDocumentShareSourceService;
import com.lawoffice.document.service.IDocumentShareTargetService;
import com.lawoffice.document.service.IDocumentSharedSpaceService;
import com.lawoffice.document.vo.DocumentShareVO;
import com.lawoffice.util.EntityFillUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentShareManagementServiceImpl implements IDocumentShareManagementService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileAclMapper fileAclMapper;
    private final IDocumentAclPermissionService documentAclPermissionService;
    private final IDocumentBusinessAccessService documentBusinessAccessService;
    private final IDocumentShareSourceService documentShareSourceService;
    private final IDocumentShareTargetService documentShareTargetService;
    private final IDocumentSharedSpaceService documentSharedSpaceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentShareVO> shareDocument(DocumentAccessContext context, DocumentShareReq req) {
        if (req == null) {
            throw new IllegalArgumentException("共享信息不能为空");
        }
        SysFiles file = getActiveFile(req.getFileId(), context.tenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        assertNotBusinessReadonlyDocument(file);
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<SysFileAcl> deleteWrapper = Wrappers.lambdaUpdate(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, context.tenantId())
                .eq(SysFileAcl::getFileId, file.getId())
                .eq(SysFileAcl::getDeleteFlag, 0)
                .set(SysFileAcl::getDeleteFlag, 1)
                .set(SysFileAcl::getDeleteTime, now)
                .set(SysFileAcl::getDeleteBy, context.username());
        fileAclMapper.update(null, deleteWrapper);

        Set<String> seenTargets = new HashSet<>();
        for (DocumentShareTargetReq target : req.getTargets()) {
            documentShareTargetService.validateShareTarget(target, context.tenantId());
            String targetKey = target.getTargetType() + ":" + target.getTargetId();
            if (!seenTargets.add(targetKey)) {
                continue;
            }
            SysFileAcl acl = new SysFileAcl();
            acl.setId(newId());
            acl.setTenantId(context.tenantId());
            acl.setFileId(file.getId());
            acl.setTargetType(target.getTargetType());
            acl.setTargetId(target.getTargetId());
            acl.setPermission(StringUtils.hasText(target.getPermission()) ? target.getPermission() : PERMISSION_DOWNLOAD);
            acl.setExpireTime(req.getExpireTime());
            acl.setCreateBy(context.username());
            acl.setCreateTime(now);
            acl.setDeleteFlag(0);
            fileAclMapper.insert(acl);
        }

        file.setSharePerms(seenTargets.isEmpty() ? "1" : "2");
        file.setEnableDown(normalizeBinaryFlag(req.getEnableDown(), file.getEnableDown(), FLAG_YES));
        file.setEnableUpdat(normalizeBinaryFlag(req.getEnableUpdat(), file.getEnableUpdat(), FLAG_NO));
        fillUpdate(file, context.username());
        sysFilesMapper.updateById(file);
        return listDocumentShares(context, file.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShareVO> listDocumentShares(DocumentAccessContext context, String fileId) {
        SysFiles file = getActiveFile(fileId, context.tenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        List<SysFileAcl> acls = fileAclMapper.selectList(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getTenantId, context.tenantId())
                .eq(SysFileAcl::getFileId, fileId)
                .eq(SysFileAcl::getDeleteFlag, 0)
                .orderByAsc(SysFileAcl::getTargetType, SysFileAcl::getCreateTime));
        return acls.stream()
                .map(documentShareSourceService::buildDocumentShareVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeDocumentShare(DocumentAccessContext context, String aclId) {
        SysFileAcl acl = fileAclMapper.selectOne(Wrappers.lambdaQuery(SysFileAcl.class)
                .eq(SysFileAcl::getId, aclId)
                .eq(SysFileAcl::getTenantId, context.tenantId())
                .eq(SysFileAcl::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (acl == null) {
            throw new IllegalArgumentException("共享记录不存在");
        }
        SysFiles file = getActiveFile(acl.getFileId(), context.tenantId());
        documentSharedSpaceService.assertCanManageDocument(file, context);
        EntityFillUtils.fillDeleteFields(acl, context.username());
        fileAclMapper.updateById(acl);
        if (!documentAclPermissionService.hasActiveAcl(file.getId(), file.getTenantId())) {
            file.setSharePerms("1");
            fillUpdate(file, context.username());
            sysFilesMapper.updateById(file);
        }
    }

    /**
     * 业务文档在文档中心只允许聚合查看，共享关系必须由业务模块生命周期控制。
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

    private String normalizeBinaryFlag(String value, String currentValue, String defaultValue) {
        if (FLAG_YES.equals(value) || FLAG_NO.equals(value)) {
            return value;
        }
        if (FLAG_YES.equals(currentValue) || FLAG_NO.equals(currentValue)) {
            return currentValue;
        }
        return defaultValue;
    }

    /**
     * 共享管理只能操作当前租户未删除文件，避免给回收站或其他租户文件授权。
     */
    private SysFiles getActiveFile(String fileId, String tenantId) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
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

    private void fillUpdate(SysFiles file, String username) {
        file.setUpdateBy(username);
        file.setUpdateTime(LocalDateTime.now());
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
