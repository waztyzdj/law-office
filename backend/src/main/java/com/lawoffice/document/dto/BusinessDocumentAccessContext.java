package com.lawoffice.document.dto;

import java.util.List;

public record BusinessDocumentAccessContext(
        String username,
        String userId,
        String tenantId,
        List<String> departIds,
        List<String> roleIds) {
}
