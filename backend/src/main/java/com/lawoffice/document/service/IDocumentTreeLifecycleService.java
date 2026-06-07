package com.lawoffice.document.service;

import com.lawoffice.system.entity.SysFiles;

/**
 * 文档树删除、恢复和物理清理生命周期能力。
 */
public interface IDocumentTreeLifecycleService {

    /**
     * 递归软删除文档树。
     *
     * @param file 根文档
     * @param username 操作人账号
     */
    void softDeleteDocumentTree(SysFiles file, String username);

    /**
     * 递归恢复文档树。
     *
     * @param file 根文档
     * @param username 操作人账号
     */
    void restoreDocumentTree(SysFiles file, String username);

    /**
     * 递归物理删除文档树，并清理对象存储、历史版本、共享授权和个人归类关系。
     *
     * @param file 根文档
     */
    void hardDeleteDocumentTree(SysFiles file);
}
