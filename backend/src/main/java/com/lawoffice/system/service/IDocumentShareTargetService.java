package com.lawoffice.system.service;

import com.lawoffice.system.req.DocumentShareTargetReq;

/**
 * 文档共享目标的校验和展示信息解析规则。
 */
public interface IDocumentShareTargetService {

    /**
     * 校验共享目标是否存在且属于当前租户可共享范围。
     *
     * @param target 共享目标
     * @param tenantId 当前租户 ID
     */
    void validateShareTarget(DocumentShareTargetReq target, String tenantId);

    /**
     * 解析共享目标展示名称，人员目标优先返回姓名。
     *
     * @param targetType 目标类型
     * @param targetId 目标 ID
     * @return 展示名称，不存在时返回目标 ID
     */
    String resolveTargetName(String targetType, String targetId);

    /**
     * 解析共享目标类型文案。
     *
     * @param targetType 目标类型
     * @return 目标类型文案
     */
    String resolveTargetTypeText(String targetType);
}
