package com.lawoffice.document.service;

import com.lawoffice.document.dto.BusinessDocumentAccessContext;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.dto.DocumentBusinessRecordNode;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;

import java.util.List;

/**
 * 业务文档关系识别、业务权限过滤和访问判断能力。
 */
public interface IDocumentBusinessAccessService {

    /**
     * 查询当前租户下全部有效业务文档文件 ID，不校验当前用户业务权限。
     *
     * @param context 文档访问上下文
     * @return 文件 ID 列表
     */
    List<String> findActiveBusinessFileIds(DocumentAccessContext context);

    /**
     * 查询当前租户下全部有效业务文档关系，不校验当前用户业务权限。
     *
     * @param context 文档访问上下文
     * @return 业务文档关系列表
     */
    List<SysFileRelation> findActiveBusinessRelations(DocumentAccessContext context);

    /**
     * 查询当前用户可访问的业务文档文件 ID。
     *
     * @param context 文档访问上下文
     * @return 文件 ID 列表
     */
    List<String> findAccessibleBusinessFileIds(DocumentAccessContext context);

    /**
     * 查询当前用户可访问的业务文档关系。
     *
     * @param context 文档访问上下文
     * @return 业务文档关系列表
     */
    List<SysFileRelation> findAccessibleBusinessRelations(DocumentAccessContext context);

    /**
     * 判断指定业务记录虚拟目录是否可访问。
     *
     * @param recordNode 业务记录节点
     * @param context 文档访问上下文
     * @return 是否可访问
     */
    boolean hasAccessibleBusinessRecord(DocumentBusinessRecordNode recordNode, DocumentAccessContext context);

    /**
     * 判断文件是否属于指定可访问业务记录。
     *
     * @param fileId 文件 ID
     * @param recordNode 业务记录节点
     * @param context 文档访问上下文
     * @return 是否可访问
     */
    boolean hasAccessibleBusinessRecordFile(
            String fileId,
            DocumentBusinessRecordNode recordNode,
            DocumentAccessContext context);

    /**
     * 判断文件是否存在有效业务文档关系。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @return 是否存在业务关系
     */
    boolean hasActiveBusinessRelation(String fileId, String tenantId);

    /**
     * 判断当前用户是否可访问该业务文档。
     *
     * @param file 文件元数据
     * @param context 文档访问上下文
     * @return 是否可访问
     */
    boolean hasBusinessDocumentAccess(SysFiles file, DocumentAccessContext context);

    /**
     * 判断当前用户是否可访问指定业务文档关系。
     *
     * @param relation 文件关系
     * @param context 文档访问上下文
     * @return 是否可访问
     */
    boolean hasBusinessRelationAccess(SysFileRelation relation, DocumentAccessContext context);

    /**
     * 判断关系是否属于业务模块创建的附件关系。
     *
     * @param relation 文件关系
     * @return 是否业务文档关系
     */
    boolean isBusinessRelation(SysFileRelation relation);

    /**
     * 转换为业务文档 Provider 使用的访问上下文。
     *
     * @param context 文档访问上下文
     * @return 业务文档访问上下文
     */
    BusinessDocumentAccessContext toBusinessDocumentAccessContext(DocumentAccessContext context);
}
