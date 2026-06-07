package com.lawoffice.document.service;

import com.lawoffice.document.dto.BusinessDocumentAccessContext;
import com.lawoffice.document.dto.DocumentBusinessRecordNode;

import java.util.Collection;
import java.util.Map;

/**
 * 文档中心虚拟节点 ID 编解码和业务文档展示名称解析规则。
 */
public interface IDocumentVirtualNodeService {

    /**
     * 构造共享人虚拟目录 ID。
     *
     * @param owner 共享人账号
     * @return 虚拟目录 ID
     */
    String sharedOwnerId(String owner);

    /**
     * 判断是否共享人虚拟目录 ID。
     *
     * @param id 节点 ID
     * @return 是否共享人虚拟目录
     */
    boolean isSharedOwnerVirtualId(String id);

    /**
     * 解析共享人虚拟目录中的共享人账号。
     *
     * @param id 节点 ID
     * @return 共享人账号，非共享人虚拟目录时返回 null
     */
    String parseSharedOwner(String id);

    /**
     * 构造业务模块虚拟目录 ID。
     *
     * @param bizType 业务类型
     * @return 虚拟目录 ID
     */
    String businessModuleId(String bizType);

    /**
     * 构造业务记录虚拟目录 ID。
     *
     * @param bizType 业务类型
     * @param bizId 业务数据 ID
     * @return 虚拟目录 ID
     */
    String businessRecordId(String bizType, String bizId);

    /**
     * 判断是否业务模块虚拟目录 ID。
     *
     * @param id 节点 ID
     * @return 是否业务模块虚拟目录
     */
    boolean isBusinessModuleVirtualId(String id);

    /**
     * 判断是否业务记录虚拟目录 ID。
     *
     * @param id 节点 ID
     * @return 是否业务记录虚拟目录
     */
    boolean isBusinessRecordVirtualId(String id);

    /**
     * 从业务模块虚拟目录 ID 中解析业务类型。
     *
     * @param id 节点 ID
     * @return 业务类型
     */
    String parseBusinessModuleBizType(String id);

    /**
     * 从业务记录虚拟目录 ID 中解析业务节点。
     *
     * @param id 节点 ID
     * @return 业务记录节点，非法格式返回 null
     */
    DocumentBusinessRecordNode parseBusinessRecordNode(String id);

    /**
     * 解析业务模块展示名称。
     *
     * @param bizType 业务类型
     * @return 业务模块展示名称
     */
    String resolveBusinessModuleName(String bizType);

    /**
     * 批量解析业务记录展示名称。
     *
     * @param bizType 业务类型
     * @param bizIds 业务数据 ID 集合
     * @param context 业务访问上下文
     * @return 业务数据 ID 到展示名称的映射
     */
    Map<String, String> resolveBusinessRecordNames(
            String bizType,
            Collection<String> bizIds,
            BusinessDocumentAccessContext context);

    /**
     * 查找指定业务类型的业务文档 Provider。
     *
     * @param bizType 业务类型
     * @return Provider，不存在时返回 null
     */
    IBusinessDocumentProvider findBusinessDocumentProvider(String bizType);
}
