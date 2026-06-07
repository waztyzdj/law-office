package com.lawoffice.document.dto;

import java.io.InputStream;

public record OnlyOfficeHistoryFileContent(
        String fileName,
        String fileType,
        InputStream inputStream) {
}
