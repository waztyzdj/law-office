package com.lawoffice.document.service;

import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.document.req.DocumentBatchDeleteReq;
import com.lawoffice.document.req.DocumentBatchMoveReq;
import com.lawoffice.document.req.DocumentCopyReq;
import com.lawoffice.document.req.DocumentFolderReq;
import com.lawoffice.document.req.DocumentMoveReq;
import com.lawoffice.document.req.DocumentPageReq;
import com.lawoffice.document.req.DocumentRenameReq;
import com.lawoffice.document.req.DocumentShareReq;
import com.lawoffice.document.req.DocumentTreeBatchReq;
import com.lawoffice.document.req.DocumentTreePrefetchReq;
import com.lawoffice.document.req.DocumentUploadReq;
import com.lawoffice.document.vo.DocumentFileVO;
import com.lawoffice.document.vo.DocumentShareVO;
import com.lawoffice.document.vo.DocumentStatusVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文档中心业务入口，承载浏览、共享、回收站、状态栏和在线编辑等能力。
 * <p>
 * Controller 层通过文档中心独立路由暴露接口，通用文件服务只保留上传、绑定和下载等基础能力。
 */
public interface IDocumentCenterService {

    /**
     * 分页查询文档中心文件。
     * 范围支持我的文档、业务文档、共享给我、我的共享、租户/部门共享和回收站。
     *
     * @param username 当前用户名
     * @param req 查询条件
     * @return 文档分页结果
     */
    PageVO<DocumentFileVO> pageDocuments(String username, DocumentPageReq req);

    /**
     * 批量加载多个左侧树节点的下一层文件夹，用于初始化时一次性加载多个根分类。
     *
     * @param username 当前用户名
     * @param req 批量树查询条件
     * @return 按父级 ID 分组的文件夹列表
     */
    Map<String, List<DocumentFileVO>> batchLoadDocumentFolderTree(String username, DocumentTreeBatchReq req);

    /**
     * 批量预取多个父级目录的下一层文件夹，用于前端树展开时预热下一级缓存。
     *
     * @param username 当前用户名
     * @param req 预取条件
     * @return 按父级 ID 分组的文件夹列表
     */
    Map<String, List<DocumentFileVO>> prefetchDocumentFolderTree(String username, DocumentTreePrefetchReq req);

    /**
     * 上传文档中心文件。
     * 业务文档附件必须从业务模块进入，文档中心只允许用户和共享空间文件。
     *
     * @param username 当前用户名
     * @param file 上传文件
     * @param req 上传请求
     * @return 上传后的文档元数据
     */
    DocumentFileVO uploadDocument(String username, MultipartFile file, DocumentUploadReq req);

    /**
     * 创建文档中心文件夹。
     * 共享给我和业务文档下创建的是当前用户个人整理文件夹，不改变原文件父级。
     *
     * @param username 当前用户名
     * @param req 文件夹请求
     * @return 创建后的文件夹元数据
     */
    DocumentFileVO createDocumentFolder(String username, DocumentFolderReq req);

    /**
     * 重命名本人拥有的文档或文件夹。
     *
     * @param username 当前用户名
     * @param req 重命名请求
     * @return 重命名后的文档元数据
     */
    DocumentFileVO renameDocument(String username, DocumentRenameReq req);

    /**
     * 移动本人拥有的文档或文件夹。
     * 共享给我和业务文档的归类移动通过个人关系记录实现，不影响原始文件层级。
     *
     * @param username 当前用户名
     * @param req 移动请求
     * @return 移动后的文档元数据
     */
    DocumentFileVO moveDocument(String username, DocumentMoveReq req);

    /**
     * 批量移动本人拥有的文档或文件夹。任一文档校验失败时整体回滚。
     *
     * @param username 当前用户名
     * @param req 批量移动请求
     * @return 移动后的文档列表
     */
    List<DocumentFileVO> batchMoveDocuments(String username, DocumentBatchMoveReq req);

    /**
     * 复制当前用户可下载的文档或文件夹到目标目录。
     * 文件复制会生成新的对象存储文件，避免副本之间共享物理对象。
     *
     * @param username 当前用户名
     * @param req 复制请求
     * @return 复制后的文档列表
     */
    List<DocumentFileVO> copyDocuments(String username, DocumentCopyReq req);

