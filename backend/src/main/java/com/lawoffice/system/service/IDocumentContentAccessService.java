package com.lawoffice.system.service;

import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.vo.DocumentFileVO;

import java.io.InputStream;

/**
 * 文档下载、预览、阅读和编辑保存访问能力。
 */
public interface IDocumentContentAccessService {

    /**
     * 校验并记录文件下载。
     *
     * @param fileId  文件 ID
     * @param context 当前文档访问上下文
     * @return 文件展示对象
     */
    DocumentFileVO checkDocumentDownload(String fileId, DocumentAccessContext context);

    /**
     * 校验并记录文件预览。
     *
     * @param fileId  文件 ID
     * @param context 当前文档访问上下文
     * @return 文件展示对象
     */
    DocumentFileVO checkDocumentPreview(String fileId, DocumentAccessContext context);

    /**
     * 校验文件阅读权限，不增加阅读次数。
     *
     * @param fileId  文件 ID
     * @param context 当前文档访问上下文
     * @return 文件展示对象
     */
    DocumentFileVO checkDocumentRead(String fileId, DocumentAccessContext context);

    /**
     * 校验文件编辑权限。
     *
     * @param fileId  文件 ID
     * @param context 当前文档访问上下文
     * @return 文件展示对象
     */
    DocumentFileVO checkDocumentEdit(String fileId, DocumentAccessContext context);

    /**
     * 保存在线编辑后的文件内容。
     *
     * @param fileId          文件 ID
     * @param context         当前文档访问上下文
     * @param inputStream     文件内容流
     * @param contentType     内容类型
     * @param contentLength   内容长度
     * @param touchUpdateTime 是否触碰更新时间，保留接口兼容
     */
    void saveDocumentEdit(
            String fileId,
            DocumentAccessContext context,
            InputStream inputStream,
            String contentType,
            Long contentLength,
            boolean touchUpdateTime);
}
