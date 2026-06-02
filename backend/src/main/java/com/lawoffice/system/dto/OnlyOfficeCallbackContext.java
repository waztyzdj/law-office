package com.lawoffice.system.dto;

public record OnlyOfficeCallbackContext(
        String fileId,
        String tenantId,
        String username) {
}
