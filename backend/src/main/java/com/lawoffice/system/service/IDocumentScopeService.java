package com.lawoffice.system.service;

import com.lawoffice.system.req.DocumentCopyReq;
import com.lawoffice.system.req.DocumentFolderReq;
import com.lawoffice.system.req.DocumentMoveReq;
import com.lawoffice.system.req.DocumentPageReq;
import com.lawoffice.system.req.DocumentUploadReq;

/**
 * 文档中心范围参数归一化和请求类型判断能力。
 */
public interface IDocumentScopeService {

    /**
     * 归一化文档中心范围参数。
     *
     * @param scope 前端范围值
     * @return 有效范围，非法值返回我的文档
     */
    String normalizeScope(String scope);

    /**
     * 判断分页请求是否属于共享给我。
     *
     * @param req 请求参数
     * @param scope 已归一化范围
     * @return 是否共享给我
     */
    boolean isSharedInboxRequest(DocumentPageReq req, String scope);

    /**
     * 判断新建文件夹请求是否属于共享给我。
     *
     * @param req 请求参数
     * @param scope 已归一化范围
     * @return 是否共享给我
     */
    boolean isSharedInboxRequest(DocumentFolderReq req, String scope);

    /**
     * 判断上传请求是否属于共享给我。
     *
     * @param req 请求参数
     * @param scope 已归一化范围
     * @return 是否共享给我
     */
    boolean isSharedInboxRequest(DocumentUploadReq req, String scope);

    /**
     * 判断移动请求是否属于共享给我。
     *
     * @param req 请求参数
     * @param scope 已归一化范围
     * @return 是否共享给我
     */
    boolean isSharedInboxRequest(DocumentMoveReq req, String scope);

    /**
     * 判断复制请求是否属于共享给我。
     *
     * @param req 请求参数
     * @param scope 已归一化范围
     * @return 是否共享给我
     */
    boolean isSharedInboxRequest(DocumentCopyReq req, String scope);
}
