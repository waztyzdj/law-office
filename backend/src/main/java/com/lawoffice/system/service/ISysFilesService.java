package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.req.DocumentBatchDeleteReq;
import com.lawoffice.system.req.DocumentBatchMoveReq;
import com.lawoffice.system.req.DocumentCopyReq;
import com.lawoffice.system.req.DocumentFolderReq;
import com.lawoffice.system.req.DocumentMoveReq;
import com.lawoffice.system.req.DocumentPageReq;
import com.lawoffice.system.req.DocumentRenameReq;
import com.lawoffice.system.req.DocumentShareReq;
import com.lawoffice.system.req.DocumentTreeBatchReq;
import com.lawoffice.system.req.DocumentTreePrefetchReq;
import com.lawoffice.system.req.DocumentUploadReq;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.system.vo.DocumentShareVO;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.SysFilesVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface ISysFilesService extends IBaseService<SysFiles, SysFilesVO> {

    /**
     * 上传文件并保存元数据。
     */
    FileUploadVO uploadFile(String username, MultipartFile file, FileUploadReq req);

    /**
     * 将文件绑定到业务对象。
     */
    FileRelationVO bindFile(String username, FileRelationReq req);

    /**
     * 解绑文件业务关系。
     */
    void unbindFile(String username, String relationId);

    /**
     * 查询业务下绑定的文件。
     */
    List<FileUploadVO> listFilesByBiz(String bizType, String bizId);

    /**
     * 查询当前上传人在指定业务下绑定的文件。
     */
    List<FileUploadVO> listFilesByBizForOwner(String bizType, String bizId, String username);

    /**
     * 查询文件详情。
     */
    FileUploadVO getFileById(String fileId);

    /**
     * 校验当前操作人是否为文件上传者。
     */
    void checkFileOwner(String fileId, String username);

    /**
     * 下载文件内容流。
     */
    InputStream downloadFileContent(String fileId);

    /**
     * 分页查询文档中心文件。
     * 范围支持我的文档、业务文档、共享给我、我的共享、租户/部门共享和回收站。
     */
    PageVO<DocumentFileVO> pageDocuments(String username, DocumentPageReq req);

    /**
     * 批量加载多个左侧树节点的下一层文件夹，用于初始化时一次性加载多个根分类。
     */
    Map<String, List<DocumentFileVO>> batchLoadDocumentFolderTree(String username, DocumentTreeBatchReq req);

    /**
     * 批量预取多个父级目录的下一层文件夹，用于前端树展开时预热下一级缓存。
     */
    Map<String, List<DocumentFileVO>> prefetchDocumentFolderTree(String username, DocumentTreePrefetchReq req);

    /**
     * 上传文档中心文件。
     * 允许上传到本人目录、我的共享目录以及本人可管理的租户/部门共享目录；业务文档必须从业务模块上传。
     */
    DocumentFileVO uploadDocument(String username, MultipartFile file, DocumentUploadReq req);

    /**
     * 创建文档中心文件夹。
     * 共享给我和业务文档下创建的是当前用户个人整理文件夹，不改变原文件父级。
     */
    DocumentFileVO createDocumentFolder(String username, DocumentFolderReq req);

    /**
     * 重命名本人拥有的文档或文件夹。
     */
    DocumentFileVO renameDocument(String username, DocumentRenameReq req);

    /**
     * 移动本人拥有的文档或文件夹。
     * 共享给我和业务文档的归类移动通过个人关系记录实现，不影响原始文件层级。
     */
    DocumentFileVO moveDocument(String username, DocumentMoveReq req);

    /**
     * 批量移动本人拥有的文档或文件夹。任一文档校验失败时整体回滚。
     */
    List<DocumentFileVO> batchMoveDocuments(String username, DocumentBatchMoveReq req);

    /**
     * 复制当前用户可下载的文档或文件夹到目标目录。
     * 文件复制会生成新的对象存储文件，避免副本之间共享物理对象。
     */
    List<DocumentFileVO> copyDocuments(String username, DocumentCopyReq req);

    /**
     * 将本人拥有的文档移入回收站。
     */
    void deleteDocument(String username, String fileId);

    /**
     * 批量将本人拥有的文档移入回收站。任一文档校验失败时整体回滚。
     */
    void batchDeleteDocuments(String username, DocumentBatchDeleteReq req);

    /**
     * 从回收站恢复本人拥有的文档。
     */
    DocumentFileVO restoreDocument(String username, String fileId);

    /**
     * 批量从回收站恢复本人拥有的文档。任一文档校验失败时整体回滚。
     */
    List<DocumentFileVO> batchRestoreDocuments(String username, DocumentBatchDeleteReq req);

    /**
     * 从回收站彻底删除本人拥有的文档。
     */
    void purgeDocument(String username, String fileId);

    /**
     * 清空本人回收站。
     */
    void clearDocumentTrash(String username);

    /**
     * 切换本人文档收藏状态。
     */
    DocumentFileVO toggleDocumentStar(String username, String fileId);

    /**
     * 保存本人文档的共享目标。保存采用覆盖式同步，空列表表示取消全部共享。
     */
    List<DocumentShareVO> shareDocument(String username, DocumentShareReq req);

    /**
     * 查询本人文档的共享目标。
     */
    List<DocumentShareVO> listDocumentShares(String username, String fileId);

    /**
     * 撤销本人文档的一条共享授权。
     */
    void revokeDocumentShare(String username, String aclId);

    /**
     * 校验文档中心下载权限并返回文件元数据。
     */
    DocumentFileVO checkDocumentDownload(String fileId, String username);

    /**
     * Check document-center preview access and return file metadata.
     * Preview uses read permission and does not grant direct object-storage access.
     */
    DocumentFileVO checkDocumentPreview(String fileId, String username);

    /**
     * Check document-center read access and return file metadata without changing counters.
     * Lightweight reads such as image thumbnails should not be counted as user previews.
     */
    DocumentFileVO checkDocumentRead(String fileId, String username);

    /**
     * Check document-center edit access and return file metadata.
     *
     * @param fileId document file id
     * @param username current username
     * @return file metadata after edit permission validation
     */
    DocumentFileVO checkDocumentEdit(String fileId, String username);

    /**
     * Save edited document content back to object storage after rechecking edit permission.
     *
     * @param fileId document file id
     * @param username current username
     * @param inputStream edited file content
     * @param contentType edited file MIME type
     * @param contentLength edited file byte length, may be unknown
     * @param touchUpdateTime whether this save should create a new persisted document version
     */
    void saveDocumentEdit(
            String fileId,
            String username,
            InputStream inputStream,
            String contentType,
            Long contentLength,
            boolean touchUpdateTime);
}
