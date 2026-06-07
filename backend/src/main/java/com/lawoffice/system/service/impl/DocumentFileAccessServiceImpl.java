package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.PERMISSION_DOWNLOAD;
import static com.lawoffice.system.constant.DocumentCenterConstants.PERMISSION_READ;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_NO;

import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.service.IDocumentAclPermissionService;
import com.lawoffice.system.service.IDocumentBusinessAccessService;
import com.lawoffice.system.service.IDocumentFileAccessService;
import com.lawoffice.system.service.IDocumentSharedSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentFileAccessServiceImpl implements IDocumentFileAccessService {

    private final IDocumentAclPermissionService documentAclPermissionService;
    private final IDocumentBusinessAccessService documentBusinessAccessService;
    private final IDocumentSharedSpaceService documentSharedSpaceService;

    @Override
    public boolean canDownload(SysFiles file, DocumentAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return true;
        }
        if (documentSharedSpaceService.hasSharedSpaceAccess(file, context)) {
            return true;
        }
        if (documentBusinessAccessService.hasBusinessDocumentAccess(file, context)) {
            return true;
        }
        if (FLAG_NO.equals(file.getEnableDown())) {
            return false;
        }
        return documentAclPermissionService.resolvePermissionRank(file, context)
                >= documentAclPermissionService.permissionRank(PERMISSION_DOWNLOAD);
    }

    @Override
    public boolean canUpdate(SysFiles file, DocumentAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return true;
        }
        if (documentSharedSpaceService.hasSharedSpaceAccess(file, context)) {
            return true;
        }
        return documentAclPermissionService.resolveUpdatePermission(file, context);
    }

    @Override
    public void assertCanViewDocument(SysFiles file, DocumentAccessContext context) {
        if (Objects.equals(file.getCreateBy(), context.username())) {
            return;
        }
        if (documentSharedSpaceService.hasSharedSpaceAccess(file, context)) {
            return;
        }
        if (documentBusinessAccessService.hasBusinessDocumentAccess(file, context)) {
            return;
        }
        if (documentAclPermissionService.resolvePermissionRank(file, context)
                < documentAclPermissionService.permissionRank(PERMISSION_READ)) {
            throw new IllegalArgumentException("无权访问该文档");
        }
    }
}
