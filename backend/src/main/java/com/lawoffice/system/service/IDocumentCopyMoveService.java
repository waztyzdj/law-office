package com.lawoffice.system.service;

import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.dto.DocumentCopyTarget;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.req.DocumentCopyReq;

import java.util.Set;

/**
 * 文档复制、移动辅助和个人归类关系维护能力。
 */
public interface IDocumentCopyMoveService {

    /**
     * 校验移动/复制目标不能位于源节点的子孙层级内，避免形成目录环。
     *
     * @param sourceId       源文件或文件夹 ID
     * @param targetParentId 目标父文件夹 ID
     * @param tenantId       当前租户 ID
     */
    void validateNotMoveToDescendant(String sourceId, String targetParentId, String tenantId);

    /**
     * 按复制请求解析目标父级、存储类型和共享目标上下文。
     *
     * @param context 当前文档访问上下文
     * @param req     复制请求
     * @return 复制目标信息
     */
    DocumentCopyTarget resolveCopyTarget(DocumentAccessContext context, DocumentCopyReq req);

    /**
     * 复制文件或目录树，目录会递归复制子节点，文件会同步复制对象存储内容。
     *
     * @param context         当前文档访问上下文
     * @param source          源文件或文件夹
     * @param targetParentId  目标父文件夹 ID
     * @param targetStoreType 目标存储类型
     * @return 新复制出的根节点
     */
    SysFiles copyDocumentTree(
            DocumentAccessContext context,
            SysFiles source,
            String targetParentId,
            String targetStoreType);

    /**
     * 维护“共享给我”视图下当前用户的个人归类关系，不改变原文件父级。
     *
     * @param context  当前文档访问上下文
     * @param file     被归类的共享文件
     * @param parentId 个人整理文件夹 ID，空值表示移出个人归类
     */
    void moveSharedInboxPlacement(DocumentAccessContext context, SysFiles file, String parentId);

    /**
     * 查询当前用户已经归类过的“共享给我”文件 ID。
     *
     * @param context 当前文档访问上下文
     * @return 已归类文件 ID 集合
     */
    Set<String> findPersonalSharedPlacedFileIds(DocumentAccessContext context);

    /**
     * 查询当前用户已经归类过的“业务文档”文件 ID。
     *
     * @param context 当前文档访问上下文
     * @return 已归类文件 ID 集合
     */
    Set<String> findPersonalBusinessPlacedFileIds(DocumentAccessContext context);

    /**
     * 判断文件夹是否是当前用户在“共享给我”下创建的个人整理文件夹。
     *
     * @param file     文件夹实体
     * @param username 当前用户名
     * @return 是否为个人共享整理文件夹
     */
    boolean isPersonalSharedFolder(SysFiles file, String username);

    /**
     * 判断文件夹是否是当前用户在“业务文档”下创建的个人整理文件夹。
     *
     * @param file     文件夹实体
     * @param username 当前用户名
     * @return 是否为业务文档个人整理文件夹
     */
    boolean isBusinessFolder(SysFiles file, String username);

    /**
     * 生成当前用户“共享给我”个人归类关系的业务类型。
     *
     * @param context 当前文档访问上下文
     * @return 个人归类关系 bizType
     */
    String personalSharedRelationBizType(DocumentAccessContext context);

    /**
     * 生成当前用户“业务文档”个人归类关系的业务类型。
     *
     * @param context 当前文档访问上下文
     * @return 个人归类关系 bizType
     */
    String personalBusinessRelationBizType(DocumentAccessContext context);
}
