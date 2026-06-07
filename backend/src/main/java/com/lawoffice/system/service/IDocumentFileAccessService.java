package com.lawoffice.system.service;

import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFiles;

/**
 * 文档文件级查看、下载和更新权限判断能力。
 */
public interface IDocumentFileAccessService {

    /**
     * 判断当前用户是否可下载文件。
     *
     * @param file    文件元数据
     * @param context 当前文档访问上下文
     * @return 是否可下载
     */
    boolean canDownload(SysFiles file, DocumentAccessContext context);

    /**
     * 判断当前用户是否可更新文件。
     *
     * @param file    文件元数据
     * @param context 当前文档访问上下文
     * @return 是否可更新
     */
    boolean canUpdate(SysFiles file, DocumentAccessContext context);

    /**
     * 校验当前用户是否可查看文档。
     *
     * @param file    文件元数据
     * @param context 当前文档访问上下文
     */
    void assertCanViewDocument(SysFiles file, DocumentAccessContext context);
}
