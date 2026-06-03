package com.lawoffice.system.dto;

import java.io.InputStream;

public record OnlyOfficeHistoryFileContent(
        String fileName,
        String fileType,
        InputStream inputStream) {
}
