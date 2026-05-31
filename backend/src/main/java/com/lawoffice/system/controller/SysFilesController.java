package com.lawoffice.system.controller;

import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.req.FileRelationReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.req.SysFilesReq;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileRelationVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.system.vo.SysFilesVO;
import com.lawoffice.util.HttpDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/files")
@Tag(name = "文件中心", description = "文件元数据和业务关联管理")
@ModuleInfo(value = "files", name = "文件中心", description = "文件元数据和业务关联管理")
public class SysFilesController extends BaseController<ISysFilesService, SysFiles, SysFilesVO, SysFilesReq> {

    public SysFilesController(ISysFilesService sysFilesService) {
        this.baseService = sysFilesService;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件并写入文件元数据，支持同步绑定业务")
    @AutoLog(value = "上传文件", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<FileUploadVO> upload(
            @RequestPart("file") MultipartFile file,
            @Valid FileUploadReq req,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(baseService.uploadFile(username, file, req));
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定文件", description = "将已上传文件绑定到业务对象")
    @AutoLog(value = "绑定文件", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<FileRelationVO> bind(
            @Valid @RequestBody FileRelationReq req,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return BaseResult.success(baseService.bindFile(username, req));
    }

    @PostMapping("/unbind/{relationId}")
    @Operation(summary = "解绑文件", description = "逻辑删除文件与业务之间的关联")
    @AutoLog(value = "解绑文件", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> unbind(
            @PathVariable String relationId,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        baseService.unbindFile(username, relationId);
        return BaseResult.success();
    }

    @GetMapping("/biz/{bizType}/{bizId}")
    @Operation(summary = "按业务查询文件", description = "查询指定业务下的文件列表")
    public BaseResult<List<FileUploadVO>> listByBiz(
            @PathVariable String bizType,
            @PathVariable String bizId,
            HttpServletRequest request) {
        return BaseResult.success(baseService.listFilesByBizForOwner(bizType, bizId, getUsername(request)));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "查询文件详情", description = "根据文件ID查询文件元数据和访问地址")
    public BaseResult<FileUploadVO> getFileById(@PathVariable String fileId, HttpServletRequest request) {
        baseService.checkFileOwner(fileId, getUsername(request));
        return BaseResult.success(baseService.getFileById(fileId));
    }

    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文件", description = "按原始上传文件名下载文件")
    public void download(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        baseService.checkFileOwner(fileId, getUsername(request));
        FileUploadVO file = baseService.getFileById(fileId);
        String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveContentType(file.getFileType()));
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (InputStream inputStream = baseService.downloadFileContent(fileId)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    private String resolveContentType(String fileType) {
        if (fileType != null && fileType.contains("/")) {
            return fileType;
        }
        return "application/octet-stream";
    }

    private String getUsername(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}
