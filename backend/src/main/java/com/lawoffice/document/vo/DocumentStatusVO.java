package com.lawoffice.document.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentStatusVO {

    private DocumentFileVO file;

    private List<DocumentShareVO> directShares = new ArrayList<>();

    private DocumentShareSourceVO accessShareSource;

    private DocumentShareSourceVO inheritedShareSource;

    private DocumentShareSourceVO favoriteSource;

    private DocumentFolderStatsVO folderStats;

    private String originalPath;

    private String deleteBy;

    private String businessBizType;

    private String businessBizId;

    private String businessModuleName;

    private String businessGroupId;

    private String businessGroupName;

    private String businessRecordName;
}
