package com.lawoffice.system.service;

import com.lawoffice.system.dto.OnlyOfficeDownloadContext;
import com.lawoffice.system.dto.OnlyOfficeCallbackReq;
import com.lawoffice.system.vo.OnlyOfficeHistoryVersionVO;
import com.lawoffice.system.vo.OnlyOfficePreviewVO;

import java.util.List;

public interface IOnlyOfficeDocumentService {

    /**
     * Build a ONLYOFFICE editor config after checking document-center access.
     *
     * @param username current username
     * @param userId current user id
     * @param fileId document file id
     * @param mode requested editor mode, view or edit
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

    /**
     * Handle ONLYOFFICE edit-save callback.
     *
     * @param token signed callback token
     * @param req callback payload from Document Server
     */
    void handleCallback(String token, OnlyOfficeCallbackReq req);

    /**
     * Reserved history-version list endpoint. Real version persistence is implemented later.
     *
     * @param username current username
     * @param fileId document file id
     * @return currently empty history version list
     */
    List<OnlyOfficeHistoryVersionVO> listHistory(String username, String fileId);
}
