package com.lawoffice.document.vo;

import lombok.Data;

@Data
public class DocumentFolderStatsVO {

    private Integer fileCount;

    private Integer folderCount;

    private Long totalSize;
}
