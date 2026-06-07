package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.service.IDocumentStatisticsService;
import com.lawoffice.system.vo.DocumentFolderStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentStatisticsServiceImpl implements IDocumentStatisticsService {

    private final SysFilesMapper sysFilesMapper;

    @Override
    public DocumentFolderStatsVO calculateFolderStats(SysFiles folder) {
        DocumentFolderStatsVO stats = new DocumentFolderStatsVO();
        int fileCount = 0;
        int folderCount = 0;
        long totalSize = 0L;
        List<String> cursor = List.of(folder.getId());
        int guard = 0;
        while (!cursor.isEmpty() && guard++ < 20) {
            List<SysFiles> children = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                    .eq(SysFiles::getTenantId, folder.getTenantId())
                    .eq(SysFiles::getDeleteFlag, folder.getDeleteFlag())
                    .in(SysFiles::getParentId, cursor));
            List<String> nextCursor = new ArrayList<>();
            for (SysFiles child : children) {
                if (FLAG_YES.equals(child.getIzFolder())) {
                    folderCount++;
                    if (StringUtils.hasText(child.getId())) {
                        nextCursor.add(child.getId());
                    }
                } else {
                    fileCount++;
                    totalSize += toFileSizeBytes(child);
                }
            }
            cursor = nextCursor;
        }
        stats.setFileCount(fileCount);
        stats.setFolderCount(folderCount);
        stats.setTotalSize(totalSize);
        return stats;
    }

    @Override
    public long toFileSizeBytes(SysFiles file) {
        return file.getFileSize() == null ? 0L : Math.round(file.getFileSize() * 1024);
    }

    @Override
    public String resolveDocumentPath(SysFiles file) {
        List<String> names = new ArrayList<>();
        SysFiles current = file;
        int guard = 0;
        while (current != null && guard++ < 20) {
            if (StringUtils.hasText(current.getFileName())) {
                names.add(current.getFileName());
            }
            current = StringUtils.hasText(current.getParentId())
                    ? sysFilesMapper.selectOne(Wrappers.lambdaQuery(SysFiles.class)
                            .eq(SysFiles::getId, current.getParentId())
                            .eq(SysFiles::getTenantId, file.getTenantId())
                            .last("LIMIT 1"))
                    : null;
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }
}
