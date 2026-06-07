package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.INITIAL_HISTORY_EXTENSIONS;
import static com.lawoffice.system.constant.SysFileConstants.*;

import com.lawoffice.system.service.ISysFileMetadataService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
public class SysFileMetadataServiceImpl implements ISysFileMetadataService {

    @Override
    public void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("单个文件不能超过50MB");
        }
        String fileName = resolveFileName(file);
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("文件名不能超过255个字符");
        }
        String extension = resolveExtension(fileName);
        if (!StringUtils.hasText(extension) || !ALLOWED_UPLOAD_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持上传常规文档、图片和视频文件");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new IllegalArgumentException("文件MIME类型过长");
        }
        String normalizedContentType = StringUtils.hasText(contentType)
                ? contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)
                : "";
        if (StringUtils.hasText(normalizedContentType)
                && BLOCKED_UPLOAD_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new IllegalArgumentException("不支持上传可执行或脚本类型文件");
        }
    }

    @Override
    public String resolveFileName(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        String filename = StringUtils.hasText(originalFilename)
                ? StringUtils.getFilename(originalFilename)
                : "file";
        if (!StringUtils.hasText(filename) || filename.contains("..")) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return filename;
    }

    @Override
    public String resolveBaseFileType(MultipartFile file) {
        String extension = resolveExtension(resolveFileName(file));
        if (EXCEL_EXTENSIONS.contains(extension)) {
            return "excel";
        }
        if (WORD_EXTENSIONS.contains(extension)) {
            return "word";
        }
        if (PPT_EXTENSIONS.contains(extension)) {
            return "ppt";
        }
        if (PDF_EXTENSIONS.contains(extension)) {
            return "pdf";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        if (OFFICE_COMPAT_EXTENSIONS.contains(extension)) {
            return "office";
        }
        return "file";
    }

    @Override
    public String resolveDocumentFileType(MultipartFile file) {
        String extension = resolveExtension(resolveFileName(file));
        if (!StringUtils.hasText(extension)) {
            return "unknown";
        }
        if (EXCEL_EXTENSIONS.contains(extension)) {
            return "excel";
        }
        if (WORD_EXTENSIONS.contains(extension)) {
            return "doc";
        }
        if (PPT_EXTENSIONS.contains(extension)) {
            return "ppt";
        }
        if (PDF_EXTENSIONS.contains(extension)) {
            return "pdf";
        }
        if (TEXT_EXTENSIONS.contains(extension) || OFFICE_COMPAT_EXTENSIONS.contains(extension)) {
            return "doc";
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        }
        return "doc";
    }

    @Override
    public String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean supportsInitialHistoryVersion(String fileName) {
        return INITIAL_HISTORY_EXTENSIONS.contains(resolveExtension(fileName));
    }

    @Override
    public String safeContentType(String contentType) {
        return StringUtils.hasText(contentType)
                ? contentType.split(";", 2)[0].trim()
                : "application/octet-stream";
    }

    @Override
    public String buildVersionFileName(String fileName, int versionNo) {
        if (!StringUtils.hasText(fileName)) {
            return "document-v" + versionNo;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName + "-v" + versionNo;
        }
        return fileName.substring(0, dotIndex) + "-v" + versionNo + fileName.substring(dotIndex);
    }
}
