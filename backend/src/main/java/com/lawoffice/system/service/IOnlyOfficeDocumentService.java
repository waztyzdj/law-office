package com.lawoffice.system.service;

import com.lawoffice.system.dto.OnlyOfficeDownloadContext;
import com.lawoffice.system.vo.OnlyOfficePreviewVO;

public interface IOnlyOfficeDocumentService {

    /**
     * Build a read-only ONLYOFFICE editor config after checking document-center access.
     *
     * @param username current username
     * @param userId current user id
     * @param fileId document file id
     * @param mode requested editor mode, currently only view is supported
     * @return editor bootstrap payload
     */
    OnlyOfficePreviewVO buildPreviewConfig(String username, String userId, String fileId, String mode);

    /**
     * Parse a short-lived ONLYOFFICE file download token.
     *
     * @param token signed download token
     * @return file and tenant context encoded in the token
     */
    OnlyOfficeDownloadContext parseDownloadToken(String token);
}
