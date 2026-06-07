package com.lawoffice.document.service;

import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.dto.DocumentSharedTargetContext;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.document.req.DocumentPageReq;

import java.util.List;

/**
 * 文档中心租户/部门共享空间写入、同步和访问校验能力。
 */
public interface IDocumentSharedSpaceService {

    /**
     * 解析租户/部门共享空间目标，并校验当前用户是否可进入该共享空间。
     *
     * @param context       当前文档访问上下文
     * @param rawTargetType 共享目标类型
     * @param rawTargetId   共享目标 ID
     * @param required      是否要求必须传入共享目标
     * @return 共享目标上下文，非共享空间请求时可为空
     */
    DocumentSharedTargetContext resolveSharedTargetContext(
            DocumentAccessContext context,
            String rawTargetType,
            String rawTargetId,
            boolean required);

    /**
     * 判断分页请求是否访问租户/部门共享空间根节点。
     *
     * @param req   分页请求
     * @param scope 已规范化的文档范围
     * @return 是否为共享空间目标请求
     */
    boolean isSharedTargetScope(DocumentPageReq req, String scope);

    /**
     * 根据当前范围和共享目标解析文件存储类型。
     *
     * @param scope        已规范化的文档范围
     * @param sharedTarget 共享目标上下文
     * @return 存储类型
     */
    String resolveDocumentStoreType(String scope, DocumentSharedTargetContext sharedTarget);

    /**
     * 校验目标父级是否属于当前租户/部门共享空间。
     *
     * @param parent       目标父文件夹
     * @param sharedTarget 共享目标上下文
     */
    void validateSharedSpaceParent(SysFiles parent, DocumentSharedTargetContext sharedTarget);

    /**
     * 校验被移动文件是否可以在当前租户/部门共享空间内移动。
     *
     * @param file         被移动文件
     * @param sharedTarget 共享目标上下文
     */
    void validateSharedSpaceMember(SysFiles file, DocumentSharedTargetContext sharedTarget);

    /**
     * 部门共享根文件写入时同步根节点与部门的关系。
     *
     * @param file         共享根文件或文件夹
     * @param sharedTarget 共享目标上下文
     */
    void bindDepartSharedRootIfNeeded(SysFiles file, DocumentSharedTargetContext sharedTarget);

    /**
     * 文件在共享空间和个人空间之间移动后，同步目录树存储类型。
     *
     * @param file         被移动文件或文件夹
     * @param parentId     新父级 ID
     * @param sharedTarget 共享目标上下文
     * @param username     操作人用户名
     */
    void updateSharedSpaceStoreType(
            SysFiles file,
            String parentId,
            DocumentSharedTargetContext sharedTarget,
            String username);

    /**
     * 查询租户/部门共享空间根文件。
     *
     * @param context      当前文档访问上下文
     * @param sharedTarget 共享目标上下文
     * @param folderOnly   是否只查询文件夹
     * @return 共享空间根文件列表
     */
    List<SysFiles> selectSharedSpaceRootFiles(
            DocumentAccessContext context,
            DocumentSharedTargetContext sharedTarget,
            Boolean folderOnly);

    /**
     * 校验当前用户是否可查看共享空间文件。
     *
     * @param file    文件实体
     * @param context 当前文档访问上下文
     */
    void assertCanViewSharedSpace(SysFiles file, DocumentAccessContext context);

    /**
     * 校验当前用户是否可管理文档中心文件。
     *
     * @param file    文件实体
     * @param context 当前文档访问上下文
     */
    void assertCanManageDocument(SysFiles file, DocumentAccessContext context);

    /**
     * 判断当前用户是否拥有租户/部门共享空间访问权。
     *
     * @param file    文件实体
     * @param context 当前文档访问上下文
     * @return 是否可访问
     */
    boolean hasSharedSpaceAccess(SysFiles file, DocumentAccessContext context);

    /**
     * 校验目标父级是否属于当前共享 ACL 节点。
     *
     * @param parent       目标父文件夹
     * @param sharedTarget 共享目标上下文
     */
    void validateSharedTargetParent(SysFiles parent, DocumentSharedTargetContext sharedTarget);

    /**
     * 校验被移动文件是否属于当前共享 ACL 节点或其继承链路。
     *
     * @param file         被移动文件
     * @param sharedTarget 共享目标上下文
     */
    void validateSharedTargetMember(SysFiles file, DocumentSharedTargetContext sharedTarget);

    /**
     * 文件写入共享节点时补齐当前目标的 ACL，保证后续共享来源和权限解析一致。
     *
     * @param file         写入共享节点的文件
     * @param sharedTarget 共享目标上下文
     */
    void syncSharedTargetAcl(SysFiles file, DocumentSharedTargetContext sharedTarget);
}
