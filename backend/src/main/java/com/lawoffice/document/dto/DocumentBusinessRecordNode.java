package com.lawoffice.document.dto;

/**
 * 业务文档二级虚拟目录节点，记录业务类型和业务数据 ID。
 */
public record DocumentBusinessRecordNode(String bizType, String bizId) {
}
