package com.lawoffice.document.dto;

import java.util.List;

/**
 * 文档中心当前访问用户上下文。
 */
public record DocumentAccessContext(
        String username,
        String userId,
        String tenantId,
        List<String> departIds,
        List<String> roleIds,
        DocumentRequestCache cache) {
}
