package com.lawoffice.document.service.impl;

import static com.lawoffice.document.constant.DocumentCenterConstants.*;

import com.lawoffice.document.req.DocumentCopyReq;
import com.lawoffice.document.req.DocumentFolderReq;
import com.lawoffice.document.req.DocumentMoveReq;
import com.lawoffice.document.req.DocumentPageReq;
import com.lawoffice.document.req.DocumentUploadReq;
import com.lawoffice.document.service.IDocumentScopeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DocumentScopeServiceImpl implements IDocumentScopeService {

    @Override
    public String normalizeScope(String scope) {
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

    @Override
    public boolean isSharedInboxRequest(DocumentPageReq req, String scope) {
        return isSharedInboxScope(scope, req == null ? null : req.getShareTargetType());
    }

    @Override
    public boolean isSharedInboxRequest(DocumentFolderReq req, String scope) {
        return isSharedInboxScope(scope, req == null ? null : req.getShareTargetType());
    }

    @Override
    public boolean isSharedInboxRequest(DocumentUploadReq req, String scope) {
        return isSharedInboxScope(scope, req == null ? null : req.getShareTargetType());
    }

    @Override
    public boolean isSharedInboxRequest(DocumentMoveReq req, String scope) {
        return isSharedInboxScope(scope, req == null ? null : req.getShareTargetType());
    }

    @Override
    public boolean isSharedInboxRequest(DocumentCopyReq req, String scope) {
        return isSharedInboxScope(scope, req == null ? null : req.getShareTargetType());
    }

    private boolean isSharedInboxScope(String scope, String shareTargetType) {
        return SCOPE_SHARED.equals(scope) && !StringUtils.hasText(shareTargetType);
    }
}
