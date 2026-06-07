package com.lawoffice.document.service;

import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.document.vo.DocumentFolderStatsVO;

/**
 * 文档状态栏使用的文件统计和路径解析能力。
 */
public interface IDocumentStatisticsService {

    /**
     * 递归统计文件夹下的文件数、文件夹数和文件总大小。
     *
     * @param folder 文件夹
     * @return 文件夹统计信息
     */
    DocumentFolderStatsVO calculateFolderStats(SysFiles folder);

    /**
     * 将数据库中的 KB 大小换算为前端展示使用的字节数。
     *
     * @param file 文件元数据
     * @return 文件大小字节数
     */
    long toFileSizeBytes(SysFiles file);

    /**
     * 解析文件在文档树中的原始路径，包含已删除父级节点。
     *
     * @param file 文件元数据
     * @return 原始路径
     */
    String resolveDocumentPath(SysFiles file);
}
