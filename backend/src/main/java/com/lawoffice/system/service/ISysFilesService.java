package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.SysFilesVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

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
}
