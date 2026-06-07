package com.lawoffice.document.service;

import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.req.DocumentShareReq;
import com.lawoffice.document.vo.DocumentShareVO;

import java.util.List;

/**
 * 文档共享管理能力，负责共享 ACL 的创建、查询和撤销。
 */
public interface IDocumentShareManagementService {

    /**
     * 重新设置文档的直接共享目标。
     *
     * @param context 当前文档访问上下文
     * @param req     共享请求
     * @return 当前文档的有效共享列表
     */
    List<DocumentShareVO> shareDocument(DocumentAccessContext context, DocumentShareReq req);

    /**
     * 查询文档的直接共享列表。
     *
     * @param context 当前文档访问上下文
     * @param fileId  文件 ID
     * @return 有效共享列表
     */
    List<DocumentShareVO> listDocumentShares(DocumentAccessContext context, String fileId);

    /**
     * 撤销一条直接共享记录。
     *
     * @param context 当前文档访问上下文
     * @param aclId   共享 ACL ID
     */
    void revokeDocumentShare(DocumentAccessContext context, String aclId);
}
