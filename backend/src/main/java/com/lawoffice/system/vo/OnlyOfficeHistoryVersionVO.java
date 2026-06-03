package com.lawoffice.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OnlyOfficeHistoryVersionVO {

    private String id;

    private String fileId;

    private Integer versionNo;

    private String version;

    private String versionType;

    private String editor;

    private String editorName;

    private LocalDateTime editTime;

    private Long fileSize;

    private String remark;
}
