package com.lawoffice.system.vo;

import lombok.Data;

@Data
public class DocumentFolderStatsVO {

    private Integer fileCount;

    private Integer folderCount;

    private Long totalSize;
}
