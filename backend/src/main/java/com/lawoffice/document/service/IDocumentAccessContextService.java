package com.lawoffice.document.service;

import com.lawoffice.document.dto.DocumentAccessContext;

/**
 * 文档中心访问用户上下文构建能力。
 */
public interface IDocumentAccessContextService {

    /**
     * 根据当前账号和租户构建文档中心访问上下文。
     *
     * @param username 当前账号
     * @param tenantId 当前租户 ID
     * @return 文档访问上下文
     */
    DocumentAccessContext buildDocumentAccessContext(String username, String tenantId);
}
