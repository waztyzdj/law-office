package com.lawoffice.system.service;

import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.vo.DocumentShareSourceVO;
import com.lawoffice.system.vo.DocumentShareVO;

import java.util.List;

/**
 * 文档共享来源、继承来源和直接共享展示信息解析能力。
 */
public interface IDocumentShareSourceService {

    /**
     * 构造单条直接共享展示信息。
     *
     * @param acl 共享授权
     * @return 共享展示信息
     */
    DocumentShareVO buildDocumentShareVO(SysFileAcl acl);

    /**
     * 判断当前用户是否可以查看文件直接共享明细。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 是否可见
     */
    boolean canSeeDirectShares(SysFiles file, DocumentAccessContext context);

    /**
     * 查询文件直接共享明细。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @return 共享明细
     */
    List<DocumentShareVO> listActiveDirectShareVOs(String fileId, String tenantId);

    /**
     * 解析当前用户访问文件时命中的共享来源。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 共享来源
     */
    DocumentShareSourceVO resolveAccessShareSource(SysFiles file, DocumentAccessContext context);

    /**
     * 解析文件继承自上级文件夹的共享来源。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 继承来源
     */
    DocumentShareSourceVO resolveInheritedShareSource(SysFiles file, DocumentAccessContext context);

    /**
     * 解析文件继承自上级文件夹的收藏来源。
     *
     * @param file 文件元数据
     * @param context 访问上下文
     * @return 收藏来源
     */
    DocumentShareSourceVO resolveFavoriteSource(SysFiles file, DocumentAccessContext context);
}
