package com.lawoffice.document.dto;

public record OnlyOfficeCallbackContext(
        String fileId,
        String tenantId,
        String username) {
}
