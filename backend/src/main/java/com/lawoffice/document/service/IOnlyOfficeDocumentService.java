package com.lawoffice.document.service;

import com.lawoffice.document.dto.OnlyOfficeDownloadContext;
import com.lawoffice.document.dto.OnlyOfficeCallbackReq;
import com.lawoffice.document.dto.OnlyOfficeHistoryDownloadContext;
import com.lawoffice.document.dto.OnlyOfficeHistoryFileContent;
import com.lawoffice.document.vo.OnlyOfficeHistoryVersionVO;
import com.lawoffice.document.vo.OnlyOfficePreviewVO;

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
     * List immutable document history versions after checking read permission.
     *
     * @param username current username
     * @param fileId document file id
     * @return currently empty history version list
     */
    List<OnlyOfficeHistoryVersionVO> listHistory(String username, String fileId);

    /**
     * Build a view-only ONLYOFFICE config for one immutable history version.
     *
     * @param username current username
     * @param userId current user id
     * @param versionId history version id
     * @return editor bootstrap payload in view mode
     */
    OnlyOfficePreviewVO buildHistoryPreviewConfig(String username, String userId, String versionId);

    /**
     * Parse a short-lived history-version download token.
     *
     * @param token signed history download token
     * @return version and tenant context encoded in the token
     */
    OnlyOfficeHistoryDownloadContext parseHistoryDownloadToken(String token);

    /**
     * Open immutable history version content for ONLYOFFICE download.
     *
     * @param versionId history version id
     * @return stream and metadata; caller must close the stream
     */
    OnlyOfficeHistoryFileContent openHistoryFileContent(String versionId);

    /**
     * Restore one history version to the current file after checking edit permission.
     *
     * @param username current username
     * @param versionId history version id
     * @return created restore history record
     */
    OnlyOfficeHistoryVersionVO restoreHistoryVersion(String username, String versionId);
}
