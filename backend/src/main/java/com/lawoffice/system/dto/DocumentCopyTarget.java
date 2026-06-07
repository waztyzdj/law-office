package com.lawoffice.system.dto;

/**
 * 文档复制目标上下文。
 */
public record DocumentCopyTarget(
        String parentId,
        String storeType,
        DocumentSharedTargetContext sharedTarget) {
}
