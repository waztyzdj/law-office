package com.lawoffice.document.dto;

/**
 * 业务文档分组虚拟目录节点，记录业务类型和分组 ID。
 */
public record DocumentBusinessGroupNode(String bizType, String groupId) {
}
