package com.lawoffice.system.service;

import com.lawoffice.system.dto.BusinessDocumentAccessContext;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface IBusinessDocumentProvider {

    /**
     * 返回该 Provider 处理的业务类型，对应 sys_file_relation.biz_type。
     */
    String bizType();

    /**
     * 返回文档中心业务文档一级虚拟目录名称。
     */
    String moduleName();

    /**
     * 将业务数据 ID 转换为二级虚拟目录展示名称。
     */
    default Map<String, String> resolveRecordNames(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        return Collections.emptyMap();
    }

    /**
     * 判断当前用户是否有权限在业务文档中查看指定业务数据的附件。
     */
    boolean canAccess(String bizId, BusinessDocumentAccessContext context);

    /**
     * 批量筛选当前用户可访问的业务数据 ID，避免业务文档列表按记录逐条调用权限 SQL。
     */
    default Set<String> filterAccessibleBizIds(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        if (bizIds == null || bizIds.isEmpty()) {
            return Collections.emptySet();
        }
        return bizIds.stream()
                .filter(bizId -> canAccess(bizId, context))
                .collect(Collectors.toSet());
    }
}
