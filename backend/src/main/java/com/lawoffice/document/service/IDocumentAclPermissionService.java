package com.lawoffice.document.service;

import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFiles;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 文档共享 ACL 查询、权限等级和继承权限解析能力。
 */
public interface IDocumentAclPermissionService {

    /**
     * 解析权限等级。
     *
     * @param permission 权限值
     * @return 权限等级
     */
    int permissionRank(String permission);

    /**
     * 判断文件是否存在有效 ACL。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @return 是否存在有效 ACL
     */
    boolean hasActiveAcl(String fileId, String tenantId);

    /**
     * 判断文件是否存在当前用户可命中的有效 ACL。
     *
     * @param fileId 文件 ID
     * @param context 访问上下文
     * @return 是否存在有效 ACL
     */
    boolean hasActiveAcl(String fileId, DocumentAccessContext context);

    /**
     * 批量查询存在有效 ACL 的文件 ID。
     *
     * @param fileIds 文件 ID 集合
     * @param tenantId 租户 ID
     * @return 文件 ID 集合
     */
    Set<String> findActiveAclFileIds(Collection<String> fileIds, String tenantId);

    /**
     * 查询文件直接配置的有效 ACL。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @return ACL 列表
     */
    List<SysFileAcl> listActiveDirectAcls(String fileId, String tenantId);

    /**
     * 查询当前用户可命中的有效 ACL，支持请求级缓存。
     *
     * @param fileId 文件 ID，空值表示查询全部
     * @param context 访问上下文
     * @return ACL 列表
     */
    List<SysFileAcl> selectActiveAclsForContext(String fileId, DocumentAccessContext context);

    /**
     * 解析文件共享权限等级，支持父级继承。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 权限等级
     */
    int resolvePermissionRank(SysFiles file, DocumentAccessContext context);

    /**
     * 校验并解析共享文件访问权限等级。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 权限等级
     */
    int resolveSharedDocumentPermissionRank(SysFiles file, DocumentAccessContext context);

    /**
     * 解析文件是否允许当前用户编辑，支持父级 ACL 和父级编辑开关继承。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 是否允许编辑
     */
    boolean resolveUpdatePermission(SysFiles file, DocumentAccessContext context);
}