    /**
     * 将本人拥有的文档移入回收站。
     *
     * @param username 当前用户名
     * @param fileId 文档 ID
     */
    void deleteDocument(String username, String fileId);

    /**
     * 批量将本人拥有的文档移入回收站。任一文档校验失败时整体回滚。
     *
     * @param username 当前用户名
     * @param req 批量删除请求
     */
    void batchDeleteDocuments(String username, DocumentBatchDeleteReq req);

    /**
     * 从回收站恢复本人拥有的文档。
     *
     * @param username 当前用户名
     * @param fileId 文档 ID
     * @return 恢复后的文档元数据
     */
    DocumentFileVO restoreDocument(String username, String fileId);

    /**
     * 批量从回收站恢复本人拥有的文档。任一文档校验失败时整体回滚。
     *
     * @param username 当前用户名
     * @param req 批量恢复请求
     * @return 恢复后的文档列表
     */
    List<DocumentFileVO> batchRestoreDocuments(String username, DocumentBatchDeleteReq req);

    /**
     * 从回收站彻底删除本人拥有的文档。
     *
     * @param username 当前用户名
     * @param fileId 文档 ID
     */
    void purgeDocument(String username, String fileId);

    /**
     * 清空本人回收站。
     *
     * @param username 当前用户名
     */
    void clearDocumentTrash(String username);

    /**
     * 切换本人文档收藏状态。
     *
     * @param username 当前用户名
     * @param fileId 文档 ID
     * @return 更新后的文档元数据
     */
    DocumentFileVO toggleDocumentStar(String username, String fileId);

    /**
     * 保存本人文档的共享目标。保存采用覆盖式同步，空列表表示取消全部共享。
     *
     * @param username 当前用户名
     * @param req 共享请求
     * @return 当前共享授权列表
     */
    List<DocumentShareVO> shareDocument(String username, DocumentShareReq req);

    /**
     * 查询本人文档的共享目标。
     *
     * @param username 当前用户名
     * @param fileId 文档 ID
     * @return 当前共享授权列表
     */
    List<DocumentShareVO> listDocumentShares(String username, String fileId);

    /**
     * 查询文档中心状态栏详情，包含共享来源、统计信息和业务来源。
     *
     * @param username 当前用户名
     * @param fileId 文档 ID
     * @return 状态栏详情
     */
    DocumentStatusVO getDocumentStatus(String username, String fileId);

    /**
     * 撤销本人文档的一条共享授权。
     *
     * @param username 当前用户名
     * @param aclId 授权 ID
     */
    void revokeDocumentShare(String username, String aclId);

    /**
     * 校验文档中心下载权限并返回文件元数据。
     *
     * @param fileId 文档 ID
     * @param username 当前用户名
     * @return 文档元数据
     */
    DocumentFileVO checkDocumentDownload(String fileId, String username);

    /**
     * 校验文档中心预览权限并返回文件元数据。
     * 预览只要求阅读权限，不授予对象存储直连权限。
     *
     * @param fileId 文档 ID
     * @param username 当前用户名
     * @return 文档元数据
     */
    DocumentFileVO checkDocumentPreview(String fileId, String username);

    /**
     * 校验文档中心阅读权限并返回文件元数据，不更新阅读次数。
     * 图片缩略图等轻量读取不应计入用户预览。
     *
     * @param fileId 文档 ID
     * @param username 当前用户名
     * @return 文档元数据
     */
    DocumentFileVO checkDocumentRead(String fileId, String username);

    /**
     * 校验文档中心编辑权限并返回文件元数据。
     *
     * @param fileId 文档 ID
     * @param username 当前用户名
     * @return 文档元数据
     */
    DocumentFileVO checkDocumentEdit(String fileId, String username);

    /**
     * 保存在线编辑后的文档内容，保存前会重新校验编辑权限。
     *
     * @param fileId 文档 ID
     * @param username 当前用户名
     * @param inputStream 编辑后的文件内容
     * @param contentType 编辑后的 MIME 类型
     * @param contentLength 编辑后的字节长度，可为空
     * @param touchUpdateTime 是否需要触发持久化版本
     */
    void saveDocumentEdit(
            String fileId,
            String username,
            InputStream inputStream,
            String contentType,
            Long contentLength,
            boolean touchUpdateTime);
}
