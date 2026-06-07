package com.lawoffice.system.service;

import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.DocumentStatusVO;

import java.util.List;

/**
 * 文档中心文件展示对象、虚拟目录和状态栏业务信息组装能力。
 */
public interface IDocumentFileViewService {

    /**
     * 批量构建文档列表展示对象，并批量解析直接共享标记。
     *
     * @param files   文件元数据列表
     * @param context 当前文档访问上下文
     * @return 文档展示对象列表
     */
    List<DocumentFileVO> buildDocumentVOList(List<SysFiles> files, DocumentAccessContext context);

    /**
     * 构建单个文档展示对象。
     *
     * @param file    文件元数据
     * @param context 当前文档访问上下文
     * @return 文档展示对象
     */
    DocumentFileVO buildDocumentVO(SysFiles file, DocumentAccessContext context);

    /**
     * 构建共享文件夹子级展示对象，权限由上级共享节点继承。
     *
     * @param file              子文件元数据
     * @param context           当前文档访问上下文
     * @param inheritedDownload 是否继承下载权限
     * @param inheritedUpdate   是否继承更新权限
     * @return 文档展示对象
     */
    DocumentFileVO buildSharedFolderChildVO(
            SysFiles file,
            DocumentAccessContext context,
            boolean inheritedDownload,
            boolean inheritedUpdate);

    /**
     * 批量填充文件夹是否有子节点，用于左侧树和列表展开态展示。
     *
     * @param records 文档展示对象列表
     * @param context 当前文档访问上下文
     */
    void fillFolderChildFlags(List<DocumentFileVO> records, DocumentAccessContext context);

    /**
     * 构建业务文档虚拟目录节点。
     *
     * @param context   当前文档访问上下文
     * @param id        虚拟节点 ID
     * @param fileName  虚拟节点名称
     * @param storeType 存储类型
     * @param parentId  父级虚拟节点 ID
     * @return 虚拟文件夹元数据
     */
    SysFiles buildBusinessVirtualFolder(
            DocumentAccessContext context,
            String id,
            String fileName,
            String storeType,
            String parentId);

    /**
     * 按共享人聚合构建“我的共享”虚拟目录。
     *
     * @param context     当前文档访问上下文
     * @param sharedFiles 当前用户共享出去的文件
     * @return 共享人虚拟目录列表
     */
    List<SysFiles> buildSharedOwnerFolders(DocumentAccessContext context, List<SysFiles> sharedFiles);

    /**
     * 判断文件元数据是否为“我的共享”共享人虚拟目录。
     *
     * @param folder 文件夹元数据
     * @return 是否为共享人虚拟目录
     */
    boolean isSharedOwnerVirtualFolder(SysFiles folder);

    /**
     * 填充状态栏中的业务文档关联信息。
     *
     * @param status  状态栏响应对象
     * @param file    文件元数据
     * @param context 当前文档访问上下文
     */
    void fillBusinessStatus(DocumentStatusVO status, SysFiles file, DocumentAccessContext context);
}
