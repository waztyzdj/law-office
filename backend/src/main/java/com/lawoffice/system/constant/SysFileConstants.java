package com.lawoffice.system.constant;

import java.util.Set;

/**
 * 文件基础能力使用的稳定取值和校验边界。
 */
public final class SysFileConstants {

    public static final String DEFAULT_STORE_TYPE = "minio";
    public static final Integer DEFAULT_RELATION_TYPE = 1;
    public static final String FOLDER_TYPE = "folder";
    public static final String FLAG_YES = "1";
    public static final String FLAG_NO = "0";

    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;
    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int MAX_CONTENT_TYPE_LENGTH = 128;

    public static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");
    public static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    public static final Set<String> PPT_EXTENSIONS = Set.of("ppt", "pptx");
    public static final Set<String> TEXT_EXTENSIONS = Set.of("csv", "md", "rtf", "txt");
    public static final Set<String> PDF_EXTENSIONS = Set.of("pdf");
    public static final Set<String> OFFICE_COMPAT_EXTENSIONS = Set.of("dps", "et", "odp", "ods", "odt", "wps");
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("bmp", "gif", "jpeg", "jpg", "png", "webp");
    public static final Set<String> VIDEO_EXTENSIONS = Set.of("avi", "flv", "mkv", "mov", "mp4", "wmv");
    public static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = Set.of(
            "avi", "bmp", "csv", "doc", "docx", "dps", "et", "flv", "gif", "jpeg", "jpg",
            "md", "mkv", "mov", "mp4", "odp", "ods", "odt", "pdf", "png", "ppt", "pptx",
            "rtf", "txt", "webp", "wmv", "wps", "xls", "xlsx"
    );
    public static final Set<String> BLOCKED_UPLOAD_CONTENT_TYPES = Set.of(
            "application/bat",
            "application/cmd",
            "application/javascript",
            "application/msdos-windows",
            "application/powershell",
            "application/vnd.microsoft.portable-executable",
            "application/x-bat",
            "application/x-cmd",
            "application/x-dosexec",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-msi",
            "application/x-powershell",
            "application/x-sh",
            "application/x-shellscript",
            "text/javascript",
            "text/vbscript",
            "text/x-powershell",
            "text/x-python",
            "text/x-script",
            "text/x-shellscript",
            "text/x-sh"
    );

    private SysFileConstants() {
    }
}
