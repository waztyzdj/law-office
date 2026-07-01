package com.lawoffice.workflow.dto;

/**
 * 审批下载文件结果。
 *
 * @param fileName 下载文件名
 * @param contentType 响应 Content-Type
 * @param content 文件字节
 */
public record WorkflowDownloadFile(String fileName, String contentType, byte[] content) {
}
