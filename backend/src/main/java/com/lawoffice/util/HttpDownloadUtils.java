package com.lawoffice.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 下载响应头工具。
 */
public final class HttpDownloadUtils {

    private static final String DEFAULT_FILE_NAME = "download";

    private HttpDownloadUtils() {
    }

    /**
     * 构建符合 RFC 5987 的附件下载响应头。
     *
     * @param fileName 原始文件名
     * @return Content-Disposition 响应头
     */
    public static String buildContentDisposition(String fileName) {
        String safeFileName = resolveDownloadFileName(fileName);
        String fallbackName = buildAsciiFallbackName(safeFileName);
        String encodedName = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fallbackName + "\"; filename*=UTF-8''" + encodedName;
    }

    /**
     * 清理 CR/LF、路径分隔符等不应进入响应头的字符。
     *
     * @param fileName 原始文件名
     * @return 可用于 filename* 的安全文件名
     */
    public static String resolveDownloadFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT_FILE_NAME;
        }
        return fileName.trim()
                .replace("\r", "_")
                .replace("\n", "_")
                .replace("\\", "_")
                .replace("/", "_");
    }

    private static String buildAsciiFallbackName(String fileName) {
        String extension = extractAsciiExtension(fileName);
        String baseName = extension.isEmpty()
                ? fileName
                : fileName.substring(0, fileName.length() - extension.length());
        String asciiBaseName = toAsciiToken(baseName);
        if (asciiBaseName.isBlank()) {
            asciiBaseName = DEFAULT_FILE_NAME;
        }
        return asciiBaseName + extension;
    }

    private static String extractAsciiExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex);
        if (extension.length() > 16) {
            return "";
        }
        return extension.chars().allMatch(HttpDownloadUtils::isAsciiExtensionChar)
                ? extension
                : "";
    }

    private static String toAsciiToken(String text) {
        StringBuilder builder = new StringBuilder();
        boolean lastWasUnderscore = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isAsciiFileNameChar(ch)) {
                builder.append(ch);
                lastWasUnderscore = false;
            } else if (!lastWasUnderscore) {
                builder.append('_');
                lastWasUnderscore = true;
            }
        }
        return builder.toString().replaceAll("^[._\\s]+|[._\\s]+$", "");
    }

    private static boolean isAsciiFileNameChar(char ch) {
        return ch >= 0x20 && ch <= 0x7E && ch != '"' && ch != ';' && ch != '\\';
    }

    private static boolean isAsciiExtensionChar(int ch) {
        return ch == '.' || ch == '_' || ch == '-' || Character.isLetterOrDigit(ch);
    }
}
