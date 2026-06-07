package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.BUSINESS_VIEW_STORE_TYPE;
import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.dto.DocumentAccessContext;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.service.IDocumentBusinessAccessService;
import com.lawoffice.system.service.IDocumentContentAccessService;
import com.lawoffice.system.service.IDocumentFileAccessService;
import com.lawoffice.system.service.IDocumentFileViewService;
import com.lawoffice.system.service.ISysFileMetadataService;
import com.lawoffice.system.vo.DocumentFileVO;
import com.lawoffice.util.MinioUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentContentAccessServiceImpl implements IDocumentContentAccessService {

    private final SysFilesMapper sysFilesMapper;
    private final IDocumentBusinessAccessService documentBusinessAccessService;
    private final IDocumentFileAccessService documentFileAccessService;
    private final IDocumentFileViewService documentFileViewService;
    private final ISysFileMetadataService fileMetadataService;
    private final MinioUtils minioUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO checkDocumentDownload(String fileId, DocumentAccessContext context) {
        SysFiles file = getReadableDocumentFile(fileId, context);
        if (FLAG_YES.equals(file.getIzFolder())) {
            throw new IllegalArgumentException("文件夹不能下载");
        }
        if (!documentFileAccessService.canDownload(file, context)) {
            throw new IllegalArgumentException("无权下载该文件");
        }
        file.setDownCount((file.getDownCount() == null ? 0 : file.getDownCount()) + 1);
        sysFilesMapper.updateById(file);
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentFileVO checkDocumentPreview(String fileId, DocumentAccessContext context) {
        SysFiles file = checkDocumentReadAccess(fileId, context);
        file.setReadCount((file.getReadCount() == null ? 0 : file.getReadCount()) + 1);
        sysFilesMapper.updateById(file);
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFileVO checkDocumentRead(String fileId, DocumentAccessContext context) {
        SysFiles file = checkDocumentReadAccess(fileId, context);
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFileVO checkDocumentEdit(String fileId, DocumentAccessContext context) {
        SysFiles file = checkDocumentReadAccess(fileId, context);
        if (Objects.equals(file.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("回收站中的文档不允许编辑");
        }
        assertNotBusinessReadonlyDocument(file);
        if (!documentFileAccessService.canUpdate(file, context)) {
            throw new IllegalArgumentException("无权编辑该文档");
        }
        return documentFileViewService.buildDocumentVO(file, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDocumentEdit(
            String fileId,
            DocumentAccessContext context,
            InputStream inputStream,
            String contentType,
            Long contentLength,
            boolean touchUpdateTime) {
        SysFiles file = checkDocumentReadAccess(fileId, context);
        if (Objects.equals(file.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("回收站中的文档不允许编辑");
        }
        assertNotBusinessReadonlyDocument(file);
        if (!documentFileAccessService.canUpdate(file, context)) {
            throw new IllegalArgumentException("无权保存该文档");
        }
        if (!StringUtils.hasText(file.getUrl())) {
            throw new IllegalArgumentException("文档内容不存在，无法保存");
        }
        String safeContentType = fileMetadataService.safeContentType(contentType);
        minioUtils.replaceFile(file.getUrl(), inputStream, safeContentType);
        if (contentLength != null && contentLength > 0) {
            file.setFileSize(contentLength / 1024.0);
        }
        fillUpdate(file, context.username());
        sysFilesMapper.updateById(file);
    }

    private SysFiles checkDocumentReadAccess(String fileId, DocumentAccessContext context) {
        SysFiles file = getReadableDocumentFile(fileId, context);
        if (FLAG_YES.equals(file.getIzFolder())) {
            throw new IllegalArgumentException("文件夹不能预览");
        }
        documentFileAccessService.assertCanViewDocument(file, context);
        return file;
    }

    private SysFiles getReadableDocumentFile(String fileId, DocumentAccessContext context) {
        SysFiles file = getFileIncludingDeleted(fileId, context.tenantId());
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或已删除");
        }
        if (Objects.equals(file.getDeleteFlag(), 1)) {
            assertOwner(file, context.username());
        }
        return file;
    }

    /**
     * 业务文档在文档中心只承担聚合查看入口，内容维护必须回到对应业务模块处理。
     */
    private void assertNotBusinessReadonlyDocument(SysFiles file) {
        if (file == null) {
            return;
        }
        if (BUSINESS_VIEW_STORE_TYPE.equals(file.getStoreType())
                || documentBusinessAccessService.hasActiveBusinessRelation(file.getId(), file.getTenantId())) {
            throw new IllegalArgumentException("业务文档只允许查看，请在业务模块中维护");
        }
    }

    private SysFiles getFileIncludingDeleted(String fileId, String tenantId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        return sysFilesMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getId, fileId)
                .eq(SysFiles::getTenantId, tenantId)
                .last("LIMIT 1"));
    }

    private void assertOwner(SysFiles file, String username) {
        if (!StringUtils.hasText(username) || file == null || !Objects.equals(file.getCreateBy(), username)) {
            throw new IllegalArgumentException("无权管理该文档");
        }
    }

    private void fillUpdate(SysFiles file, String username) {
        file.setUpdateBy(username);
        file.setUpdateTime(LocalDateTime.now());
    }
}
