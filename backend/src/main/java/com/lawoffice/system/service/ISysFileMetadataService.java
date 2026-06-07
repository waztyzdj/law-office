package com.lawoffice.system.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件名、扩展名、文件类型和上传校验的统一规则。
 */
public interface ISysFileMetadataService {

    /**
     * 校验上传文件的基础安全边界，包括空文件、大小、扩展名、文件名和 MIME 类型。
     *
     * @param file 上传文件
     */
    void validateUploadFile(MultipartFile file);

    /**
     * 解析并清洗上传文件名，避免路径穿越和非法名称进入数据库。
     *
     * @param file 上传文件
     * @return 清洗后的文件名
     */
    String resolveFileName(MultipartFile file);

    /**
     * 解析通用文件服务返回的文件类型。
     *
     * @param file 上传文件
     * @return 通用文件类型
     */
    String resolveBaseFileType(MultipartFile file);

    /**
     * 解析文档中心返回的文件类型，保持现有文档中心图标和预览类型口径。
     *
     * @param file 上传文件
     * @return 文档中心文件类型
     */
    String resolveDocumentFileType(MultipartFile file);

    /**
     * 解析文件扩展名。
     *
     * @param fileName 文件名
     * @return 小写扩展名，不含点号
     */
    String resolveExtension(String fileName);

    /**
     * 判断上传文件是否需要创建初始历史版本。
     *
     * @param fileName 文件名
     * @return 是否支持初始历史版本
     */
    boolean supportsInitialHistoryVersion(String fileName);

    /**
     * 清洗 MIME 类型，空值时使用二进制流兜底。
     *
     * @param contentType 原始 MIME 类型
     * @return 安全 MIME 类型
     */
    String safeContentType(String contentType);

    /**
     * 构造历史版本文件名。
     *
     * @param fileName 原文件名
     * @param versionNo 版本号
     * @return 版本文件名
     */
    String buildVersionFileName(String fileName, int versionNo);
}
